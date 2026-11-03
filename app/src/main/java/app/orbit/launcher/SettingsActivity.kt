package app.orbit.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Orbit's own settings, as against the television's.
 *
 * The TV's settings already have a glyph in the top bar, and the two are not the same thing:
 * one is the set, this is the launcher. Everything here is Orbit's to change and is stored on
 * the television and nowhere else.
 *
 * Three sections, in the order they are worth reaching: how it looks, where the pictures come
 * from, and what is running. The last was a card of its own before, reached by a glyph that
 * said nothing about the rest, which left no room for anything that could be set.
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { SettingsScreen() }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    // Nothing here is reachable until something holds focus. A television has no pointer to
    // place it, and Compose does not hand it out on its own, so the screen opened and every
    // key on the remote did nothing at all. The picker has asked for it since it was written.
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SafeH, vertical = SafeV)
    ) {
        Text("Orbit settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(4.dp))
        Text("Orbit's own settings.", color = Muted, fontSize = 13.sp)

        Section("Visual")
        Text(
            "The colour Orbit marks its own things in: the bar beside the hero, the line " +
                "naming a picture, the hints. The screen stays dark either way, because the " +
                "panel is usually in one.",
            color = Muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Swatches(first)

        Section("Layout")
        Text(
            "The rows of the home screen, top to bottom. Left and right move one, OK turns " +
                "it off.",
            color = Muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Rows()

        Section("Pictures")
        Entry(
            label = "Backdrop pictures",
            detail = "Which apps supply the artwork behind the hero",
            anchor = Anchors.BACKDROPS,
            onClick = {
                context.openScreen(
                    Intent(context, AppPickerActivity::class.java)
                        .putExtra(AppPickerActivity.EXTRA_BACKDROPS, true)
                )
            },
        )

        Section("Home Assistant")
        val hassReady = remember(HomeAssistantStore.revision) {
            HomeAssistantStore.configured(context)
        }
        Text(
            if (hassReady) {
                "Connected to your own Home Assistant. Orbit only reads from it, and nothing " +
                    "it reads leaves this television."
            } else {
                "Show readings from your own Home Assistant in the hero and in a " +
                    "notifications row. Orbit only reads, and needs the address and a " +
                    "long-lived token."
            },
            color = Muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Entry(
            label = if (hassReady) "Connection" else "Set up Home Assistant",
            // Deliberately not the address. It is the owner's own server, and a screen that
            // prints it is a screen that prints it into every photograph of the television.
            detail = if (hassReady) "Saved on this television" else "Address and access token",
            anchor = Anchors.HASS_CONNECTION,
            onClick = { context.openScreen(Intent(context, HassSetupActivity::class.java)) },
        )
        if (hassReady) {
            Entry(
                label = "What to show",
                detail = "Pick from what your Home Assistant has, and where each one goes",
                anchor = Anchors.HASS_WHAT_TO_SHOW,
                onClick = { context.openScreen(Intent(context, HassEntitiesActivity::class.java)) },
            )
        }

        Section("About")
        // The same four lines the card showed, so what is on the television can still be
        // checked against the repository at a glance.
        Text(BuildInfo.line(), color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(3.dp))
        Text(BuildInfo.source(), color = Muted, fontSize = 13.sp)
        Text(BuildInfo.release(), color = Muted, fontSize = 13.sp)
        Text(BuildInfo.built(), color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(26.dp))
    Text(title.uppercase(), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
}

/**
 * The colours, as the colours themselves rather than as their names in a list.
 *
 * A swatch says what it does without being read, which matters on a screen looked at from the
 * other side of a room. The one in use carries a ring; the one in focus grows, the way a tile
 * does everywhere else in Orbit.
 */
@Composable
private fun Swatches(first: FocusRequester) {
    val context = LocalContext.current
    val chosen = remember(ThemeStore.revision) { ThemeStore.accent(context).id }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ThemeStore.accents.forEachIndexed { index, accent ->
            var focused by remember { mutableStateOf(false) }
            val inUse = accent.id == chosen
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(if (focused) 52.dp else 44.dp)
                        .clip(CircleShape)
                        .background(accent.color)
                        .then(
                            if (inUse) {
                                Modifier.border(3.dp, Color.White, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .testTag(Anchors.accent(accent.id))
                        .onFocusChanged { focused = it.isFocused }
                        // No focusable() in front: clickable brings its own focus node, and
                        // two of them means the one holding focus is not the one that acts.
                        .clickable(interactionSource = null, indication = null) {
                            ThemeStore.choose(context, accent.id)
                        }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    accent.label,
                    color = if (focused || inUse) Color.White else Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** Wide enough for the longest row name, so every state word starts at the same place. */
private val RowNameWidth = 150.dp

/**
 * The home screen's rows, as a list in the order they are drawn in.
 *
 * Left and right move the row under the cursor, which is the gesture a tile already uses for
 * a move, without the hold: there is nothing beside a row in a list this narrow, so the keys
 * are free and a mode to enter would be ceremony. OK turns a row off.
 */
@Composable
private fun Rows() {
    val context = LocalContext.current
    val offered = remember(LayoutStore.revision) { LayoutStore.offered(context) }
    Column {
        // Keyed on the row, so the cursor follows the row it is moving rather than staying
        // at the place in the list the row has just left.
        offered.forEach { row -> key(row) {
            val shown = remember(LayoutStore.revision, row) { LayoutStore.shown(context, row) }
            val required = row in LayoutStore.required
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (focused) Color(0xFF1E2838) else Color.Transparent)
                    .testTag(Anchors.layoutRow(row))
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        // Left and right, not up and down: up and down are how the cursor
                        // gets from one row of this list to the next, and taking them would
                        // trap it on whichever row it landed on.
                        when (event.key) {
                            Key.DirectionLeft -> LayoutStore.move(context, row, -1)
                            Key.DirectionRight -> LayoutStore.move(context, row, 1)
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    }
                    .clickable(interactionSource = null, indication = null) {
                        LayoutStore.setShown(context, row, !shown)
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (focused) Accent else Color.Transparent)
                )
                Spacer(Modifier.width(11.dp))
                // In front of every row, so the column of labels after it lines up whatever
                // each row is called. Nothing on the row said it could be moved before.
                Icon(
                    imageVector = Glyphs.Reorder,
                    contentDescription = "Move",
                    tint = if (focused) Accent else Muted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    rowLabel(row),
                    color = if (shown) Color.White else Muted,
                    fontSize = 16.sp,
                    // One width for every label, so the on and off words line up down the
                    // list instead of stepping in and out with the length of the name.
                    modifier = Modifier.width(RowNameWidth),
                )
                Text(
                    when {
                        required -> "always on"
                        shown -> "on"
                        else -> "off"
                    },
                    color = if (shown && !required) Accent else Muted,
                    fontSize = 12.sp,
                )
            }
        } }
    }
}

/** What a row is called on screen, which is what its label says on the home screen. */
internal fun rowLabel(row: HomeRow): String = when (row) {
    HomeRow.FAVORITES -> "Favorite apps"
    HomeRow.POSTERS -> "Posters"
    HomeRow.INPUTS -> "Inputs"
    HomeRow.NOTIFICATIONS -> "Notifications"
}

/** A row that opens something else, with what it is for underneath. */
@Composable
private fun Entry(label: String, detail: String, anchor: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF1E2838) else Color.Transparent)
            .testTag(anchor)
            .onFocusChanged { focused = it.isFocused }
            // As above: the clickable is the only focus target this row has.
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (focused) Accent else Color.Transparent)
        )
        Spacer(Modifier.width(11.dp))
        Column {
            Text(label, color = Color.White, fontSize = 16.sp)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
    }
}
