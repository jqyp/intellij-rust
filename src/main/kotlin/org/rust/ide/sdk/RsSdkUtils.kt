/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.rust.ide.sdk.flavors.RsSdkFlavor
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.execute
import java.nio.file.Path

var Project.rustSdk: Sdk?
    get() {
        val sdk = ProjectRootManager.getInstance(this).projectSdk
        return sdk?.takeIf { it.sdkType is RsSdkType }
    }
    set(value) {
        val application = ApplicationManager.getApplication()
        application.invokeAndWait {
            application.runWriteAction {
                ProjectRootManager.getInstance(this).projectSdk = value
            }
        }
    }

object RsSdkUtils {
    private const val RUST_SDK_ID_NAME: String = "Rust SDK"

    fun isRustSdk(sdk: Sdk): Boolean = sdk.sdkType.name == RUST_SDK_ID_NAME

    fun findRustSdk(module: Module?): Sdk? {
        if (module == null) return null
        val sdk = ModuleRootManager.getInstance(module).sdk
        return if (sdk != null && isRustSdk(sdk)) sdk else null
    }

    fun getAllSdks(): List<Sdk> = ProjectJdkTable.getInstance().allJdks.filter { sdk -> isRustSdk(sdk) }

    fun findSdkByPath(path: String?): Sdk? = path?.let { findSdkByPath(getAllSdks(), it) }

    fun findSdkByPath(sdkList: List<Sdk?>, path: String?): Sdk? {
        if (path == null) return null
        for (sdk in sdkList) {
            if (sdk != null && FileUtil.pathsEqual(path, sdk.homePath)) {
                return sdk
            }
        }
        return null
    }

    fun findSdkByKey(key: String): Sdk? = ProjectJdkTable.getInstance().findJdk(key)

    fun detectRustSdks(existingSdks: List<Sdk>): List<RsDetectedSdk> =
        detectRustupSdks(existingSdks) + detectSystemWideSdks(existingSdks)

    fun detectRustupSdks(existingSdks: List<Sdk>): List<RsDetectedSdk> {
        val flavors = listOf(RustupSdkFlavor)
        return detectSdks(flavors, existingSdks)
    }

    fun detectSystemWideSdks(existingSdks: List<Sdk>): List<RsDetectedSdk> {
        val flavors = RsSdkFlavor.getApplicableFlavors().filterNot { it is RustupSdkFlavor }
        return detectSdks(flavors, existingSdks)
    }

    private fun detectSdks(flavors: List<RsSdkFlavor>, existingSdks: List<Sdk>): List<RsDetectedSdk> {
        val existingPaths = existingSdks.map { it.homePath }.toSet()
        return flavors.asSequence()
            .flatMap { it.suggestHomePaths() }
            .map { it.absolutePath }
            .filterNot { it in existingPaths }
            .map { RsDetectedSdk(it) }
            .toList()
    }

    fun isInvalid(sdk: Sdk): Boolean {
        val toolchain = sdk.homeDirectory
        return toolchain == null || !toolchain.exists()
    }

    fun listToolchains(rustup: Path): List<String> =
        GeneralCommandLine(rustup)
            .withParameters("toolchain", "list")
            .execute()
            ?.stdoutLines
            ?.map { it.removeSuffix(" (default)") }
            .orEmpty()
}
