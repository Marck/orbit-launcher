package app.orbit.launcher

import androidx.compose.ui.input.key.KeyEvent

/** How long OK counts as held on a remote that sends no long-press flag of its own. */
internal const val LongPressMs = 500L

/** Android marks a held key with a flag and a repeat, which is the signal to trust. */
internal fun KeyEvent.isHeld(): Boolean {
    val native = nativeKeyEvent
    return native.repeatCount > 0 ||
        (native.flags and android.view.KeyEvent.FLAG_LONG_PRESS) != 0
}
