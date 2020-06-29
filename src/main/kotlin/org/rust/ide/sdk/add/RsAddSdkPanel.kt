/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import org.rust.ide.icons.RsIcons
import java.awt.Component
import javax.swing.Icon
import javax.swing.JPanel

abstract class RsAddSdkPanel : JPanel(), RsAddSdkView {
    abstract override val panelName: String
    override val icon: Icon = RsIcons.RUST

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

    override fun onSelected() {}

    override fun validateAll(): List<ValidationInfo> = emptyList()

    open fun addChangeListener(listener: Runnable) {}

    companion object {

        @JvmStatic
        protected fun validateSdkComboBox(field: RsSdkPathChoosingComboBox): ValidationInfo? =
            if (field.selectedSdk == null) ValidationInfo("SDK field is empty", field) else null

        /**
         * Obtains a list of sdk on a pool using [sdkObtainer], then fills [sdkComboBox] on the EDT.
         */
        @JvmStatic
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
