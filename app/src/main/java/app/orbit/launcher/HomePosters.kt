package app.orbit.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The row of library posters: the tall covers the publishing apps send that the hero cannot use.
 *
 * The hero takes wide artwork only, so until now every cover was read and thrown away, 112 of
 * the media server's 117 rows among them. They are the shape a poster is printed in, so shown
 * at that shape they need no cropping and carry their own title, which is why nothing is
 * written under them.
 *
 * The row is left out entirely when there is nothing in it. A set with no media server, or one
 * whose owner has not allowed the listings to be read, keeps exactly the screen it had.
 */

/**
 * Every picture the chosen apps publish, read once for the whole screen.
 *
 * The hero and this row want different shapes out of the same table. Reading it in each of them
 * walked 1359 rows twice on this set to answer one question, so the screen reads it here and
 * hands both of them what came back.
 *
 * Null until the read comes back, and an empty list once it has come back with nothing. The
 * hero tells the two apart: one is worth waiting for, the other is not.
 *
 * The lint check cannot see the assignment through the withContext call and reports it on every
 * produceState in this project, which is why this one says so too rather than growing the
 * baseline.
 */
@Composable
internal fun rememberLibrary(): List<Picture>? {
    val context = LocalContext.current
    @Suppress("ProduceStateDoesNotAssignValue")
    val library by produceState<List<Picture>?>(
        initialValue = null,
        Backdrops.revision,
        FavoriteStore.revision,
        Returns.count,
    ) {
        value = withContext(Dispatchers.IO) { Backdrops.all(context) }
    }
    return library
}

/** The covers out of what was read, ordered the usual way. */
@Composable
internal fun rememberPosters(library: List<Picture>?): List<Picture> {
    val context = LocalContext.current
    return remember(library) { library?.let { Backdrops.covers(it, context) }.orEmpty() }
}

/**
 * What to call the row of covers.
 *
 * "POSTERS" says what they are and nothing about where they came from, which was the honest
 * name while the row was taken to be mostly shop fronts. Measured on the set it is not: every
 * cover in the row came from the owner's own media server. So when they all come from one app
 * the row is named after that app, in the same words the hero uses directly above it, and when
 * they come from more than one it keeps the name that claims nothing.
 *
 * An app Orbit cannot name keeps the neutral name as well. A package name is not something to
 * put on a television, and naming the row after an app that is no longer installed would be
 * claiming more than Orbit knows.
 */
internal fun posterRowLabel(posters: List<Picture>, apps: List<LaunchableApp>): String {
    val only = posters.mapTo(mutableSetOf()) { it.packageName }.singleOrNull() ?: return "POSTERS"
    val named = apps.firstOrNull { it.packageName == only }?.label?.trim()
        ?.takeIf { it.isNotEmpty() } ?: return "POSTERS"
    // The hero's own formatter, so the two lines on the screen read as one voice.
    return sourceOf(named, null)
}

@Composable
internal fun PosterRow(
    posters: List<Picture>,
    refocus: Int,
    /** Focus belongs in this row: it was last here, and no menu has it. */
    active: Boolean,
    onFocused: () -> Unit,
    onOpen: (Picture) -> Unit,
) {
    val ids = remember(posters) { posters.map { it.uri } }
    val row = rememberRowFocus(ids)

    // Only on an explicit request, never on composition, so a row that arrives late cannot pull
    // focus out of the apps row above it or the inputs row below.
    LaunchedEffect(refocus, ids) {
        if (refocus == 0 || !active || row.lastFocused == null) return@LaunchedEffect
        row.restore(null)
    }

    LazyRow(
        modifier = Modifier
            .height(PosterRowHeight)
            .rowFocus(row, onFocused),
        state = row.listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = SafeH, top = RowTop, end = SafeH),
    ) {
        itemsIndexed(posters, key = { _, picture -> picture.uri }) { index, picture ->
            PosterTile(
                picture = picture,
                requester = row.requester(picture.uri),
                onFocused = { row.took(picture.uri, index) },
                onClick = { onOpen(picture) },
            )
        }
    }
}

/**
 * One cover, at the shape its app drew it in.
 *
 * The artwork is fetched at the width it is shown at rather than the hero's, so a row of them
 * costs a fraction of one backdrop. Until it arrives the tile holds its title, so the row has
 * its shape from the first frame instead of growing as pictures land.
 */
@Composable
private fun PosterTile(
    picture: Picture,
    requester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    @Suppress("ProduceStateDoesNotAssignValue")
    val art by produceState<ImageBitmap?>(initialValue = null, picture.uri) {
        value = withContext(Dispatchers.IO) {
            ContinueWatching.poster(context, picture.uri, maxWidth = PosterFetchPx)?.asImageBitmap()
        }
    }
    Box(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .size(PosterW, PosterH)
            .popOut(focused)
            .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocused()
            }
            // The clickable is the tile's only focus target. A focusable() in front of it would
            // make a second one, which is what broke the rows during the move to Compose.
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(6.dp))
            .background(Surface),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = art
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = picture.title,
                // The tile is the artwork's own shape, so this crops nothing worth keeping and
                // covers the rounding error when an app draws a cover slightly off 2:3.
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                picture.title.orEmpty(),
                color = OnSurface,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
