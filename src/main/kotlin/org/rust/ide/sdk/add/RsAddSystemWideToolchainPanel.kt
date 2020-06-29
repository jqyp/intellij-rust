/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.FormBuilder
import org.rust.ide.sdk.RsDetectedSdk
import org.rust.ide.sdk.RsSdkAdditionalData
import org.rust.ide.sdk.RsSdkUtils.detectSystemWideSdks
import java.awt.BorderLayout

class RsAddSystemWideToolchainPanel(private val existingSdks: List<Sdk>) : RsAddSdkPanel() {
    override val panelName: String = "System toolchain"
    private val sdkComboBox: RsSdkPathChoosingComboBox = RsSdkPathChoosingComboBox()

    init {
        layout = BorderLayout()

        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Toolchain path:", sdkComboBox)
            .panel
        add(formPanel, BorderLayout.NORTH)
        addToolchainsAsync(sdkComboBox) { detectSystemWideSdks(existingSdks) }
    }

    override fun validateAll(): List<ValidationInfo> = listOfNotNull(validateSdkComboBox(sdkComboBox))

    override fun getOrCreateSdk(): Sdk? {
        val sdk = when (val sdk = sdkComboBox.selectedSdk) {
            is RsDetectedSdk -> sdk.setup(existingSdks)
            else -> sdk
        }

        val additionalData = sdk?.sdkAdditionalData as? RsSdkAdditionalData
        additionalData?.toolchainPath = sdk?.homePath

        return sdk
    }

    override fun addChangeListener(listener: Runnable) {
        sdkComboBox.childComponent.addItemListener { listener.run() }
    }
}
