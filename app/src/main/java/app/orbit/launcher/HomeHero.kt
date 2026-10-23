package app.orbit.launcher

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The top of the screen: what is worth saying, and the artwork behind it. */

internal val HeroMinH = 108.dp

/** How long each thing to carry on with is shown before the next one. */
internal const val CycleMs = 10_000L

/**
 * The longest the hero will wait, on arrival, for a picture it has reason to expect.
 *
 * A safety net rather than the usual path: the wait normally ends when the fetch itself comes
 * back, success or failure, and a fetch is already bounded by its own timeouts. This only
 * covers one that somehow never returns at all. Only arrival: once a picture is up, the one
 * on the screen stays there while the next is fetched, so there is no gap to cover.
 */
internal const val SettleMs = 6_000L

/** The hero's words stay left of the artwork, whatever length an app gives them. */
internal const val HeroTextWidth = 0.52f

/** The notice stays left of where the artwork becomes visible through its fade. */
internal const val NoticeWidth = 0.56f

/** How much of the screen the artwork covers, before it is faded out. */
internal const val BackdropWidth = 0.60f
internal const val BackdropHeight = 0.78f

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
internal fun Hero(
    apps: List<LaunchableApp>,
    inputs: List<TvInput>?,
    refocus: Int,
    self: FocusRequester,
    above: FocusRequester,
    below: FocusRequester?,
    watching: Watching,
    onFocused: () -> Unit,
) {
    val context = LocalContext.current
    val requester = self
    var focused by remember { mutableStateOf(false) }
    var heldPress by remember { mutableLongStateOf(0L) }
    val granted = watching.granted
    val asked = watching.asked

    val item = watching.item
    val progress = watching.progress
    val ambient = watching.picture?.takeIf { it.title != null }
    // Orbit's own record of what it opened last, read again on every return so it is the one
    // just left rather than the one from when the screen was built.
    val back = remember(Returns.count) { LastOpened.recent(context) }
    val backApp = back?.takeIf { it.kind == LastOpened.APP }
        ?.let { entry -> apps.firstOrNull { it.packageName == entry.id } }
    val backInput = back?.takeIf { it.kind == LastOpened.INPUT }
        ?.let { entry -> inputs?.firstOrNull { it.id == entry.id } }
    // Named after the app that published it. "From your library" was a claim Orbit cannot
    // make: most of these come from a shop front, not from anything the owner has.
    val ambientSource = ambient?.let { picture ->
        apps.firstOrNull { it.packageName == picture.packageName }?.label
    }
    val action: (() -> Unit)? = when {
        item != null -> {
            {
                ContinueWatching.intentFor(item)?.let { context.startActivitySafely(it) }
            }
        }
        // Asked for once, in the hero, rather than as a popup on a screen nobody opened yet.
        !granted && !asked -> watching.ask
        // The picture came from an app, so OK opens that app. Holding OK configures which
        // apps supply them, the same gesture a tile uses for its menu.
        ambient != null -> { { context.launchApp(ambient.packageName) } }
        watching.settling -> null
        // Last of all, and only when there is neither something to carry on with nor a picture
        // to show for it. It is a way back rather than something to watch, so anything with a
        // name of its own goes first.
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
            // IntrinsicSize.Min first: the accent beside the text fills the height, and without
            // a bound to fill it would take the whole screen and push the rows off it.
            .height(IntrinsicSize.Min)
            .heightIn(min = HeroMinH)
            .focusProperties {
                up = above
                down = below ?: FocusRequester.Default
            },
        // Top aligned, not centred: one thing to carry on with has an episode line and the next
        // one does not, and centred text would jump up and down as the hero steps through them.
        verticalAlignment = Alignment.Top,
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
                .fillMaxWidth(HeroTextWidth)
                .focusRequester(requester)
                .onFocusChanged {
                    focused = it.hasFocus
                    if (it.hasFocus) onFocused()
                }
                // Holding OK is how the pictures are configured, the same gesture a tile uses
                // for its menu. The release is swallowed so the hero does not also resume.
                .onPreviewKeyEvent { event ->
                    val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
                    val press = event.nativeKeyEvent.downTime
                    when {
                        ok && event.type == KeyEventType.KeyDown && event.isHeld() -> {
                            if (heldPress != press) {
                                heldPress = press
                                context.openScreen(
                                    Intent(context, AppPickerActivity::class.java)
                                        .putExtra(AppPickerActivity.EXTRA_BACKDROPS, true)
                                )
                            }
                            true
                        }
                        ok && event.type == KeyEventType.KeyUp && heldPress == press -> true
                        else -> false
                    }
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
            when {
                item != null -> Watching(item, progress, focused)
                !granted && !asked -> Offer(focused)
                ambient != null -> Ambient(ambient, ambientSource, focused)
                // Still looking. Nothing is claimed in the meantime.
                watching.settling -> Unit
                backApp != null -> BackTo(backApp.label, focused)
                backInput != null -> BackTo(backInput.label, focused)
                else -> Clock()
            }
        }
    }
}

/** What the hero knows about Continue watching, read once for the whole screen. */
internal class Watching(
    val item: Resumable?,
    /** The bar, from the row itself or from what the TV was last seen playing. */
    val progress: Progress?,
    /** What the ambient picture is of, when one is being shown instead of a programme. */
    val picture: Picture?,
    /**
     * A picture is still being looked for, on arrival at the screen.
     *
     * The hero says nothing at all while this is true. What comes after a picture is a way back
     * to the last app, and offering that for the half second before the picture lands reads as
     * the hero changing its mind about what it has.
     */
    val settling: Boolean,
    val art: ImageBitmap?,
    val granted: Boolean,
    val asked: Boolean,
    val ask: () -> Unit,
)

/**
 * Reads the TV's Watch Next list and steps through it, so a second thing to carry on with is
 * not hidden behind the first. Read here rather than in the hero, because the artwork is drawn
 * behind the whole screen.
 */
@Composable
internal fun rememberWatching(focusedApp: String?): Watching {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContinueWatching.granted(context)) }
    var asked by rememberSaveable { mutableStateOf(HomePrefs.listingsAsked(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
        HomePrefs.rememberListingsAsked(context)
    }

    // The lint check cannot see the assignment through the withContext call, and reports it on
    // every produceState in this project; the baseline already carries eight of them. Writing
    // the result to a local first does not help, so these say so here instead of growing the
    // baseline, which is meant for warnings that were already there.
    @Suppress("ProduceStateDoesNotAssignValue")
    val items by produceState(initialValue = emptyList<Resumable>(), granted, Returns.count) {
        value = withContext(Dispatchers.IO) { ContinueWatching.all(context) }
    }
    // Only what the app under the cursor published. Another app's programme behind a tile that
    // knows nothing about it was the picture claiming something it could not.
    val mine = remember(items, focusedApp) { items.filter { it.packageName == focusedApp } }
    // Back to the first whenever the list itself changes, so the step is always within it.
    var index by remember(mine) { mutableIntStateOf(0) }
    LaunchedEffect(mine) {
        if (mine.size < 2) return@LaunchedEffect
        while (true) {
            delay(CycleMs)
            index = (index + 1) % mine.size
        }
    }
    val item = mine.getOrNull(index)

    // Ambient pictures, for an app that serves nothing of its own.
    // Null until the rows have been read. An empty list means they were read and there is
    // nothing to show, which is a different thing: one is worth waiting for, the other is not.
    @Suppress("ProduceStateDoesNotAssignValue")
    val ambient by produceState<List<Picture>?>(
        initialValue = null,
        Backdrops.revision,
        FavoriteStore.revision,
        Returns.count,
        items,
    ) {
        // Keyed on the titles so the pool is rebuilt when Watch Next changes.
        val shown = items.mapNotNull { it.title }.toSet()
        value = withContext(Dispatchers.IO) { Backdrops.art(context, except = shown) }
    }
    val pool = ambient.orEmpty()
    var ambientIndex by remember(ambient) { mutableIntStateOf(0) }
    LaunchedEffect(ambient, item != null) {
        if (item != null || pool.size < 2) return@LaunchedEffect
        while (true) {
            delay(CycleMs)
            ambientIndex = (ambientIndex + 1) % pool.size
        }
    }

    val picture = pool.getOrNull(ambientIndex)
    val uri = item?.posterUri ?: picture?.uri
    // The link this came back for, so a fetch still running can be told apart from one that
    // came back with nothing. Only the second is a reason to stop waiting for a picture.
    var fetched by remember { mutableStateOf<String?>(null) }
    @Suppress("ProduceStateDoesNotAssignValue")
    val art by produceState<ImageBitmap?>(initialValue = null, uri) {
        val decoded = withContext(Dispatchers.IO) {
            uri?.let { ContinueWatching.poster(context, it, maxWidth = 1280)?.asImageBitmap() }
        }
        fetched = uri
        value = decoded
    }
    // The row's own position where the app gave one, and what the TV was last seen playing
    // where it did not. A "next episode" row never carries a position.
    val progress = remember(item, Returns.count) {
        item?.let { it.progress ?: PlaybackMemory.progressFor(context, it.packageName, it.title) }
    }
    // The safety net, in case a fetch never comes back at all.
    var waited by remember(Returns.count) { mutableStateOf(false) }
    LaunchedEffect(Returns.count) {
        delay(SettleMs)
        waited = true
    }
    // Only once its picture is actually on screen: a title over a black rectangle is worse
    // than no hero at all, and some artwork never loads.
    val shown = picture.takeIf { item == null && art != null }
    // Still looking: the rows have not been read yet, or one of them is being fetched. Rows can
    // only be read with the permission, so without it nothing is coming and nothing is waited
    // for. On a cold start this runs to a few seconds, which was long enough to watch the hero
    // offer a way back and then take it away again, the thing it was taken out for.
    val looking = ambient == null || (picture != null && art == null && fetched != uri)
    val settling = item == null && granted && shown == null && looking && !waited
    return Watching(item, progress, shown, settling, art, granted, asked) {
        ask.launch(ContinueWatching.PERMISSION)
    }
}

/**
 * The programme's own artwork, bled in from the right and faded out towards the middle and the
 * bottom so it has no edge, with the rows sitting on top of it. The screen behind is black, so
 * black is what it fades into.
 */
@Composable
internal fun Backdrop(art: ImageBitmap?, modifier: Modifier) {
    val shown = art ?: return
    Image(
        bitmap = shown,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxWidth(BackdropWidth)
            .fillMaxHeight(BackdropHeight)
            .drawWithContent {
                drawContent()
                drawRect(Brush.horizontalGradient(0f to Color.Black, 0.55f to Color.Transparent))
                drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black))
                // A touch along the top as well: artwork that starts on a bright sky otherwise
                // meets the edge of the screen in a hard line, and the top bar sits on it.
                drawRect(Brush.verticalGradient(0f to Color.Black, 0.10f to Color.Transparent))
            },
    )
}

/**
 * The hero's words, the same shape wherever they come from: what this is, its name, what it is
 * about, a line of detail and the bar. A Watch Next row and an ambient picture both come through
 * here, so a description reads the same whichever one is on the screen.
 */
@Composable
internal fun HeroText(
    label: String,
    title: String,
    focused: Boolean,
    description: String? = null,
    detail: String? = null,
    progress: Progress? = null,
) {
    Column {
        HeroLabel(label)
        // Two lines, because a title does not deserve to be cut: the hero's column is a little
        // over half the screen, and plenty of real programmes are longer than that. Tighter
        // leading than the default, so a second line costs the rows as little height as it can.
        Text(
            title,
            color = if (focused) Color.White else OnSurface,
            fontSize = 33.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Light,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            // Two lines. These run to 300 characters, which left to themselves would fill the
            // screen and push the rows off it; one line cuts most of them mid-sentence.
            Text(
                it,
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        detail?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Only when a position was actually reported. No bar beats a made-up one.
        progress?.let {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .width(300.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x33FFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(it.fraction)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
internal fun Watching(item: Resumable, progress: Progress?, focused: Boolean) {
    HeroText(
        label = "CONTINUE WATCHING",
        title = item.title,
        focused = focused,
        description = item.description,
        // One line above the bar: the episode, where there is one, and the two numbers. A film
        // has no episode line, so it is left out rather than filled with a season.
        detail = listOfNotNull(item.subtitle?.uppercase(), progress?.summary).joinToString(" - "),
        progress = progress,
    )
}

@Composable
internal fun Offer(focused: Boolean) {
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

/** A picture, named after the app that published it, with whatever that app said about it. */
@Composable
internal fun Ambient(picture: Picture, source: String?, focused: Boolean) {
    HeroText(
        label = source?.uppercase()?.let { "ON $it" } ?: "ALSO ON THIS TV",
        title = picture.title.orEmpty(),
        focused = focused,
        description = picture.description,
        detail = picture.seasons?.let { if (it == 1) "1 SEASON" else "$it SEASONS" },
    )
}

/**
 * The way back to the last thing opened, when there is nothing to carry on with and no picture.
 *
 * A way back rather than something to watch, which is why it comes after both of those. It can
 * only name what Orbit itself opened: an app reached with the remote's own keys is not in here.
 */
@Composable
internal fun BackTo(label: String, focused: Boolean) {
    HeroText(label = "CARRY ON", title = "Back to $label", focused = focused)
}

/** Nothing to offer, so the hero says something true and useful instead. */
@Composable
internal fun Clock() {
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
internal fun HeroLabel(text: String) {
    Text(text, color = Color(0xFF7FD4FF), fontSize = 11.sp)
    Spacer(Modifier.height(5.dp))
}
