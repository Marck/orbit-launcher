package app.orbit.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Sizes come straight from section B of the requirements.
private val SafeH = 48.dp
private val SafeV = 27.dp
private val TileW = 111.dp
private val TileH = 66.dp
// Narrow enough that a typical set's inputs fit the safe area without the row scrolling. A
// scrolling row slides its first tile out of line with the apps above it.
private val InputW = 100.dp
/** Both rows, so the inputs row holds still while its list is still loading too. */
private val RowHeight = 86.dp

private val Surface = Color(0xFF14181F)
private val OnSurface = Color(0xFFDDE5F0)
private val Muted = Color(0xFF8B93A4)
private val LastInput = Color(0xFF1AA3C4)
private val Editing = Color(0xFFFFC14D)
private val Glyph = Color(0xFFBFC9DA)
private val Scrim = Color(0xB3000000)
private val MenuCard = Color(0xFFDADCE0)
private val MenuLit = Color.White
private val MenuInk = Color(0xFF202124)

private const val ADD_TILE = "add-favourite"
private const val ADD_INPUT_TILE = "add-input"

/** What the long-press menu is acting on. */
private data class TileMenu(
    val key: String,
    val id: String,
    /** The tile and its label on screen, for the menu to point at and to leave undimmed. */
    val anchor: Rect,
    val packageName: String? = null,
)

/** Where a tile and its label sit on screen. Read only when its menu opens. */
private class Spot {
    var tile = Rect.Zero
    var whole = Rect.Zero

    /** The tile, stretched down over the label beneath it when there is one. */
    fun rect(): Rect = tile.copy(bottom = maxOf(tile.bottom, whole.bottom))
}

/**
 * Counts returns to the launcher.
 *
 * Compose does not restore focus across an activity switch, so coming back from an app or from
 * the picker leaves nothing focused and the remote does nothing at all. The apps row watches this
 * and takes focus back.
 */
private object Returns {
    var count by mutableIntStateOf(0)
}

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomeScreen() }
    }

    override fun onResume() {
        super.onResume()
        Returns.count++
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    // Off the main thread, all of it. produceState runs its producer on the composition's
    // context, so reading every launcher activity and decoding its artwork there holds up the
    // first frame and the screen stays black until it finishes.
    // Re-read on every return, so an app removed through the system dialog disappears here.
    val apps by produceState(initialValue = AppRepository.cached(), Returns.count) {
        value = withContext(Dispatchers.IO) { AppRepository.load(context) }
    }
    // Null until the first read lands. A TV can have no inputs at all, so an empty list cannot
    // stand in for "not read yet" the way it does for apps.
    val inputs by produceState<List<TvInput>?>(initialValue = null, Returns.count) {
        value = withContext(Dispatchers.IO) { InputRepository.load(context) }
    }
    val isDefaultHome by produceState(initialValue = true, Returns.count) {
        value = withContext(Dispatchers.IO) { AppRepository.isDefaultHome(context) }
    }
    var noticeDismissed by remember { mutableStateOf(false) }

    // Only the favourites, in the owner's order; everything else is one press of + away.
    // Reading the revision here is what updates the rows the moment a favourite changes,
    // without touching the device again.
    val revision = FavouriteStore.revision
    val appFavourites = remember(revision) { FavouriteStore.favourites(context, FavouriteStore.APPS) }
    val inputFavourites = remember(revision) { FavouriteStore.favourites(context, FavouriteStore.INPUTS) }
    val favouriteApps = remember(apps, appFavourites) {
        FavouriteStore.pick(apps, appFavourites) { it.packageName }
    }
    val favouriteInputs = remember(inputs, inputFavourites) {
        FavouriteStore.pick(inputs.orEmpty(), inputFavourites) { it.id }
    }

    var menu by remember { mutableStateOf<TileMenu?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var editingKey by remember { mutableStateOf(FavouriteStore.APPS) }
    var editSnapshot by remember { mutableStateOf<List<String>?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    // Closing the menu has to hand focus back to the tile it belonged to. Without this the
    // focus falls to whatever comes first on screen and the remote walks off somewhere else.
    var refocusApps by remember { mutableIntStateOf(0) }
    var refocusInputs by remember { mutableIntStateOf(0) }
    var known by remember { mutableStateOf<Set<String>>(emptySet()) }

    // An app can be removed from anywhere, so notice it by comparing successive reads rather
    // than by watching the uninstall we started.
    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        val present = apps.map { it.packageName }.toSet()
        if (known.isNotEmpty() && (known - present).isNotEmpty()) confirmation = "It's gone now"
        FavouriteStore.prune(context, FavouriteStore.APPS, present)
        known = present
    }
    LaunchedEffect(inputs) {
        val all = inputs
        if (all.isNullOrEmpty()) return@LaunchedEffect
        FavouriteStore.prune(context, FavouriteStore.INPUTS, all.map { it.id }.toSet())
    }
    LaunchedEffect(confirmation) {
        if (confirmation != null) {
            delay(2600)
            confirmation = null
        }
    }

    fun closeMenu(target: TileMenu) {
        menu = null
        if (target.key == FavouriteStore.APPS) refocusApps++ else refocusInputs++
    }

    fun beginEdit(key: String, id: String) {
        editSnapshot = FavouriteStore.favourites(context, key)
        editingKey = key
        editing = id
    }

    fun endEdit(revert: Boolean) {
        if (revert) editSnapshot?.let { FavouriteStore.replace(context, editingKey, it) }
        editing = null
        editSnapshot = null
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = SafeH, vertical = SafeV)) {
                TopBar()
                Spacer(Modifier.height(10.dp))
                Hero()
            }
            Spacer(Modifier.weight(1f))
            if (!isDefaultHome && !noticeDismissed) {
                Notice(
                    title = "Orbit is not the home app yet",
                    detail = "Set it as default so the home button opens Orbit",
                    onDetails = { context.startActivitySafely(Intent(Settings.ACTION_HOME_SETTINGS)) },
                    onDismiss = { noticeDismissed = true },
                )
                Spacer(Modifier.height(14.dp))
            }
            RowLabel(if (editing != null && editingKey == FavouriteStore.APPS) "APPS - MOVING, OK TO KEEP" else "APPS")
            AppsRow(
                apps = favouriteApps,
                // A TV always has some app installed, so an empty list means not read yet.
                ready = apps.isNotEmpty(),
                held = menu?.takeIf { it.key == FavouriteStore.APPS }?.id,
                refocus = refocusApps,
                editing = editing.takeIf { editingKey == FavouriteStore.APPS },
                onMenu = { app, anchor ->
                    menu = TileMenu(
                        key = FavouriteStore.APPS,
                        id = app.packageName,
                        anchor = anchor,
                        // Only set when the system would actually accept an uninstall, which
                        // is what decides whether the entry is offered at all.
                        packageName = app.packageName.takeIf { app.removable },
                    )
                },
                onMove = { delta -> editing?.let { FavouriteStore.move(context, FavouriteStore.APPS, it, delta) } },
                onEndEdit = { revert -> endEdit(revert) },
            )
            Spacer(Modifier.height(20.dp))
            RowLabel(if (editing != null && editingKey == FavouriteStore.INPUTS) "INPUTS - MOVING, OK TO KEEP" else "INPUTS")
            InputsRow(
                inputs = favouriteInputs,
                ready = inputs != null,
                held = menu?.takeIf { it.key == FavouriteStore.INPUTS }?.id,
                refocus = refocusInputs,
                editing = editing.takeIf { editingKey == FavouriteStore.INPUTS },
                onMenu = { input, anchor ->
                    menu = TileMenu(key = FavouriteStore.INPUTS, id = input.id, anchor = anchor)
                },
                onMove = { delta -> editing?.let { FavouriteStore.move(context, FavouriteStore.INPUTS, it, delta) } },
                onEndEdit = { revert -> endEdit(revert) },
            )
        }

        confirmation?.let { Confirmation(it) }

        menu?.let { target ->
            TileMenuSheet(
                target = target,
                onOpen = {
                    closeMenu(target)
                    if (target.key == FavouriteStore.APPS) {
                        context.launchApp(target.id)
                    } else {
                        context.switchToInput(target.id)
                    }
                },
                onRemove = {
                    FavouriteStore.remove(context, target.key, target.id)
                    confirmation = "Removed from favourites"
                    closeMenu(target)
                },
                onMove = {
                    closeMenu(target)
                    beginEdit(target.key, target.id)
                },
                onUninstall = {
                    closeMenu(target)
                    target.packageName?.let { pkg ->
                        // The white frame before the dialog is the system uninstaller's own
                        // starting window, drawn from its light theme. FLAG_ACTIVITY_NO_ANIMATION
                        // was tried and does not suppress it; another app's window is not ours
                        // to restyle.
                        context.startActivitySafely(
                            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))
                        )
                    }
                },
                onDismiss = { closeMenu(target) },
            )
        }
    }
}

@Composable
private fun Confirmation(text: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = SafeH, vertical = SafeV)) {
        Text(
            text,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xCC1E2838))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

private class MenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The long-press menu, after the stock launcher's: a light card hanging from the tile, which
 * stays lit while the rest of the screen dims. Uninstall is deliberately handed to the system to
 * confirm.
 *
 * The card hangs below the tile when it fits and stands above it when it does not, which is
 * always the case for the inputs row at the bottom of the screen.
 */
@Composable
private fun TileMenuSheet(
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
        add(MenuItem("Remove from favourites", Glyphs.HeartMinus, onRemove))
        if (target.packageName != null) add(MenuItem("Uninstall", Icons.Outlined.Delete, onUninstall))
    }
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    var current by remember { mutableIntStateOf(0) }
    LaunchedEffect(target.id) { requesters.first().focusWhenReady() }

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
                val hole = target.anchor.inflate(2.dp.toPx())
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
private fun MenuEntry(item: MenuItem, requester: FocusRequester, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
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
private fun Caret(up: Boolean, color: Color) {
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

@Composable
private fun AppsRow(
    apps: List<LaunchableApp>,
    ready: Boolean,
    held: String?,
    refocus: Int,
    editing: String?,
    onMenu: (LaunchableApp, Rect) -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    // The tile to come back to, by id and by place. Focus is only ever requested from the effect
    // below, never from a tile's own composition, which is what used to yank focus back while
    // arrowing left.
    var lastFocused by remember { mutableStateOf<String?>(null) }
    var lastIndex by remember { mutableIntStateOf(0) }
    val ids = remember(apps) { apps.map { it.packageName } + ADD_TILE }
    val firstPackage = apps.firstOrNull()?.packageName
    val scope = rememberCoroutineScope()

    fun jumpTo(index: Int, id: String) {
        scope.launch {
            runCatching { listState.scrollToItem(index) }
            requesters[id].focusWhenReady()
        }
    }

    // Only when the tile being moved has actually left the screen. Scrolling on every swap
    // drags it to the left edge, which reads as the row jumping rather than the tile moving.
    LaunchedEffect(editing, apps) {
        val moving = editing ?: return@LaunchedEffect
        val index = apps.indexOfFirst { it.packageName == moving }
        if (index < 0) return@LaunchedEffect
        if (!listState.isFullyShown(index)) runCatching { listState.animateScrollToItem(index) }
    }

    // Keyed on the row's contents, not just its first entry: when the tile that had focus leaves
    // the favourites or the TV, nothing else would re-run and the focus would be left on a tile
    // that no longer exists. The one that slid into its place takes the focus instead.
    LaunchedEffect(ids, ready, Returns.count, refocus) {
        if (!ready) return@LaunchedEffect
        val target = lastFocused?.takeIf { it in ids } ?: ids[lastIndex.coerceAtMost(ids.lastIndex)]
        val index = ids.indexOf(target)
        // The row only composes what is on screen, so the tile has to be scrolled into view
        // before its requester exists at all. Not during a move, which reorders the row on every
        // press and does its own scrolling above.
        if (editing == null && !listState.isFullyShown(index)) {
            runCatching { listState.scrollToItem(index) }
        }
        // Items are placed during layout, so the requester is not attached on the frame the
        // list arrives. Keep asking until it takes.
        repeat(30) {
            if (runCatching { requesters[target]?.requestFocus() != null }.getOrDefault(false)) {
                return@LaunchedEffect
            }
            delay(16)
        }
    }

    LazyRow(
        // Fixed so the rows below hold still while the list is still loading.
        modifier = Modifier.height(RowHeight),
        state = listState,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = SafeH),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppTile(
                app = app,
                editing = editing == app.packageName,
                held = held == app.packageName,
                requester = requesters.getOrPut(app.packageName) { FocusRequester() },
                onFocused = {
                    lastFocused = app.packageName
                    lastIndex = index
                },
                onClick = { context.launchApp(app.packageName) },
                onMenu = { anchor -> onMenu(app, anchor) },
                onMove = onMove,
                onEndEdit = onEndEdit,
                onEdgeLeft = if (index == 0) {
                    { jumpTo(apps.size, ADD_TILE) }
                } else {
                    null
                },
            )
        }
        if (ready) item(key = ADD_TILE) {
            PlusTile(
                requester = requesters.getOrPut(ADD_TILE) { FocusRequester() },
                onFocused = {
                    lastFocused = ADD_TILE
                    lastIndex = apps.size
                },
                onEdgeRight = { firstPackage?.let { jumpTo(0, it) } },
            ) { context.startActivitySafely(Intent(context, AppPickerActivity::class.java)) }
        }
    }
}

@Composable
private fun InputsRow(
    inputs: List<TvInput>,
    ready: Boolean,
    held: String?,
    refocus: Int,
    editing: String?,
    onMenu: (TvInput, Rect) -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lastInput = remember(inputs) { InputRepository.lastUsedInput(context) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastFocused by remember { mutableStateOf<String?>(null) }
    var lastIndex by remember { mutableIntStateOf(0) }
    val ids = remember(inputs) { inputs.map { it.id } + ADD_INPUT_TILE }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun jumpTo(index: Int, id: String) {
        scope.launch {
            runCatching { listState.scrollToItem(index) }
            requesters[id].focusWhenReady()
        }
    }

    // Only ever on an explicit request, never on composition, so the row cannot steal focus
    // from the apps above it.
    LaunchedEffect(refocus) {
        if (refocus == 0 || lastFocused == null) return@LaunchedEffect
        val target = lastFocused?.takeIf { it in ids } ?: ids[lastIndex.coerceAtMost(ids.lastIndex)]
        val index = ids.indexOf(target)
        if (!listState.isFullyShown(index)) runCatching { listState.scrollToItem(index) }
        repeat(30) {
            if (runCatching { requesters[target]?.requestFocus() != null }.getOrDefault(false)) {
                return@LaunchedEffect
            }
            delay(16)
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth().height(RowHeight),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        contentPadding = PaddingValues(horizontal = SafeH),
    ) {
        itemsIndexed(inputs, key = { _, input -> input.id }) { index, input ->
            InputTile(
                label = input.label,
                sub = input.sub,
                highlighted = input.id == lastInput,
                dimmed = !input.connected,
                editing = editing == input.id,
                held = held == input.id,
                requester = requesters.getOrPut(input.id) { FocusRequester() },
                onFocused = {
                    lastFocused = input.id
                    lastIndex = index
                },
                onMenu = { anchor -> onMenu(input, anchor) },
                onMove = onMove,
                onEndEdit = onEndEdit,
                onEdgeLeft = if (index == 0) {
                    { jumpTo(inputs.size, ADD_INPUT_TILE) }
                } else {
                    null
                },
            ) { context.switchToInput(input.id) }
        }
        if (ready) item(key = ADD_INPUT_TILE) {
            PlusTile(
                requester = requesters.getOrPut(ADD_INPUT_TILE) { FocusRequester() },
                onFocused = {
                    lastFocused = ADD_INPUT_TILE
                    lastIndex = inputs.size
                },
                onEdgeRight = { inputs.firstOrNull()?.let { jumpTo(0, it.id) } },
                width = InputW,
                label = "Add input to favourites",
            ) { context.startActivitySafely(Intent(context, InputPickerActivity::class.java)) }
        }
    }
}

@Composable
private fun TopBar() {
    val now by produceState(initialValue = Date()) {
        while (true) {
            value = Date()
            delay(10_000)
        }
    }
    val date = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()) }
    val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(date.format(now), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Shortcuts()
        Avatar()
        Spacer(Modifier.width(9.dp))
        Text(time.format(now), color = Muted, fontSize = 12.sp)
    }
}

/**
 * The way out to the TV's own screens, left of the profile picture as on the stock launcher.
 *
 * Each is offered only when the TV has somewhere for it to go.
 */
@Composable
private fun Shortcuts() {
    val context = LocalContext.current
    val inputsIntent = remember { InputRepository.allInputsIntent(context) }
    val storeIntent = remember { InputRepository.storeIntent(context) }
    val settingsIntent = remember { InputRepository.settingsIntent() }
    // The store's own icon, so it reads as the store at a glance. Inputs and settings have no
    // icon worth borrowing, so they get the same glyphs the stock top bar uses.
    val storeIcon = remember(storeIntent) {
        runCatching {
            InputRepository.iconFor(context, storeIntent)?.toBitmap()?.asImageBitmap()
        }.getOrNull()
    }

    if (inputsIntent != null) {
        TopBarButton(onClick = { context.startActivitySafely(inputsIntent) }) {
            Icon(
                imageVector = Glyphs.Input,
                contentDescription = "All inputs",
                tint = Glyph,
                modifier = Modifier.padding(4.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
    }
    if (storeIntent != null) {
        TopBarButton(onClick = { context.startActivitySafely(storeIntent) }) {
            if (storeIcon != null) {
                Image(
                    bitmap = storeIcon,
                    contentDescription = "Play Store",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.ShoppingCart,
                    contentDescription = "Play Store",
                    tint = Glyph,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
        Spacer(Modifier.width(9.dp))
    }
    TopBarButton(onClick = { context.startActivitySafely(settingsIntent) }) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Settings",
            tint = Glyph,
            modifier = Modifier.padding(3.dp),
        )
    }
    Spacer(Modifier.width(9.dp))
}

@Composable
private fun Avatar() {
    val context = LocalContext.current
    // Keyed on the revision so a picture chosen in the picker shows on return.
    val picture by produceState<android.graphics.Bitmap?>(initialValue = null, Profile.revision) {
        value = withContext(Dispatchers.IO) { Profile.avatar(context) }
    }
    TopBarButton(
        onClick = { context.startActivitySafely(Intent(context, AvatarPickerActivity::class.java)) },
    ) {
        val bitmap = picture
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = "Profile",
                tint = Glyph,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A small round button in the top bar. */
@Composable
private fun TopBarButton(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(21.dp)
            .scale(if (focused) 1.15f else 1f)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .clip(CircleShape)
            .background(Color(0xFF2B3242))
            // A faint ring always, so a dark picture still reads as a disc rather than a hole.
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x3DFFFFFF),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
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
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("S2 E5 - THE SUB-LEVEL - 33 MIN LEFT", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(225.dp).height(2.dp).background(Color(0x33FFFFFF))) {
            Box(Modifier.width(60.dp).height(2.dp).background(Color.White))
        }
    }
}

@Composable
private fun Notice(title: String, detail: String, onDetails: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xA60B1017))
            .padding(horizontal = SafeH, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
        Pill("Details", onDetails)
        Spacer(Modifier.width(9.dp))
        Pill("Dismiss", onDismiss)
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color(0x1FFFFFFF))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (focused) Color.Black else OnSurface, fontSize = 12.sp)
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(text, color = Color(0x59FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(horizontal = SafeH))
    Spacer(Modifier.height(8.dp))
}


/**
 * Requests focus once the target exists.
 *
 * A row composes only what is on screen, so after scrolling to the far end the requester there
 * is not attached for another frame or two.
 */
private suspend fun FocusRequester?.focusWhenReady(): Boolean {
    repeat(30) {
        if (runCatching { this?.requestFocus() != null }.getOrDefault(false)) return true
        delay(16)
    }
    return false
}

/** True when the item at [index] is on screen from edge to edge, so focusing it scrolls nothing. */
private fun LazyListState.isFullyShown(index: Int): Boolean {
    val layout = layoutInfo
    return layout.visibleItemsInfo.any {
        it.index == index && it.offset >= 0 && it.offset + it.size <= layout.viewportEndOffset
    }
}

/** How long OK must be held for the menu rather than a launch, if no long-press flag arrives. */
private const val LongPressMs = 500L

/** Android marks a held key with a flag and a repeat, which is the signal to trust. */
private fun androidx.compose.ui.input.key.KeyEvent.isHeld(): Boolean {
    val native = nativeKeyEvent
    return native.repeatCount > 0 ||
        (native.flags and android.view.KeyEvent.FLAG_LONG_PRESS) != 0
}

/**
 * Keys for a tile that can be moved.
 *
 * The hold is measured here rather than left to `combinedClickable`, whose long-press handling
 * for hardware keys is not something to depend on sight unseen. Holding OK opens the menu and
 * swallows the release so the tile does not also activate; a tap falls through untouched. Menu
 * does the same, for remotes that have the key.
 *
 * While moving, every key is swallowed: left and right reorder instead of changing focus, OK
 * keeps the new place, Back puts the order back.
 */
@Composable
private fun Modifier.tileKeys(
    editing: Boolean,
    onMenu: () -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
    onEdgeLeft: (() -> Unit)? = null,
    onEdgeRight: (() -> Unit)? = null,
): Modifier {
    var pressedAt by remember { mutableLongStateOf(0L) }
    // Identified by the press itself, because opening the menu moves focus away and the tile
    // never sees the release. A flag cleared on release would stay set and eat the next hold.
    var handledPress by remember { mutableLongStateOf(0L) }
    return onPreviewKeyEvent { event ->
        val down = event.type == KeyEventType.KeyDown
        val up = event.type == KeyEventType.KeyUp
        val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
        when {
            editing && event.key == Key.DirectionLeft -> { if (down) onMove(-1); true }
            editing && event.key == Key.DirectionRight -> { if (down) onMove(1); true }
            editing && ok -> { if (up) onEndEdit(false); true }
            editing && event.key == Key.Back -> { if (up) onEndEdit(true); true }
            editing -> true
            // At either end, left and right jump to the other end rather than stopping dead.
            down && event.key == Key.DirectionLeft && onEdgeLeft != null -> { onEdgeLeft(); true }
            down && event.key == Key.DirectionRight && onEdgeRight != null -> { onEdgeRight(); true }
            event.key == Key.DirectionLeft && onEdgeLeft != null -> true
            event.key == Key.DirectionRight && onEdgeRight != null -> true
            up && event.key == Key.Menu -> { onMenu(); true }
            ok && down && event.isHeld() -> {
                // The system has decided this is a hold. Auto-repeat keeps sending downs, so
                // act once per press.
                val press = event.nativeKeyEvent.downTime
                if (handledPress != press) {
                    handledPress = press
                    onMenu()
                }
                true
            }
            ok && down -> {
                if (pressedAt == 0L) pressedAt = SystemClock.elapsedRealtime()
                false
            }
            ok && up -> {
                val press = event.nativeKeyEvent.downTime
                val duration = SystemClock.elapsedRealtime() - pressedAt
                val longByTime = pressedAt != 0L && duration >= LongPressMs
                pressedAt = 0L
                when {
                    // The menu is already open for this press; swallow the release so the tile
                    // does not activate as well.
                    handledPress == press -> true
                    // A hold that never produced a flag still counts, so a remote that does not
                    // send one is not left without the menu.
                    longByTime -> { onMenu(); true }
                    else -> false
                }
            }
            else -> false
        }
    }
}


@Composable
private fun AppTile(
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
    var focused by remember { mutableStateOf(false) }
    // Stays lit while its menu is open, the way the stock launcher keeps the tile up behind it.
    val lit = focused || held
    val spot = remember { Spot() }
    Column(
        Modifier.onGloballyPositioned { spot.whole = it.rootBounds() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(TileW, TileH)
                .scale(if (lit) 1.06f else 1f)
                .onGloballyPositioned { spot.tile = it.rootBounds() }
                .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .tileKeys(editing, { onMenu(spot.rect()) }, onMove, onEndEdit, onEdgeLeft = onEdgeLeft)
                .focusable()
                .clickable(onClick = onClick)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .border(
                    width = if (editing || lit) 3.dp else 0.dp,
                    color = if (editing) Editing else if (lit) Color.White else Color.Transparent,
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
                Text(app.label, color = OnSurface, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                editing -> "Left or right to move"
                lit -> app.label
                else -> ""
            },
            color = if (editing) Editing else Color.White,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlusTile(
    requester: FocusRequester,
    onFocused: () -> Unit,
    onEdgeRight: (() -> Unit)?,
    width: Dp = TileW,
    label: String = "Add app to favourites",
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width, TileH)
                .scale(if (focused) 1.06f else 1f)
                .focusRequester(requester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
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
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = Color(0xFF9FC3FF), fontSize = 26.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(6.dp))
        // Held to the tile's width, so a long label cannot widen the tile and shove the row.
        Text(
            if (focused) label else "",
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(width),
        )
    }
}

@Composable
private fun InputTile(
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
            .size(InputW, TileH)
            .scale(if (lit) 1.06f else 1f)
            .onGloballyPositioned { spot.tile = it.rootBounds() }
            .alpha(if (dimmed) 0.45f else 1f)
            .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tileKeys(editing, { onMenu(spot.rect()) }, onMove, onEndEdit, onEdgeLeft = onEdgeLeft)
            .focusable()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) LastInput else Color(0xFF2A3444))
            .border(
                width = if (editing || lit) 3.dp else 0.dp,
                color = if (editing) Editing else if (lit) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = if (sub == null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            sub?.let {
                Text(
                    it,
                    color = Color(0xCCFFFFFF),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Where this sits on screen, unclipped, so a tile part-scrolled off the row still reports its size. */
private fun LayoutCoordinates.rootBounds(): Rect =
    findRootCoordinates().localBoundingBoxOf(this, clipBounds = false)

private fun Context.launchApp(packageName: String) {
    AppRepository.launchIntent(this, packageName)?.let { startActivitySafely(it) }
}

private fun Context.switchToInput(inputId: String) {
    InputRepository.rememberUse(this, inputId)
    startActivitySafely(InputRepository.switchIntent(inputId))
}

private fun Context.startActivitySafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
