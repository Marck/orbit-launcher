package app.orbit.launcher

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.robolectric.Shadows.shadowOf
import java.util.TimeZone

/**
 * Base for tests that drive the real Orbit screens like a TV remote. See HARNESS.md.
 *
 * Subclasses need, on the class:
 *   @RunWith(AndroidJUnit4::class)
 *   @GraphicsMode(GraphicsMode.Mode.NATIVE)
 *   @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
 */
abstract class TvTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Where screenshots go. Override per test class to keep them apart. */
    open val outDir = "build/tv-shots/shots"

    val myApps = listOf(
        "ar.tvplayer.tv", "nl.uitzendinggemist", "com.google.android.youtube.tv",
        "org.jellyfin.androidtv", "org.xbmc.kodi", "com.tv.help",
    )
    val myInputs = listOf("hdmi1", "ps5", "switch")

    fun banner(label: String, bg: Int, fg: Int, size: Float = 64f): Drawable {
        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText(label, 160f, 90f - (p.descent() + p.ascent()) / 2, p)
        return BitmapDrawable(context.resources, bmp)
    }

    fun app(pkg: String, label: String, art: Drawable?, removable: Boolean = true, isBanner: Boolean = true) =
        LaunchableApp(pkg, label, art, isBanner = isBanner, removable = removable)

    companion object {
        /**
         * What every screenshot's clock reads: 2024-01-02 03:04:00 UTC.
         *
         * Any fixed instant would do. The zone is pinned with it, because the clock is
         * formatted in the default one and a shot taken here would otherwise show a different
         * hour from the same shot taken on a build machine.
         */
        const val PINNED_CLOCK = 1_704_164_640_000L
    }

    /** The default fixture: the owner's apps plus two extra, and four inputs. Override freely. */
    @Before
    fun fixture() {
        ShotHooks.apps = listOf(
            app("ar.tvplayer.tv", "TiviMate", banner("tivimate", 0xFFFFFFFF.toInt(), 0xFF1E88E5.toInt())),
            app("nl.uitzendinggemist", "NPO Start", banner("NPO start", 0xFF1A3DFF.toInt(), 0xFFFF7A00.toInt(), 52f)),
            app("com.google.android.youtube.tv", "YouTube", banner("YouTube", 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 50f), removable = false),
            app("org.jellyfin.androidtv", "Jellyfin", banner("Jellyfin", 0xFF101828.toInt(), 0xFFAA5CC3.toInt())),
            app("org.xbmc.kodi", "Kodi", banner("KODI", 0xFFFFFFFF.toInt(), 0xFF17B2E7.toInt())),
            app("com.tv.help", "Help", banner("? Help", 0xFFB9A6D9.toInt(), 0xFFFFFFFF.toInt())),
            app("com.plexapp.android", "Plex: Stream Movies & TV", banner("plex", 0xFF1F1F1F.toInt(), 0xFFE5A00D.toInt())),
            app("com.netflix.ninja", "Netflix", banner("NETFLIX", 0xFF000000.toInt(), 0xFFE50914.toInt())),
        )
        // Invented, like the apps above. Nothing here says what is actually attached to any
        // set: read the device with `adb shell dumpsys tv_input` for that, never this list.
        ShotHooks.inputs = listOf(
            TvInput("hdmi1", "HDMI 1", null, connected = true),
            TvInput("ps5", "PlayStation 5", "HDMI 2", connected = true),
            TvInput("switch", "Nintendo Switch OLED", "HDMI 3", connected = true),
            TvInput("hdmi4", "HDMI 4", "eARC/ARC", connected = false),
        )
        ShotHooks.store = Intent("test.store")
        ShotHooks.storeIcon = banner(">", 0xFFFFFFFF.toInt(), 0xFF01875F.toInt(), 120f)
        ShotHooks.allInputs = Intent("test.allinputs")
        ShotHooks.search = Intent("test.search")
        ShotHooks.defaultHome = true
        // Being the home app and the home button working are two things. A fixture that only
        // said the first now gets the notice about the second, which is a real state on a real
        // TV but not the one these tests are about. Saying a home press has arrived is what
        // "home is sorted" was always meant to mean here.
        HomePrefs.rememberHomePress(context)
        // The hooks are one global object and the test JVM is shared, so a class that sets
        // these would otherwise hand them to every class that runs after it.
        ShotHooks.watchNext = null
        ShotHooks.pictures = null
        ShotHooks.artwork = null
        // On unless a test driving the hero from the remote turns it off.
        ShotHooks.cycling = true
        // Decoded pictures outlive a test, since the cache is one object in a shared process,
        // and the written tiles outlive it on disk, where the process is shared too.
        ArtworkCache.clear()
        ArtworkDisk.reset(context)
        // Every screen here has a clock on it, so without this each shot differs from the last
        // recording by the minute it happened to be taken in, and the date rolls over nightly
        // on top of that. Pinned to a fixed instant so a difference means the layout moved.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        ShotHooks.now = PINNED_CLOCK
        // Infinite marquee animations would make an auto-advancing clock never settle.
        rule.mainClock.autoAdvance = false
    }

    /**
     * Takes an app off the TV, the way the TV does it: the entry goes, and the system says so.
     *
     * The home screen listens for that broadcast rather than reading every launcher activity
     * again on every return, so a test that only swaps the fixture is not an uninstall.
     */
    fun uninstall(packageName: String) {
        ShotHooks.apps = ShotHooks.apps?.filter { it.packageName != packageName }
        context.sendBroadcast(
            Intent(Intent.ACTION_PACKAGE_REMOVED, android.net.Uri.parse("package:$packageName"))
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    fun favorites(apps: List<String>, inputs: List<String>) {
        context.getSharedPreferences("orbit.favorites", Context.MODE_PRIVATE).edit()
            .putString(FavoriteStore.APPS, apps.joinToString("\n"))
            .putString(FavoriteStore.INPUTS, inputs.joinToString("\n"))
            .commit()
    }

    fun stored(key: String): List<String> =
        context.getSharedPreferences("orbit.favorites", Context.MODE_PRIVATE)
            .getString(key, null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

    /** Lets time pass the way a device does: state flushed, frame clock and looper advanced. */
    fun pump(ms: Long) {
        var left = ms
        while (left > 0) {
            Snapshot.sendApplyNotifications()
            rule.mainClock.advanceTimeBy(16)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
            left -= 16
        }
    }

    /**
     * Pumps until [condition] holds, and fails saying what it was waiting for if it never does.
     *
     * Anything read on Dispatchers.IO lands on a real thread while the clock here is a fake
     * one, so how many frames it takes is a property of the machine, not of the code. A fixed
     * settle that is generous on a laptop is not generous on a build runner. Wait for the thing
     * itself instead of guessing how long it takes.
     *
     * The budget is real time, and has to be. It used to count the milliseconds this pumped
     * into the fake clock, which run about four times faster than the wall: a measured 2000ms
     * budget handed the background work 562ms to finish in. That is what kept N7 coming back,
     * a test that passes on a laptop and times out on a loaded runner while the read it is
     * waiting for is still perfectly healthy. [WaitBudgetTest] holds this to it.
     */
    fun waitFor(
        what: String,
        budgetMs: Long = 12_000,
        /** What the screen looked like when the wait ran out. Only called on the way to failing. */
        diagnose: () -> String = { "" },
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + budgetMs * 1_000_000L
        while (true) {
            if (condition()) return
            if (System.nanoTime() >= deadline) break
            Thread.sleep(20)
            pump(100)
        }
        check(condition()) {
            val state = runCatching { diagnose() }.getOrElse { "diagnose threw: $it" }
            "waited ${budgetMs}ms for $what" + if (state.isBlank()) "" else ". $state"
        }
    }

    /** Launches an activity the way a TV would show it: not in touch mode. */
    fun <A : Activity> launch(cls: Class<A>, intent: Intent? = null, settle: Long = 1500): ActivityScenario<A> {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val scenario = if (intent != null) ActivityScenario.launch<A>(intent) else ActivityScenario.launch(cls)
        pump(settle)
        return scenario
    }

    /** One tap of a remote key, with real timestamps. */
    fun <A : Activity> press(s: ActivityScenario<A>, code: Int, settle: Long = 400) {
        println("TV press ${KeyEvent.keyCodeToString(code)}")
        s.onActivity {
            val now = SystemClock.uptimeMillis()
            it.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
            it.dispatchKeyEvent(KeyEvent(now, now + 50, KeyEvent.ACTION_UP, code, 0))
        }
        pump(settle)
    }

    fun <A : Activity> key(s: ActivityScenario<A>, action: Int, code: Int, downTime: Long, repeat: Int = 0, flags: Int = 0) {
        s.onActivity {
            val now = SystemClock.uptimeMillis()
            it.dispatchKeyEvent(KeyEvent(downTime, now, action, code, repeat, 0, -1, 0, flags))
        }
    }

    /** What a remote sends while OK is held: a press, then auto-repeats flagged as a long press, then the release. */
    fun <A : Activity> holdOk(s: ActivityScenario<A>) {
        val down = SystemClock.uptimeMillis()
        key(s, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, down)
        pump(500)
        key(s, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, down, repeat = 1, flags = KeyEvent.FLAG_LONG_PRESS)
        pump(200)
        key(s, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, down, repeat = 2)
        pump(200)
        key(s, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, down)
        pump(400)
    }

    /** Screenshot of the current compose root. */
    fun shot(name: String) {
        // A shot taken with the clock unpinned carries the minute it was recorded in, so it
        // differs from the last recording for a reason nobody changed. This is the fault that
        // made two recordings of unchanged code disagree on 47 of 79 shots, and the one that
        // survived in this class because it does not take its setup from TvTest.
        assertNotNull(
            "the clock was not pinned, so this shot would differ on every run",
            ShotHooks.now,
        )
        rule.onRoot().captureRoboImage("$outDir/$name.png")
    }

    /**
     * What the focused node says about itself (content description or text), or null when
     * nothing in Compose has focus. Focus may sit on an inner clickable; both are tried.
     */
    /** Every button the top bar can show, left to right. */
    val topBarButtons = listOf("Search", "All apps", "All inputs", "Play Store", "Settings", "Profile")

    /**
     * Walks up to the top bar and right to [name].
     *
     * By name rather than by a count of presses, so adding a button, or a stop on the way up
     * such as the hero, does not break every test that only wanted to reach Settings.
     */
    fun <A : Activity> toTopBar(s: ActivityScenario<A>, name: String) {
        var guard = 0
        while (focused() !in topBarButtons && guard++ < 6) press(s, KeyEvent.KEYCODE_DPAD_UP)
        guard = 0
        while (focused() != name && guard++ < 8) press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    fun focused(): String? {
        val nodes = rule.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
        if (nodes.isEmpty()) return null
        return nodes.joinToString(" | ") { n ->
            val cd = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            val tx = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            val merged = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()
            val mcd = merged?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            val mtx = merged?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            listOfNotNull(cd, tx, mcd, mtx).filter { it.isNotBlank() }.distinct().joinToString(" / ").ifBlank { "(focused, no label)" }
        }
    }

    /** The next activity the app asked the system to start, or null. */
    fun nextStarted(): Intent? =
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
}
