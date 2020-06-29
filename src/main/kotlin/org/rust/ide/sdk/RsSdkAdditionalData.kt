/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import org.jdom.Element
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.ide.sdk.flavors.RsSdkFlavor
import java.nio.file.Path
import java.nio.file.Paths

class RsSdkAdditionalData(val flavor: RsSdkFlavor?) : SdkAdditionalData {
    var toolchainName: String? = null
    var toolchainPath: String? = null
    var rustupPath: String? = null
    var stdlibPath: String? = null

    val toolchain: RustToolchain?
        get() = toolchainPath?.let { RustToolchain(Paths.get(it)) }

    fun rustup(projectDirectory: Path = Paths.get(".")): Rustup? {
        val toolchain = toolchain ?: return null
        val rustupPath = rustupPath?.let { Paths.get(it) } ?: return null
        return Rustup(toolchain, rustupPath, projectDirectory)
    }

    private constructor(from: RsSdkAdditionalData) : this(from.flavor) {
        toolchainName = from.toolchainName
        toolchainPath = from.toolchainPath
        rustupPath = from.rustupPath
        stdlibPath = from.stdlibPath
    }

    fun copy(): RsSdkAdditionalData = RsSdkAdditionalData(this)

    fun save(rootElement: Element) {
        toolchainName?.let { rootElement.setAttribute(TOOLCHAIN_NAME, it) }
        toolchainPath?.let { rootElement.setAttribute(TOOLCHAIN_PATH, it) }
        rustupPath?.let { rootElement.setAttribute(RUSTUP_PATH, it) }
        stdlibPath?.let { rootElement.setAttribute(STDLIB_PATH, it) }
    }

    private fun load(element: Element?) {
        if (element == null) return
        toolchainName = element.getAttributeValue(TOOLCHAIN_NAME)
        toolchainPath = element.getAttributeValue(TOOLCHAIN_PATH)
        rustupPath = element.getAttributeValue(RUSTUP_PATH)
        stdlibPath = element.getAttributeValue(STDLIB_PATH)
    }

    companion object {
        private const val TOOLCHAIN_NAME: String = "TOOLCHAIN_NAME"
        private const val TOOLCHAIN_PATH: String = "TOOLCHAIN_PATH"
        private const val STDLIB_PATH: String = "STDLIB_PATH"
        private const val RUSTUP_PATH: String = "RUSTUP_PATH"

        fun load(sdk: Sdk, element: Element?): RsSdkAdditionalData {
            val data = RsSdkAdditionalData(RsSdkFlavor.getFlavor(sdk.homePath))
            data.load(element)
            return data
        }
    }
}
