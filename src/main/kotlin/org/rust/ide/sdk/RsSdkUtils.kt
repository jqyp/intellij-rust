/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.cargo.toolchain.Rustup.Companion.listToolchains
import org.rust.ide.sdk.flavors.RsSdkFlavor
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

val Sdk.toolchain: RustToolchain?
    get() = (sdkAdditionalData as? RsSdkAdditionalData)?.toolchain

fun Sdk.rustup(projectDirectory: Path): Rustup? =
    if (isRustupAvailable) {
        (sdkAdditionalData as? RsSdkAdditionalData)?.rustup(projectDirectory)
    } else {
        null
    }

val Sdk.isRustupAvailable: Boolean
    get() {
        val homePath = homePath?.let(::File) ?: return false
        return RustupSdkFlavor.isValidSdkHome(homePath)
    }

object RsSdkUtils {

    fun findOrCreateSdk(): Sdk? {
        val allSdks = ProjectJdkTable.getInstance().allJdks.toList()
        val existingSdk = allSdks.find { it.sdkType is RsSdkType }
        if (existingSdk != null) return existingSdk

        val detectedSdk = detectRustSdks(allSdks).firstOrNull() ?: return null
        val sdk = detectedSdk.setup(allSdks) ?: return null
        val homePath = sdk.homePath ?: return null

        val data = sdk.sdkAdditionalData as? RsSdkAdditionalData ?: return null
        if (data.flavor is RustupSdkFlavor) {
            val toolchain = listToolchains(Paths.get(homePath)).find { it.isDefault } ?: return null
            data.toolchainName = toolchain.name
            data.toolchainPath = toolchain.path
            data.rustupPath = homePath
        } else {
            data.toolchainPath = homePath
        }

        return sdk
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
            .distinct()
            .filterNot { it in existingPaths }
            .map { RsDetectedSdk(it) }
            .toList()
    }

    fun isInvalid(sdk: Sdk): Boolean {
        val toolchain = sdk.homeDirectory
        return toolchain == null || !toolchain.exists()
    }
}
