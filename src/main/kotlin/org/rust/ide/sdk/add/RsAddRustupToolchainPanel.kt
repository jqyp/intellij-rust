/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.panel
import org.rust.ide.sdk.*
import org.rust.ide.sdk.RsSdkUtils.detectRustupSdks
import org.rust.ide.sdk.RsSdkUtils.listToolchains
import org.rust.openapiext.UiDebouncer
import org.rust.stdext.toPath
import java.awt.BorderLayout

class RsAddRustupToolchainPanel(private val existingSdks: List<Sdk>) : RsAddSdkPanel() {
    override val panelName: String = "Rustup toolchain"
    private val sdkComboBox: RsSdkPathChoosingComboBox = RsSdkPathChoosingComboBox()
    private val toolchainComboBox: ComboBox<String> = ComboBox()
    private val toolchainUpdateDebouncer: UiDebouncer = UiDebouncer(this)

    init {
        layout = BorderLayout()

        val formPanel = panel {
            row("Rustup executable:") { sdkComboBox() }
            row("Toolchain:") { toolchainComboBox() }
        }
        add(formPanel, BorderLayout.NORTH)

        addToolchainsAsync(sdkComboBox) {
            val detectedSdks = detectRustupSdks(existingSdks)
            detectedSdks.takeIf { it.isNotEmpty() || existingSdks.any { sdk -> sdk.sdkType is RsSdkType } }
                ?: getSdksToInstall()
        }

        addChangeListener(Runnable(::update))
    }

    override fun validateAll(): List<ValidationInfo> = listOfNotNull(validateSdkComboBox(sdkComboBox))

    override fun getOrCreateSdk(): Sdk? {
        val sdk = when (val sdk = sdkComboBox.selectedSdk) {
            is RsSdkToInstall -> sdk.install(null) { detectRustupSdks(existingSdks) }?.setup(existingSdks)
            is RsDetectedSdk -> sdk.setup(existingSdks)
            else -> sdk
        }
        val additionalData = sdk?.sdkAdditionalData as? RsSdkAdditionalData
        additionalData?.toolchainName = toolchainComboBox.selectedItem as? String
        return sdk
    }

    override fun addChangeListener(listener: Runnable) {
        sdkComboBox.childComponent.addItemListener { listener.run() }
    }

    private fun update() {
        val sdk = sdkComboBox.selectedSdk
        toolchainUpdateDebouncer.run(
            onPooledThread = {
                val rustupPath = sdk?.homePath?.toPath()
                if (rustupPath != null) {
                    listToolchains(rustupPath)
                } else {
                    emptyList()
                }
            },
            onUiThread = { toolchains ->
                toolchainComboBox.removeAllItems()
                toolchains.forEach { toolchainComboBox.addItem(it) }
                toolchainComboBox.isEnabled = toolchains.isNotEmpty()
            }
        )
    }
}
