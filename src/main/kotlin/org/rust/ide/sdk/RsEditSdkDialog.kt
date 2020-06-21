/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import org.rust.ide.ui.layout
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class RsEditSdkDialog(
    project: Project,
    sdk: SdkModificator,
    nameValidator: (String) -> String?
) : DialogWrapper(project, true) {
//    private val sdkSettings: RsSdkSettingsPanel = RsSdkSettingsPanel()

    private val nameTextField: JTextField = JTextField().apply {
        text = sdk.name
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val nameError = nameValidator(name)
                setErrorText(nameError, this@apply)
                isOKActionEnabled = nameError == null
            }
        })
    }

    private val toolchainPathTextField: TextFieldWithBrowseButton = TextFieldWithBrowseButton().apply {
        text = sdk.homePath
        addBrowseFolderListener("Specify Toolchain Path", null, project, RsSdkType.getInstance().homeChooserDescriptor)
    }

    val name: String
        get() = nameTextField.text

    val homePath: String
        get() = toolchainPathTextField.text

    init {
        title = "Edit Rust Toolchain"
        init()
    }

    override fun createCenterPanel(): JComponent = layout {
//        sdkSettings.attachTo(this)
    }

    override fun getPreferredFocusedComponent(): JComponent = nameTextField
}
