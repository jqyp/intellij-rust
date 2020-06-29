/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isUnitTestMode
import org.rust.cargo.CargoConstants.XARGO_MANIFEST_FILE
import org.rust.cargo.toolchain.RustcVersion.Companion.scrapeRustcVersion
import org.rust.ide.sdk.flavors.RsSdkFlavor
import org.rust.openapiext.*
import java.nio.file.Files
import java.nio.file.Path

data class RustToolchain(val location: Path) {

    fun looksLikeValidToolchain(): Boolean =
        hasExecutable(CARGO) && hasExecutable(RUSTC)

    fun queryVersions(): VersionInfo {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        return VersionInfo(scrapeRustcVersion(pathToExecutable(RUSTC)))
    }

    fun getSysroot(projectDirectory: Path): String? {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val timeoutMs = 10000
        val output = GeneralCommandLine(pathToExecutable(RUSTC))
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "sysroot")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdout.trim() else null
    }

    fun getStdlibFromSysroot(projectDirectory: Path): VirtualFile? {
        val sysroot = getSysroot(projectDirectory) ?: return null
        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(FileUtil.join(sysroot, "lib/rustlib/src/rust/src"))
    }

    fun getCfgOptions(projectDirectory: Path): List<String>? {
        val timeoutMs = 10000
        val output = GeneralCommandLine(pathToExecutable(RUSTC))
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "cfg")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdoutLines else null
    }

    fun rawCargo(): Cargo = Cargo(pathToExecutable(CARGO))

    fun cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
        val hasXargoToml = cargoProjectDirectory?.resolve(XARGO_MANIFEST_FILE)?.let { Files.isRegularFile(it) } == true
        val cargoWrapper = if (hasXargoToml && hasExecutable(XARGO)) XARGO else CARGO
        return Cargo(pathToExecutable(cargoWrapper))
    }

    fun rustfmt(): Rustfmt = Rustfmt(pathToExecutable(RUSTFMT))

    fun grcov(): Grcov? = if (hasExecutable(GRCOV)) Grcov(pathToExecutable(GRCOV)) else null

    fun evcxr(): Evcxr? = if (hasExecutable(EVCXR)) Evcxr(pathToExecutable(EVCXR)) else null

    private fun pathToExecutable(toolName: String): Path {
        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        return location.resolve("bin/$exeName").toAbsolutePath()
    }

    private fun hasExecutable(exec: String): Boolean =
        Files.isExecutable(pathToExecutable(exec))

    data class VersionInfo(
        val rustc: RustcVersion?
    )

    companion object {
        private const val RUSTC = "rustc"
        private const val RUSTFMT = "rustfmt"
        private const val CARGO = "cargo"
        private const val XARGO = "xargo"
        private const val GRCOV = "grcov"
        private const val EVCXR = "evcxr"

        fun suggest(): RustToolchain? = RsSdkFlavor.getApplicableFlavors()
            .asSequence()
            .flatMap { it.suggestHomePaths() }
            .mapNotNull { home ->
                val homePath = home.toPath().toAbsolutePath()
                val candidate = RustToolchain(homePath)
                candidate.takeIf { it.looksLikeValidToolchain() }
            }.firstOrNull()
    }
}
