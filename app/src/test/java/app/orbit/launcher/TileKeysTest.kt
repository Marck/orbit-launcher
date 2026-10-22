package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class TileKeysTest {

    private val keys = TileKeys()

    private fun send(
        key: TileKey,
        phase: KeyPhase,
        downTime: Long = 1_000L,
        now: Long = downTime,
        repeatCount: Int = 0,
        longPress: Boolean = false,
        editing: Boolean = false,
        edgeLeft: Boolean = false,
        edgeRight: Boolean = false,
    ): TileAction = keys.onKey(key, phase, repeatCount, longPress, downTime, now, editing, edgeLeft, edgeRight)

    /** Both halves of a press of [key], as quick as a tap. */
    private fun tap(
        key: TileKey,
        downTime: Long = 1_000L,
        editing: Boolean = false,
        edgeLeft: Boolean = false,
        edgeRight: Boolean = false,
    ): Pair<TileAction, TileAction> = Pair(
        send(key, KeyPhase.DOWN, downTime, editing = editing, edgeLeft = edgeLeft, edgeRight = edgeRight),
        send(key, KeyPhase.UP, downTime, downTime + 80, editing = editing, edgeLeft = edgeLeft, edgeRight = edgeRight),
    )

    @Test
    fun tapOnOkPassesThrough() {
        assertEquals(Pair(TileAction.Pass, TileAction.Pass), tap(TileKey.OK))
    }

    @Test
    fun holdByFlagOpensTheMenuOncePerPress() {
        assertEquals(TileAction.Pass, send(TileKey.OK, KeyPhase.DOWN))
        assertEquals(TileAction.Menu, send(TileKey.OK, KeyPhase.DOWN, now = 1_500, repeatCount = 1, longPress = true))
        assertEquals(TileAction.Swallow, send(TileKey.OK, KeyPhase.DOWN, now = 1_550, repeatCount = 2))
        assertEquals(TileAction.Swallow, send(TileKey.OK, KeyPhase.DOWN, now = 1_600, repeatCount = 3))
    }

    @Test
    fun holdByRepeatAloneOpensTheMenu() {
        assertEquals(TileAction.Pass, send(TileKey.OK, KeyPhase.DOWN))
        assertEquals(TileAction.Menu, send(TileKey.OK, KeyPhase.DOWN, now = 1_050, repeatCount = 1))
    }

    @Test
    fun holdByFlagAloneOpensTheMenu() {
        assertEquals(TileAction.Menu, send(TileKey.OK, KeyPhase.DOWN, longPress = true))
    }

    @Test
    fun anotherHoldOpensTheMenuAgain() {
        assertEquals(TileAction.Menu, send(TileKey.OK, KeyPhase.DOWN, downTime = 1_000, repeatCount = 1))
        assertEquals(TileAction.Menu, send(TileKey.OK, KeyPhase.DOWN, downTime = 5_000, repeatCount = 1))
    }

    @Test
    fun holdByTimeWithoutAFlagOpensTheMenuOnRelease() {
        assertEquals(TileAction.Pass, send(TileKey.OK, KeyPhase.DOWN, now = 1_000))
        assertEquals(TileAction.Menu, send(TileKey.OK, KeyPhase.UP, now = 1_000 + LongPressMs))
    }

    @Test
    fun releaseJustShortOfTheHoldTimeIsATap() {
        assertEquals(TileAction.Pass, send(TileKey.OK, KeyPhase.DOWN, now = 1_000))
        assertEquals(TileAction.Pass, send(TileKey.OK, KeyPhase.UP, now = 1_000 + LongPressMs - 1))
    }

    @Test
    fun holdTimeCountsFromTheFirstDown() {
        assertEquals(TileAction.Pass, send(TileKey.OK, KeyPhase.DOWN, now = 1_000))
        assertEquals(TileAction.Pass, send(TileKey.OK, KeyPhase.DOWN, now = 1_400))
        assertEquals(TileAction.Menu, send(TileKey.OK, KeyPhase.UP, now = 1_000 + LongPressMs))
    }

    @Test
    fun releaseAfterAHoldIsSwallowed() {
        send(TileKey.OK, KeyPhase.DOWN)
        assertEquals(TileAction.Menu, send(TileKey.OK, KeyPhase.DOWN, now = 1_500, repeatCount = 1, longPress = true))
        assertEquals(TileAction.Swallow, send(TileKey.OK, KeyPhase.UP, now = 2_000))
    }

    @Test
    fun quickTapAfterAHoldIsATap() {
        send(TileKey.OK, KeyPhase.DOWN, downTime = 1_000)
        assertEquals(TileAction.Menu, send(TileKey.OK, KeyPhase.DOWN, downTime = 1_000, now = 1_500, repeatCount = 1))
        assertEquals(TileAction.Swallow, send(TileKey.OK, KeyPhase.UP, downTime = 1_000, now = 1_700))
        assertEquals(Pair(TileAction.Pass, TileAction.Pass), tap(TileKey.OK, downTime = 3_000))
    }

    @Test
    fun quickTapAfterAHoldWhoseReleaseTheTileNeverSawIsATap() {
        // The menu took focus, so the release went to the menu and not to the tile.
        send(TileKey.OK, KeyPhase.DOWN, downTime = 1_000)
        assertEquals(TileAction.Menu, send(TileKey.OK, KeyPhase.DOWN, downTime = 1_000, now = 1_500, longPress = true))
        assertEquals(Pair(TileAction.Pass, TileAction.Pass), tap(TileKey.OK, downTime = 9_000))
    }

    @Test
    fun menuKeyOpensTheMenuOnRelease() {
        assertEquals(Pair(TileAction.Pass, TileAction.Menu), tap(TileKey.MENU))
    }

    @Test
    fun otherKeysPassThrough() {
        assertEquals(Pair(TileAction.Pass, TileAction.Pass), tap(TileKey.BACK))
        assertEquals(Pair(TileAction.Pass, TileAction.Pass), tap(TileKey.OTHER))
        assertEquals(Pair(TileAction.Pass, TileAction.Pass), tap(TileKey.LEFT))
        assertEquals(Pair(TileAction.Pass, TileAction.Pass), tap(TileKey.RIGHT))
        assertEquals(TileAction.Pass, send(TileKey.OK, KeyPhase.OTHER))
    }

    @Test
    fun leftAtTheStartWrapsToTheEnd() {
        assertEquals(Pair(TileAction.EdgeLeft, TileAction.Swallow), tap(TileKey.LEFT, edgeLeft = true))
        assertEquals(Pair(TileAction.Pass, TileAction.Pass), tap(TileKey.RIGHT, edgeLeft = true))
    }

    @Test
    fun rightAtTheEndWrapsToTheStart() {
        assertEquals(Pair(TileAction.EdgeRight, TileAction.Swallow), tap(TileKey.RIGHT, edgeRight = true))
        assertEquals(Pair(TileAction.Pass, TileAction.Pass), tap(TileKey.LEFT, edgeRight = true))
    }

    @Test
    fun whileMovingLeftAndRightMoveTheTile() {
        assertEquals(Pair(TileAction.Move(-1), TileAction.Swallow), tap(TileKey.LEFT, editing = true))
        assertEquals(Pair(TileAction.Move(1), TileAction.Swallow), tap(TileKey.RIGHT, editing = true))
    }

    @Test
    fun whileMovingTheEdgesDoNotWrap() {
        assertEquals(
            Pair(TileAction.Move(-1), TileAction.Swallow),
            tap(TileKey.LEFT, editing = true, edgeLeft = true),
        )
        assertEquals(
            Pair(TileAction.Move(1), TileAction.Swallow),
            tap(TileKey.RIGHT, editing = true, edgeRight = true),
        )
    }

    @Test
    fun whileMovingOkKeepsTheNewPlace() {
        assertEquals(Pair(TileAction.Swallow, TileAction.EndEdit(false)), tap(TileKey.OK, editing = true))
    }

    @Test
    fun whileMovingAHeldOkDoesNotOpenTheMenu() {
        assertEquals(TileAction.Swallow, send(TileKey.OK, KeyPhase.DOWN, editing = true, repeatCount = 1, longPress = true))
        assertEquals(TileAction.EndEdit(false), send(TileKey.OK, KeyPhase.UP, now = 2_000, editing = true))
    }

    @Test
    fun whileMovingBackPutsTheOrderBack() {
        assertEquals(Pair(TileAction.Swallow, TileAction.EndEdit(true)), tap(TileKey.BACK, editing = true))
    }

    @Test
    fun whileMovingEveryOtherKeyIsSwallowed() {
        assertEquals(Pair(TileAction.Swallow, TileAction.Swallow), tap(TileKey.MENU, editing = true))
        assertEquals(Pair(TileAction.Swallow, TileAction.Swallow), tap(TileKey.OTHER, editing = true))
        assertEquals(TileAction.Swallow, send(TileKey.LEFT, KeyPhase.OTHER, editing = true))
    }
}
