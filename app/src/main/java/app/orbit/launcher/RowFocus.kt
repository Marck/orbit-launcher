package app.orbit.launcher

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The focus bookkeeping a row of tiles needs, which both rows of the home screen were keeping
 * their own copy of: which tile to come back to, where it sat, and the requester that gets it
 * there. The rows themselves stay separate, because what they draw and which keys they answer
 * are genuinely different; this is only the part that was the same in both, down to the comments.
 */
internal class RowFocus(
    /** Every tile in the row, in order, the add tile last. */
    val ids: List<String>,
    val listState: LazyListState,
    private val requesters: MutableMap<String, FocusRequester>,
    private val scope: CoroutineScope,
    private val focused: MutableState<String?>,
    private val place: MutableIntState,
) {

    /** The tile that had focus, remembered across a recreated activity. */
    var lastFocused: String?
        get() = focused.value
        set(value) { focused.value = value }

    /**
     * The requester for a tile, made once and kept for as long as the row is.
     *
     * Composable so the one it hands back is remembered against the id: the map outlives any
     * one composition, but a lint check reads a FocusRequester built during composition as a
     * thing that will be rebuilt on every frame, and it is clearer this way in any case.
     */
    @Composable
    fun requester(id: String): FocusRequester =
        remember(id) { requesters.getOrPut(id) { FocusRequester() } }

    /**
     * The tile that has just taken focus, and the place it sits in.
     *
     * The place is kept as well as the tile because a tile can leave the row while focus is
     * elsewhere, and then its place is all there is to come back to.
     */
    fun took(id: String, index: Int) {
        focused.value = id
        place.intValue = index
    }

    /** For the row's own focus modifier, which needs to look tiles up without creating them. */
    val known: Map<String, FocusRequester> get() = requesters

    /**
     * Where focus goes when it comes back: the tile that had it, or whatever has since slid
     * into its place. A tile can leave the row while focus is elsewhere.
     */
    val target: String
        get() = lastFocused?.takeIf { it in ids } ?: ids[place.intValue.coerceAtMost(ids.lastIndex)]

    /** Moves focus to a tile by place, scrolling to it first. Used by a wrap-around. */
    fun jumpTo(index: Int, id: String) {
        scope.launch {
            runCatching { listState.scrollToItem(index) }
            requesters[id].focusWhenReady()
        }
    }

    /**
     * Brings [target] back, scrolling to it first.
     *
     * The row only composes what is on screen, so the tile has to be scrolled into view before
     * its requester exists at all. Not during a move, which reorders the row on every press and
     * does its own scrolling. Items are placed during layout, so the requester is not attached
     * on the frame the list arrives; keep asking until it takes.
     */
    suspend fun restore(editing: String?) {
        if (editing == null) runCatching { listState.reveal(ids.indexOf(target)) }
        focusWhenReady { requesters[target] }
    }

    /** Back to the first tile, as Home does. */
    suspend fun toStart() {
        lastFocused = ids.first()
        place.intValue = 0
        runCatching { listState.scrollToItem(0) }
        requesters[ids.first()].focusWhenReady()
    }

    /** Only as far as it takes to bring the tile being moved back inside the safe area. */
    suspend fun reveal(index: Int) {
        runCatching { listState.reveal(index) }
    }
}

/**
 * Remembers the bookkeeping for a row of [ids].
 *
 * The place of the focused tile is kept current on every composition, so that when that tile
 * leaves the row the focus goes to whatever slid into its place rather than to the start.
 */
@Composable
internal fun rememberRowFocus(ids: List<String>): RowFocus {
    val listState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    // Saved with the activity, so a recreated one comes back to the same tile.
    val focused = rememberSaveable { mutableStateOf<String?>(null) }
    val place = rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    SideEffect {
        val at = focused.value?.let { ids.indexOf(it) } ?: -1
        if (at >= 0) place.intValue = at
    }
    return remember(ids, listState, scope) { RowFocus(ids, listState, requesters, scope, focused, place) }
}

/**
 * Requests focus once the target exists.
 *
 * A row composes only what is on screen, so after scrolling to the far end the requester there
 * is not attached for another frame or two.
 */
internal suspend fun FocusRequester?.focusWhenReady(): Boolean = focusWhenReady { this }

/**
 * The same, for a requester that may not exist yet either: [requester] is asked again on every
 * try, because a row only creates a tile's requester when it composes the tile.
 */
internal suspend fun focusWhenReady(requester: () -> FocusRequester?): Boolean {
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
internal suspend fun LazyListState.reveal(index: Int) {
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
internal fun LazyListState.holdStill() {
    requestScrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset)
}

/**
 * Makes a row one group for focus, and brings focus coming in from above or below back to the
 * tile it left, rather than to whichever tile happens to sit nearest. Only while that tile is on
 * screen: asking for one that is not would cancel the move altogether.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.rowFocus(
    row: RowFocus,
    onFocused: () -> Unit,
    // Where up goes when leaving the row. Compose would pick whatever overlaps the tile's own
    // column, which is the hero, right past the notice sitting between them.
    exitUp: () -> FocusRequester? = { null },
): Modifier = onFocusChanged { if (it.hasFocus) onFocused() }
    .focusProperties {
        enter = { direction ->
            val back = row.lastFocused
                ?.takeIf { id -> row.listState.layoutInfo.visibleItemsInfo.any { it.key == id } }
            val across = direction == FocusDirection.Up || direction == FocusDirection.Down
            back?.takeIf { across }?.let { row.known[it] } ?: FocusRequester.Default
        }
        exit = { direction ->
            exitUp().takeIf { direction == FocusDirection.Up } ?: FocusRequester.Default
        }
    }
    .focusGroup()
