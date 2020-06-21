/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.packaging.ui

import com.intellij.openapi.project.Project
import com.intellij.webcore.packaging.InstalledPackage
import com.intellij.webcore.packaging.InstalledPackagesPanel
import com.intellij.webcore.packaging.PackagesNotificationPanel

class CargoInstalledPackagesPanel(
    project: Project,
    area: PackagesNotificationPanel
) : InstalledPackagesPanel(project, area) {
    override fun installEnabled(): Boolean = false
    override fun canInstallPackage(pyPackage: InstalledPackage): Boolean = false
}
