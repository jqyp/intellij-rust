/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import java.io.File

class WinSdkFlavor private constructor() : RsSdkFlavor {

    override fun getHomePathCandidates(): Sequence<File> {
        val fromHome = File(FileUtil.expandUserHome("~/.cargo"))

        val programFiles = File(System.getenv("ProgramFiles") ?: "")
        val fromProgramFiles = if (programFiles.exists() && programFiles.isDirectory) {
            programFiles.listFiles { file -> file.isDirectory }.asSequence()
                .filter { it.nameWithoutExtension.toLowerCase().startsWith("rust") }
        } else {
            emptySequence()
        }

        return sequenceOf(fromHome) + fromProgramFiles
    }

    override fun isApplicable(): Boolean = SystemInfo.isWindows

    companion object {
        fun getInstance(): RsSdkFlavor? = RsSdkFlavor.EP_NAME.findExtension(WinSdkFlavor::class.java)
    }
}
