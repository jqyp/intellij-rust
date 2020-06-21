/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor
import com.intellij.ui.ComboboxWithBrowseButton
import javax.swing.DefaultComboBoxModel

class RsSdkComboBox : ComboboxWithBrowseButton() {
    var project: Project? = null
    val selectedSdk: Sdk?
        get() = comboBox.selectedItem as? Sdk

    init {
        comboBox.renderer = RsSdkListCellRenderer(null)
        addActionListener {
            var selectedSdk: Sdk? = selectedSdk
            val project = project ?: ProjectManager.getInstance().defaultProject
            val editor = ProjectJdksEditor(selectedSdk, project, this@RsSdkComboBox)
            if (editor.showAndGet()) {
                selectedSdk = editor.selectedJdk
                updateSdkList(selectedSdk, false)
            }
        }
        updateSdkList(null, true)
    }

    fun updateSdkList(
        sdkToSelect: Sdk? = comboBox.selectedItem as? Sdk,
        selectAnySdk: Boolean = false
    ) {
        var sdkToSelect = sdkToSelect
        val sdkList = ProjectJdkTable.getInstance().getSdksOfType(RsSdkType.getInstance())
        if (selectAnySdk && sdkList.isNotEmpty()) {
            sdkToSelect = sdkList.first()
        }
        sdkList.add(0, null)
        comboBox.model = DefaultComboBoxModel(sdkList.toTypedArray())
        comboBox.selectedItem = sdkToSelect
    }
}
