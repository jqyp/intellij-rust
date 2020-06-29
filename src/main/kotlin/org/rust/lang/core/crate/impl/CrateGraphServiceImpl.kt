/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.crate.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.util.CachedValueProvider
import gnu.trove.TIntObjectHashMap
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.CargoProjectsService.CargoProjectsListener
import org.rust.cargo.project.model.CargoProjectsService.Companion.CARGO_PROJECTS_TOPIC
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CrateGraphService
import org.rust.lang.core.crate.CratePersistentId
import org.rust.openapiext.CachedValueDelegate
import org.rust.openapiext.checkReadAccessAllowed
import java.nio.file.Path
import java.util.*
import kotlin.collections.LinkedHashSet

class CrateGraphServiceImpl(val project: Project) : CrateGraphService {

    private val cargoProjectsModTracker = SimpleModificationTracker()

    init {
        project.messageBus.connect().subscribe(CARGO_PROJECTS_TOPIC, object : CargoProjectsListener {
            override fun cargoProjectsUpdated(service: CargoProjectsService, projects: Collection<CargoProject>) {
                cargoProjectsModTracker.incModificationCount()
            }
        })
    }

    private val crateGraph: CrateGraph by CachedValueDelegate {
        val result = buildCrateGraph(project, project.cargoProjects.allProjects)
        CachedValueProvider.Result(result, cargoProjectsModTracker, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
    }

    override val topSortedCrates: List<Crate>
        get() {
            checkReadAccessAllowed()
            return crateGraph.topSortedCrates
        }

    override fun findCrateById(id: CratePersistentId): Crate? {
        checkReadAccessAllowed()
        return crateGraph.idToCrate.get(id)
    }

    override fun findCrateByRootModule(rootModuleFile: VirtualFile): Crate? {
        checkReadAccessAllowed()
        return if (rootModuleFile is VirtualFileWithId) findCrateById(rootModuleFile.id) else null
    }

}

private data class CrateGraph(
    val topSortedCrates: List<Crate>,
    val idToCrate: TIntObjectHashMap<Crate>
)

private val LOG = Logger.getInstance(CrateGraphServiceImpl::class.java)

private fun buildCrateGraph(ideaProject: Project, projects: Collection<CargoProject>): CrateGraph {
    val builder = CrateGraphBuilder(ideaProject)
    for (cargoProject in projects) {
        val workspace = cargoProject.workspace ?: continue
        for (pkg in workspace.packages) {
            try {
                builder.lowerPackage(ProjectPackage(cargoProject, pkg))
            } catch (e: CyclicGraphException) {
                // This should not occur, but if it is, let's just log the exception instead of breaking everything
                LOG.error(e)
            }
        }
    }
    return builder.finish()
}

private class CrateGraphBuilder(val project: Project) {
    private val states = hashMapOf<Path, NodeState>()
    private val topSortedCrates = mutableListOf<CargoBasedCrate>()

    private val cratesToLowerLater = mutableListOf<NonLibraryCrates>()
    private val cratesToReplaceTargetLater = mutableListOf<ReplaceProjectAndTarget>()

    fun lowerPackage(pkg: ProjectPackage): CargoBasedCrate? {
        val pkgRootDirectory = pkg.pkg.rootDirectory
        when (val state = states[pkgRootDirectory]) {
            is NodeState.Done -> {
                val libCrate = state.libCrate
                if (state.pkgs.add(pkg.pkg)) {
                    // Duplicated package found. This can occur if a package is used in multiple CargoProjects.
                    // Merging them into a single crate
                    if (libCrate != null) {
                        libCrate.features = mergeFeatures(pkg.pkg.features, libCrate.features)
                    }

                    // Prefer workspace target
                    if (pkg.pkg.origin == PackageOrigin.WORKSPACE) {
                        cratesToReplaceTargetLater += ReplaceProjectAndTarget(state, pkg)
                    }
                }
                return libCrate
            }

            NodeState.Processing -> throw CyclicGraphException(pkg.pkg.name)

            else -> states[pkgRootDirectory] = NodeState.Processing
        }

        val (normalDependencies, devDependencies, buildDependencies) = pkg.pkg.dependencies.splitByKind()
        devDependencies.removeAll(normalDependencies)

        val customBuildCrate = pkg.pkg.targets.find { it.kind == CargoWorkspace.TargetKind.CustomBuild }
            ?.let { target ->
                val buildDeps = lowerDeps(buildDependencies, pkg)
                CargoBasedCrate(pkg.project, target, buildDeps, buildDeps.flattenTopSortedDeps())
            }
        customBuildCrate?.let { topSortedCrates += it }

        val cyclicDevDependencies = mutableListOf<CargoWorkspace.Dependency>()
        val normalAndNonCyclicDevDeps = lowerDeps(normalDependencies, pkg) + devDependencies.mapNotNull { dep ->
            try {
                lowerPackage(ProjectPackage(pkg.project, dep.pkg))?.let { Crate.Dependency(dep.name, it) }
            } catch (ignored: CyclicGraphException) {
                // This can occur because `dev-dependencies` can cyclic depends on this package
                CrateGraphTestmarks.cyclicDevDependency.hit()
                cyclicDevDependencies += dep
                null
            }
        }

        val flatNormalAndNonCyclicDevDeps = normalAndNonCyclicDevDeps.flattenTopSortedDeps()

        val libCrate = pkg.pkg.libTarget?.let { libTarget ->
            CargoBasedCrate(pkg.project, libTarget, normalAndNonCyclicDevDeps, flatNormalAndNonCyclicDevDeps)
        }

        val newState = NodeState.Done(libCrate)
        newState.pkgs += pkg.pkg
        customBuildCrate?.let { newState.nonLibraryCrates += it }

        states[pkgRootDirectory] = newState
        libCrate?.let { topSortedCrates += it }

        val ctx = NonLibraryCrates(
            pkg,
            newState,
            normalAndNonCyclicDevDeps,
            cyclicDevDependencies,
            flatNormalAndNonCyclicDevDeps
        )
        if (cyclicDevDependencies.isEmpty()) {
            ctx.lowerNonLibraryCrates()
        } else {
            cratesToLowerLater += ctx
        }

        return libCrate
    }

    private class ReplaceProjectAndTarget(
        val state: NodeState.Done,
        val pkg: ProjectPackage
    )

    private fun ReplaceProjectAndTarget.replaceProjectAndTarget() {
        val libCrate = state.libCrate
        if (libCrate != null) {
            pkg.pkg.libTarget?.let {
                libCrate.cargoTarget = it
                libCrate.cargoProject = pkg.project
            }
        }
        for (crate in state.nonLibraryCrates) {
            val newTarget = pkg.pkg.targets.find { it.name == crate.cargoTarget.name }
                ?: continue
            crate.cargoTarget = newTarget
            crate.cargoProject = pkg.project
        }
    }

    private data class SpittedDependencies(
        val normalDependencies: List<CargoWorkspace.Dependency>,
        val devDependencies: MutableSet<CargoWorkspace.Dependency>,
        val buildDependencies: List<CargoWorkspace.Dependency>
    )

    private fun Collection<CargoWorkspace.Dependency>.splitByKind(): SpittedDependencies {
        val normalDependencies = mutableListOf<CargoWorkspace.Dependency>()
        val devDependencies = mutableSetOf<CargoWorkspace.Dependency>()
        val buildDependencies = mutableListOf<CargoWorkspace.Dependency>()

        for (dependency in this) {
            val visitedDepKinds = EnumSet.noneOf(CargoWorkspace.DepKind::class.java)

            for (depKind in dependency.depKinds) {
                if (!visitedDepKinds.add(depKind.kind)) continue

                when (depKind.kind) {
                    CargoWorkspace.DepKind.All -> {
                        normalDependencies += dependency
                        buildDependencies += dependency
                    }
                    CargoWorkspace.DepKind.Normal -> normalDependencies += dependency
                    CargoWorkspace.DepKind.Development -> devDependencies += dependency
                    CargoWorkspace.DepKind.Build -> buildDependencies += dependency
                }
            }
        }
        return SpittedDependencies(normalDependencies, devDependencies, buildDependencies)
    }

    private class NonLibraryCrates(
        val pkg: ProjectPackage,
        val doneState: NodeState.Done,
        val normalAndNonCyclicTestDeps: List<Crate.Dependency>,
        val cyclicDevDependencies: List<CargoWorkspace.Dependency>,
        val flatNormalAndNonCyclicDevDeps: LinkedHashSet<Crate>
    )

    private fun NonLibraryCrates.lowerNonLibraryCrates() {
        val cyclicDevDeps = lowerDeps(cyclicDevDependencies, pkg)
        val normalAndTestDeps = normalAndNonCyclicTestDeps + cyclicDevDeps

        val libCrate = doneState.libCrate
        val (depsWithLib, flatDepsWithLib) = if (libCrate != null) {
            val libDep = Crate.Dependency(libCrate.normName, libCrate)
            Pair(normalAndTestDeps + libDep, LinkedHashSet<Crate>().apply { addAll(flatNormalAndNonCyclicDevDeps); add(libCrate) })
        } else {
            normalAndTestDeps to flatNormalAndNonCyclicDevDeps
        }

        val nonLibraryCrates = pkg.pkg.targets.mapNotNull { target ->
            if (target.kind.isLib || target.kind == CargoWorkspace.TargetKind.CustomBuild) return@mapNotNull null

            CargoBasedCrate(pkg.project, target, depsWithLib, flatDepsWithLib)
        }

        doneState.nonLibraryCrates += nonLibraryCrates
        topSortedCrates += nonLibraryCrates
        if (cyclicDevDeps.isNotEmpty()) {
            libCrate?.cyclicDevDeps = cyclicDevDeps
        }
    }

    private fun lowerDeps(deps: Iterable<CargoWorkspace.Dependency>, pkg: ProjectPackage): List<Crate.Dependency> {
        return try {
            deps.mapNotNull { dep ->
                lowerPackage(ProjectPackage(pkg.project, dep.pkg))?.let { Crate.Dependency(dep.name, it) }
            }
        } catch (e: CyclicGraphException) {
            states.remove(pkg.pkg.rootDirectory)
            e.pushCrate(pkg.pkg.name)
            throw e
        }
    }

    fun finish(): CrateGraph {
        for (ctx in cratesToLowerLater) {
            ctx.lowerNonLibraryCrates()
        }
        for (ctx in cratesToReplaceTargetLater) {
            ctx.replaceProjectAndTarget()
        }

        topSortedCrates.assertTopSorted()

        val idToCrate = TIntObjectHashMap<Crate>()
        for (crate in topSortedCrates) {
            crate.flatDependencies.assertTopSorted()
            val id = crate.id
            if (id != null) {
                idToCrate.put(id, crate)
            }
        }
        return CrateGraph(topSortedCrates, idToCrate)
    }
}

private fun Iterable<Crate>.assertTopSorted() {
    if (!isUnitTestMode) return
    val set = hashSetOf<Crate>()
    for (crate in this) {
        check(crate.dependencies.all { it.crate in set })
        set += crate
    }
}

private fun mergeFeatures(
    features1: Collection<CargoWorkspace.Feature>,
    features2: Collection<CargoWorkspace.Feature>
): Collection<CargoWorkspace.Feature> {
    val featureMap = features1.associateTo(hashMapOf()) { it.name to it.state }
    for ((k, v) in features2) {
        featureMap.merge(k, v) { v1, v2 ->
            when {
                v1 == CargoWorkspace.FeatureState.Enabled -> CargoWorkspace.FeatureState.Enabled
                v2 == CargoWorkspace.FeatureState.Enabled -> CargoWorkspace.FeatureState.Enabled
                else -> CargoWorkspace.FeatureState.Disabled
            }
        }
    }
    return featureMap.entries.map { (k, v) -> CargoWorkspace.Feature(k, v) }
}

private data class ProjectPackage(val project: CargoProject, val pkg: CargoWorkspace.Package)

private sealed class NodeState {
    data class Done(
        val libCrate: CargoBasedCrate?,
        val nonLibraryCrates: MutableList<CargoBasedCrate> = mutableListOf(),
        val pkgs: MutableSet<CargoWorkspace.Package> = hashSetOf()
    ) : NodeState()

    object Processing : NodeState()
}

private class CyclicGraphException(crateName: String) : RuntimeException("Cyclic graph detected") {
    private val stack: MutableList<String> = mutableListOf(crateName)

    fun pushCrate(crateName: String) {
        stack += crateName
    }

    override val message: String?
        get() = super.message + stack.asReversed().joinToString(prefix = " (", separator = " -> ", postfix = ")")
}

