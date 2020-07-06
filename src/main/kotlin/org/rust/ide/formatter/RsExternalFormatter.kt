/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoConstants
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.ExternalFormatProcessor
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.Rustup.Companion.checkNeedInstallRustfmt
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.document

@Suppress("UnstableApiUsage")
class RsExternalFormatter : ExternalFormatProcessor {
    override fun getId(): String = "rustfmt"

    override fun activeForFile(source: PsiFile): Boolean =
        source.project.rustSettings.useRustfmtByDefault && source is RsFile

    override fun format(
        source: PsiFile,
        range: TextRange,
        canChangeWhiteSpacesOnly: Boolean,
        keepLineBreaks: Boolean
    ): TextRange? {
        if (source !is RsFile) return null
        val file = source.virtualFile ?: return null
        val document = file.document ?: return null
        val project = source.project
        val cargoProject = project.cargoProjects.findProjectForFile(file) ?: return null
        val rustfmt = project.toolchain?.rustfmt() ?: return null

        val before = document.modificationStamp
        val application = ApplicationManager.getApplication()
        application.executeOnPooledThread {
            if (checkNeedInstallRustfmt(project, cargoProject.workingDirectory)) return@executeOnPooledThread
            val output = rustfmt.reformatFileText(cargoProject, file) ?: return@executeOnPooledThread
            if (output.exitCode == 0) {
                val text = output.stdout
                application.invokeLater {
                    val after = document.modificationStamp
                    if (after > before) return@invokeLater
                    CommandProcessor.getInstance().executeCommand(project, {
                        application.runWriteAction {
                            document.setText(text)
                        }
                        file.putUserData(UndoConstants.FORCE_RECORD_UNDO, null)
                    }, "Reformat Code with $id", null, document)
                }
            } else {
                LOG.debug(output.stderr)
            }
        }

        return range
    }

    override fun indent(source: PsiFile, lineStartOffset: Int): String? = null

    companion object {
        private val LOG: Logger = Logger.getInstance(RsExternalFormatter::class.java)
    }
}
