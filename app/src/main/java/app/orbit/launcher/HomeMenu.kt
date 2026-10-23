package app.orbit.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** The menu a tile opens on a long press of OK, and the sheet it is drawn in. */

/** What the long-press menu is acting on. */
internal data class TileMenu(
    val key: String,
    val id: String,
    val label: String,
    /** The tile and its label on screen, for the menu to point at and to leave undimmed. */
    val anchor: Rect,
    val packageName: String? = null,
)

internal class MenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The long-press menu, after the stock launcher's: a light card hanging from the tile, which
 * stays lit while the rest of the screen dims. Uninstall is deliberately handed to the system to
 * confirm.
 *
 * The card hangs below the tile when it fits and stands above it when it does not. With both
 * rows in the lower half of the screen, as they are now, it stands above in either row.
 */
@Composable
internal fun TileMenuSheet(
    target: TileMenu,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val items = buildList {
        add(MenuItem("Open", Glyphs.Input, onOpen))
        add(MenuItem("Move", Glyphs.Swap, onMove))
        add(MenuItem("Remove from favorites", Glyphs.HeartMinus, onRemove))
        if (target.packageName != null) add(MenuItem("Uninstall", Icons.Outlined.Delete, onUninstall))
    }
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    var current by remember { mutableIntStateOf(0) }
    var armed by remember(target.id) { mutableStateOf(false) }
    // Again on a return, which leaves nothing focused: the remote would otherwise reach the tiles
    // under the scrim while the menu is still up.
    LaunchedEffect(target.id, Returns.count) { requesters[current].focusWhenReady() }

    Layout(
        content = {
            Column(
                Modifier
                    .widthIn(min = 240.dp)
                    .width(IntrinsicSize.Max)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MenuCard),
            ) {
                items.forEachIndexed { index, item ->
                    MenuEntry(item, requesters[index], onFocused = { current = index })
                }
            }
            // The caret takes the colour of the entry it touches, so a lit entry runs into it.
            Caret(up = true, color = if (current == 0) MenuLit else MenuCard)
            Caret(up = false, color = if (current == items.lastIndex) MenuLit else MenuCard)
        },
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Only above and below: a grown tile already reaches into the gap beside it, and
                // widening the hole would light the edge of its neighbour.
                val pad = 2.dp.toPx()
                val hole = target.anchor.let { Rect(it.left, it.top - pad, it.right, it.bottom + pad) }
                clipPath(
                    Path().apply { addRoundRect(RoundRect(hole, CornerRadius(12.dp.toPx()))) },
                    clipOp = ClipOp.Difference,
                ) {
                    drawRect(Scrim)
                }
            }
            .onPreviewKeyEvent { event ->
                val up = event.type == KeyEventType.KeyUp
                when {
                    event.key == Key.Back -> { if (up) onDismiss(); true }
                    // The tiles are still there under the scrim, and focus must not wander off
                    // to them while the menu is open.
                    event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> true
                    event.key == Key.DirectionUp && current == 0 -> true
                    event.key == Key.DirectionDown && current == items.lastIndex -> true
                    // Holding OK opens the menu while OK is still down, and focus is already on
                    // Open when it comes back up. OK only counts once the menu has seen a fresh
                    // press of it, so the repeats and the release of that hold press nothing.
                    event.key == Key.DirectionCenter || event.key == Key.Enter ||
                        event.key == Key.NumPadEnter -> {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                            armed = true
                        }
                        !armed
                    }
                    else -> false
                }
            },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val card = measurables[0].measure(loose)
        val caretUp = measurables[1].measure(loose)
        val caretDown = measurables[2].measure(loose)
        val anchor = target.anchor
        val gap = 4.dp.roundToPx()
        val edgeX = SafeH.roundToPx()
        val edgeY = SafeV.roundToPx()

        val x = anchor.left.roundToInt()
            .coerceAtMost(constraints.maxWidth - edgeX - card.width)
            .coerceAtLeast(edgeX)
        // Pointing at the tile's left quarter rather than its middle, as the stock menu does,
        // so the label under the tile stays clear.
        val inset = 14.dp.roundToPx()
        val caretX = (anchor.left + anchor.width / 4).roundToInt() - caretUp.width / 2
        val caretLeft = caretX
            .coerceAtMost(x + card.width - inset - caretUp.width)
            .coerceAtLeast(x + inset)
        val below = anchor.bottom.roundToInt() + gap + caretUp.height + card.height <=
            constraints.maxHeight - edgeY

        layout(constraints.maxWidth, constraints.maxHeight) {
            // The card first and the caret over it, so no seam shows where they meet.
            if (below) {
                val caretTop = anchor.bottom.roundToInt() + gap
                card.place(x, caretTop + caretUp.height - 1)
                caretUp.place(caretLeft, caretTop)
            } else {
                val cardTop = (anchor.top.roundToInt() - gap - caretDown.height - card.height)
                    .coerceAtLeast(edgeY)
                card.place(x, cardTop)
                caretDown.place(caretLeft, cardTop + card.height - 1)
            }
        }
    }
}

@Composable
internal fun MenuEntry(item: MenuItem, requester: FocusRequester, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocused()
            }
            .clickable(onClick = item.onClick)
            .background(if (focused) MenuLit else Color.Transparent)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, contentDescription = null, tint = MenuInk, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(item.label, color = MenuInk, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
internal fun Caret(up: Boolean, color: Color) {
    Canvas(Modifier.size(18.dp, 9.dp)) {
        val tip = if (up) 0f else size.height
        val base = if (up) size.height else 0f
        drawPath(
            Path().apply {
                moveTo(0f, base)
                lineTo(size.width / 2, tip)
                lineTo(size.width, base)
                close()
            },
            color,
        )
    }
}
