package app.orbit.launcher

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt
import kotlin.reflect.KProperty
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
/** How much a focused tile grows, about what the stock launcher's own tiles do. */
private const val FocusScale = 1.2f

/** How far a focused tile reaches past each of its edges. */
private val PopOut = TileH * (FocusScale - 1f) / 2

/** Room above the tiles. A row clips at its sides only, so a grown tile can reach past it. */
private val RowTop = 4.dp

/** Between a tile and its label, far enough that the grown tile does not cover the label. */
private val LabelGap = PopOut + 4.dp

/**
 * Both rows: the tiles, the gap and a label line. Fixed, so the rows below hold still while a
 * list is still loading.
 */
private val RowHeight = RowTop + TileH + LabelGap + 16.dp

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

private const val ADD_TILE = "add-favorite"
private const val ADD_INPUT_TILE = "add-input"

/** What the long-press menu is acting on. */
private data class TileMenu(
    val key: String,
    val id: String,
    val label: String,
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
 * the picker leaves nothing focused and the remote does nothing at all. The home screen watches
 * this and puts focus back where it was.
 */
private object Returns {
    var count by mutableIntStateOf(0)
}

/** The parts of the screen focus can be in, so a return can put it back in the same one. */
private enum class Zone { APPS, INPUTS, HERO, TOP }

/**
 * Whether a key from the remote is being handled right now.
 *
 * Focus that moves while one is, the owner moved. Focus that moves at any other time was placed
 * by Orbit putting it back, or by the system, which hands it to whatever comes first on screen
 * whenever the focused item disappears or the window opens. Only the first kind says where the
 * owner is.
 */
private object Remote {
    var handling = false
}

/** What the activity tells the screen that composition cannot see for itself. */
private enum class HomeEvent {
    /** Home was pressed while Orbit was already in front. */
    HOME,

    /** Orbit went out of sight: an app opened over it, the screensaver started, the screen went off. */
    LEFT,
}

class HomeActivity : ComponentActivity() {
    private val events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)

    /** Resumed and not stopped since. Home pressed now is pressed on Orbit itself. */
    private var inFront = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SafeAreaScrolling { HomeScreen(events) } }
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        Returns.count++
    }

    // Home on the home screen pauses Orbit, delivers the intent and resumes it, without ever
    // stopping it. Coming home from an app stops it first, so that case is told apart here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (inFront) events.tryEmit(HomeEvent.HOME)
    }

    override fun onStop() {
        super.onStop()
        inFront = false
        events.tryEmit(HomeEvent.LEFT)
    }

    // Focus moves as part of handling the key, before this returns. The lint warning is about
    // AndroidX's own override of this framework method, which is still called through super.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        Remote.handling = true
        try {
            return super.dispatchKeyEvent(event)
        } finally {
            Remote.handling = false
        }
    }
}

@Composable
private fun HomeScreen(events: SharedFlow<HomeEvent>) {
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
    var inputs by remember { mutableStateOf<List<TvInput>?>(null) }
    LaunchedEffect(Returns.count) {
        inputs = withContext(Dispatchers.IO) { InputRepository.load(context) }
    }
    // The TV also says when an input comes, goes or changes while Orbit is in front, such as a
    // console being switched on, which would otherwise only show after leaving and coming back.
    // A device coming up reports several changes in a row, so the read waits for them to stop.
    var inputsChanged by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val stop = InputRepository.watch(context) { inputsChanged++ }
        onDispose { stop() }
    }
    LaunchedEffect(inputsChanged) {
        if (inputsChanged == 0) return@LaunchedEffect
        delay(300)
        inputs = withContext(Dispatchers.IO) { InputRepository.load(context) }
    }
    // Read again on every return too, so a store disabled or enabled meanwhile is picked up. The
    // previous read stays up until then, so the buttons do not blink out and back.
    var destinations by remember { mutableStateOf(Destinations.last) }
    LaunchedEffect(Returns.count) {
        destinations = withContext(Dispatchers.IO) { Destinations.read(context) }
    }
    // With the home button service on, Home reaches Orbit without it being the home app, and the
    // notice would be asking for something already done.
    val homeOpensOrbit by produceState(initialValue = true, Returns.count) {
        value = withContext(Dispatchers.IO) {
            AppRepository.isDefaultHome(context) || isHomeButtonServiceOn(context)
        }
    }
    // Kept, so a dismissed notice stays dismissed when Orbit is next started.
    var noticeDismissed by remember { mutableStateOf(HomePrefs.noticeDismissed(context)) }

    // Only the favorites, in the owner's order; everything else is one press of + away.
    // Reading the revision here is what updates the rows the moment a favorite changes,
    // without touching the device again.
    val revision = FavoriteStore.revision
    val appFavorites = remember(revision) { FavoriteStore.favorites(context, FavoriteStore.APPS) }
    val inputFavorites = remember(revision) { FavoriteStore.favorites(context, FavoriteStore.INPUTS) }
    val favoriteApps = remember(apps, appFavorites) {
        FavoriteStore.pick(apps, appFavorites) { it.packageName }
    }
    // Input favorites are never pruned, unlike apps. An input can drop out of the list for a
    // while, at boot or when a console on HDMI-CEC goes to standby, and should be back in its
    // place when it returns. Until then it is simply not shown.
    val favoriteInputs = remember(inputs, inputFavorites) {
        FavoriteStore.pick(inputs.orEmpty(), inputFavorites) { it.id }
    }

    var menu by remember { mutableStateOf<TileMenu?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var editingKey by remember { mutableStateOf(FavoriteStore.APPS) }
    var editSnapshot by remember { mutableStateOf<List<String>?>(null) }
    val note = remember { Note() }
    var confirmation by note
    // Closing the menu has to hand focus back to the tile it belonged to. Without this the
    // focus falls to whatever comes first on screen and the remote walks off somewhere else.
    var refocusApps by remember { mutableIntStateOf(0) }
    var refocusInputs by remember { mutableIntStateOf(0) }
    var refocusHero by remember { mutableIntStateOf(0) }
    val noticeFocus = remember { FocusRequester() }
    val topBarFocus = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    var refocusTop by remember { mutableIntStateOf(0) }
    // Asks the apps row to go back to its first tile, for Home and Back.
    var toFirst by remember { mutableIntStateOf(0) }
    // Saved with the activity, so focus comes back to the same place after it is recreated.
    var zone by rememberSaveable { mutableStateOf(Zone.APPS) }
    var lastTop by rememberSaveable { mutableStateOf<String?>(null) }
    fun moveTo(where: Zone) {
        if (Remote.handling) zone = where
    }
    var known by remember { mutableStateOf<Set<String>>(emptySet()) }

    // An app can be removed from anywhere, so notice it by comparing successive reads rather
    // than by watching the uninstall we started.
    // A favorite is dropped only once the system confirms the app is uninstalled. One that is
    // merely missing from this read keeps its place and comes back when the app does.
    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        val present = apps.map { it.packageName }.toSet()
        val missing = (known - present) + FavoriteStore.favorites(context, FavoriteStore.APPS).filter { it !in present }
        val gone = withContext(Dispatchers.IO) {
            missing.filterTo(mutableSetOf()) { !AppRepository.isInstalled(context, it) }
        }
        if (known.isNotEmpty() && (known - present).any { it in gone }) confirmation = "It's gone now"
        if (gone.isNotEmpty()) FavoriteStore.prune(context, FavoriteStore.APPS, present + (missing - gone))
        known = present
    }
    LaunchedEffect(note.shown) {
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
        editing = id
    }

    fun endEdit(revert: Boolean) {
        if (revert) editSnapshot?.let { FavoriteStore.replace(context, editingKey, it) }
        editing = null
        editSnapshot = null
    }

    // OK that opens nothing at all reads as the remote not working, so a failure says so.
    fun openApp(packageName: String, label: String) {
        if (!context.launchApp(packageName)) confirmation = "Couldn't open $label"
    }

    fun switchTo(inputId: String, label: String) {
        if (!context.switchToInput(inputId)) confirmation = "Couldn't switch to $label"
    }

    fun open(intent: Intent, label: String) {
        if (!context.startActivitySafely(intent)) confirmation = "Couldn't open $label"
    }

    fun refocus(where: Zone) {
        when (where) {
            Zone.APPS -> refocusApps++
            Zone.INPUTS -> refocusInputs++
            Zone.HERO -> refocusHero++
            Zone.TOP -> refocusTop++
        }
    }

    // Back from an app, or from anything else that took the screen: focus goes back where it
    // was. An open menu takes its own focus back, and a move keeps the tile being moved.
    LaunchedEffect(Returns.count) {
        if (menu != null) return@LaunchedEffect
        refocus(
            when {
                editing == null -> zone
                editingKey == FavoriteStore.APPS -> Zone.APPS
                else -> Zone.INPUTS
            }
        )
    }

    // A top bar button can go while it has focus: the Play Store, disabled while Orbit was out
    // of sight, goes when the read on return lands, after focus was already put back on it.
    // Asking the top bar to put it back again finds the button gone and hands focus to the apps.
    LaunchedEffect(destinations.inputs != null, destinations.store != null) {
        if (zone == Zone.TOP && menu == null) refocusTop++
    }

    // Leaving puts away the menu and any move not yet kept, so coming back finds the rows as
    // they were. Home on the home screen does the same and then goes back to the start, as the
    // stock launcher does.
    LaunchedEffect(events) {
        events.collect { event ->
            menu = null
            if (editing != null) endEdit(revert = true)
            if (event == HomeEvent.HOME) {
                zone = Zone.APPS
                toFirst++
            }
        }
    }

    // There is nothing behind the home screen for Back to go to. Left to the system, Back would
    // close Orbit, only for it to be started again from scratch with nothing focused. A menu or a
    // move takes Back for itself before it gets here.
    BackHandler {
        zone = Zone.APPS
        toFirst++
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            // No safe margin below: the space under the hero stretches anyway, and the notice
            // needs that room to fit above the rows.
            Column(Modifier.padding(start = SafeH, top = SafeV, end = SafeH)) {
                TopBar(
                    first = topBarFocus,
                    destinations = destinations,
                    refocus = refocusTop,
                    last = lastTop,
                    onFocused = { name ->
                        if (Remote.handling) lastTop = name
                        moveTo(Zone.TOP)
                    },
                    // The button last used can be gone by the time Orbit is back: the Play
                    // Store, if it was disabled.
                    onLost = {
                        zone = Zone.APPS
                        refocusApps++
                    },
                    onOpen = ::open,
                )
                Spacer(Modifier.height(10.dp))
                Hero(
                    apps = apps,
                    inputs = inputs,
                    refocus = refocusHero,
                    // Set by hand: the hero sits at the left and the top bar at the right, so
                    // Compose's own search finds nothing above the hero and focus would be
                    // stuck there. The notice is at the right too, below the hero.
                    self = heroFocus,
                    above = topBarFocus,
                    below = noticeFocus.takeIf { !homeOpensOrbit && !noticeDismissed },
                    onFocused = { moveTo(Zone.HERO) },
                )
            }
            Spacer(Modifier.weight(1f))
            if (!homeOpensOrbit && !noticeDismissed) {
                Notice(
                    title = "Orbit is not the home app yet",
                    detail = "Set it as default so the home button opens Orbit",
                    requester = noticeFocus,
                    above = heroFocus,
                    // Not every TV has the home app setting. Where it is missing, the
                    // accessibility settings are where the home button service is turned on.
                    onDetails = {
                        context.startActivitySafely(Intent(Settings.ACTION_HOME_SETTINGS)) ||
                            context.startActivitySafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onDismiss = {
                        noticeDismissed = true
                        HomePrefs.dismissNotice(context)
                        // The focused button goes with the notice. Without this, focus falls to
                        // whatever comes first on screen, which is the top bar.
                        zone = Zone.APPS
                        refocusApps++
                    },
                )
                Spacer(Modifier.height(14.dp))
            }
            RowLabel(if (editing != null && editingKey == FavoriteStore.APPS) "APPS - MOVING, OK TO KEEP" else "APPS")
            AppsRow(
                noticeFocus = noticeFocus.takeIf { !homeOpensOrbit && !noticeDismissed },
                apps = favoriteApps,
                // A TV always has some app installed, so an empty list means not read yet.
                ready = apps.isNotEmpty(),
                held = menu?.takeIf { it.key == FavoriteStore.APPS }?.id,
                refocus = refocusApps,
                toFirst = toFirst,
                active = zone == Zone.APPS && menu == null,
                onFocused = { moveTo(Zone.APPS) },
                editing = editing.takeIf { editingKey == FavoriteStore.APPS },
                onOpen = { app -> openApp(app.packageName, app.label) },
                onMenu = { app, anchor ->
                    menu = TileMenu(
                        key = FavoriteStore.APPS,
                        id = app.packageName,
                        label = app.label,
                        anchor = anchor,
                        // Only set when the system would actually accept an uninstall, which
                        // is what decides whether the entry is offered at all.
                        packageName = app.packageName.takeIf { app.removable },
                    )
                },
                onMove = { delta ->
                    editing?.let {
                        FavoriteStore.move(context, FavoriteStore.APPS, it, delta, favoriteApps.map { a -> a.packageName })
                    }
                },
                onEndEdit = { revert -> endEdit(revert) },
            )
            // Smaller than it looks: the apps row already ends in room for its tile labels.
            Spacer(Modifier.height(12.dp))
            RowLabel(if (editing != null && editingKey == FavoriteStore.INPUTS) "INPUTS - MOVING, OK TO KEEP" else "INPUTS")
            InputsRow(
                inputs = favoriteInputs,
                ready = inputs != null,
                held = menu?.takeIf { it.key == FavoriteStore.INPUTS }?.id,
                refocus = refocusInputs,
                active = zone == Zone.INPUTS && menu == null,
                onFocused = { moveTo(Zone.INPUTS) },
                editing = editing.takeIf { editingKey == FavoriteStore.INPUTS },
                onOpen = { input -> switchTo(input.id, input.label) },
                onMenu = { input, anchor ->
                    menu = TileMenu(
                        key = FavoriteStore.INPUTS,
                        id = input.id,
                        label = input.label,
                        anchor = anchor,
                    )
                },
                onMove = { delta ->
                    editing?.let {
                        FavoriteStore.move(context, FavoriteStore.INPUTS, it, delta, favoriteInputs.map { i -> i.id })
                    }
                },
                onEndEdit = { revert -> endEdit(revert) },
            )
            // The bottom of the safe area. Without it the labels under the inputs row sit in the
            // strip a TV may crop off.
            Spacer(Modifier.height(SafeV))
        }

        confirmation?.let { Confirmation(it) }

        menu?.let { target ->
            TileMenuSheet(
                target = target,
                onOpen = {
                    closeMenu(target)
                    if (target.key == FavoriteStore.APPS) {
                        openApp(target.id, target.label)
                    } else {
                        switchTo(target.id, target.label)
                    }
                },
                onRemove = {
                    FavoriteStore.remove(context, target.key, target.id)
                    confirmation = "Removed from favorites"
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

/**
 * The confirmation in the corner. Every time it is set counts as new, even to the same words, so
 * that removing two favorites in a row keeps the second one up for its full time.
 */
private class Note {
    private var text by mutableStateOf<String?>(null)

    var shown by mutableIntStateOf(0)
        private set

    operator fun getValue(thisRef: Any?, property: KProperty<*>): String? = text

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
        text = value
        if (value != null) shown++
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
 * The card hangs below the tile when it fits and stands above it when it does not. With both
 * rows in the lower half of the screen, as they are now, it stands above in either row.
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
private fun MenuEntry(item: MenuItem, requester: FocusRequester, onFocused: () -> Unit) {
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
    noticeFocus: FocusRequester?,
    apps: List<LaunchableApp>,
    ready: Boolean,
    held: String?,
    refocus: Int,
    toFirst: Int,
    /** Focus belongs in this row: it was last here, and no menu has it. */
    active: Boolean,
    onFocused: () -> Unit,
    editing: String?,
    onOpen: (LaunchableApp) -> Unit,
    onMenu: (LaunchableApp, Rect) -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    // The tile to come back to, by id and by place. Focus is only ever requested from the effect
    // below, never from a tile's own composition, which is what used to yank focus back while
    // arrowing left. Saved with the activity, so a recreated one comes back to the same tile.
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }
    var lastIndex by rememberSaveable { mutableIntStateOf(0) }
    val ids = remember(apps) { apps.map { it.packageName } + ADD_TILE }
    // A move changes where the focused tile sits. Keep its place current, so that when it leaves
    // the row the focus goes to whatever slid into that place.
    SideEffect {
        val at = lastFocused?.let { ids.indexOf(it) } ?: -1
        if (at >= 0) lastIndex = at
    }
    val firstPackage = apps.firstOrNull()?.packageName
    val scope = rememberCoroutineScope()

    fun jumpTo(index: Int, id: String) {
        scope.launch {
            runCatching { listState.scrollToItem(index) }
            requesters[id].focusWhenReady()
        }
    }

    // Only when the tile being moved has left the safe area, and only as far as it takes to bring
    // it back. Scrolling on every swap drags it to the left edge, which reads as the row jumping
    // rather than the tile moving.
    LaunchedEffect(editing, apps) {
        val moving = editing ?: return@LaunchedEffect
        val index = apps.indexOfFirst { it.packageName == moving }
        if (index < 0) return@LaunchedEffect
        runCatching { listState.reveal(index) }
    }

    // Keyed on the row's contents, not just its first entry: when the tile that had focus leaves
    // the favorites or the TV, nothing else would re-run and the focus would be left on a tile
    // that no longer exists. The one that slid into its place takes the focus instead. Only while
    // focus belongs here, so a list read on a return cannot pull focus out of the inputs row or
    // the top bar, or out from under an open menu.
    LaunchedEffect(ids, ready, refocus) {
        if (!ready || !active) return@LaunchedEffect
        val target = lastFocused?.takeIf { it in ids } ?: ids[lastIndex.coerceAtMost(ids.lastIndex)]
        val index = ids.indexOf(target)
        // The row only composes what is on screen, so the tile has to be scrolled into view
        // before its requester exists at all. Not during a move, which reorders the row on every
        // press and does its own scrolling above.
        if (editing == null) runCatching { listState.reveal(index) }
        // Items are placed during layout, so the requester is not attached on the frame the
        // list arrives. Keep asking until it takes.
        focusWhenReady { requesters[target] }
    }

    LaunchedEffect(toFirst) {
        if (toFirst == 0 || !ready) return@LaunchedEffect
        lastFocused = ids.first()
        lastIndex = 0
        runCatching { listState.scrollToItem(0) }
        requesters[ids.first()].focusWhenReady()
    }

    LazyRow(
        modifier = Modifier
            .height(RowHeight)
            .rowFocus(listState, { lastFocused }, requesters, onFocused) { noticeFocus },
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = SafeH, top = RowTop, end = SafeH),
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
                onClick = { onOpen(app) },
                onMenu = { anchor -> onMenu(app, anchor) },
                onMove = { delta -> listState.holdStill(); onMove(delta) },
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
            ) { context.openScreen(Intent(context, AppPickerActivity::class.java)) }
        }
    }
}

@Composable
private fun InputsRow(
    inputs: List<TvInput>,
    ready: Boolean,
    held: String?,
    refocus: Int,
    /** Focus belongs in this row: it was last here, and no menu has it. */
    active: Boolean,
    onFocused: () -> Unit,
    editing: String?,
    onOpen: (TvInput) -> Unit,
    onMenu: (TvInput, Rect) -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // Read again on every return too: the TV hands back the same inputs after a switch, so the
    // list alone would not change, and the input just switched to would not be marked.
    val lastInput = remember(inputs, Returns.count) { InputRepository.lastUsedInput(context) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }
    var lastIndex by rememberSaveable { mutableIntStateOf(0) }
    val ids = remember(inputs) { inputs.map { it.id } + ADD_INPUT_TILE }
    SideEffect {
        val at = lastFocused?.let { ids.indexOf(it) } ?: -1
        if (at >= 0) lastIndex = at
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun jumpTo(index: Int, id: String) {
        scope.launch {
            runCatching { listState.scrollToItem(index) }
            requesters[id].focusWhenReady()
        }
    }

    LaunchedEffect(editing, inputs) {
        val moving = editing ?: return@LaunchedEffect
        val index = inputs.indexOfFirst { it.id == moving }
        if (index < 0) return@LaunchedEffect
        runCatching { listState.reveal(index) }
    }

    // Only ever on an explicit request, never on composition, so the row cannot steal focus
    // from the apps above it. Also once the inputs have been read, for a request made before.
    LaunchedEffect(refocus, ready) {
        if (refocus == 0 || !ready || !active || lastFocused == null) return@LaunchedEffect
        val target = lastFocused?.takeIf { it in ids } ?: ids[lastIndex.coerceAtMost(ids.lastIndex)]
        val index = ids.indexOf(target)
        if (editing == null) runCatching { listState.reveal(index) }
        focusWhenReady { requesters[target] }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .rowFocus(listState, { lastFocused }, requesters, onFocused),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        contentPadding = PaddingValues(start = SafeH, top = RowTop, end = SafeH),
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
                onMove = { delta -> listState.holdStill(); onMove(delta) },
                onEndEdit = onEndEdit,
                onEdgeLeft = if (index == 0) {
                    { jumpTo(inputs.size, ADD_INPUT_TILE) }
                } else {
                    null
                },
            ) { onOpen(input) }
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
                label = "Add input to favorites",
            ) { context.openScreen(Intent(context, InputPickerActivity::class.java)) }
        }
    }
}

/**
 * The date, the way out to the TV's own screens, the profile picture and the time.
 *
 * [last] is the button last used, for focus to come back to after where it led, and
 * [onFocused] reports each button by name as it takes focus. [onOpen] starts where a button leads,
 * given the name to tell the owner if it cannot.
 */
@Composable
private fun TopBar(
    first: FocusRequester,
    destinations: Destinations,
    refocus: Int,
    last: String?,
    onFocused: (String) -> Unit,
    onLost: () -> Unit,
    onOpen: (Intent, String) -> Unit,
) {
    val context = LocalContext.current
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val button: (String) -> Modifier = { name ->
        Modifier
            .focusRequester(requesters.getOrPut(name) { FocusRequester() })
            .onFocusChanged { if (it.hasFocus) onFocused(name) }
    }
    LaunchedEffect(refocus) {
        if (refocus == 0) return@LaunchedEffect
        if (!requesters[last].focusWhenReady()) onLost()
    }

    // The 12 or 24 hour setting is read again on every tick, so changing it on the TV shows
    // within the minute. Each tick lands on the minute itself, so the clock turns over with the
    // others in the room rather than up to a tick behind them.
    var now by remember { mutableStateOf(Date()) }
    var twentyFour by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            twentyFour = DateFormat.is24HourFormat(context)
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    // In the order the language puts them: "Monday 3 March" here, "Monday, March 3" there.
    val date = remember {
        val locale = Locale.getDefault()
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    }
    // Twelve hour time leaves off AM and PM, the way the stock launcher's clock does.
    val time = remember(twentyFour) {
        SimpleDateFormat(if (twentyFour) "HH:mm" else "h:mm", Locale.getDefault())
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(date.format(now), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Shortcuts(first, button, destinations, onOpen)
        Avatar(button)
        Spacer(Modifier.width(9.dp))
        Text(time.format(now), color = Muted, fontSize = 12.sp)
    }
}

/**
 * Where the top bar's buttons lead, and the store's own icon, so it reads as the store at a
 * glance. Null where the TV has nowhere for a button to go.
 */
private class Destinations(
    val search: Intent? = null,
    val inputs: Intent? = null,
    val store: Intent? = null,
    val storeIcon: ImageBitmap? = null,
) {
    companion object {
        /**
         * The last read, kept for the lifetime of the process. A screen started again shows its
         * buttons from the first frame, so focus coming back to one of them finds it there.
         */
        @Volatile
        var last = Destinations()
            private set

        /** Package manager lookups and an icon decode, so never on the main thread. */
        fun read(context: Context): Destinations {
            val store = InputRepository.storeIntent(context)
            return Destinations(
                search = InputRepository.searchIntent(context),
                inputs = InputRepository.allInputsIntent(context),
                store = store,
                storeIcon = runCatching {
                    InputRepository.iconFor(context, store)?.toBitmap()?.asImageBitmap()
                }.getOrNull(),
            ).also { last = it }
        }
    }
}

/**
 * The way out to the TV's own screens, left of the profile picture as on the stock launcher.
 *
 * Each is offered only when the TV has somewhere for it to go.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Shortcuts(
    first: FocusRequester,
    button: (String) -> Modifier,
    destinations: Destinations,
    onOpen: (Intent, String) -> Unit,
) {
    val context = LocalContext.current
    val searchIntent = destinations.search
    val inputsIntent = destinations.inputs
    val storeIntent = destinations.store
    val settingsIntent = remember { InputRepository.settingsIntent() }
    // Inputs and settings have no icon worth borrowing, so they get the same glyphs the stock top
    // bar uses.
    val storeIcon = destinations.storeIcon

    // Leftmost, as on the stock top bar. It carries the shared requester and holds the left
    // edge, which pass to All apps when the TV has no search to offer.
    if (searchIntent != null) {
        TopBarButton(
            button("search")
                .focusRequester(first)
                .focusProperties { left = FocusRequester.Cancel },
            onClick = { context.startActivitySafely(searchIntent) },
        ) {
            Icon(
                imageVector = Glyphs.Search,
                contentDescription = "Search",
                tint = Glyph,
                modifier = Modifier.padding(4.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
    }

    // Favorites are what the rows show, so without this an app that is not a favorite could not
    // be opened from Orbit at all.
    TopBarButton(
        button("apps")
            .then(if (searchIntent == null) Modifier.focusRequester(first) else Modifier)
            .focusProperties { left = FocusRequester.Cancel },
        onClick = {
            context.openScreen(
                Intent(context, AppPickerActivity::class.java)
                    .putExtra(AppPickerActivity.EXTRA_OPEN, true)
            )
        },
    ) {
        Icon(
            imageVector = Glyphs.Apps,
            contentDescription = "All apps",
            tint = Glyph,
            modifier = Modifier.padding(4.dp),
        )
    }
    Spacer(Modifier.width(9.dp))

    if (inputsIntent != null) {
        TopBarButton(button("inputs"), onClick = { onOpen(inputsIntent, "All inputs") }) {
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
        TopBarButton(button("store"), onClick = { onOpen(storeIntent, "Play Store") }) {
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
    TopBarButton(button("settings"), onClick = { onOpen(settingsIntent, "Settings") }) {
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
private fun Avatar(button: (String) -> Modifier) {
    val context = LocalContext.current
    // Keyed on the revision so a picture chosen in the picker shows on return.
    val picture by produceState<android.graphics.Bitmap?>(initialValue = null, Profile.revision) {
        value = withContext(Dispatchers.IO) { Profile.avatar(context) }
    }
    TopBarButton(
        button("profile"),
        onClick = { context.openScreen(Intent(context, AvatarPickerActivity::class.java)) },
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
private fun TopBarButton(
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(21.dp)
            .scale(if (focused) 1.15f else 1f)
            .then(modifier)
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

private val HeroArtW = 192.dp
private val HeroArtH = 108.dp

/**
 * The top of the screen, showing the most truthful thing Orbit can say, in this order:
 *
 * 1. what the owner was actually watching, from the TV's own Watch Next list;
 * 2. the last app or input Orbit opened, which needs no permission;
 * 3. the time, when there is nothing to offer.
 *
 * Nothing is invented. The programme's own fields are shown when its app published them and
 * left out when it did not, so there is no progress bar for a position nobody reported.
 */
@Composable
private fun Hero(
    apps: List<LaunchableApp>,
    inputs: List<TvInput>?,
    refocus: Int,
    self: FocusRequester,
    above: FocusRequester,
    below: FocusRequester?,
    onFocused: () -> Unit,
) {
    val context = LocalContext.current
    val requester = self
    var focused by remember { mutableStateOf(false) }

    var granted by remember { mutableStateOf(ContinueWatching.granted(context)) }
    var asked by rememberSaveable { mutableStateOf(HomePrefs.listingsAsked(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
        HomePrefs.rememberListingsAsked(context)
    }

    // The lint check cannot see the assignment through the withContext call, and reports it on
    // every produceState in this project; the baseline already carries eight of them. Writing
    // the result to a local first does not help, so the two new ones say so here instead of
    // growing the baseline, which is meant for warnings that were already there.
    @Suppress("ProduceStateDoesNotAssignValue")
    val watching by produceState<Resumable?>(initialValue = null, granted, Returns.count) {
        value = withContext(Dispatchers.IO) { ContinueWatching.latest(context) }
    }
    @Suppress("ProduceStateDoesNotAssignValue")
    val art by produceState<ImageBitmap?>(initialValue = null, watching?.posterUri) {
        val uri = watching?.posterUri
        value = withContext(Dispatchers.IO) {
            uri?.let { ContinueWatching.poster(context, it, maxWidth = 384)?.asImageBitmap() }
        }
    }

    val back = remember(Returns.count) { LastOpened.recent(context) }
    val backApp = back?.takeIf { it.kind == LastOpened.APP }
        ?.let { entry -> apps.firstOrNull { it.packageName == entry.id } }
    val backInput = back?.takeIf { it.kind == LastOpened.INPUT }
        ?.let { entry -> inputs?.firstOrNull { it.id == entry.id } }

    val item = watching
    val action: (() -> Unit)? = when {
        item != null -> {
            {
                ContinueWatching.intentFor(item)?.let { intent ->
                    // Resuming is opening that app, so the hero can offer it again tomorrow.
                    if (context.startActivitySafely(intent)) {
                        LastOpened.record(context, LastOpened.APP, item.packageName)
                    }
                }
            }
        }
        // Asked for once, in the hero, rather than as a popup on a screen nobody opened yet.
        !granted && !asked -> {
            { ask.launch(ContinueWatching.PERMISSION) }
        }
        backApp != null -> { { context.launchApp(backApp.packageName) } }
        backInput != null -> { { context.switchToInput(backInput.id) } }
        else -> null
    }

    LaunchedEffect(refocus) {
        if (refocus == 0 || action == null) return@LaunchedEffect
        requester.focusWhenReady()
    }

    Row(
        // On the row rather than on the focusable inside it, to keep it away from the
        // clickable's own focus target.
        Modifier
            .height(HeroArtH)
            .focusProperties {
                up = above
                down = below ?: FocusRequester.Default
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The focus mark for a block this size is an accent down its side, not a ring.
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(if (focused) Color(0xFF7FD4FF) else Color.Transparent)
        )
        Spacer(Modifier.width(13.dp))

        Box(
            Modifier
                .focusRequester(requester)
                .onFocusChanged {
                    focused = it.hasFocus
                    if (it.hasFocus) onFocused()
                }
                // The clickable is the hero's only focus target. A focusable() in front of it
                // would make a second one, and the focus properties set above would then not be
                // read from whichever of the two actually held focus.
                .then(
                    action?.let {
                        Modifier.clickable(interactionSource = null, indication = null, onClick = it)
                    } ?: Modifier
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val picture = art
                if (item != null && picture != null) {
                    Image(
                        bitmap = picture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(HeroArtW, HeroArtH)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(18.dp))
                }
                when {
                    item != null -> Watching(item, focused)
                    !granted && !asked -> Offer(focused)
                    backApp != null -> BackTo(backApp.label, focused)
                    backInput != null -> BackTo(backInput.label, focused)
                    else -> Clock()
                }
            }
        }
    }
}

@Composable
private fun Watching(item: Resumable, focused: Boolean) {
    Column {
        HeroLabel("CONTINUE WATCHING")
        Text(
            item.title,
            color = if (focused) Color.White else OnSurface,
            fontSize = 33.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.description?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val line = listOfNotNull(
            item.subtitle,
            item.minutesLeft?.let { "$it MIN LEFT" },
        ).joinToString(" - ")
        if (line.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(line.uppercase(), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Only when the app reported where it had got to. No bar beats a made-up one.
        item.progress?.let { fraction ->
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(225.dp).height(2.dp).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(Color.White))
            }
        }
    }
}

@Composable
private fun Offer(focused: Boolean) {
    Column {
        HeroLabel("CONTINUE WATCHING")
        Text(
            "Show what you were watching",
            color = if (focused) Color.White else OnSurface,
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Orbit can read the TV's own Continue watching list. Press OK to allow it.",
            color = Muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun BackTo(label: String, focused: Boolean) {
    Column {
        HeroLabel("CARRY ON")
        Text(
            "Back to $label",
            color = if (focused) Color.White else OnSurface,
            fontSize = 33.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Nothing to offer, so the hero says something true and useful instead. */
@Composable
private fun Clock() {
    val now by produceState(initialValue = Date()) {
        while (true) {
            value = Date()
            delay(10_000)
        }
    }
    val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val day = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()) }
    Column {
        Text(time.format(now), color = OnSurface, fontSize = 48.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(2.dp))
        Text(day.format(now), color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun HeroLabel(text: String) {
    Text(text, color = Color(0xFF7FD4FF), fontSize = 11.sp)
    Spacer(Modifier.height(5.dp))
}

@Composable
private fun Notice(
    title: String,
    detail: String,
    requester: FocusRequester,
    above: FocusRequester,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .focusProperties { up = above }
            .fillMaxWidth()
            .background(Color(0xA60B1017))
            .padding(horizontal = SafeH, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
        Pill("Details", onDetails, Modifier.focusRequester(requester))
        Spacer(Modifier.width(9.dp))
        Pill("Dismiss", onDismiss)
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
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
private suspend fun FocusRequester?.focusWhenReady(): Boolean = focusWhenReady { this }

/**
 * The same, for a requester that may not exist yet either: [requester] is asked again on every
 * try, because a row only creates a tile's requester when it composes the tile.
 */
private suspend fun focusWhenReady(requester: () -> FocusRequester?): Boolean {
    repeat(30) {
        if (runCatching { requester()?.requestFocus() != null }.getOrDefault(false)) return true
        delay(16)
    }
    return false
}

/**
 * Scrolls just far enough to bring the item at [index] inside the safe area, the same way focus
 * does. An item not on screen at all is scrolled to the start, which is also what composes it.
 */
private suspend fun LazyListState.reveal(index: Int) {
    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return scrollToItem(index)
    // Item offsets start where the content padding ends, which is the safe area's edge.
    val end = layout.viewportEndOffset - layout.afterContentPadding
    val by = when {
        item.offset < 0 -> item.offset
        item.offset + item.size > end -> item.offset + item.size - end
        else -> return
    }
    animateScrollBy(by.toFloat())
}

/**
 * Keeps the row where it is through the reorder that comes next.
 *
 * A row keeps its first tile on screen through a reorder, so moving the first tile on screen
 * scrolled the whole row along under it instead, and the tile appeared not to move. Holding the
 * position by place rather than by tile lets the tile itself move.
 */
private fun LazyListState.holdStill() {
    requestScrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset)
}

/**
 * Makes a row one group for focus, and brings focus coming in from above or below back to the
 * tile it left, rather than to whichever tile happens to sit nearest. Only while that tile is on
 * screen: asking for one that is not would cancel the move altogether.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.rowFocus(
    listState: LazyListState,
    last: () -> String?,
    requesters: Map<String, FocusRequester>,
    onFocused: () -> Unit,
    // Where up goes when leaving the row. Compose would pick whatever overlaps the tile's own
    // column, which is the hero, right past the notice sitting between them.
    exitUp: () -> FocusRequester? = { null },
): Modifier = onFocusChanged { if (it.hasFocus) onFocused() }
    .focusProperties {
        enter = { direction ->
            val back = last()?.takeIf { id -> listState.layoutInfo.visibleItemsInfo.any { it.key == id } }
            val across = direction == FocusDirection.Up || direction == FocusDirection.Down
            back?.takeIf { across }?.let { requesters[it] } ?: FocusRequester.Default
        }
        exit = { direction ->
            exitUp().takeIf { direction == FocusDirection.Up } ?: FocusRequester.Default
        }
    }
    .focusGroup()

/**
 * How far a row scrolls to show the tile that takes focus: only as far as it takes to bring the
 * tile inside the safe area, [margin] in from either edge of the screen.
 *
 * Left to itself, Compose scrolls a focused tile only until it touches the edge of the screen, in
 * the strip a TV may crop off. On a TV it does something else again: it holds the focused tile 30%
 * of the way along the row, so the row slides under it on nearly every press.
 */
@OptIn(ExperimentalFoundationApi::class)
private class SafeAreaScroll(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val beforeStart = offset - margin
        val pastEnd = offset + size - (containerSize - margin)
        return when {
            beforeStart < 0 -> beforeStart
            pastEnd > 0 -> pastEnd
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SafeAreaScrolling(content: @Composable () -> Unit) {
    val spec = with(LocalDensity.current) { remember(this) { SafeAreaScroll(SafeH.toPx()) } }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Keys for a tile that can be moved: feeds each one to the tile's [TileKeys] and does what it
 * says, which is where the rules and their reasons live.
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
private fun PlusTile(
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
private fun TileLabel(text: String, width: Dp, color: Color = Color.White) {
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
private fun Modifier.popOut(lit: Boolean): Modifier {
    val scale by animateFloatAsState(if (lit) FocusScale else 1f, tween(150), label = "pop-out")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
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

/** Where this sits on screen, unclipped, so a tile part-scrolled off the row still reports its size. */
private fun LayoutCoordinates.rootBounds(): Rect =
    findRootCoordinates().localBoundingBoxOf(this, clipBounds = false)

/** Says whether the app could be started. One can lose its launcher entry while installed. */
private fun Context.launchApp(packageName: String): Boolean = AppRepository.launch(this, packageName)

/**
 * Marks the input as last used only once the switch is under way, so a failed one does not, and
 * records it for the hero at the same time.
 */
private fun Context.switchToInput(inputId: String): Boolean =
    startActivitySafely(InputRepository.switchIntent(inputId)).also { switched ->
        if (switched) {
            InputRepository.rememberUse(this, inputId)
            LastOpened.record(this, LastOpened.INPUT, inputId)
        }
    }

/** Starts [intent], and says whether it could. A TV can lack any of the screens Orbit links to. */
private fun Context.startActivitySafely(intent: Intent): Boolean =
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

/**
 * Opens one of Orbit's own screens, in Orbit's task.
 *
 * Deliberately without FLAG_ACTIVITY_NEW_TASK: with it, a second open finds the screen's task
 * already there and brings it forward as it was, dropping the intent and every extra on it. The
 * picker then reopens in whichever mode it was in last. In Orbit's own task it also means Back
 * comes straight home.
 */
private fun Context.openScreen(intent: Intent): Boolean =
    runCatching { startActivity(intent) }.isSuccess

/** True when the owner has turned on Orbit's home button service in the accessibility settings. */
private fun isHomeButtonServiceOn(context: Context): Boolean {
    val ours = ComponentName(context, HomeButtonService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
}

/** The home screen's own settings. */
private object HomePrefs {
    private const val KEY_NOTICE_DISMISSED = "notice_dismissed"
    private const val KEY_LISTINGS_ASKED = "listings_asked"

    fun noticeDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTICE_DISMISSED, false)

    /** Asked once. A refusal is an answer, and the hero stops asking either way. */
    fun listingsAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LISTINGS_ASKED, false)

    fun rememberListingsAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_LISTINGS_ASKED, true).apply()
    }

    fun dismissNotice(context: Context) {
        prefs(context).edit().putBoolean(KEY_NOTICE_DISMISSED, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("orbit.home", Context.MODE_PRIVATE)
}
