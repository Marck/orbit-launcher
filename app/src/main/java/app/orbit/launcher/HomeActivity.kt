package app.orbit.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

// Safe area and tile sizes come straight from section B of REQUIREMENTS.md.
private val SafeH = 48.dp
private val SafeV = 27.dp
private val TileW = 111.dp
private val TileH = 66.dp

private val Surface = Color(0xFF14181F)
private val OnSurface = Color(0xFFDDE5F0)
private val Muted = Color(0xFF8B93A4)
private val Current = Color(0xFF1AA3C4)

private data class Tile(val label: String, val sub: String? = null)

// Placeholder data only. Milestone 1 answers M1 and M2; the real app list,
// inputs and hero arrive in later milestones.
private val apps = listOf(
    Tile("App 1"), Tile("App 2"), Tile("App 3"), Tile("App 4"),
    Tile("App 5"), Tile("App 6"), Tile("App 7"), Tile("App 8"), Tile("+")
)
private val inputs = listOf(
    Tile("HDMI 1", "Free"), Tile("HDMI 2", "Current"), Tile("HDMI 3", "Free"),
    Tile("HDMI 4", "Free"), Tile("Antenna", "TV"), Tile("All inputs", null)
)

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomeScreen() }
    }
}

@Composable
private fun HomeScreen() {
    val first = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = SafeH, vertical = SafeV)
    ) {
        TopBar()
        Spacer(Modifier.height(10.dp))
        Hero()
        Spacer(Modifier.weight(1f))
        Notice()
        Spacer(Modifier.height(14.dp))
        RowLabel("APPS")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(apps) { app ->
                AppTile(app, if (app === apps.first()) first else null)
            }
        }
        Spacer(Modifier.height(20.dp))
        RowLabel("INPUTS")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(inputs) { input -> InputTile(input) }
        }
    }
}

@Composable
private fun TopBar() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Tuesday 20 October", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(Color(0xFF2B3242))
        )
        Spacer(Modifier.width(9.dp))
        Text("21:14", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Hero() {
    Column {
        Text("CONTINUE WATCHING", color = Color(0xFF7FD4FF), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text("Night Watch", color = Color.White, fontSize = 39.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text(
            "A maintenance crew on the late shift finds that the building keeps rearranging its own floors.",
            color = OnSurface,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))
        Text("S2 E5 - THE SUB-LEVEL - 33 MIN LEFT", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .width(225.dp)
                .height(2.dp)
                .background(Color(0x33FFFFFF))
        ) {
            Box(
                Modifier
                    .width(60.dp)
                    .height(2.dp)
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun Notice() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xA60B1017))
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Ethernet is not connected", color = Color.White, fontSize = 14.sp)
            Text("Select for options", color = Muted, fontSize = 12.sp)
        }
        Pill("Details", focused = false)
        Spacer(Modifier.width(9.dp))
        Pill("Dismiss", focused = false)
    }
}

@Composable
private fun Pill(label: String, focused: Boolean) {
    var hasFocus by remember { mutableStateOf(focused) }
    Box(
        Modifier
            .onFocusChanged { hasFocus = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(50))
            .background(if (hasFocus) Color.White else Color(0x1FFFFFFF))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (hasFocus) Color.Black else OnSurface, fontSize = 12.sp)
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(text, color = Color(0x59FFFFFF), fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AppTile(tile: Tile, requester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    if (requester != null) {
        LaunchedEffect(requester) { runCatching { requester.requestFocus() } }
    }
    Box(
        Modifier
            .size(TileW, TileH)
            .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(tile.label, color = OnSurface, fontSize = 13.sp)
    }
}

@Composable
private fun InputTile(tile: Tile) {
    var focused by remember { mutableStateOf(false) }
    val isCurrent = tile.sub == "Current"
    Box(
        Modifier
            .size(110.dp, TileH)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) Current else Color(0xFF2A3444))
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tile.label, color = Color.White, fontSize = 13.sp)
            tile.sub?.let { Text(it, color = Color(0xCCFFFFFF), fontSize = 11.sp) }
        }
    }
}
