/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isUnitTestMode
import org.rust.cargo.toolchain.RustcVersion
import org.rust.cargo.toolchain.RustcVersion.Companion.scrapeRustcVersion
import org.rust.ide.icons.RsIcons
import org.rust.ide.sdk.RsSdkAdditionalData
import org.rust.lang.RsConstants.CARGO
import org.rust.lang.RsConstants.RUSTC
import org.rust.openapiext.checkIsBackgroundThread
import org.rust.stdext.toPath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon

interface RsSdkFlavor {
    val name: String get() = "Rust"
    val icon: Icon get() = RsIcons.RUST

    fun suggestHomePaths(): Sequence<File> = getHomePathCandidates().distinct().filter { isValidSdkHome(it) }

    fun getHomePathCandidates(): Sequence<File>

    /**
     * Flavor is added to result in [getApplicableFlavors] if this method returns true.
     * @return whether this flavor is applicable
     */
    fun isApplicable(): Boolean = true

    fun isValidSdkHome(sdkHome: File): Boolean = sdkHome.hasExecutable(RUSTC) && sdkHome.hasExecutable(CARGO)

    fun getSdkPath(path: VirtualFile?): VirtualFile? = path

    companion object {
        @JvmField
        val EP_NAME: ExtensionPointName<RsSdkFlavor> = ExtensionPointName.create("org.rust.sdkFlavor")

        fun getApplicableFlavors(): List<RsSdkFlavor> = EP_NAME.extensionList.filter { it.isApplicable() }

        fun getFlavor(sdk: Sdk): RsSdkFlavor? {
            val flavor = (sdk.sdkAdditionalData as? RsSdkAdditionalData)?.flavor
            if (flavor != null) return flavor
            return getFlavor(sdk.homePath)
        }

        fun getFlavor(sdkPath: String?): RsSdkFlavor? {
            if (sdkPath == null) return null
            val file = File(sdkPath)
            return getApplicableFlavors().find { flavor -> flavor.isValidSdkHome(file) }
        }

        @JvmStatic
        fun File.hasExecutable(toolName: String): Boolean =
            Files.isExecutable(pathToExecutable(toolName))

        private fun File.pathToExecutable(toolName: String): Path {
            val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
            return resolve("bin/$exeName").absolutePath.toPath()
        }
    }
}
