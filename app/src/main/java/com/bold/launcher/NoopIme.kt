package com.bold.launcher

import android.inputmethodservice.InputMethodService

/** An IME that never shows anything, so the system keyboard stays out of the way. */
class NoopIme : InputMethodService() {
    override fun onEvaluateInputViewShown() = false
    override fun onShowInputRequested(flags: Int, configChange: Boolean) = false
}
