package app.orbit.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit

/**
 * One of the colours Orbit can be set to, by name rather than by value.
 *
 * The name is what is stored, so a palette that is retuned later follows the owner's choice
 * instead of leaving them on a colour that no longer exists. [id] never changes; [label] is
 * what the screen says and may.
 */
internal data class AccentChoice(val id: String, val label: String, val color: Color)

/**
 * The colour Orbit draws its own marks in: the bar beside the hero, the line naming what a
 * picture is, the hint under the clock.
 *
 * Only the accent is the owner's to set. The surfaces stay near black because the panel is in
 * a dark room and a launcher that lights the wall is a worse launcher, and the text stays
 * close to white for the same reason. What was grey about Orbit was that its one coloured mark
 * was a pale blue and nothing else, which is the part worth choosing.
 */
object ThemeStore {

    internal val accents = listOf(
        AccentChoice("sky", "Sky", Color(0xFF7FD4FF)),
        AccentChoice("mint", "Mint", Color(0xFF5FD9A6)),
        AccentChoice("amber", "Amber", Color(0xFFFFC14D)),
        AccentChoice("coral", "Coral", Color(0xFFFF8A6B)),
        AccentChoice("orchid", "Orchid", Color(0xFFC79BFF)),
        AccentChoice("slate", "Slate", Color(0xFFB8C2D4)),
    )

    /** What Orbit has always drawn in, so an install that never chooses looks as it did. */
    internal val default = accents.first()

    /** Bumped on a change, so every screen reading the accent redraws with it. */
    var revision by mutableIntStateOf(0)
        private set

    internal fun accent(context: Context): AccentChoice =
        prefs(context).getString(KEY_ACCENT, null)
            ?.let { stored -> accents.firstOrNull { it.id == stored } }
            ?: default

    fun choose(context: Context, id: String) {
        if (accents.none { it.id == id }) return
        prefs(context).edit { putString(KEY_ACCENT, id) }
        revision++
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "orbit.theme"
    private const val KEY_ACCENT = "accent"
}
