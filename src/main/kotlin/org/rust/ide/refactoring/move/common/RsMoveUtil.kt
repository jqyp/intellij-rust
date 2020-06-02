/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move.common

import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.RsVisible
import org.rust.lang.core.psi.ext.isVisibleFrom

fun RsPath.resolvesToAndAccessible(target: RsQualifiedNamedElement): Boolean {
    val reference = reference ?: return false
    if (!reference.isReferenceTo(target)) return false

    for (subpath in generateSequence(this) { it.path }) {
        val subpathTarget = subpath.reference?.resolve() as? RsVisible ?: continue
        if (!subpathTarget.isVisibleFrom(containingMod)) return false
    }
    return true
}
