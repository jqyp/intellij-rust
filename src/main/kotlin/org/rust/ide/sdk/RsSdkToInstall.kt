/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rust.ide.sdk

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.io.HttpRequests
import com.intellij.webcore.packaging.PackageManagementService
import com.intellij.webcore.packaging.PackagesNotificationPanel
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.rust.ide.sdk.RsSdkUtils.resetRustupSdksDetectors
import java.io.File
import java.io.IOException

private val LOG: Logger = Logger.getInstance(RsSdkToInstall::class.java)

@CalledInAny
internal fun getRustupToInstall(): RsSdkToInstall? =
    if (SystemInfo.isWindows) getRustupToInstallOnWindows() else null

private fun getRustupToInstallOnWindows(): RsSdkToInstallOnWindows {
    val version = "1.21.1"
    val target = "pc-windows-gnu"
    val name = "rustup $version"
    val hashFunction = Hashing.md5()

    return if (SystemInfo.is32Bit) {
        RsSdkToInstallOnWindows(
            name,
            version,
            "https://static.rust-lang.org/rustup/archive/$version/x86_64-$target/rustup-init.exe",
            "75ad07c91de9cb689f033569a3274184",
            hashFunction,
            "rustup-init.exe"
        )
    } else {
        RsSdkToInstallOnWindows(
            name,
            version,
            "https://static.rust-lang.org/rustup/archive/$version/i686-$target/rustup-init.exe",
            "7a8dfde55036f74f599d6a71fa502e0c",
            hashFunction,
            "rustup-init.exe"
        )
    }
}

internal abstract class RsSdkToInstall internal constructor(name: String, version: String)
    : ProjectJdkImpl(name, RsSdkType.getInstance(), null, version) {

    @CalledInAny
    abstract fun renderInList(renderer: RsSdkListCellRenderer)

    @CalledInAny
    abstract fun getInstallationWarning(defaultButtonName: String): String

    @CalledInAwt
    abstract fun install(module: Module?, sdksDetector: () -> List<RsDetectedSdk>): RsDetectedSdk?
}

private class RsSdkToInstallOnWindows(
    name: String,
    version: String,
    private val url: String,
    private val hash: String,
    private val hashFunction: HashFunction,
    private val targetFileName: String
) : RsSdkToInstall(name, version) {

    override fun renderInList(renderer: RsSdkListCellRenderer) {
        renderer.append(name)
        renderer.append(" $url", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        renderer.icon = AllIcons.Actions.Download
    }

    override fun getInstallationWarning(defaultButtonName: String): String {
        val header = "Rustup executable is not found. Choose one of the following options:"

        val browseButtonName = "..." // ComponentWithBrowseButton
        val firstOption = "Click <strong>$browseButtonName</strong> to specify a path to rustup.exe in your file system"

        val secondOption = "Click <strong>$defaultButtonName</strong> to download and install rustup from https://rustup.rs)"

        return "$header<ul><li>$firstOption</li><li>$secondOption</li></ul>"
    }

    override fun install(module: Module?, sdksDetector: () -> List<RsDetectedSdk>): RsDetectedSdk? {
        try {
            return ProgressManager.getInstance().run(
                object : Task.WithResult<RsDetectedSdk?, Exception>(module?.project, "Installing $name", true) {
                    override fun compute(indicator: ProgressIndicator): RsDetectedSdk? = install(sdksDetector, indicator)
                }
            )
        } catch (e: IOException) {
            handleIOException(e)
        } catch (e: RsInstallationExecutionException) {
            handleExecutionException(e)
        } catch (e: RsInstallationException) {
            handleInstallationException(e)
        }

        return null
    }

    private fun install(
        rustupSdksDetector: () -> List<RsDetectedSdk>,
        indicator: ProgressIndicator
    ): RsDetectedSdk? {
        val targetFile = File(PathManager.getTempPath(), targetFileName)

        try {
            indicator.text = "Downloading $targetFileName"
            if (indicator.isCanceled) return null
            downloadInstaller(targetFile, indicator)
            if (indicator.isCanceled) return null
            checkInstallerConsistency(targetFile)

            indicator.text = "Running $targetFileName"
            indicator.text2 = "Windows may require your approval to install rustup. Please check the taskbar."
            indicator.isIndeterminate = true
            if (indicator.isCanceled) return null
            runInstaller(targetFile, indicator)

            return findInstalledSdk(rustupSdksDetector)
        } finally {
            FileUtil.delete(targetFile)
        }
    }

    private fun downloadInstaller(targetFile: File, indicator: ProgressIndicator) {
        LOG.info("Downloading $url to $targetFile")

        return try {
            HttpRequests.request(url).saveToFile(targetFile, indicator)
        } catch (e: IOException) {
            throw IOException("Failed to download $url to $targetFile.", e)
        }
    }

    private fun checkInstallerConsistency(installer: File) {
        LOG.debug("Checking installer checksum")
        val actualHashCode = Files.asByteSource(installer).hash(hashFunction).toString()
        if (!actualHashCode.equals(hash, ignoreCase = true)) {
            throw IOException("Checksums for $installer does not match. Actual value is $actualHashCode, expected $hash.")
        }
    }

    private fun handleIOException(e: IOException) {
        LOG.info(e)

        val message = e.message ?: return
        PackagesNotificationPanel.showError(
            "Failed to install $name",
            PackageManagementService.ErrorDescription(
                message,
                null,
                e.cause?.message,
                "Try to install rustup from https://rustup.rs manually."
            )
        )
    }

    private fun runInstaller(installer: File, indicator: ProgressIndicator) {
        val commandLine = GeneralCommandLine(installer.absolutePath, "-y")
        LOG.info("Running ${commandLine.commandLineString}")

        val output = runInstaller(commandLine, indicator)
        if (output.exitCode != 0 || output.isTimeout) throw RsInstallationException(commandLine, output)
    }

    private fun handleInstallationException(e: RsInstallationException) {
        val processOutput = e.output
        processOutput.checkSuccess(LOG)

        if (processOutput.isCancelled) {
            PackagesNotificationPanel.showError(
                "$name Installation Has Been Cancelled",
                PackageManagementService.ErrorDescription(
                    "Some rustup components that have been installed might get inconsistent after cancellation.",
                    e.commandLine.commandLineString,
                    listOf(processOutput.stderr, processOutput.stdout).firstOrNull { it.isNotBlank() },
                    "Consider installing rustup from https://rustup.rs manually."
                )
            )
        } else {
            PackagesNotificationPanel.showError(
                "Failed to install $name",
                PackageManagementService.ErrorDescription(
                    if (processOutput.isTimeout) "Timed out" else "Exit code ${processOutput.exitCode}",
                    e.commandLine.commandLineString,
                    listOf(processOutput.stderr, processOutput.stdout).firstOrNull { it.isNotBlank() },
                    "Try to install rustup from https://rustup.rs manually."
                )
            )
        }
    }

    private fun runInstaller(commandLine: GeneralCommandLine, indicator: ProgressIndicator): ProcessOutput =
        try {
            CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator)
        } catch (e: ExecutionException) {
            throw RsInstallationExecutionException(commandLine, e)
        }

    private fun handleExecutionException(e: RsInstallationExecutionException) {
        LOG.info(e)

        val message = e.cause.message ?: return
        PackagesNotificationPanel.showError(
            "Failed To Install $name",
            PackageManagementService.ErrorDescription(
                message,
                e.commandLine.commandLineString,
                null,
                "Try to install rustup from https://rustup.rs manually."
            )
        )
    }

    private fun findInstalledSdk(rustupSdksDetector: () -> List<RsDetectedSdk>): RsDetectedSdk? {
        LOG.debug("Resetting rustup sdks detectors")
        resetRustupSdksDetectors()

        return rustupSdksDetector()
            .also { sdks ->
                LOG.debug { sdks.joinToString(prefix = "Detected rustup sdks: ") { it.homePath ?: it.name } }
            }
            .singleOrNull()
    }

    private class RsInstallationException(
        val commandLine: GeneralCommandLine,
        val output: ProcessOutput
    ) : Exception()

    private class RsInstallationExecutionException(
        val commandLine: GeneralCommandLine,
        override val cause: ExecutionException
    ) : Exception()
}
