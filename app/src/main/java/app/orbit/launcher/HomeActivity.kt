package app.orbit.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
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
private val AppsRowHeight = 86.dp

private val Surface = Color(0xFF14181F)
private val OnSurface = Color(0xFFDDE5F0)
private val Muted = Color(0xFF8B93A4)
private val LastInput = Color(0xFF1AA3C4)
private val Editing = Color(0xFFFFC14D)
private val Glyph = Color(0xFFBFC9DA)

private const val ADD_TILE = "add-favorite"
private const val ADD_INPUT_TILE = "add-input"

/** What the long-press menu is acting on. */
private data class TileMenu(
    val key: String,
    val id: String,
    val label: String,
    val favorite: Boolean,
    val packageName: String? = null,
)

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
    val inputs by produceState(initialValue = emptyList<TvInput>(), Returns.count) {
        value = withContext(Dispatchers.IO) { InputRepository.load(context) }
    }
    val isDefaultHome by produceState(initialValue = true, Returns.count) {
        value = withContext(Dispatchers.IO) { AppRepository.isDefaultHome(context) }
    }
    var noticeDismissed by remember { mutableStateOf(false) }

    // Favorites first, in the owner's order. Reading the revision here is what reorders the
    // rows the moment a favorite changes, without touching the device again.
    val revision = FavoriteStore.revision
    val appFavorites = remember(revision) { FavoriteStore.favorites(context, FavoriteStore.APPS) }
    val inputFavorites = remember(revision) { FavoriteStore.favorites(context, FavoriteStore.INPUTS) }
    val orderedApps = remember(apps, appFavorites) { FavoriteStore.arrange(apps, appFavorites) { it.packageName } }
    val orderedInputs = remember(inputs, inputFavorites) { FavoriteStore.arrange(inputs, inputFavorites) { it.id } }

    var menu by remember { mutableStateOf<TileMenu?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var editingKey by remember { mutableStateOf(FavoriteStore.APPS) }
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
        FavoriteStore.prune(context, FavoriteStore.APPS, present)
        known = present
    }
    LaunchedEffect(inputs) {
        if (inputs.isNotEmpty()) FavoriteStore.prune(context, FavoriteStore.INPUTS, inputs.map { it.id }.toSet())
    }
    LaunchedEffect(confirmation) {
        if (confirmation != null) {
            delay(2600)
            confirmation = null
        }
    }

    fun closeMenu(target: TileMenu) {
        menu = null
        if (target.key == FavoriteStore.APPS) refocusApps++ else refocusInputs++
    }

    fun beginEdit(key: String, id: String) {
        editSnapshot = FavoriteStore.favorites(context, key)
        editingKey = key
        // Only a favorite has a place in the order, so moving one makes it a favorite first.
        if (!FavoriteStore.isFavorite(context, key, id)) FavoriteStore.toggle(context, key, id)
        editing = id
    }

    fun endEdit(revert: Boolean) {
        if (revert) editSnapshot?.let { FavoriteStore.replace(context, editingKey, it) }
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
            RowLabel(if (editing != null && editingKey == FavoriteStore.APPS) "APPS - MOVING, OK TO KEEP" else "APPS")
            AppsRow(
                apps = orderedApps,
                favorites = appFavorites,
                refocus = refocusApps,
                editing = editing.takeIf { editingKey == FavoriteStore.APPS },
                onMenu = { app ->
                    menu = TileMenu(
                        key = FavoriteStore.APPS,
                        id = app.packageName,
                        label = app.label,
                        favorite = app.packageName in appFavorites,
                        // Only set when the system would actually accept an uninstall, which
                        // is what decides whether the entry is offered at all.
                        packageName = app.packageName.takeIf { app.removable },
                    )
                },
                onMove = { delta -> editing?.let { FavoriteStore.move(context, FavoriteStore.APPS, it, delta) } },
                onEndEdit = { revert -> endEdit(revert) },
            )
            Spacer(Modifier.height(20.dp))
            RowLabel(if (editing != null && editingKey == FavoriteStore.INPUTS) "INPUTS - MOVING, OK TO KEEP" else "INPUTS")
            InputsRow(
                inputs = orderedInputs,
                favorites = inputFavorites,
                refocus = refocusInputs,
                editing = editing.takeIf { editingKey == FavoriteStore.INPUTS },
                onMenu = { input ->
                    menu = TileMenu(
                        key = FavoriteStore.INPUTS,
                        id = input.id,
                        label = input.label,
                        favorite = input.id in inputFavorites,
                    )
                },
                onMove = { delta -> editing?.let { FavoriteStore.move(context, FavoriteStore.INPUTS, it, delta) } },
                onEndEdit = { revert -> endEdit(revert) },
            )
        }

        confirmation?.let { Confirmation(it) }

        menu?.let { target ->
            TileMenuSheet(
                target = target,
                onFavorite = {
                    FavoriteStore.toggle(context, target.key, target.id)
                    confirmation = if (target.favorite) "Removed from favorites" else "Added to favorites"
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

/** The long-press menu. Uninstall is deliberately handed to the system to confirm. */
@Composable
private fun TileMenuSheet(
    target: TileMenu,
    onFavorite: () -> Unit,
    onMove: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(target.id) {
        repeat(30) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(16)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1A2130))
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(target.label, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
            MenuEntry(
                label = if (target.favorite) "Remove from favorites" else "Add to favorites",
                requester = first,
                onClick = onFavorite,
            )
            Spacer(Modifier.height(8.dp))
            MenuEntry(label = "Move", requester = null, onClick = onMove)
            if (target.packageName != null) {
                Spacer(Modifier.height(8.dp))
                MenuEntry(label = "Uninstall", requester = null, onClick = onUninstall)
            }
        }
    }
}

@Composable
private fun MenuEntry(label: String, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .width(220.dp)
            .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else Color(0x1FFFFFFF))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused) Color.Black else OnSurface, fontSize = 13.sp)
    }
}

@Composable
private fun AppsRow(
    apps: List<LaunchableApp>,
    favorites: List<String>,
    refocus: Int,
    editing: String?,
    onMenu: (LaunchableApp) -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    // The tile to come back to. Focus is only ever requested from the effect below, never from a
    // tile's own composition, which is what used to yank focus back while arrowing left.
    var lastFocused by remember { mutableStateOf<String?>(null) }
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
        val layout = listState.layoutInfo
        val visible = layout.visibleItemsInfo.any {
            it.index == index && it.offset >= 0 && it.offset + it.size <= layout.viewportEndOffset
        }
        if (!visible) runCatching { listState.animateScrollToItem(index) }
    }

    // Keyed on the list itself, not just its first entry: when the tile that had focus is
    // uninstalled the first entry is unchanged, so nothing would re-run and the focus would be
    // left on a tile that no longer exists.
    LaunchedEffect(apps, Returns.count, refocus) {
        if (firstPackage == null) return@LaunchedEffect
        val target = lastFocused?.takeIf { wanted -> apps.any { it.packageName == wanted } }
            ?: firstPackage
        val index = apps.indexOfFirst { it.packageName == target }.coerceAtLeast(0)
        // The row only composes what is on screen, so the tile has to be scrolled into view
        // before its requester exists at all.
        runCatching { listState.scrollToItem(index) }
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
        modifier = Modifier.height(AppsRowHeight),
        state = listState,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = SafeH),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppTile(
                app = app,
                favorite = app.packageName in favorites,
                editing = editing == app.packageName,
                requester = requesters.getOrPut(app.packageName) { FocusRequester() },
                onFocused = { lastFocused = app.packageName },
                onClick = {
                    AppRepository.launchIntent(context, app.packageName)
                        ?.let { context.startActivitySafely(it) }
                },
                onMenu = { onMenu(app) },
                onMove = onMove,
                onEndEdit = onEndEdit,
                onEdgeLeft = if (index == 0) {
                    { jumpTo(apps.size, ADD_TILE) }
                } else {
                    null
                },
            )
        }
        if (apps.isNotEmpty()) item(key = ADD_TILE) {
            PlusTile(
                requester = requesters.getOrPut(ADD_TILE) { FocusRequester() },
                onEdgeRight = { firstPackage?.let { jumpTo(0, it) } },
            ) { context.startActivitySafely(Intent(context, AppPickerActivity::class.java)) }
        }
    }
}

@Composable
private fun InputsRow(
    inputs: List<TvInput>,
    favorites: List<String>,
    refocus: Int,
    editing: String?,
    onMenu: (TvInput) -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lastInput = remember(inputs) { InputRepository.lastUsedInput(context) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastFocused by remember { mutableStateOf<String?>(null) }
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
        if (refocus == 0) return@LaunchedEffect
        val target = lastFocused ?: return@LaunchedEffect
        repeat(30) {
            if (runCatching { requesters[target]?.requestFocus() != null }.getOrDefault(false)) {
                return@LaunchedEffect
            }
            delay(16)
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
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
                favorite = input.id in favorites,
                editing = editing == input.id,
                requester = requesters.getOrPut(input.id) { FocusRequester() },
                onFocused = { lastFocused = input.id },
                onMenu = { onMenu(input) },
                onMove = onMove,
                onEndEdit = onEndEdit,
                onEdgeLeft = if (index == 0) {
                    { jumpTo(inputs.size, ADD_INPUT_TILE) }
                } else {
                    null
                },
            ) {
                InputRepository.rememberUse(context, input.id)
                context.startActivitySafely(InputRepository.switchIntent(input.id))
            }
        }
        if (inputs.isNotEmpty()) item(key = ADD_INPUT_TILE) {
            PlusTile(
                requester = requesters.getOrPut(ADD_INPUT_TILE) { FocusRequester() },
                onEdgeRight = { inputs.firstOrNull()?.let { jumpTo(0, it.id) } },
                width = InputW,
                label = "Add input to favorites",
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
            // hasFocus, not isFocused: while OK is down, Compose hands focus to the clickable
            // inside, and the button should stay lit through the press.
            .onFocusChanged { focused = it.hasFocus }
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
private fun BoxScope.FavoriteDot() {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(5.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(LastInput)
    )
}

@Composable
private fun AppTile(
    app: LaunchableApp,
    favorite: Boolean,
    editing: Boolean,
    requester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
    onEdgeLeft: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(TileW, TileH)
                .scale(if (focused) 1.06f else 1f)
                .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .tileKeys(editing, onMenu, onMove, onEndEdit, onEdgeLeft = onEdgeLeft)
                .focusable()
                .clickable(onClick = onClick)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .border(
                    width = if (editing || focused) 3.dp else 0.dp,
                    color = if (editing) Editing else if (focused) Color.White else Color.Transparent,
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
            if (favorite) FavoriteDot()
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                editing -> "Left or right to move"
                focused -> app.label
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
    onEdgeRight: (() -> Unit)?,
    width: Dp = TileW,
    label: String = "Add app to favorites",
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width, TileH)
                .scale(if (focused) 1.06f else 1f)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
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
    favorite: Boolean,
    editing: Boolean,
    requester: FocusRequester?,
    onFocused: () -> Unit,
    onMenu: () -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
    onEdgeLeft: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(InputW, TileH)
            .scale(if (focused) 1.06f else 1f)
            .alpha(if (dimmed) 0.45f else 1f)
            .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tileKeys(editing, onMenu, onMove, onEndEdit, onEdgeLeft = onEdgeLeft)
            .focusable()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) LastInput else Color(0xFF2A3444))
            .border(
                width = if (editing || focused) 3.dp else 0.dp,
                color = if (editing) Editing else if (focused) Color.White else Color.Transparent,
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
        if (favorite) FavoriteDot()
    }
}

private fun Context.startActivitySafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
