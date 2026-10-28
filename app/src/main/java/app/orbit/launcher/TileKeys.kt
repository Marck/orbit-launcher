package app.orbit.launcher

/** How long OK must be held for the menu rather than a launch, if no long-press flag arrives. */
const val LongPressMs = 500L

/** The keys a tile acts on. Every other key is [OTHER]. */
enum class TileKey { OK, LEFT, RIGHT, BACK, MENU, OTHER }

/** Which half of a press a key event is. Anything that is neither is [OTHER]. */
enum class KeyPhase { DOWN, UP, OTHER }

/** What a tile does with a key. Everything but [Pass] uses the key up. */
sealed interface TileAction {
    /** Not the tile's key: it goes on to the click and to focus as usual. */
    data object Pass : TileAction

    /** Used up, with nothing to do. */
    data object Swallow : TileAction

    data object Menu : TileAction

    data class Move(val delta: Int) : TileAction

    /** The move is over: kept, or put back when [revert]. */
    data class EndEdit(val revert: Boolean) : TileAction

    data object EdgeLeft : TileAction

    data object EdgeRight : TileAction
}

/**
 * The remote's rules for a tile that can be moved, kept apart from Compose so they can be checked
 * one key at a time.
 *
 * The hold is measured here rather than left to `combinedClickable`, whose long-press handling
 * for hardware keys is not something to depend on sight unseen. Holding OK opens the menu and
 * swallows the release so the tile does not also activate; a tap falls through untouched. Menu
 * does the same, for remotes that have the key.
 *
 * While moving, every key is swallowed: left and right reorder instead of changing focus, OK
 * keeps the new place, Back puts the order back.
 *
 * One instance belongs to one tile, for as long as the tile is on screen.
 */
class TileKeys {
    private var pressedAt = 0L

    // Identified by the press itself, because opening the menu moves focus away and the tile
    // never sees the release. A flag cleared on release would stay set and eat the next hold.
    private var handledPress = 0L

    /**
     * Decides what [key] does. [repeatCount], [longPress] (the system's long-press flag) and
     * [downTime] come from the event as Android sent it, [now] is the clock [downTime] is on, and
     * [edgeLeft] and [edgeRight] say whether the tile is at that end of its row.
     */
    fun onKey(
        key: TileKey,
        phase: KeyPhase,
        repeatCount: Int,
        longPress: Boolean,
        downTime: Long,
        now: Long,
        editing: Boolean,
        edgeLeft: Boolean = false,
        edgeRight: Boolean = false,
    ): TileAction {
        val down = phase == KeyPhase.DOWN
        val up = phase == KeyPhase.UP
        val ok = key == TileKey.OK
        // Android marks a held key with a flag and a repeat, which is the signal to trust.
        val held = repeatCount > 0 || longPress
        return when {
            editing && key == TileKey.LEFT -> if (down) TileAction.Move(-1) else TileAction.Swallow
            editing && key == TileKey.RIGHT -> if (down) TileAction.Move(1) else TileAction.Swallow
            editing && ok -> if (up) TileAction.EndEdit(false) else TileAction.Swallow
            editing && key == TileKey.BACK -> if (up) TileAction.EndEdit(true) else TileAction.Swallow
            editing -> TileAction.Swallow
            // At either end, left and right jump to the other end rather than stopping dead,
            // but only on a press of their own. Held down to run along the row, the key used
            // to carry straight past the end and start again, so aiming at the last tile by
            // holding the key overshot it and there was no way to stop short. Held, the row
            // now stops at the end; let go and press again to come round.
            down && key == TileKey.LEFT && edgeLeft && !held -> TileAction.EdgeLeft
            down && key == TileKey.RIGHT && edgeRight && !held -> TileAction.EdgeRight
            key == TileKey.LEFT && edgeLeft -> TileAction.Swallow
            key == TileKey.RIGHT && edgeRight -> TileAction.Swallow
            up && key == TileKey.MENU -> TileAction.Menu
            ok && down && held -> {
                // The system has decided this is a hold. Auto-repeat keeps sending downs, so
                // act once per press.
                if (handledPress != downTime) {
                    handledPress = downTime
                    // The menu takes the release, so the tile never clears this itself. Left
                    // set, it would make the next quick tap read as a hold.
                    pressedAt = 0L
                    TileAction.Menu
                } else {
                    TileAction.Swallow
                }
            }
            ok && down -> {
                if (pressedAt == 0L) pressedAt = now
                TileAction.Pass
            }
            ok && up -> {
                val longByTime = pressedAt != 0L && now - pressedAt >= LongPressMs
                pressedAt = 0L
                when {
                    // The menu is already open for this press; swallow the release so the tile
                    // does not activate as well.
                    handledPress == downTime -> TileAction.Swallow
                    // A hold that never produced a flag still counts, so a remote that does not
                    // send one is not left without the menu.
                    longByTime -> TileAction.Menu
                    else -> TileAction.Pass
                }
            }
            else -> TileAction.Pass
        }
    }
}
