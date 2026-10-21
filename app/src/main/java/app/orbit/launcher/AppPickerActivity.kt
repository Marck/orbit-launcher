package app.orbit.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SafeH = 48.dp
private val SafeV = 27.dp
private val TileW = 111.dp
private val TileH = 66.dp

// The grid clips to its own bounds, so a focused first-row tile needs this much room above it to
// grow into, ring and all. It comes out of the gap above the grid, so nothing moves.
private val PopRoom = 6.dp

private val Surface = Color(0xFF14181F)
private val Muted = Color(0xFF8B93A4)
private val Chosen = Color(0xFF2E9E4F)

/** The grid behind the add tile: every app, with the favorites already ticked. */
class AppPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PickerScreen() }
    }
}

@Composable
private fun PickerScreen() {
    val context = LocalContext.current
    val apps by produceState(initialValue = AppRepository.cached()) {
        value = withContext(Dispatchers.IO) { AppRepository.load(context) }
    }
    val revision = FavoriteStore.revision
    val favorites = remember(revision) { FavoriteStore.favorites(context, FavoriteStore.APPS) }
    // Deliberately keyed on the apps alone, so toggling a favorite does not reshuffle the grid
    // under the owner's hand while they are still choosing. Keyed on their package names rather
    // than the list itself: the fresh read that replaces the cached one is a new list of the same
    // apps, and it must not rearrange the grid with the favorites as they have been toggled since.
    // Only the order is kept, so the tiles still show the fresh read's names and banners.
    val order = remember(apps.map { it.packageName }) {
        FavoriteStore.arrange(apps, favorites) { it.packageName }.map { it.packageName }
    }
    val ordered = remember(order, apps) {
        val byName = apps.associateBy { it.packageName }
        order.mapNotNull { byName[it] }
    }

    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Focus starts on the first tile, and only until a tile has had it. A list that lands later
    // must not pull the cursor back to the start, unless the tile under it is no longer there.
    var current by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(order) {
        if (current in order) return@LaunchedEffect
        val first = order.firstOrNull() ?: return@LaunchedEffect
        repeat(30) {
            if (runCatching { requesters[first]?.requestFocus() != null }.getOrDefault(false)) {
                return@LaunchedEffect
            }
            delay(16)
        }
    }

    // The ends of the grid are joined up, so reaching the far end is one press either way.
    fun jumpTo(index: Int, id: String) {
        scope.launch {
            runCatching { gridState.scrollToItem(index) }
            repeat(30) {
                if (runCatching { requesters[id]?.requestFocus() != null }.getOrDefault(false)) {
                    return@launch
                }
                delay(16)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = SafeH, vertical = SafeV)
    ) {
        Text("Favorite apps", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(6.dp))
        Text(
            if (favorites.isEmpty()) "Select an app to add it to the row."
            else "Select to add or remove. ${favorites.size} chosen.",
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(18.dp - PopRoom))

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(TileW + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = PopRoom, bottom = SafeV),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(ordered, key = { _, app -> app.packageName }) { index, app ->
                PickerTile(
                    app = app,
                    favorite = app.packageName in favorites,
                    requester = requesters.getOrPut(app.packageName) { FocusRequester() },
                    onFocused = { current = app.packageName },
                    onEdgeLeft = if (index == 0) {
                        { ordered.lastOrNull()?.let { jumpTo(ordered.lastIndex, it.packageName) } }
                    } else {
                        null
                    },
                    onEdgeRight = if (index == ordered.lastIndex) {
                        { ordered.firstOrNull()?.let { jumpTo(0, it.packageName) } }
                    } else {
                        null
                    },
                ) {
                    FavoriteStore.toggle(context, FavoriteStore.APPS, app.packageName)
                }
            }
        }
    }
}

@Composable
private fun PickerTile(
    app: LaunchableApp,
    favorite: Boolean,
    requester: FocusRequester?,
    onFocused: () -> Unit,
    onEdgeLeft: (() -> Unit)? = null,
    onEdgeRight: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    // The clickable is the tile's only focus target. A focusable() in front of it would make a
    // second one: OK would move focus into the clickable, and Back would then only move it out
    // again instead of leaving the picker. Read with hasFocus all the same, so the highlight does
    // not depend on which node inside the tile holds focus.
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(TileW, TileH)
                .scale(if (focused) 1.06f else 1f)
                .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged {
                    focused = it.hasFocus
                    if (it.hasFocus) onFocused()
                }
                .onPreviewKeyEvent { event ->
                    val down = event.type == KeyEventType.KeyDown
                    when {
                        event.key == Key.DirectionLeft && onEdgeLeft != null -> {
                            if (down) onEdgeLeft(); true
                        }
                        event.key == Key.DirectionRight && onEdgeRight != null -> {
                            if (down) onEdgeRight(); true
                        }
                        else -> false
                    }
                }
                // No indication: now that the clickable holds focus itself, its focus layer would
                // draw a dark wash over the tile and its ring. The ring and the pop-out already say
                // which tile it is.
                .clickable(interactionSource = null, indication = null, onClick = onClick)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = remember(app.packageName) {
                runCatching { app.art?.toBitmap()?.asImageBitmap() }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.label,
                    contentScale = if (app.isBanner) ContentScale.Fit else ContentScale.Inside,
                    modifier = Modifier.fillMaxSize().padding(if (app.isBanner) 0.dp else 12.dp),
                )
            } else {
                Text(app.label, color = Color.White, fontSize = 12.sp)
            }

            if (favorite) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Chosen),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            color = if (focused) Color.White else Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
