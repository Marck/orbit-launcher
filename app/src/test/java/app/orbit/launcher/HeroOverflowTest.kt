package app.orbit.launcher

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A description too long for the hero stops at a word.
 *
 * Compose ellipsizes by character, so the line ended "who will never check o...". The ellipsis
 * was there and the screen still read as having broken off mid word, which is what was
 * reported. Measured before changing anything: three separate ink marks at the end of the
 * second line, ending at x=1003 inside a text column 1010 wide, so this was never a clip.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HeroOverflowTest : TvTest() {
    override val outDir = "build/tv-shots/overflow"

    private val server = "org.jellyfin.androidtv"

    private val long =
        "After inheriting a hotel from her late brother, a single mom moves in with his " +
            "good-natured ghost and high-maintenance guests who will never check out, which " +
            "goes on for a good while longer than two lines of anything could ever hold."

    @Before
    fun oneLongDescription() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(ContinueWatching.PERMISSION)
        favorites(listOf(server) + myApps.filter { it != server }, myInputs)
        ShotHooks.watchNext = emptyList()
        ShotHooks.artwork = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        ShotHooks.cycling = false
        ShotHooks.pictures = listOf(
            Picture(server, "pic://ambient/long", "A Long One", null, long, wide = true)
        )
    }

    /** What the hero is actually showing of the description, ellipsis and all. */
    private fun shownDescription(): String? =
        rule.onAllNodes(hasText("After inheriting", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
            }

    @Test
    fun `the hero cuts a long description at a word, not part way through one`() {
        launch(HomeActivity::class.java)
        waitFor("the hero to settle on the trimmed description") {
            shownDescription()?.endsWith("…") == true
        }
        val shown = shownDescription()!!

        assertTrue("the description was not shortened at all: $shown", shown.length < long.length)
        val body = shown.removeSuffix("…")
        // The cut lands where a word ends: what is left is a prefix of the original, and the
        // original carries straight on with a space. That is the whole claim.
        assertTrue("what is shown is not a prefix of the description: $body", long.startsWith(body))
        assertTrue(
            "the description was cut part way through a word: ...${body.takeLast(24)}",
            long.length == body.length || long[body.length] == ' ',
        )
        shot("overflow")
    }

    @Test
    fun `a description that fits is left exactly as the app wrote it`() {
        val short = "A short line."
        ShotHooks.pictures = listOf(
            Picture(server, "pic://ambient/short", "A Short One", null, short, wide = true)
        )
        launch(HomeActivity::class.java)
        waitFor("the short description") {
            rule.onAllNodes(hasText(short, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val node = rule.onAllNodes(hasText(short, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().first()
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        assertEquals("a description that fits was trimmed anyway", short, text)
    }

    @Test
    fun `the rule itself, at the edges`() {
        // Cut back to the last whole word, with the ellipsis joined on.
        assertEquals("one two…", trimToWord("one two three", 11))
        // Trailing punctuation belongs to the word that went, not to the line.
        assertEquals("one two…", trimToWord("one two, three", 12))
        // Nothing to do when all of it fits.
        assertNull(trimToWord("one two", 7))
        assertNull(trimToWord("one two", 99))
        // One very long word is better shown part way through than dropped to nothing.
        assertNull(trimToWord("Supercalifragilistic", 8))
        assertNull(trimToWord("anything", 0))
    }
}
