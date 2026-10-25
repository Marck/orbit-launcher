package app.orbit.launcher

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Counts returns to the launcher.
 *
 * Compose does not restore focus across an activity switch, so coming back from an app or from
 * the picker leaves nothing focused and the remote does nothing at all. The home screen watches
 * this and puts focus back where it was.
 */
internal object Returns {
    var count by mutableIntStateOf(0)
}

/** The parts of the screen focus can be in, so a return can put it back in the same one. */
private enum class Zone { APPS, LIBRARY, INPUTS, HERO, TOP }

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
    // The system says when an app is installed, removed or replaced, which is the only reason
    // the list can change while Orbit is not looking. Listening for that is what lets the read
    // below happen once rather than on every return.
    DisposableEffect(Unit) {
        val receiver = PackageWatch.watch(context)
        onDispose { PackageWatch.stop(context, receiver) }
    }
    // Off the main thread, all of it. produceState runs its producer on the composition's
    // context, so reading every launcher activity and decoding its artwork there holds up the
    // first frame and the screen stays black until it finishes.
    val apps by produceState(initialValue = AppRepository.cached(), PackageWatch.revision) {
        // The favorites first. They are all the rows show, so the screen is finished once they
        // are in; the whole catalogue takes long enough on a cold start to watch it arrive.
        if (value.isEmpty()) {
            val favorites = withContext(Dispatchers.IO) {
                FavoriteStore.favorites(context, FavoriteStore.APPS).toSet()
            }
            if (favorites.isNotEmpty()) {
                value = withContext(Dispatchers.IO) { AppRepository.load(context, only = favorites) }
            }
        }
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
    var playbackDismissed by remember { mutableStateOf(HomePrefs.playbackNoticeDismissed(context)) }
    // Asked for only once the home button is sorted, so there is never more than one bar at a
    // time, and never at all on a TV where nothing publishes a row without a position.
    @Suppress("ProduceStateDoesNotAssignValue")
    val playbackSeen by produceState(initialValue = true, Returns.count) {
        value = withContext(Dispatchers.IO) { PlaybackMemory.granted(context) }
    }

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
    var refocusLibrary by remember { mutableIntStateOf(0) }
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
    // Hoisted so the column can be put back to the top. See the effect further down.
    val column = rememberScrollState()
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
            Zone.LIBRARY -> refocusLibrary++
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

    // Compose scrolls a row into view as it takes focus, but it never scrolls back out. Going
    // down to the posters pushed the column up, and coming back to the apps row left it exactly
    // where the posters had put it, with the top bar and the date off the top of the screen.
    // The top bar, the hero and the apps row all sit at the top of the column, so whenever
    // focus is in one of them that is where the column belongs.
    LaunchedEffect(zone) {
        if (zone == Zone.TOP || zone == Zone.HERO || zone == Zone.APPS) column.animateScrollTo(0)
    }

    // There is nothing behind the home screen for Back to go to. Left to the system, Back would
    // close Orbit, only for it to be started again from scratch with nothing focused. A menu or a
    // move takes Back for itself before it gets here.
    BackHandler {
        zone = Zone.APPS
        toFirst++
    }

    // Kept while focus is elsewhere, so stepping up to the top bar does not blank the hero.
    var focusedApp by rememberSaveable { mutableStateOf<String?>(null) }
    // One read of the preview rows for the whole screen: the hero takes the wide ones, the
    // poster row takes the covers. Read unconditionally, because the row it feeds is only drawn
    // when it has something in it and a read inside that test would restart every time the row
    // appeared or went away.
    val library = rememberLibrary()
    val watching = rememberWatching(focusedApp, library)
    val posters = rememberPosters(library)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Backdrop(watching.art, Modifier.align(Alignment.TopEnd))
        // The artwork runs under the top bar, and a bright frame up there swallowed the clock
        // and the glyphs whole. A fade down from the top edge holds them legible against any
        // picture, and over a dark one there is nothing to see.
        TopScrim(Modifier.align(Alignment.TopCenter))
        Column(Modifier.fillMaxSize().verticalScroll(column)) {
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
                    watching = watching,
                    below = noticeFocus.takeIf { !homeOpensOrbit && !noticeDismissed },
                    onFocused = { moveTo(Zone.HERO) },
                )
            }
            // Was a weight, which pushed the rows to the bottom of a screen that always
            // fitted. With the posters in, the content is taller than the screen, so there is
            // no slack to hand out and the gap is the one the hero wants rather than whatever
            // is left over.
            Spacer(Modifier.height(HeroGap))
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
            } else if (watching.item != null && watching.progress == null &&
                !playbackSeen && !playbackDismissed
            ) {
                Notice(
                    title = "Orbit cannot see how far you got",
                    detail = "Turn Orbit on under notification access to show a progress bar",
                    requester = noticeFocus,
                    above = heroFocus,
                    onDetails = {
                        context.startActivitySafely(
                            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        )
                    },
                    onDismiss = {
                        playbackDismissed = true
                        HomePrefs.dismissPlaybackNotice(context)
                        zone = Zone.APPS
                        refocusApps++
                    },
                )
                Spacer(Modifier.height(14.dp))
            }
            // "Favorite apps" is what the picker that fills this row calls itself, and what
            // the row actually holds: the apps the owner put here, not every app on the set.
            RowLabel(
                if (editing != null && editingKey == FavoriteStore.APPS) {
                    "FAVORITE APPS - MOVING, OK TO KEEP"
                } else {
                    "FAVORITE APPS"
                }
            )
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
                onTile = { focusedApp = it },
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
            // Named after the app the covers came from when they all came from one, and after
            // what they are when they did not. The old fixed "POSTERS" was justified by the
            // covers mostly coming from a shop front, which measuring the set disproved.
            if (posters.isNotEmpty()) {
                RowLabel(posterRowLabel(posters, apps))
                PosterRow(
                    posters = posters,
                    refocus = refocusLibrary,
                    active = zone == Zone.LIBRARY && menu == null,
                    onFocused = { moveTo(Zone.LIBRARY) },
                    onOpen = { picture -> context.openPicture(picture) },
                )
                Spacer(Modifier.height(12.dp))
            }
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

/** Where this sits on screen, unclipped, so a tile part-scrolled off the row still reports its size. */
internal fun LayoutCoordinates.rootBounds(): Rect =
    findRootCoordinates().localBoundingBoxOf(this, clipBounds = false)

/** Says whether the app could be started. One can lose its launcher entry while installed. */
internal fun Context.launchApp(packageName: String): Boolean = AppRepository.launch(this, packageName)

/**
 * Marks the input as last used only once the switch is under way, so a failed one does not, and
 * records it for the hero at the same time.
 */
internal fun Context.switchToInput(inputId: String): Boolean =
    startActivitySafely(InputRepository.switchIntent(inputId)).also { switched ->
        if (switched) {
            InputRepository.rememberUse(this, inputId)
            LastOpened.record(this, LastOpened.INPUT, inputId)
        }
    }

/** Starts [intent], and says whether it could. A TV can lack any of the screens Orbit links to. */
internal fun Context.startActivitySafely(intent: Intent): Boolean =
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

/**
 * Opens one of Orbit's own screens, in Orbit's task.
 *
 * Deliberately without FLAG_ACTIVITY_NEW_TASK: with it, a second open finds the screen's task
 * already there and brings it forward as it was, dropping the intent and every extra on it. The
 * picker then reopens in whichever mode it was in last. In Orbit's own task it also means Back
 * comes straight home.
 */
internal fun Context.openScreen(intent: Intent): Boolean =
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
internal object HomePrefs {
    private const val KEY_NOTICE_DISMISSED = "notice_dismissed"
    private const val KEY_LISTINGS_ASKED = "listings_asked"
    private const val KEY_PLAYBACK_DISMISSED = "playback_notice_dismissed"

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

    fun playbackNoticeDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PLAYBACK_DISMISSED, false)

    fun dismissPlaybackNotice(context: Context) {
        prefs(context).edit().putBoolean(KEY_PLAYBACK_DISMISSED, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("orbit.home", Context.MODE_PRIVATE)
}
