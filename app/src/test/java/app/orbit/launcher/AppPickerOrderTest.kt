package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The order the picker offers apps in, and what the filter keeps.
 *
 * A television carries far more factory apps than installed ones: on the set this was written
 * for, 18 of the 28 with a launcher came with the TV. Sorted purely by name, the ten the owner
 * installed were scattered among them, so finding one meant reading past the Sony apps.
 */
class AppPickerOrderTest {

    private fun app(name: String, factory: Boolean = false) = LaunchableApp(
        packageName = "pkg.$name",
        label = name,
        art = null,
        isBanner = false,
        removable = !factory,
    )

    /** The repository hands the picker its apps already sorted by name. */
    private fun named(vararg names: Pair<String, Boolean>) =
        names.map { (name, factory) -> app(name, factory) }.sortedBy { it.label.lowercase() }

    @Test
    fun `what the owner installed comes before what the television came with`() {
        val apps = named(
            "Album" to true,
            "Disney" to false,
            "Help" to true,
            "Viaplay" to false,
        )
        assertEquals(
            listOf("Disney", "Viaplay", "Album", "Help"),
            inPickerOrder(apps, emptyList()).map { it.label },
        )
    }

    @Test
    fun `favorites come first, in the owner's own order`() {
        val apps = named(
            "Album" to true,
            "Disney" to false,
            "Help" to true,
            "Viaplay" to false,
        )
        val order = inPickerOrder(apps, listOf("pkg.Viaplay", "pkg.Album"))
        assertEquals(
            listOf("Viaplay", "Album", "Disney", "Help"),
            order.map { it.label },
        )
    }

    @Test
    fun `a favorite that came with the television is still a favorite`() {
        // A favorite is already something the owner picked out. Moving it down because the TV
        // happens to ship it would be sorting by a fact nobody cares about at that moment.
        val apps = named("Netflix" to true, "Disney" to false)
        assertEquals(
            listOf("Netflix", "Disney"),
            inPickerOrder(apps, listOf("pkg.Netflix")).map { it.label },
        )
    }

    @Test
    fun `each group keeps the alphabetical order it arrived in`() {
        val apps = named(
            "Zebra" to false,
            "Apple" to false,
            "Mango" to true,
            "Banana" to true,
        )
        assertEquals(
            listOf("Apple", "Zebra", "Banana", "Mango"),
            inPickerOrder(apps, emptyList()).map { it.label },
        )
    }

    @Test
    fun `the filter keeps what it says it keeps`() {
        val installed = app("Disney")
        val factory = app("Netflix", factory = true)
        assertEquals(true, AppFilter.ALL.keeps(installed))
        assertEquals(true, AppFilter.ALL.keeps(factory))
        assertEquals(true, AppFilter.INSTALLED.keeps(installed))
        assertEquals(false, AppFilter.INSTALLED.keeps(factory))
        assertEquals(false, AppFilter.FACTORY.keeps(installed))
        assertEquals(true, AppFilter.FACTORY.keeps(factory))
    }

    @Test
    fun `the two halves add up to the whole`() {
        // The counts above the grid are read together, so a hidden third state would show up
        // here as the halves not summing.
        val apps = named(
            "Album" to true, "Disney" to false, "Help" to true,
            "Viaplay" to false, "Netflix" to true,
        )
        val installed = apps.count { AppFilter.INSTALLED.keeps(it) }
        val factory = apps.count { AppFilter.FACTORY.keeps(it) }
        assertEquals(apps.size, installed + factory)
        assertEquals(apps.count { AppFilter.ALL.keeps(it) }, installed + factory)
    }

    @Test
    fun `filtering then ordering leaves only that kind, still favorites first`() {
        val apps = named(
            "Album" to true, "Disney" to false, "Help" to true, "Viaplay" to false,
        )
        val kept = apps.filter { AppFilter.INSTALLED.keeps(it) }
        assertEquals(
            listOf("Viaplay", "Disney"),
            inPickerOrder(kept, listOf("pkg.Viaplay")).map { it.label },
        )
    }
}
