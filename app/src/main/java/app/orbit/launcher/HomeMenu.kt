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
import androidx.compose.material.icons.filled.Settings
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

/**
 * What the system would accept for an app, which decides both whether the uninstall entry is
 * offered and how it has to be worded.
 */
internal enum class Removal {
    /** A factory app that never updated. It cannot be removed at all, so nothing is offered. */
    NONE,

    /** A factory app that has since updated itself: the update comes off, the app stays. */
    UPDATES,

    /** An app the owner installed, which goes entirely. */
    WHOLE,
}

/** Which of the three [app] is. The rules themselves are AppRepository's; this names them. */
internal fun removalFor(app: LaunchableApp): Removal = when {
    app.removable -> Removal.WHOLE
    app.factoryUpdated -> Removal.UPDATES
    else -> Removal.NONE
}

/** What the long-press menu is acting on. */
internal data class TileMenu(
    val key: String,
    val id: String,
    val label: String,
    /** The tile and its label on screen, for the menu to point at and to leave undimmed. */
    val anchor: Rect,
    /** The app the tile stands for, or null for an input, which is not an app at all. */
    val packageName: String? = null,
    /**
     * What can be taken off. The update coming off but not the app has to be said, since calling
     * that "Uninstall" would promise to remove an app that is still going to be there afterwards.
     */
    val removal: Removal = Removal.NONE,
    /**
     * Whether the app is a favorite as the menu opens, which decides whether the entry adds or
     * removes. The rows only hold favorites, so it is only the picker that can open a menu on an
     * app that is not one yet.
     */
    val favorite: Boolean = true,
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
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    onUninstall: (() -> Unit)? = null,
    onManage: (() -> Unit)? = null,
) {
    // Each entry is offered only where it means something. The rows offer all four; the picker
    // leaves Open and Move out, since a press there already opens or adds and the order of the
    // row is not what that screen is for.
    val items = buildList {
        onOpen?.let { add(MenuItem("Open", Glyphs.Input, it)) }
        onMove?.let { add(MenuItem("Move", Glyphs.Swap, it)) }
        onFavorite?.let {
            add(
                MenuItem(
                    if (target.favorite) "Remove from favorites" else "Add to favorites",
                    if (target.favorite) Glyphs.HeartMinus else Glyphs.HeartPlus,
                    it,
                )
            )
        }
        if (target.packageName != null) when (target.removal) {
            Removal.WHOLE -> onUninstall?.let {
                add(MenuItem("Uninstall", Icons.Outlined.Delete, it))
            }
            Removal.UPDATES -> onUninstall?.let {
                add(MenuItem("Uninstall updates", Icons.Outlined.Delete, it))
            }
            // Nothing can be taken off, so the entry would be a button that cannot work. The
            // television's own page for the app can still turn it off, which is the nearest
            // thing to removing one of the eighteen a set like this came with.
            Removal.NONE -> onManage?.let {
                add(MenuItem("Manage", Icons.Filled.Settings, it))
            }
        }
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
                // The tile exactly as it is drawn, and not a pixel more. The anchor is read
                // after the pop-out scale, so it already covers the grown tile; the 2dp that
                // used to be added above and below covered nothing and left four rows of
                // undimmed artwork showing. Measured on the set over a bright backdrop, those
                // rows came back at 104 against 19 for the scrim beside them, which is the
                // light bar that appeared across the top of a tile and under its label.
                clipPath(
                    Path().apply { addRoundRect(RoundRect(target.anchor, CornerRadius(12.dp.toPx()))) },
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
