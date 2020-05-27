/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Comparing
import org.rust.ide.sdk.flavors.RsSdkFlavor
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import java.util.*

object RsPreferredSdkComparator : Comparator<Sdk> {
    override fun compare(o1: Sdk, o2: Sdk): Int {
        for (comparator in RsSdkComparator.EP_NAME.extensionList) {
            val result: Int = comparator.compare(o1, o2)
            if (result != 0) {
                return result
            }
        }

        val detected1Weight = if (o1 is RsDetectedSdk) 0 else 1
        val detected2Weight = if (o2 is RsDetectedSdk) 0 else 1
        if (detected1Weight != detected2Weight) {
            return detected2Weight - detected1Weight
        }

        val flavor1 = RsSdkFlavor.getFlavor(o1)
        val flavor2 = RsSdkFlavor.getFlavor(o2)
        val flavor1Weight = if (flavor1 is RustupSdkFlavor) 0 else 1
        val flavor2Weight = if (flavor2 is RustupSdkFlavor) 0 else 1
        if (flavor1Weight != flavor2Weight) {
            return flavor2Weight - flavor1Weight
        }

        return -Comparing.compare(o1.versionString, o2.versionString)
    }
}
