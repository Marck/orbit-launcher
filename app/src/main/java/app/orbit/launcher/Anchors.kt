package app.orbit.launcher

/**
 * Names for the things a remote can land on, so a test can say where it means to be.
 *
 * A test that walks down a screen has to know when it has arrived. Matching the words under
 * the cursor worked until the words changed, at which point a test about whether a row can be
 * reached failed because a sentence was reworded. These names do not change when the copy
 * does, and they are constants rather than strings in two places so a rename cannot leave a
 * test hunting for something that no longer exists.
 *
 * They cost one modifier each in the screens and nothing at runtime.
 */
object Anchors {
    /** One swatch in the Visual section, by the colour's own id. */
    fun accent(id: String) = "accent.$id"

    /** One row of the Layout section, by the row it stands for. */
    fun layoutRow(row: HomeRow) = "layout.${row.name.lowercase()}"

    const val BACKDROPS = "entry.backdrops"
    const val HASS_CONNECTION = "entry.hass.connection"
    const val HASS_WHAT_TO_SHOW = "entry.hass.whatToShow"
}
