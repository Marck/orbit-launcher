package app.orbit.launcher

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap

/** The tiles the rows are made of, and the remote keys that act on them. */

internal const val ADD_TILE = "add-favorite"
internal const val ADD_INPUT_TILE = "add-input"

/**
 * Keys for a tile that can be moved: feeds each one to the tile's [TileKeys] and does what it
 * says, which is where the rules and their reasons live.
 */
@Composable
internal fun Modifier.tileKeys(
    editing: Boolean,
    onMenu: () -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
    onEdgeLeft: (() -> Unit)? = null,
    onEdgeRight: (() -> Unit)? = null,
): Modifier {
    val keys = remember { TileKeys() }
    return onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        val action = keys.onKey(
            key = when (event.key) {
                Key.DirectionCenter, Key.Enter -> TileKey.OK
                Key.DirectionLeft -> TileKey.LEFT
                Key.DirectionRight -> TileKey.RIGHT
                Key.Back -> TileKey.BACK
                Key.Menu -> TileKey.MENU
                else -> TileKey.OTHER
            },
            phase = when (event.type) {
                KeyEventType.KeyDown -> KeyPhase.DOWN
                KeyEventType.KeyUp -> KeyPhase.UP
                else -> KeyPhase.OTHER
            },
            repeatCount = native.repeatCount,
            longPress = (native.flags and android.view.KeyEvent.FLAG_LONG_PRESS) != 0,
            downTime = native.downTime,
            now = SystemClock.elapsedRealtime(),
            editing = editing,
            edgeLeft = onEdgeLeft != null,
            edgeRight = onEdgeRight != null,
        )
        when (action) {
            TileAction.Pass -> return@onPreviewKeyEvent false
            TileAction.Swallow -> Unit
            TileAction.Menu -> onMenu()
            is TileAction.Move -> onMove(action.delta)
            is TileAction.EndEdit -> onEndEdit(action.revert)
            TileAction.EdgeLeft -> onEdgeLeft?.invoke()
            TileAction.EdgeRight -> onEdgeRight?.invoke()
        }
        true
    }
}

@Composable
internal fun AppTile(
    app: LaunchableApp,
    editing: Boolean,
    held: Boolean,
    requester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onMenu: (Rect) -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
    onEdgeLeft: (() -> Unit)? = null,
) {
    // Read with hasFocus: while OK is down, Compose hands focus to the clickable inside the tile,
    // and the tile should stay lit through the press, and through a hold that opens its menu.
    var focused by remember { mutableStateOf(false) }
    // Stays lit while its menu is open, the way the stock launcher keeps the tile up behind it.
    val lit = focused || held
    val spot = remember { Spot() }
    Column(
        Modifier
            .zIndex(if (lit) 1f else 0f)
            .onGloballyPositioned { spot.whole = it.rootBounds() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(TileW, TileH)
                .popOut(lit)
                .onGloballyPositioned { spot.tile = it.rootBounds() }
                .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged {
                    focused = it.hasFocus
                    if (it.hasFocus) onFocused()
                }
                .tileKeys(editing, { onMenu(spot.rect()) }, onMove, onEndEdit, onEdgeLeft = onEdgeLeft)
                .focusable()
                .clickable(onClick = onClick)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .border(
                    width = if (editing) 3.dp else 0.dp,
                    color = if (editing) Editing else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Keyed on the artwork itself, which the list hands back unchanged until the app is
            // updated. A new banner then shows on the next return, and nothing else is redrawn.
            val bitmap = remember(app.art) {
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
                // Clear of the focus ring, which is drawn over the tile's edge.
                Text(
                    app.label,
                    color = OnSurface,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(LabelGap))
        TileLabel(
            text = when {
                editing -> "Left or right to move"
                lit -> app.label
                else -> ""
            },
            width = TileW,
            color = if (editing) Editing else Color.White,
        )
    }
}

@Composable
internal fun PlusTile(
    requester: FocusRequester,
    onFocused: () -> Unit,
    onEdgeRight: (() -> Unit)?,
    width: Dp = TileW,
    label: String = "Add app to favorites",
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.zIndex(if (focused) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width, TileH)
                .popOut(focused)
                .focusRequester(requester)
                .onFocusChanged {
                    focused = it.hasFocus
                    if (it.hasFocus) onFocused()
                }
                .tileKeys(
                    editing = false,
                    onMenu = {},
                    onMove = {},
                    onEndEdit = {},
                    onEdgeRight = onEdgeRight,
                )
                .focusable()
                .clickable(onClick = onClick)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E2838))
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = Color(0xFF9FC3FF), fontSize = 26.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(LabelGap))
        TileLabel(if (focused) label else "", width = width)
    }
}

/**
 * One line under a tile, held to the tile's width so a long label cannot widen the tile and
 * shove the row. A label that does not fit scrolls through, as the stock launcher's do, rather
 * than being cut short.
 */
@Composable
internal fun TileLabel(text: String, width: Dp, color: Color = Color.White) {
    Box(Modifier.width(width), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
        )
    }
}

/**
 * Grows a lit tile, quickly but not instantly, so focus reads as the tile lifting towards you.
 *
 * A layer scale rather than a size change, so its neighbours stay where they are and the grown
 * tile overlaps the gap, as on the stock launcher.
 */
@Composable
internal fun Modifier.popOut(lit: Boolean): Modifier {
    val scale by animateFloatAsState(if (lit) FocusScale else 1f, tween(150), label = "pop-out")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
internal fun InputTile(
    label: String,
    sub: String?,
    highlighted: Boolean,
    dimmed: Boolean,
    editing: Boolean,
    held: Boolean,
    requester: FocusRequester?,
    onFocused: () -> Unit,
    onMenu: (Rect) -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
    onEdgeLeft: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val lit = focused || held
    val spot = remember { Spot() }
    Box(
        Modifier
            .zIndex(if (lit) 1f else 0f)
            .size(InputW, TileH)
            .popOut(lit)
            .onGloballyPositioned { spot.tile = it.rootBounds() }
            .alpha(if (dimmed) 0.45f else 1f)
            .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocused()
            }
            .tileKeys(editing, { onMenu(spot.rect()) }, onMove, onEndEdit, onEdgeLeft = onEdgeLeft)
            .focusable()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) LastInput else Color(0xFF2A3444))
            .border(
                width = if (editing) 3.dp else 0.dp,
                color = if (editing) Editing else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            // Lit, a name too long for the tile goes to one line and scrolls through, the same
            // as the labels under the app tiles. Unlit, it wraps and is cut short.
            val scroll = if (lit) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = if (sub == null && !lit) 2 else 1,
                overflow = if (lit) TextOverflow.Clip else TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = scroll,
            )
            sub?.let {
                Text(
                    it,
                    color = Color(0xCCFFFFFF),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = if (lit) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = scroll,
                )
            }
        }
    }
}
