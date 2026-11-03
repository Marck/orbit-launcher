package app.orbit.launcher

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * States that are the absence of news.
 *
 * A door that is closed and a sensor that is off are the normal state of a house, and a row
 * that lists them says nothing while taking up the screen. Lowercased because Home Assistant
 * is not consistent about it across integrations.
 */
private val quiet = setOf("off", "closed", "locked", "normal", "home", "unavailable", "unknown", "")

/** Whether an entity is worth a line on the home screen right now. */
internal fun isNotable(entity: HaEntity): Boolean = entity.state.trim().lowercase() !in quiet

/**
 * The chosen entities that currently have something to say.
 *
 * Empty when Home Assistant is not set up, when nothing was chosen, or when everything chosen
 * is quiet. The row is not drawn at all in any of those cases, which is the point of it: a
 * notifications row that is always there is just another row.
 *
 * Call off the main thread.
 */
internal fun notableReadings(context: Context): List<HaEntity> {
    val wanted = HomeAssistantStore.chosen(context, HaSlot.NOTIFICATIONS)
    if (wanted.isEmpty()) return emptyList()
    val states = HomeAssistant.states(context) ?: return emptyList()
    return wanted
        .mapNotNull { id -> states.firstOrNull { it.entityId == id } }
        .filter(::isNotable)
}

/**
 * What the house has to say, as a row of small cards.
 *
 * Not focusable, deliberately. It is something to read rather than something to operate, and
 * a stop on the way down the screen that does nothing when OK is pressed would be a worse
 * row than no row. Controls are a separate piece of work.
 */
@Composable
internal fun NotificationsRow(readings: List<HaEntity>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        readings.forEach { entity ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2838))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(entity.name, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(entity.reading, color = Accent, fontSize = 12.sp)
            }
        }
    }
}
