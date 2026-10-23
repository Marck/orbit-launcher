package app.orbit.launcher

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** The two rows of the home screen, and the focus bookkeeping they share. */

/** Where a tile and its label sit on screen. Read only when its menu opens. */
internal class Spot {
    var tile = Rect.Zero
    var whole = Rect.Zero

    /** The tile, stretched down over the label beneath it when there is one. */
    fun rect(): Rect = tile.copy(bottom = maxOf(tile.bottom, whole.bottom))
}

@Composable
internal fun AppsRow(
    noticeFocus: FocusRequester?,
    apps: List<LaunchableApp>,
    ready: Boolean,
    held: String?,
    refocus: Int,
    toFirst: Int,
    /** Focus belongs in this row: it was last here, and no menu has it. */
    active: Boolean,
    onFocused: () -> Unit,
    /** The app under the cursor, or null on the add tile. The hero follows it. */
    onTile: (String?) -> Unit,
    editing: String?,
    onOpen: (LaunchableApp) -> Unit,
    onMenu: (LaunchableApp, Rect) -> Unit,
    onMove: (Int) -> Unit,
    onEndEdit: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val ids = remember(apps) { apps.map { it.packageName } + ADD_TILE }
    // Focus is only ever requested from the effects below, never from a tile's own composition,
    // which is what used to yank focus back while arrowing left.
    val row = rememberRowFocus(ids)
    val firstPackage = apps.firstOrNull()?.packageName

    // Only when the tile being moved has left the safe area, and only as far as it takes to bring
    // it back. Scrolling on every swap drags it to the left edge, which reads as the row jumping
    // rather than the tile moving.
    LaunchedEffect(editing, apps) {
        val moving = editing ?: return@LaunchedEffect
        val index = apps.indexOfFirst { it.packageName == moving }
        if (index >= 0) row.reveal(index)
    }

    // Keyed on the row's contents, not just its first entry: when the tile that had focus leaves
    // the favorites or the TV, nothing else would re-run and the focus would be left on a tile
    // that no longer exists. The one that slid into its place takes the focus instead. Only while
    // focus belongs here, so a list read on a return cannot pull focus out of the inputs row or
    // the top bar, or out from under an open menu.
    LaunchedEffect(ids, ready, refocus) {
        if (!ready || !active) return@LaunchedEffect
        row.restore(editing)
    }

    LaunchedEffect(toFirst) {
        if (toFirst == 0 || !ready) return@LaunchedEffect
        row.toStart()
    }

    LazyRow(
        modifier = Modifier
            .height(RowHeight)
            .rowFocus(row, onFocused) { noticeFocus },
        state = row.listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = SafeH, top = RowTop, end = SafeH),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppTile(
                app = app,
                editing = editing == app.packageName,
                held = held == app.packageName,
                requester = row.requester(app.packageName),
                onFocused = {
                    row.took(app.packageName, index)
                    onTile(app.packageName)
                },
                onClick = { onOpen(app) },
                onMenu = { anchor -> onMenu(app, anchor) },
                onMove = { delta -> row.listState.holdStill(); onMove(delta) },
                onEndEdit = onEndEdit,
                onEdgeLeft = if (index == 0) {
                    { row.jumpTo(apps.size, ADD_TILE) }
                } else {
                    null
                },
            )
        }
        if (ready) item(key = ADD_TILE) {
            PlusTile(
                requester = row.requester(ADD_TILE),
                onFocused = {
                    row.took(ADD_TILE, apps.size)
                    onTile(null)
                },
                onEdgeRight = { firstPackage?.let { row.jumpTo(0, it) } },
            ) { context.openScreen(Intent(context, AppPickerActivity::class.java)) }
        }
    }
}

@Composable
internal fun InputsRow(
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
    val ids = remember(inputs) { inputs.map { it.id } + ADD_INPUT_TILE }
    val row = rememberRowFocus(ids)

    LaunchedEffect(editing, inputs) {
        val moving = editing ?: return@LaunchedEffect
        val index = inputs.indexOfFirst { it.id == moving }
        if (index >= 0) row.reveal(index)
    }

    // Only ever on an explicit request, never on composition, so the row cannot steal focus
    // from the apps above it. Also once the inputs have been read, for a request made before.
    LaunchedEffect(refocus, ready) {
        if (refocus == 0 || !ready || !active || row.lastFocused == null) return@LaunchedEffect
        row.restore(editing)
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .rowFocus(row, onFocused),
        state = row.listState,
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
                requester = row.requester(input.id),
                onFocused = {
                    row.took(input.id, index)
                },
                onMenu = { anchor -> onMenu(input, anchor) },
                onMove = { delta -> row.listState.holdStill(); onMove(delta) },
                onEndEdit = onEndEdit,
                onEdgeLeft = if (index == 0) {
                    { row.jumpTo(inputs.size, ADD_INPUT_TILE) }
                } else {
                    null
                },
            ) { onOpen(input) }
        }
        if (ready) item(key = ADD_INPUT_TILE) {
            PlusTile(
                requester = row.requester(ADD_INPUT_TILE),
                onFocused = {
                    row.took(ADD_INPUT_TILE, inputs.size)
                },
                onEdgeRight = { inputs.firstOrNull()?.let { row.jumpTo(0, it.id) } },
                width = InputW,
                label = "Add input to favorites",
            ) { context.openScreen(Intent(context, InputPickerActivity::class.java)) }
        }
    }
}
