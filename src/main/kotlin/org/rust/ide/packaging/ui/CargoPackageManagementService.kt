/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.packaging.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.CatchingConsumer
import com.intellij.webcore.packaging.InstalledPackage
import com.intellij.webcore.packaging.PackageManagementServiceEx
import com.intellij.webcore.packaging.RepoPackage
import java.lang.Exception

class CargoPackageManagementService(
    private val project: Project,
    val sdk: Sdk
) : PackageManagementServiceEx() {

    override fun reloadAllPackages(): MutableList<RepoPackage> {
        TODO("Not yet implemented")
    }

    override fun fetchPackageDetails(packageName: String, consumer: CatchingConsumer<String, Exception>) {
        TODO("Not yet implemented")
    }

    override fun uninstallPackages(installedPackages: MutableList<InstalledPackage>, listener: Listener) {
        TODO("Not yet implemented")
    }

    override fun getAllPackages(): MutableList<RepoPackage> {
        TODO("Not yet implemented")
    }

    override fun fetchLatestVersion(pkg: InstalledPackage, consumer: CatchingConsumer<String, Exception>) {
        TODO("Not yet implemented")
    }

    override fun getInstalledPackages(): MutableCollection<InstalledPackage> {
        TODO("Not yet implemented")
    }

    override fun installPackage(
        repoPackage: RepoPackage,
        version: String?,
        forceUpgrade: Boolean,
        extraOptions: String?,
        listener: Listener,
        installToUser: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun fetchPackageVersions(packageName: String, consumer: CatchingConsumer<MutableList<String>, Exception>) {
        TODO("Not yet implemented")
    }

    override fun updatePackage(installedPackage: InstalledPackage, version: String?, listener: Listener) {
        TODO("Not yet implemented")
    }
}
