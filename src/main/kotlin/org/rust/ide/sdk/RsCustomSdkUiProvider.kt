/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import java.awt.GridBagConstraints
import javax.swing.JPanel

abstract class RsCustomSdkUiProvider {

    abstract fun customizeActiveSdkPanel(
        project: Project,
        mySdkCombo: ComboBox<*>,
        myMainPanel: JPanel,
        c: GridBagConstraints,
        disposable: Disposable
    )

    companion object {
        @JvmField
        val EP_NAME: ExtensionPointName<RsCustomSdkUiProvider> = ExtensionPointName.create("org.rust.customSdkUiProvider")

        fun getInstance(): RsCustomSdkUiProvider? = EP_NAME.extensionList.firstOrNull()
    }
}
