package app.orbit.launcher

import androidx.compose.ui.input.key.KeyEvent

/**
 * Android marks a held key with a flag and a repeat, which is the signal to trust.
 *
 * Kept out of [TileKeys] because that one is free of Compose on purpose, so its rules can be
 * checked one key at a time without a device. The threshold itself lives there.
 */
internal fun KeyEvent.isHeld(): Boolean {
    val native = nativeKeyEvent
    return native.repeatCount > 0 ||
        (native.flags and android.view.KeyEvent.FLAG_LONG_PRESS) != 0
}
