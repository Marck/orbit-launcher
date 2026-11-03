package app.orbit.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What Home Assistant has, and where each of it goes.
 *
 * The list is whatever the owner's own system answers with, grouped by what kind of thing it
 * is. Nothing is hardcoded about any particular system: a sensor is a sensor because Home
 * Assistant says so.
 *
 * OK moves an entity between the places there are to put it, which is two, so a press cycles
 * rather than opening a menu over a list that is already long.
 */
class HassEntitiesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { HassEntitiesScreen() }
    }
}

/** Where an entity is shown, as one value, since a thing is in one place or none. */
internal fun placementOf(context: android.content.Context, entityId: String): HaSlot? =
    HaSlot.entries.firstOrNull { entityId in HomeAssistantStore.chosen(context, it) }

/** The next place round, so one key moves a thing through all of them and back to nowhere. */
internal fun nextPlacement(current: HaSlot?): HaSlot? = when (current) {
    null -> HaSlot.HERO
    HaSlot.HERO -> HaSlot.NOTIFICATIONS
    HaSlot.NOTIFICATIONS -> null
}

internal fun placementLabel(slot: HaSlot?): String = when (slot) {
    null -> "not shown"
    HaSlot.HERO -> "in the hero"
    HaSlot.NOTIFICATIONS -> "in notifications"
}

@Composable
private fun HassEntitiesScreen() {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    @Suppress("ProduceStateDoesNotAssignValue")
    val entities by produceState<List<HaEntity>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { HomeAssistant.states(context) }
    }
    LaunchedEffect(entities) { if (entities?.isNotEmpty() == true) runCatching { first.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SafeH, vertical = SafeV)
    ) {
        Text("What to show", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(4.dp))
        Text(
            "Everything your Home Assistant will tell Orbit about. OK moves one between the " +
                "hero, the notifications row and nowhere.",
            color = Muted,
            fontSize = 13.sp,
        )

        when {
            // Three different states, and they are not the same thing to say.
            entities == null -> Message("Asking Home Assistant...")
            entities!!.isEmpty() -> Message(
                "Nothing came back. Check the address and the token in Orbit settings, and " +
                    "that this television can reach the server."
            )
            else -> {
                var index = 0
                HomeAssistant.offered.forEach { domain ->
                    val ofDomain = entities!!.filter { it.domain == domain }
                    if (ofDomain.isEmpty()) return@forEach
                    Spacer(Modifier.height(22.dp))
                    Text(
                        domainLabel(domain).uppercase(),
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    ofDomain.forEach { entity ->
                        val isFirst = index++ == 0
                        key(entity.entityId) {
                            EntityRow(entity, if (isFirst) Modifier.focusRequester(first) else Modifier)
                        }
                    }
                }
            }
        }
    }
}

/** What a domain is called for someone who has never read a Home Assistant document. */
internal fun domainLabel(domain: String): String = when (domain) {
    "sensor" -> "Readings"
    "binary_sensor" -> "Open and closed"
    "climate" -> "Climate"
    "weather" -> "Weather"
    "camera" -> "Cameras"
    "person" -> "People"
    "light" -> "Lights"
    else -> domain.replace('_', ' ')
}

@Composable
private fun Message(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, color = Muted, fontSize = 14.sp)
}

@Composable
private fun EntityRow(entity: HaEntity, modifier: Modifier) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val slot = remember(HomeAssistantStore.revision, entity.entityId) {
        placementOf(context, entity.entityId)
    }
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF1E2838) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null) {
                val next = nextPlacement(slot)
                // Out of wherever it was first, so a thing is never in two places at once.
                slot?.let { HomeAssistantStore.toggle(context, it, entity.entityId) }
                next?.let { HomeAssistantStore.toggle(context, it, entity.entityId) }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (focused) Accent else Color.Transparent)
        )
        Spacer(Modifier.width(11.dp))
        Text(entity.name, color = Color.White, fontSize = 15.sp, modifier = Modifier.width(260.dp))
        Text(entity.reading, color = Muted, fontSize = 13.sp, modifier = Modifier.width(120.dp))
        Text(
            placementLabel(slot),
            color = if (slot == null) Muted else Accent,
            fontSize = 12.sp,
        )
    }
}
