package app.orbit.launcher

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val SafeH = 48.dp
private val SafeV = 27.dp
private val TileW = 140.dp
private val TileH = 80.dp

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
    val inputs by produceState(initialValue = emptyList<TvInput>()) {
        value = withContext(Dispatchers.IO) { InputRepository.load(context) }
    }
    val revision = FavoriteStore.revision
    val favorites = remember(revision) { FavoriteStore.favorites(context, FavoriteStore.INPUTS) }
    val ordered = remember(inputs) { FavoriteStore.arrange(inputs, favorites) { it.id } }

    val first = remember { FocusRequester() }
    LaunchedEffect(ordered.firstOrNull()?.id) {
        if (ordered.isEmpty()) return@LaunchedEffect
        repeat(30) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(16)
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
            if (favorites.isEmpty()) "Select an input to add it to the row."
            else "Select to add or remove. ${favorites.size} chosen.",
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(18.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(TileW + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = SafeV),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(ordered, key = { _, input -> input.id }) { index, input ->
                Tile(
                    input = input,
                    favorite = input.id in favorites,
                    requester = if (index == 0) first else null,
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
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(TileW, TileH)
            .scale(if (focused) 1.06f else 1f)
            .alpha(if (input.connected) 1f else 0.45f)
            .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2A3444))
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            ),
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
