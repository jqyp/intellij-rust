/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.rust.lang.RsConstants.RUSTUP
import java.io.File

object RustupSdkFlavor : RsSdkFlavor {
    override val name: String = "Rustup"

    override fun getHomePathCandidates(): Sequence<File> {
        val rustupName = if (SystemInfo.isWindows) "$RUSTUP.exe" else RUSTUP
        return sequenceOf(FileUtil.expandUserHome("~/.cargo/bin/$rustupName")).map(::File)
    }

    override fun isValidSdkHome(sdkHome: File): Boolean =
        sdkHome.nameWithoutExtension.toLowerCase() == RUSTUP && sdkHome.canExecute()
}
