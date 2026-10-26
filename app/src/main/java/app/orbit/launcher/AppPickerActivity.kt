package app.orbit.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
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

/**
 * The grid behind the add tile: every app, with the favorites already ticked.
 *
 * The same grid is also the All apps screen. The rows show favorites only, so without that an
 * app that is not a favorite could not be opened from Orbit at all.
 */
class AppPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opening = intent?.getBooleanExtra(EXTRA_OPEN, false) == true
        val backdrops = intent?.getBooleanExtra(EXTRA_BACKDROPS, false) == true
        setContent { PickerScreen(opening, backdrops) }
    }

    companion object {
        /** Open the chosen app rather than add it to the favorites. */
        const val EXTRA_OPEN = "app.orbit.launcher.extra.OPEN"

        /** Choose which apps supply the hero's ambient pictures, rather than the favorites. */
        const val EXTRA_BACKDROPS = "app.orbit.launcher.extra.BACKDROPS"
    }
}

@Composable
private fun PickerScreen(opening: Boolean, backdrops: Boolean = false) {
    val context = LocalContext.current
    // Uninstalling from the hold menu takes an app off the television while this grid is still
    // showing it, so the same watch the home screen keeps is kept here.
    DisposableEffect(Unit) {
        val receiver = PackageWatch.watch(context)
        onDispose { PackageWatch.stop(context, receiver) }
    }
    val all by produceState(initialValue = AppRepository.cached(), PackageWatch.revision) {
        value = withContext(Dispatchers.IO) { AppRepository.load(context) }
    }
    // Only the apps that actually publish a picture are worth offering as a source.
    // The lint check cannot see the assignment through the withContext call; the baseline
    // already carries eight of the same false report.
    // Nothing can be read without this, so an empty grid means one of two very different
    // things and the screen has to be able to tell them apart.
    var listings by remember { mutableStateOf(ContinueWatching.granted(context)) }
    var refusedListings by remember { mutableStateOf(false) }
    val askListings = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        listings = it
        // Set once the system will no longer show its prompt. Asking again then does nothing
        // at all, so the button has to send the owner to the app's settings instead.
        refusedListings = !it &&
            (context as? Activity)?.shouldShowRequestPermissionRationale(ContinueWatching.PERMISSION) == false
    }
    @Suppress("ProduceStateDoesNotAssignValue")
    val publishers by produceState(initialValue = emptyList<String>(), backdrops, listings) {
        value = if (backdrops) withContext(Dispatchers.IO) { Backdrops.publishers(context) } else emptyList()
    }
    val offered = remember(all, publishers, backdrops) {
        if (!backdrops) all else all.filter { it.packageName in publishers }
    }
    // The backdrops screen is already down to the handful of apps that publish anything, so a
    // filter there would be three buttons over a list of four.
    // Nor over an empty grid: three buttons reading (0) are not a filter, they are furniture,
    // and being focusable they also took the focus that belonged to nothing at all.
    val filtering = !backdrops && offered.isNotEmpty()
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    val apps = remember(offered, filter, filtering) {
        if (!filtering) offered else offered.filter { filter.keeps(it) }
    }
    val revision = FavoriteStore.revision + Backdrops.revision
    val favorites = remember(revision, backdrops) {
        if (backdrops) Backdrops.sources(context).toList()
        else FavoriteStore.favorites(context, FavoriteStore.APPS)
    }
    // Deliberately keyed on the apps alone, so toggling a favorite does not reshuffle the grid
    // under the owner's hand while they are still choosing. Keyed on their package names rather
    // than the list itself: the fresh read that replaces the cached one is a new list of the same
    // apps, and it must not rearrange the grid with the favorites as they have been toggled since.
    // Only the order is kept, so the tiles still show the fresh read's names and banners.
    val order = remember(apps.map { it.packageName }) {
        inPickerOrder(apps, favorites).map { it.packageName }
    }
    val ordered = remember(order, apps) {
        val byName = apps.associateBy { it.packageName }
        order.mapNotNull { byName[it] }
    }
    val chosen = ordered.count { it.packageName in favorites }

    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf<TileMenu?>(null) }

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

    // The menu takes the focus while it is open, so it has to be handed back on the way out,
    // or the remote is left with nothing focused on the screen behind it.
    fun closeMenu(id: String) {
        menu = null
        scope.launch { focusWhenReady { requesters[id] } }
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = SafeH, vertical = SafeV)
        ) {
            Text(
                if (backdrops) "Backdrop pictures" else if (opening) "All apps" else "Favorite apps",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                // Counted from the apps on the grid: a favorite that is disabled for now is kept, but
                // there is nothing here to untick.
                when {
                    // The rows these come from carry no genre and no age rating at all, so the app
                    // is the only thing there is to choose by. Said plainly rather than offering a
                    // genre list that would quietly match nothing.
                    backdrops && chosen == 0 ->
                        "Pick which apps supply the pictures behind the hero. There is nothing to " +
                            "choose by but the app: the TV's own rows carry no genre and no rating."
                    backdrops -> "Pictures come from $chosen app${if (chosen == 1) "" else "s"}. " +
                        "Select to add or remove."
                    opening -> "Select to open. Hold OK to add to favorites or uninstall."
                    // Said here because a hold is the only way to reach the menu, and nothing
                    // on a tile shows that it is there. Uninstall is named rather than "more
                    // options", since adding and removing is what a press already does.
                    chosen == 0 -> "Select an app to add it to the row. Hold OK to uninstall."
                    else -> "Select to add or remove. $chosen chosen. Hold OK to uninstall."
                },
                color = Muted,
                fontSize = 14.sp,
            )
            if (filtering) {
                Spacer(Modifier.height(14.dp))
                FilterRow(
                    current = filter,
                    // Counted off the whole list, so a count never changes with the filter and the
                    // owner can see what is behind the other two before pressing them.
                    countOf = { kind -> offered.count { kind.keeps(it) } },
                    onPick = { filter = it },
                )
            }
            Spacer(Modifier.height(18.dp - PopRoom))

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(TileW + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = PopRoom, bottom = SafeV),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // An empty grid used to be a title, a sentence about choosing, and then nothing:
                // no reason given and no way forward, which is what an owner who declined the
                // listings would see every time they opened this.
                if (backdrops && ordered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            if (listings) {
                                "No app on this TV publishes pictures Orbit can use. Apps that keep " +
                                    "their own rows on the TV's home screen are the ones that do."
                            } else {
                                "Orbit needs access to the TV listings to see which apps publish " +
                                    "pictures. Without it there is nothing to choose from."
                            },
                            color = Muted,
                            fontSize = 13.sp,
                        )
                        if (!listings) {
                            Spacer(Modifier.height(10.dp))
                            if (refusedListings) {
                                ActionButton("Open app settings", autoFocus = true) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", context.packageName, null),
                                            )
                                        )
                                    }
                                }
                            } else {
                                ActionButton("Allow access", autoFocus = true) {
                                    askListings.launch(ContinueWatching.PERMISSION)
                                }
                            }
                        }
                    }
                }

                itemsIndexed(ordered, key = { _, app -> app.packageName }) { index, app ->
                    val openMenu: (Rect) -> Unit = { anchor ->
                        menu = TileMenu(
                            key = FavoriteStore.APPS,
                            id = app.packageName,
                            label = app.label,
                            anchor = anchor,
                            // The same rule the rows use: offered only where the system would
                            // accept it, and worded for which of the two it would do.
                            packageName = app.packageName
                                .takeIf { app.removable || app.factoryUpdated },
                            updatesOnly = !app.removable && app.factoryUpdated,
                            favorite = app.packageName in favorites,
                        )
                    }
                    // The same menu on both app screens, so a hold means one thing wherever the
                    // grid is reached from. Not on the backdrops screen: a tile there is a source
                    // of pictures, not an app the row holds.
                    val hold: ((Rect) -> Unit)? = if (backdrops) null else openMenu
                    PickerTile(
                        app = app,
                        favorite = app.packageName in favorites,
                        requester = remember(app.packageName) {
                            requesters.getOrPut(app.packageName) { FocusRequester() }
                        },
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
                        held = menu?.id == app.packageName,
                        onHold = hold,
                    ) {
                        when {
                            backdrops -> Backdrops.toggle(context, app.packageName)
                            opening -> AppRepository.launch(context, app.packageName)
                            else -> FavoriteStore.toggle(context, FavoriteStore.APPS, app.packageName)
                        }
                    }
                }
            }
        }

        // Open and Move are left out here. A press on a tile already does the screen's own
        // thing, opening on All apps and adding or removing on the other, so neither entry
        // would be more than a second way to it, and the order of the row is set in the row
        // itself. What no press reaches is taking an app off the television.
        menu?.let { target ->
            TileMenuSheet(
                target = target,
                onFavorite = {
                    FavoriteStore.toggle(context, FavoriteStore.APPS, target.id)
                    closeMenu(target.id)
                },
                onUninstall = {
                    closeMenu(target.id)
                    target.packageName?.let { pkg ->
                        context.startActivitySafely(
                            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))
                        )
                    }
                },
                onDismiss = { closeMenu(target.id) },
            )
        }
    }
}

/**
 * Three buttons over the grid, because a set has far more factory apps than installed ones and
 * the ones nobody is looking for were sorted in among the rest by name.
 *
 * Focus reaches them by pressing up from the first row of tiles, since they are the thing above
 * the grid in the same column.
 */
@Composable
private fun FilterRow(
    current: AppFilter,
    countOf: (AppFilter) -> Int,
    onPick: (AppFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppFilter.entries.forEach { kind ->
            var focused by remember { mutableStateOf(false) }
            val chosen = kind == current
            Box(
                Modifier
                    .onFocusChanged { focused = it.hasFocus }
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (chosen) Color.White else Color(0xFF2A3444))
                    // Blue rather than white, so a button that is both chosen and focused still
                    // reads as two different things at once.
                    .border(
                        width = if (focused) 2.dp else 0.dp,
                        color = if (focused) Color(0xFF9FC3FF) else Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .clickable { onPick(kind) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    "${kind.label} (${countOf(kind)})",
                    color = if (chosen) Color(0xFF14181F) else Color.White,
                    fontSize = 13.sp,
                )
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
    /** Handed the tile as it is drawn, so a menu can hang off it and leave it undimmed. */
    onHold: ((Rect) -> Unit)? = null,
    /** Set while this tile's menu is open, so it stays popped out behind it. */
    held: Boolean = false,
    onClick: () -> Unit,
) {
    // The clickable is the tile's only focus target. A focusable() in front of it would make a
    // second one: OK would move focus into the clickable, and Back would then only move it out
    // again instead of leaving the picker. Read with hasFocus all the same, so the highlight does
    // not depend on which node inside the tile holds focus.
    var focused by remember { mutableStateOf(false) }
    var pressedAt by remember { mutableLongStateOf(0L) }
    var handledPress by remember { mutableLongStateOf(0L) }
    // Stays out while its menu is open, the way the rows keep the tile up behind theirs. The
    // scrim's hole is the grown tile, so a tile that shrank back under it would leave a lit
    // ring of background around itself.
    val lit = focused || held
    val spot = remember { Spot() }
    Column(
        Modifier.onGloballyPositioned { spot.whole = it.rootBounds() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(TileW, TileH)
                .scale(if (lit) 1.12f else 1f)
                .onGloballyPositioned { spot.tile = it.rootBounds() }
                .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged {
                    focused = it.hasFocus
                    if (it.hasFocus) onFocused()
                }
                .onPreviewKeyEvent { event ->
                    val down = event.type == KeyEventType.KeyDown
                    val up = event.type == KeyEventType.KeyUp
                    val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
                    when {
                        event.key == Key.DirectionLeft && onEdgeLeft != null -> {
                            if (down) onEdgeLeft(); true
                        }
                        event.key == Key.DirectionRight && onEdgeRight != null -> {
                            if (down) onEdgeRight(); true
                        }
                        onHold == null -> false
                        up && event.key == Key.Menu -> { onHold(spot.rect()); true }
                        ok && down && event.isHeld() -> {
                            // Auto-repeat keeps sending downs, so act once per press. Identified
                            // by the press, because the tile may never see the release.
                            val press = event.nativeKeyEvent.downTime
                            if (handledPress != press) {
                                handledPress = press
                                pressedAt = 0L
                                onHold(spot.rect())
                            }
                            true
                        }
                        ok && down -> {
                            if (pressedAt == 0L) pressedAt = SystemClock.elapsedRealtime()
                            false
                        }
                        ok && up -> {
                            val press = event.nativeKeyEvent.downTime
                            val longByTime = pressedAt != 0L &&
                                SystemClock.elapsedRealtime() - pressedAt >= LongPressMs
                            pressedAt = 0L
                            when {
                                // Already handled as a hold; swallow the release so the tile does
                                // not open the app as well.
                                handledPress == press -> true
                                // A remote that sends no long-press flag still gets the hold.
                                longByTime -> { onHold(spot.rect()); true }
                                else -> false
                            }
                        }
                        else -> false
                    }
                }
                // No indication: now that the clickable holds focus itself, its focus layer
                // would draw a dark wash over the tile. The pop-out already says which tile it is.
                .clickable(interactionSource = null, indication = null, onClick = onClick)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface),
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
            color = if (lit) Color.White else Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
