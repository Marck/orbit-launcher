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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
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
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SafeH, vertical = SafeV)
    ) {
        Text("Orbit settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(4.dp))
        Text(
            "Orbit's own settings. The television's are the gear in the top bar.",
            color = Muted,
            fontSize = 13.sp,
        )

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

        Section("Pictures")
        Entry(
            label = "Backdrop pictures",
            detail = "Which apps supply the artwork behind the hero",
            onClick = {
                context.openScreen(
                    Intent(context, AppPickerActivity::class.java)
                        .putExtra(AppPickerActivity.EXTRA_BACKDROPS, true)
                )
            },
        )

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
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
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

/** A row that opens something else, with what it is for underneath. */
@Composable
private fun Entry(label: String, detail: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF1E2838) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
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
