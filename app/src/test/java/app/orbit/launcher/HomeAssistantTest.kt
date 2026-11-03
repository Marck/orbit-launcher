package app.orbit.launcher

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reading Home Assistant, and deciding where what it says goes.
 *
 * Every entity here is invented. A real one names a room in someone's house, which is exactly
 * the sort of thing that must not end up in a public repository, so the fixtures are made up
 * and the parsing is tested against the shape of the answer rather than against anyone's.
 */
@RunWith(AndroidJUnit4::class)
class HomeAssistantTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private val body = """
        [
          {"entity_id":"sensor.made_up_one","state":"19.4",
           "attributes":{"friendly_name":"Made Up One","unit_of_measurement":"C","device_class":"temperature"}},
          {"entity_id":"binary_sensor.made_up_two","state":"on",
           "attributes":{"friendly_name":"Made Up Two","device_class":"door"}},
          {"entity_id":"sensor.no_name","state":"7","attributes":{}},
          {"not_an_entity":true},
          {"entity_id":"","state":"nothing"}
        ]
    """.trimIndent()

    @Test
    fun `what the states endpoint answers becomes entities`() {
        val parsed = HomeAssistant.parseStates(body)

        assertEquals(3, parsed.size)
        assertEquals("Made Up One", parsed[0].name)
        assertEquals("sensor", parsed[0].domain)
        assertEquals("temperature", parsed[0].deviceClass)
    }

    @Test
    fun `a reading carries its unit, and one without a unit is left alone`() {
        val parsed = HomeAssistant.parseStates(body)

        assertEquals("19.4C", parsed[0].reading)
        assertEquals("on", parsed[1].reading)
    }

    @Test
    fun `an entity with no friendly name falls back to its own id, readably`() {
        val parsed = HomeAssistant.parseStates(body)

        assertEquals("no name", parsed[2].name)
    }

    @Test
    fun `one bad entry does not cost the rest of the list`() {
        // Hundreds of entities come back at once. A single odd one must not empty the screen.
        assertEquals(3, HomeAssistant.parseStates(body).size)
    }

    @Test
    fun `a body that is not a list at all comes back empty rather than thrown`() {
        assertEquals(emptyList<HaEntity>(), HomeAssistant.parseStates("not json"))
        assertEquals(emptyList<HaEntity>(), HomeAssistant.parseStates(""))
    }

    @Test
    fun `nothing is configured to begin with, and nothing is asked`() {
        assertFalse(HomeAssistantStore.configured(context))
        assertNull(HomeAssistant.states(context))
    }

    @Test
    fun `an address has to look like one before anything is sent`() {
        // A bare host is the usual slip, and failing on it later reads as the server being
        // down rather than as the address being wrong.
        assertFalse(HomeAssistantStore.looksLikeUrl("homeassistant.local:8123"))
        assertFalse(HomeAssistantStore.looksLikeUrl("http://"))
        assertTrue(HomeAssistantStore.looksLikeUrl("http://example.invalid:8123"))
        assertTrue(HomeAssistantStore.looksLikeUrl("https://example.invalid"))
    }

    @Test
    fun `the address is kept without its trailing slash, so one can be added safely`() {
        HomeAssistantStore.save(context, "http://example.invalid:8123/", "a-token")

        assertEquals("http://example.invalid:8123", HomeAssistantStore.baseUrl(context))
        assertTrue(HomeAssistantStore.configured(context))
    }

    @Test
    fun `forgetting it takes the token and the choices with it`() {
        HomeAssistantStore.save(context, "http://example.invalid:8123", "a-token")
        HomeAssistantStore.toggle(context, HaSlot.HERO, "sensor.made_up_one")

        HomeAssistantStore.clear(context)

        assertFalse(HomeAssistantStore.configured(context))
        assertEquals(emptyList<String>(), HomeAssistantStore.chosen(context, HaSlot.HERO))
    }

    @Test
    fun `OK moves a thing through the places there are, and back to none`() {
        assertEquals(HaSlot.HERO, nextPlacement(null))
        assertEquals(HaSlot.NOTIFICATIONS, nextPlacement(HaSlot.HERO))
        assertNull(nextPlacement(HaSlot.NOTIFICATIONS))
    }

    @Test
    fun `a thing is only ever in one place`() {
        val id = "sensor.made_up_one"
        HomeAssistantStore.toggle(context, HaSlot.HERO, id)
        assertEquals(HaSlot.HERO, placementOf(context, id))

        // What the screen does on a press: out of where it was, into the next one.
        HomeAssistantStore.toggle(context, HaSlot.HERO, id)
        HomeAssistantStore.toggle(context, HaSlot.NOTIFICATIONS, id)

        assertEquals(HaSlot.NOTIFICATIONS, placementOf(context, id))
        assertFalse(id in HomeAssistantStore.chosen(context, HaSlot.HERO))
    }

    @Test
    fun `the hero says nothing when there is no server to ask`() {
        HomeAssistantStore.toggle(context, HaSlot.HERO, "sensor.made_up_one")

        // Chosen, but nothing is configured, so there is nothing to read and the hero goes
        // back to the time rather than showing a gap where a reading should be.
        assertEquals(emptyList<HaEntity>(), heroReadings(context))
    }

    @Test
    fun `a domain is named for someone who has never read a Home Assistant document`() {
        assertEquals("Readings", domainLabel("sensor"))
        assertEquals("Open and closed", domainLabel("binary_sensor"))
        assertEquals("media player", domainLabel("media_player"))
    }
}

/**
 * Which of the chosen entities are worth a row, which is what makes a notifications row a
 * notification rather than another list.
 */
@RunWith(AndroidJUnit4::class)
class NotableTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun entity(state: String) =
        HaEntity("binary_sensor.made_up", "Made Up", state, null, "door")

    @Test
    fun `the normal state of a house is not news`() {
        listOf("off", "closed", "locked", "normal", "home", "unavailable", "unknown", "")
            .forEach { assertFalse("\"$it\" should be quiet", isNotable(entity(it))) }
    }

    @Test
    fun `anything else is`() {
        listOf("on", "open", "unlocked", "detected").forEach {
            assertTrue("\"$it\" should be worth saying", isNotable(entity(it)))
        }
    }

    @Test
    fun `the state is read whatever case the integration wrote it in`() {
        // Home Assistant is not consistent about this between integrations.
        assertFalse(isNotable(entity("OFF")))
        assertFalse(isNotable(entity(" Closed ")))
    }

    @Test
    fun `nothing chosen means no row at all`() {
        assertEquals(emptyList<HaEntity>(), notableReadings(context))
    }
}
