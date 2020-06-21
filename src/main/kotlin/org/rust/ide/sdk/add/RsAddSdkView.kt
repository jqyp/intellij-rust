/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.Disposable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.Icon

/**
 * Represents the view for adding new Python SDK. It is used in
 * [RsAddSdkDialog].
 */
interface RsAddSdkView: Disposable {
    val panelName: String
        @Nls(capitalization = Nls.Capitalization.Title) get

    val icon: Icon

    /**
     * [RsAddSdkStateListener.onActionsStateChanged] is called after changes in
     * [actions].
     */
    val actions: Map<RsAddSdkDialogFlowAction, Boolean>

    /**
     * The [component] *might* return the new [Component] after [next] or
     * [previous].
     */
    val component: Component

    /**
     * Returns the created sdk after closing [RsAddSdkDialog]. The method may
     * return `null` if the dialog was closed or cancelled or if the creation
     * failed.
     *
     * The creation of the sdk may occur either in this method or in the
     * [complete] method a while back.
     */
    fun getOrCreateSdk(): Sdk?

    fun onSelected()

    /**
     * @throws IllegalStateException
     */
    fun previous()

    /**
     * @throws IllegalStateException
     */
    fun next()

    /**
     * Completes SDK creation.
     *
     * The method is called by [RsAddSdkDialog] when *OK* or *Finish* button is
     * pressed.
     *
     * The method may attempt to create the SDK and throw an [Exception] if some
     * error during the creation is occurred. The created SDK could be later
     * obtained by [getOrCreateSdk] method.
     *
     * If the method throws an [Exception] the error message is shown to the user
     * and [RsAddSdkDialog] is not closed.
     *
     * @throws Exception if SDK creation failed for some reason
     */
    fun complete()

    /**
     * Returns the list of validation errors. The returned list is empty if there
     * are no errors found.
     *
     * @see com.intellij.openapi.ui.DialogWrapper.doValidateAll
     */
    fun validateAll(): List<ValidationInfo>

    fun addStateListener(stateListener: RsAddSdkStateListener)

    override fun dispose() {}
}
