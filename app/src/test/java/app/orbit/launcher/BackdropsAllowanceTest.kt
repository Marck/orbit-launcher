package app.orbit.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which publishers supply pictures without being asked, and which have to be asked for.
 *
 * This is the rule that used to be an allow list. The allow list froze the moment the picker
 * was first used: on the set this was written for it held four apps while nine were publishing,
 * so 251 pictures from three apps installed later were dropped with nothing on screen to say
 * why. Publishing is opt out now, and this is where the default lives.
 */
class BackdropsAllowanceTest {

    private val nothingDecided = Backdrops.Allowance(off = emptySet(), on = emptySet())

    private fun allows(
        allowance: Backdrops.Allowance = nothingDecided,
        packageName: String = "app.example",
        factory: Boolean = false,
        favorite: Boolean = false,
    ) = allowance.allows(packageName, factory, favorite)

    @Test
    fun `an app the owner installed is in without being asked`() {
        assertTrue(allows())
    }

    @Test
    fun `an app the television came with stays out`() {
        assertFalse(allows(factory = true))
    }

    @Test
    fun `a factory app the owner made a favorite is theirs, so it is in`() {
        // The clearest statement there is that an app is wanted. Without this the rule would
        // turn off whichever preinstalled app the owner actually watches.
        assertTrue(allows(factory = true, favorite = true))
    }

    @Test
    fun `a favorite that is not a factory app is in, which it already was`() {
        assertTrue(allows(favorite = true))
    }

    @Test
    fun `an app turned off in the picker stays off, favorite or not`() {
        val off = Backdrops.Allowance(off = setOf("app.example"), on = emptySet())

        assertFalse(allows(off))
        assertFalse(allows(off, favorite = true))
        assertFalse(allows(off, factory = true, favorite = true))
    }

    @Test
    fun `a factory app turned on in the picker stays on, even without being a favorite`() {
        val on = Backdrops.Allowance(off = emptySet(), on = setOf("app.example"))

        assertTrue(allows(on, factory = true))
    }

    @Test
    fun `a decision applies to the app it was made about and no other`() {
        val off = Backdrops.Allowance(off = setOf("app.other"), on = emptySet())

        assertTrue(allows(off))
    }

    @Test
    fun `off wins over on, so a contradiction cannot leave an app the owner turned off showing`() {
        val both = Backdrops.Allowance(off = setOf("app.example"), on = setOf("app.example"))

        assertFalse(allows(both))
    }
}
