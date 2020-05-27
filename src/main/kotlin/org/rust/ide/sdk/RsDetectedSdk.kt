/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil

class RsDetectedSdk(name: String) : ProjectJdkImpl(name, RsSdkType.getInstance()) {

    init {
        homePath = name
    }

    override fun getVersionString(): String? = ""

    fun setup(existingSdks: List<Sdk>): Sdk? {
        return SdkConfigurationUtil.setupSdk(
            existingSdks.toTypedArray(),
            homeDirectory ?: return null,
            RsSdkType.getInstance(),
            false,
            null,
            null
        )
    }
}
