package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keys for a row of pictures.
 *
 * Compose throws out of a lazy row the moment two items share a key, and that is exactly what
 * a media server hands over: Orbit died with "Key ... was already used" while the owner was
 * moving along the poster row, because two rows carried one series cover between them.
 */
class PictureKeysTest {

    private fun cover(title: String, uri: String, intent: String? = null) =
        Picture(
            packageName = "org.jellyfin.androidtv",
            uri = uri,
            title = title,
            seasons = null,
            description = null,
            intentUri = intent,
            wide = false,
        )

    @Test
    fun `two rows sharing one cover still get a key each`() {
        val shared = "pic://cover/the-series"
        val keys = pictureKeys(
            listOf(cover("Episode 1", shared), cover("Episode 2", shared))
        )

        assertEquals(2, keys.size)
        assertEquals("the duplicate was not given a key of its own", keys.toSet().size, keys.size)
    }

    @Test
    fun `a row's own way in separates two items that share a cover`() {
        val shared = "pic://cover/the-series"
        val keys = pictureKeys(
            listOf(
                cover("Episode 1", shared, intent = "intent://item/1"),
                cover("Episode 2", shared, intent = "intent://item/2"),
            )
        )

        assertEquals(listOf("intent://item/1", "intent://item/2"), keys)
    }

    @Test
    fun `nothing is renamed when the app repeats nothing`() {
        val pictures = listOf(cover("One", "pic://a"), cover("Two", "pic://b"))
        assertEquals(listOf("pic://a", "pic://b"), pictureKeys(pictures))
    }

    @Test
    fun `however often one picture comes back, every key is its own`() {
        val same = List(6) { cover("Episode ${it + 1}", "pic://cover/one") }
        val keys = pictureKeys(same)

        assertEquals(same.size, keys.toSet().size)
        // The first keeps the plain artwork, so the common case reads as itself in a dump.
        assertEquals("pic://cover/one", keys.first())
        assertTrue("later repeats are not marked: $keys", keys.drop(1).all { it.contains('#') })
    }

    @Test
    fun `an empty row has no keys`() {
        assertEquals(emptyList<String>(), pictureKeys(emptyList()))
    }
}
