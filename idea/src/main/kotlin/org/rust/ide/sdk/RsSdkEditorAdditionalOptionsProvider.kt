/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.SdkEditorAdditionalOptionsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.layout.panel
import javax.swing.JComponent

class RsSdkEditorAdditionalOptionsProvider : SdkEditorAdditionalOptionsProvider(RsSdkType.getInstance()) {

    override fun createOptions(project: Project, sdk: Sdk): AdditionalDataConfigurable =
        RsSdkOptionsAdditionalDataConfigurable(project, sdk)

    private class RsSdkOptionsAdditionalDataConfigurable(
        private val project: Project,
        private var sdk: Sdk
    ) : AdditionalDataConfigurable {

        override fun setSdk(sdk: Sdk) {
            this.sdk = sdk
        }

        override fun createComponent(): JComponent = panel {
            row("Hello, World") {}
        }

        override fun isModified(): Boolean = false

        override fun apply() {}

        override fun reset() {}
    }
}
