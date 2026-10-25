package app.orbit.launcher

/**
 * Records that Orbit reached the foreground, so a takeover that worked can be told from one
 * that went nowhere. Written from the main thread and read from the accessibility service's,
 * hence the volatile.
 */
internal object Foreground {
    @Volatile
    private var at = 0L

    fun note(now: Long) {
        at = now
    }

    /** Whether Orbit has been in front since the takeover performed at [since]. */
    fun reachedSince(since: Long): Boolean = at > since
}

/**
 * Decides whether a stock launcher window should pull Orbit back in front.
 *
 * Answering every launcher window would loop forever if Orbit could not come up: the launcher
 * would return, and the answer would bring it back again. Guarding against that means telling
 * apart two situations that both look like a burst of launcher windows:
 *
 *  - Orbit is broken. Every takeover is followed by the launcher again, never by Orbit.
 *  - The home key is being pressed repeatedly. Every takeover works, and Orbit does appear.
 *
 * Only the first is a loop, so a takeover that landed clears the count however fast the presses
 * come, and standing down is reached only by takeovers that did not land. Counting presses
 * instead, as this first did, makes three home presses in ten seconds look like a failure and
 * hands the TV back to the stock launcher for a minute.
 */
internal class Takeover(
    private val landed: (Long) -> Boolean = Foreground::reachedSince,
) {
    private val failures = ArrayDeque<Long>()
    private var lastAt = 0L
    private var standDownUntil = 0L

    /** True when Orbit should be started for a launcher window seen at [now]. */
    fun claim(now: Long): Boolean {
        if (now < standDownUntil) return false
        // One press can raise several windows; they are the same press, not a second chance.
        if (lastAt != 0L && now - lastAt < DEBOUNCE_MS) return false

        // Whether the last takeover worked is only knowable now, on seeing what followed it.
        if (lastAt != 0L) {
            if (landed(lastAt)) failures.clear() else failures.addLast(lastAt)
        }
        while (failures.isNotEmpty() && now - failures.first() >= BURST_WINDOW_MS) {
            failures.removeFirst()
        }
        if (failures.size >= MAX_FAILURES) {
            failures.clear()
            lastAt = 0L
            standDownUntil = now + STAND_DOWN_MS
            return false
        }

        lastAt = now
        return true
    }

    internal companion object {
        const val DEBOUNCE_MS = 700L
        const val MAX_FAILURES = 3
        const val BURST_WINDOW_MS = 10_000L
        const val STAND_DOWN_MS = 60_000L
    }
}
