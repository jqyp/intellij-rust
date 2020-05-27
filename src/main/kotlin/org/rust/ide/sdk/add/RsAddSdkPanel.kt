/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.CommonBundle
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import org.rust.ide.sdk.RsSdkToInstall
import java.awt.Component
import javax.swing.JPanel

abstract class RsAddSdkPanel : JPanel(), RsAddSdkView {
    abstract override val panelName: String
    open val sdk: Sdk? = null
    open var newProjectPath: String? = null

    override val actions: Map<RsAddSdkDialogFlowAction, Boolean>
        get() = mapOf(RsAddSdkDialogFlowAction.OK.enabled())

    override val component: Component
        get() = this

    /**
     * [component] is permanent. [RsAddSdkStateListener.onComponentChanged] won't
     * be called anyway.
     */
    override fun addStateListener(stateListener: RsAddSdkStateListener): Unit = Unit

    override fun previous(): Nothing = throw UnsupportedOperationException()

    override fun next(): Nothing = throw UnsupportedOperationException()

    override fun complete() {}

    override fun getOrCreateSdk(): Sdk? = sdk

    override fun onSelected() {}

    override fun validateAll(): List<ValidationInfo> = emptyList()

    open fun addChangeListener(listener: Runnable) {}

    companion object {

        protected fun validateSdkComboBox(field: RsSdkPathChoosingComboBox): ValidationInfo? =
            when (val sdk = field.selectedSdk) {
                null -> ValidationInfo("Toolchain field is empty", field)
                is RsSdkToInstall -> {
                    val message = sdk.getInstallationWarning(CommonBundle.getOkButtonText())
                    ValidationInfo(message).asWarning().withOKEnabled()
                }
                else -> null
            }

        /**
         * Obtains a list of sdk on a pool using [sdkObtainer], then fills [sdkComboBox] on the EDT.
         */
        @Suppress("UnstableApiUsage")
        protected fun addToolchainsAsync(sdkComboBox: RsSdkPathChoosingComboBox, sdkObtainer: () -> List<Sdk>) {
            ApplicationManager.getApplication().executeOnPooledThread {
                val executor = AppUIExecutor.onUiThread(ModalityState.any())
                executor.execute { sdkComboBox.setBusy(true) }
                var sdks = emptyList<Sdk>()
                try {
                    sdks = sdkObtainer()
                } finally {
                    executor.execute {
                        sdkComboBox.setBusy(false)
                        sdks.forEach(sdkComboBox.childComponent::addItem)
                    }
                }
            }
        }
    }
}
