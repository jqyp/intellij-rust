/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.application.options.ModuleAwareProjectConfigurable
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project

open class RsActiveSdkModuleConfigurable(project: Project)
    : ModuleAwareProjectConfigurable<UnnamedConfigurable>(project, "Project Toolchain", HELP_TOPIC) {

    override fun createDefaultProjectConfigurable(): UnnamedConfigurable = RsActiveSdkConfigurable(project)

    override fun createModuleConfigurable(module: Module): UnnamedConfigurable = RsActiveSdkConfigurable(module)

    companion object {
        private const val HELP_TOPIC: String = "reference.settings.project.interpreter"
    }
}
