package app.orbit.launcher

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * One thing Home Assistant knows about, reduced to what a television can show.
 *
 * [entityId] is an address on the owner's own system, so it is treated as identifying: it is
 * stored on the set, never logged, and never written into this repository's fixtures.
 */
data class HaEntity(
    val entityId: String,
    val name: String,
    val state: String,
    val unit: String?,
    val deviceClass: String?,
) {
    /** The part before the dot, which is what decides how a thing is shown. */
    val domain: String get() = entityId.substringBefore('.', "")

    /** What the hero says for it, with the unit when the entity carries one. */
    val reading: String get() = if (unit.isNullOrBlank()) state else "$state$unit"
}

/**
 * Reading Home Assistant, which Orbit only ever reads.
 *
 * The connection is the owner's own, on their own network, configured on the television. If it
 * is not set up, or is unreachable, every call here answers null and the launcher carries on
 * exactly as it does without it. A home screen that depends on a server being up is not a home
 * screen.
 */
object HomeAssistant {

    /** The domains worth offering, in the order they are useful on a screen across a room. */
    internal val offered = listOf("sensor", "binary_sensor", "climate", "weather", "camera", "person", "light")

    /**
     * Turns the states endpoint's body into entities.
     *
     * Pure, so the shape of what Home Assistant sends can be checked without a server and
     * without putting anyone's entity names in a fixture. Anything unparseable is skipped
     * rather than thrown: one odd entry in a list of hundreds must not cost the rest.
     */
    internal fun parseStates(body: String): List<HaEntity> = runCatching {
        val array = JSONArray(body)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("entity_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val attributes = item.optJSONObject("attributes")
            HaEntity(
                entityId = id,
                name = attributes?.optString("friendly_name")?.takeIf { it.isNotBlank() }
                    ?: id.substringAfter('.').replace('_', ' '),
                state = item.optString("state"),
                unit = attributes?.optString("unit_of_measurement")?.takeIf { it.isNotBlank() },
                deviceClass = attributes?.optString("device_class")?.takeIf { it.isNotBlank() },
            )
        }
    }.getOrDefault(emptyList())

    /**
     * Everything Home Assistant will tell us about, or null when it cannot be asked.
     *
     * Call off the main thread. Null means not configured, unreachable or refused, and the
     * caller treats all three the same way: show what Orbit knows by itself.
     */
    fun states(context: Context): List<HaEntity>? {
        val base = HomeAssistantStore.baseUrl(context) ?: return null
        val token = HomeAssistantStore.token(context) ?: return null
        return runCatching {
            val connection = (URL("$base/api/states").openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                parseStates(connection.inputStream.bufferedReader().readText())
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
        // Deliberately nothing logged on the way out, not even the failure: the message of a
        // connection error quotes the address it failed to reach, and the address is the
        // owner's own server.
    }
}
