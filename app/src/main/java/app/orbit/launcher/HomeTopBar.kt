package app.orbit.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The row of buttons along the top, and where each of them leads. */

/**
 * The date, the way out to the TV's own screens, the profile picture and the time.
 *
 * [last] is the button last used, for focus to come back to after where it led, and
 * [onFocused] reports each button by name as it takes focus. [onOpen] starts where a button leads,
 * given the name to tell the owner if it cannot.
 */
/** The room a top bar glyph takes. Unchanged from the disc it replaces, so nothing below moves. */
internal val TopBarButtonSize = 21.dp

/**
 * The instant both clocks show: the real one, or whatever a screenshot run pinned.
 *
 * A screen with a clock on it photographs differently every minute, which was enough on its own
 * to make most shots of the home screen differ from the last recording for a reason nobody
 * changed. Pinning it leaves the comparison about the layout.
 */
internal fun reading(): Date = Date(ShotHooks.now ?: System.currentTimeMillis())

@Composable
internal fun TopBar(
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
    // The map is remembered, so getOrPut hands back the same requester for the life of the bar
    // rather than a new one per frame. Lint cannot see that through the map, and this is not a
    // composable lambda, so remember cannot be used here to tell it.
    @Suppress("RememberInComposition")
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
    var now by remember { mutableStateOf(reading()) }
    var twentyFour by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = reading()
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
        Text(date.format(now), color = Muted, fontSize = 12.sp, style = Legible)
        Spacer(Modifier.weight(1f))
        Shortcuts(first, button, destinations, onOpen)
        Avatar(button)
        Spacer(Modifier.width(9.dp))
        Text(time.format(now), color = Color.White, fontSize = 16.sp, style = Legible)
    }
}

/**
 * Where the top bar's buttons lead, and the store's own icon, so it reads as the store at a
 * glance. Null where the TV has nowhere for a button to go.
 */
internal class Destinations(
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
internal fun Shortcuts(
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

    // Beside search, because the only other way in was holding OK on the artwork, which says
    // nowhere that it exists. Always offered: with nothing ticked there are no backdrops, and
    // with no backdrops the hero has nothing to hold OK on, which made it a one-way door.
    TopBarButton(
        button("backdrops")
            .then(if (searchIntent == null) Modifier.focusRequester(first) else Modifier)
            .then(
                if (searchIntent == null) {
                    Modifier.focusProperties { left = FocusRequester.Cancel }
                } else {
                    Modifier
                }
            ),
        onClick = {
            context.openScreen(
                Intent(context, AppPickerActivity::class.java)
                    .putExtra(AppPickerActivity.EXTRA_BACKDROPS, true)
            )
        },
    ) {
        Icon(
            imageVector = Glyphs.Picture,
            contentDescription = "Backdrops",
            tint = Glyph,
            modifier = Modifier.padding(4.dp),
        )
    }
    Spacer(Modifier.width(9.dp))

    // Favorites are what the rows show, so without this an app that is not a favorite could not
    // be opened from Orbit at all.
    // No left edge of its own any more: Backdrops always sits to its left, so cancelling left
    // here would strand focus on it.
    TopBarButton(
        button("apps"),
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
                // The store's own icon, drawn as a silhouette rather than in its own colours.
                // Icon tints what it is given, so the shape is still the store's and the
                // colour is the one every other glyph up here uses.
                Icon(
                    bitmap = storeIcon,
                    contentDescription = "Play Store",
                    tint = Glyph,
                    modifier = Modifier.padding(4.dp),
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
            modifier = Modifier.padding(4.dp),
        )
    }
    Spacer(Modifier.width(9.dp))
}

@Composable
internal fun Avatar(button: (String) -> Modifier) {
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
internal fun TopBarButton(
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(TopBarButtonSize)
            .scale(if (focused) 1.15f else 1f)
            .then(modifier)
            // hasFocus, not isFocused: while OK is down, Compose hands focus to the clickable
            // inside, and the button should stay lit through the press.
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onClick)
            .clip(CircleShape)
            // No disc and no ring until it is focused, the way the stock top bar draws these.
            // The one that has focus gets a ring rather than only going brighter, because
            // brightness alone is a weak mark on a screen being driven from across a room.
            .then(
                if (focused) {
                    Modifier.border(width = 2.dp, color = Color.White, shape = CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** How far down the screen the top fade reaches. Deep enough to clear the bar and its scale. */
/**
 * The clock and the date, carrying their own contrast.
 *
 * A soft black halo, which over a dark picture is invisible and over a pale one is the only
 * thing holding thin white text apart from what is behind it. It is here so the fade above
 * does not have to be dark enough to do that job on its own, which is what made it show.
 */
private val Legible = TextStyle(
    shadow = Shadow(color = Color.Black.copy(alpha = 0.85f), offset = Offset.Zero, blurRadius = 10f),
)

/**
 * How far the fade reaches.
 *
 * Nearly twice what it was. The fade is what hides the fade: run over 72dp it finished while
 * there was still a third of its darkness left to lose, and the place it finished was a line
 * across the screen. Measured on the set over a bright picture, the luminance climbed from 7
 * to 43 down the bar and then flattened at 144px, which is exactly 72dp, and that flattening
 * is the edge the owner saw as a panel behind the glyphs.
 */
private val ScrimHeight = 132.dp

/**
 * The darkness at each step down the fade.
 *
 * Each step is a little over half the one before it, which is what a fade with no edge in it
 * looks like: the last stops are too faint to find, so there is no line where it stops. The
 * first stop is lighter than it was, because the clock and the date no longer lean on it
 * alone, and the glyphs are white silhouettes with a lot of weight to them.
 */
internal val ScrimStops = listOf(0.62f, 0.42f, 0.25f, 0.13f, 0.05f, 0f)

/**
 * A fade from black at the top edge to nothing below the bar.
 *
 * The hero's artwork is drawn full bleed and reaches the top of the screen, so a pale or busy
 * frame up there took the clock and the glyphs with it. This sits between the picture and the
 * bar. Over a dark picture it is invisible, so nothing is lost where nothing was wrong.
 */
@Composable
internal fun TopScrim(art: ImageBitmap?, modifier: Modifier = Modifier) {
    // Once per picture: the strip is a few thousand pixels and the artwork only changes when
    // the hero steps.
    // A launcher must not come down over a picture it could not read. Nothing pale found is
    // the same answer as a dark picture, which is the scrim it already had.
    val pale = remember(art) { art?.let { runCatching { paleShare(it) }.getOrDefault(0f) } ?: 0f }
    val strength = scrimStrength(pale)
    Box(
        modifier
            .fillMaxWidth()
            .height(ScrimHeight)
            .background(
                Brush.verticalGradient(
                    *ScrimStops.mapIndexed { i, alpha ->
                        i / (ScrimStops.size - 1f) to
                            Color.Black.copy(alpha = (alpha * strength).coerceAtMost(ScrimCeiling))
                    }.toTypedArray()
                )
            )
    )
}

/** Above this a pixel counts as pale enough to lose a white glyph in. */
private const val PaleAt = 0.7f

/** Where the scrim starts working harder, and where it stops. */
internal const val PaleFloor = 0.05f
internal const val PaleFull = 0.3f

/** The most the fade is ever multiplied by, and the darkest any one stop may end up. */
internal const val ScrimMaxStrength = 1.5f
internal const val ScrimCeiling = 0.9f

/**
 * The share of the strip under the bar, from nothing to one, that is pale enough to swallow a
 * white glyph.
 *
 * A mean would not do. Prime publishes artwork with its own banner laid across the top, a white
 * pill with black lettering in it, which covers perhaps a quarter of the strip: bright enough to
 * lose the clock in, not bright enough to move an average over a picture that is dark elsewhere.
 * Counting how much of it is pale finds that where an average cannot.
 *
 * Only the part the glyphs actually sit over is read. The artwork is drawn against the right
 * edge and covers [BackdropWidth] of the screen, so the left of it is behind the hero's words,
 * where nothing needs protecting.
 */
internal fun paleShare(art: ImageBitmap): Float {
    val width = art.width
    val height = art.height
    if (width <= 0 || height <= 0) return 0f
    val from = (width * GlyphsFrom).toInt().coerceIn(0, width - 1)
    val span = width - from
    val strip = (height * StripShare).toInt().coerceIn(1, height)
    val line = IntArray(span)
    var pale = 0
    var counted = 0
    for (r in 0 until StripRows) {
        val y = (strip.toLong() * r / StripRows).toInt().coerceAtMost(height - 1)
        art.readPixels(line, startX = from, startY = y, width = span, height = 1)
        var x = 0
        while (x < span) {
            if (luminance(line[x]) > PaleAt) pale++
            counted++
            x += PixelStep
        }
    }
    return if (counted == 0) 0f else pale.toFloat() / counted
}

/** Rec. 709, which weights the channels the way the eye does rather than evenly. */
private fun luminance(pixel: Int): Float =
    (0.2126f * ((pixel shr 16) and 0xFF) +
        0.7152f * ((pixel shr 8) and 0xFF) +
        0.0722f * (pixel and 0xFF)) / 255f

/** Where the glyphs begin across the artwork, and how far down the picture the bar reaches. */
private const val GlyphsFrom = 0.23f
private const val StripShare = 0.18f
private const val StripRows = 4
private const val PixelStep = 8

/**
 * What to multiply the fade by for a strip that is [pale] pale.
 *
 * One for the dark artwork that most of it is, so nothing changes where nothing was wrong, and
 * up to [ScrimMaxStrength] once a pale thing is sitting under the bar. Ramped rather than
 * switched, because the hero steps between pictures and a scrim that snapped between two
 * darknesses would be its own kind of ugly.
 */
internal fun scrimStrength(pale: Float): Float {
    val over = (pale - PaleFloor) / (PaleFull - PaleFloor)
    return 1f + (ScrimMaxStrength - 1f) * over.coerceIn(0f, 1f)
}
