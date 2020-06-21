/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.idea

import com.intellij.execution.ExecutionException
import com.intellij.ide.util.projectWizard.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import org.rust.cargo.CargoConstants
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.newProject.ConfigurationData
import org.rust.ide.newProject.RsPackageNameValidator
import org.rust.ide.sdk.RsSdkType

/**
 * Builder which is used when a new project or module is created and not imported from source.
 */
class RsModuleBuilder : ModuleBuilder() {
    private var sdk: Sdk? = null

    override fun getModuleType(): ModuleType<*>? = RsModuleType.INSTANCE

    override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean = sdkType is RsSdkType

    override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep =
        CargoConfigurationWizardStep.newProject(context).apply {
            Disposer.register(parentDisposable, Disposable { this.disposeUIResources() })
        }

    override fun modifyProjectTypeStep(settingsStep: SettingsStep): ModuleWizardStep =
        object : SdkSettingsStep(settingsStep, this, Condition { it === RsSdkType.getInstance() }) {
            override fun onSdkSelected(sdk: Sdk?) {
                this@RsModuleBuilder.sdk = sdk
            }
        }

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        val root = doAddContentEntry(modifiableRootModel)?.file ?: return
        modifiableRootModel.inheritSdk()
        val toolchain = configurationData?.settings?.toolchain
        root.refresh(/* async = */ false, /* recursive = */ true)

        // Just work if user "creates new project" over an existing one.
        if (toolchain != null && root.findChild(CargoConstants.MANIFEST_FILE) == null) {
            try {
                toolchain.rawCargo().init(
                    modifiableRootModel.project,
                    modifiableRootModel.module,
                    root,
                    configurationData?.createBinary ?: true
                )
            } catch (e: ExecutionException) {
                LOG.error(e)
                throw ConfigurationException(e.message)
            }
        }
    }

    @Throws(ConfigurationException::class)
    override fun validateModuleName(moduleName: String): Boolean {
        val isBinary = configurationData?.createBinary == true
        val errorMessage = RsPackageNameValidator.validate(moduleName, isBinary) ?: return true
        throw ConfigurationException(errorMessage)
    }

    var configurationData: ConfigurationData? = null

    companion object {
        private val LOG = Logger.getInstance(RsModuleBuilder::class.java)
    }
}
