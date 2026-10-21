package app.orbit.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SafeH = 48.dp
private val SafeV = 27.dp
private val TileW = 140.dp
private val TileH = 80.dp

// The grid clips to its own bounds, so a focused first-row tile needs this much room above it to
// grow into, ring and all. It comes out of the gap above the grid, so nothing moves.
private val PopRoom = 6.dp

private val Muted = Color(0xFF8B93A4)
private val Chosen = Color(0xFF2E9E4F)

/** The same idea as the app picker, for the inputs row. */
class InputPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Screen() }
    }
}

@Composable
private fun Screen() {
    val context = LocalContext.current
    // Null until the read is back, so a TV with no inputs at all can be told apart from one
    // that has not answered yet.
    val inputs by produceState<List<TvInput>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { InputRepository.load(context) }
    }
    val revision = FavoriteStore.revision
    val favorites = remember(revision) { FavoriteStore.favorites(context, FavoriteStore.INPUTS) }
    val ordered = remember(inputs) { FavoriteStore.arrange(inputs.orEmpty(), favorites) { it.id } }
    val chosen = ordered.count { it.id in favorites }

    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(ordered.firstOrNull()?.id) {
        val first = ordered.firstOrNull()?.id ?: return@LaunchedEffect
        repeat(30) {
            if (runCatching { requesters[first]?.requestFocus() != null }.getOrDefault(false)) {
                return@LaunchedEffect
            }
            delay(16)
        }
    }

    // The ends of the grid are joined up, as in the app picker and on the home rows.
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
        Text("Favorite inputs", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(6.dp))
        Text(
            // Counted from the inputs on the list: a favorite that is switched off is kept, but
            // there is nothing here to untick.
            if (chosen == 0) "Select an input to add it to the row."
            else "Select to add or remove. $chosen chosen.",
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(18.dp - PopRoom))

        // Said outright, so an empty screen does not look like one that is still loading.
        if (inputs?.isEmpty() == true) {
            Spacer(Modifier.height(PopRoom))
            Text("No inputs found on this TV.", color = Muted, fontSize = 14.sp)
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(TileW + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = PopRoom, bottom = SafeV),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(ordered, key = { _, input -> input.id }) { index, input ->
                Tile(
                    input = input,
                    favorite = input.id in favorites,
                    requester = requesters.getOrPut(input.id) { FocusRequester() },
                    onEdgeLeft = if (index == 0) {
                        { ordered.lastOrNull()?.let { jumpTo(ordered.lastIndex, it.id) } }
                    } else {
                        null
                    },
                    onEdgeRight = if (index == ordered.lastIndex) {
                        { ordered.firstOrNull()?.let { jumpTo(0, it.id) } }
                    } else {
                        null
                    },
                ) {
                    FavoriteStore.toggle(context, FavoriteStore.INPUTS, input.id)
                }
            }
        }
    }
}

@Composable
private fun Tile(
    input: TvInput,
    favorite: Boolean,
    requester: FocusRequester?,
    onEdgeLeft: (() -> Unit)? = null,
    onEdgeRight: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    // The clickable is the tile's only focus target. A focusable() in front of it would make a
    // second one: OK would move focus into the clickable, and Back would then only move it out
    // again instead of leaving the picker. Read with hasFocus all the same, so the highlight does
    // not depend on which node inside the tile holds focus.
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(TileW, TileH)
            .scale(if (focused) 1.06f else 1f)
            .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.hasFocus }
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
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            // A disconnected input is dimmed, but its ring is not: the ring sits outside the
            // dimming, so the tile in focus is just as plain to see as any other.
            .alpha(if (input.connected) 1f else 0.45f)
            .background(Color(0xFF2A3444)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                input.label,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
            input.sub?.let {
                Text(it, color = Color(0xCCFFFFFF), fontSize = 11.sp, maxLines = 1)
            }
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
}
