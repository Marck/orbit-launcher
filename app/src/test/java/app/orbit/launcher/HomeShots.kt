package app.orbit.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.TimeZone
import org.robolectric.annotation.GraphicsMode

private const val OUT = "build/tv-shots/shots"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HomeShots {

    /**
     * The pinned clock, which this class missed.
     *
     * Every other screenshot class takes it from TvTest, which this one does not extend, so
     * its shots still carried the minute they were recorded in and the date they were recorded
     * on. Two recordings happening to land in the same minute is what made the suite look
     * fully deterministic when it was not.
     */
    @Before
    fun pinTheClock() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        ShotHooks.now = TvTest.PINNED_CLOCK
    }

    @After
    fun unpinTheClock() {
        ShotHooks.now = null
    }


    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun banner(label: String, bg: Int, fg: Int, size: Float = 64f): Drawable {
        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        c.drawRoundRect(RectF(0f, 0f, 320f, 180f), 0f, 0f, back)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText(label, 160f, 90f - (p.descent() + p.ascent()) / 2, p)
        return BitmapDrawable(context.resources, bmp)
    }

    private fun app(pkg: String, label: String, art: Drawable, removable: Boolean = true) =
        LaunchableApp(pkg, label, art, isBanner = true, removable = removable)

    @Before
    fun hooks() {
        ShotHooks.apps = listOf(
            app("ar.tvplayer.tv", "TiviMate", banner("tivimate", 0xFFFFFFFF.toInt(), 0xFF1E88E5.toInt())),
            app("nl.uitzendinggemist", "NPO Start", banner("NPO start", 0xFF1A3DFF.toInt(), 0xFFFF7A00.toInt(), 52f)),
            app("com.google.android.youtube.tv", "YouTube", banner("▶ YouTube", 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 50f), removable = false),
            app("org.jellyfin.androidtv", "Jellyfin", banner("Jellyfin", 0xFF101828.toInt(), 0xFFAA5CC3.toInt())),
            app("org.xbmc.kodi", "Kodi", banner("KODI", 0xFFFFFFFF.toInt(), 0xFF17B2E7.toInt())),
            app("com.tv.help", "Help", banner("? Help", 0xFFB9A6D9.toInt(), 0xFFFFFFFF.toInt())),
            app("com.plexapp.android", "Plex: Stream Movies & TV", banner("plex", 0xFF1F1F1F.toInt(), 0xFFE5A00D.toInt())),
            app("com.netflix.ninja", "Netflix", banner("NETFLIX", 0xFF000000.toInt(), 0xFFE50914.toInt())),
        )
        ShotHooks.inputs = listOf(
            TvInput("hdmi1", "HDMI 1", null, connected = true),
            TvInput("ps5", "PlayStation 5", "HDMI 2", connected = true),
            TvInput("switch", "Nintendo Switch OLED", "HDMI 3", connected = true),
            TvInput("hdmi4", "HDMI 4", "eARC/ARC", connected = false),
        )
        ShotHooks.store = Intent("store")
        ShotHooks.storeIcon = banner("▶", 0xFFFFFFFF.toInt(), 0xFF01875F.toInt(), 120f)
        ShotHooks.allInputs = Intent("inputs")
        ShotHooks.defaultHome = true
        rule.mainClock.autoAdvance = false
    }

    private fun favorites(apps: List<String>, inputs: List<String>) {
        context.getSharedPreferences("orbit.favorites", Context.MODE_PRIVATE).edit()
            .putString(FavoriteStore.APPS, apps.joinToString("\n"))
            .putString(FavoriteStore.INPUTS, inputs.joinToString("\n"))
            .commit()
    }

    private fun pump(ms: Long) {
        var left = ms
        while (left > 0) {
            // A device flushes state writes every frame; here a key handler's writes would
            // otherwise wait for the next key.
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            rule.mainClock.advanceTimeBy(16)
            // The activity's frames come from the real Choreographer, which only runs as
            // Robolectric's own clock moves.
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
            left -= 16
        }
    }

    private fun press(scenario: ActivityScenario<HomeActivity>, code: Int, settle: Long = 400) {
        println("SHOT press ${KeyEvent.keyCodeToString(code)}")
        scenario.onActivity {
            // Real events carry their press time; the long-press guard keys on it.
            val now = android.os.SystemClock.uptimeMillis()
            it.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
            it.dispatchKeyEvent(KeyEvent(now, now + 50, KeyEvent.ACTION_UP, code, 0))
        }
        pump(settle)
    }

    private fun shot(name: String) {
        rule.onRoot().captureRoboImage("$OUT/$name.png")
    }

    private fun launch(): ActivityScenario<HomeActivity> {
        // A TV has no touchscreen, so it is never in touch mode. Robolectric starts in it.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val scenario = ActivityScenario.launch(HomeActivity::class.java)
        pump(1500)
        return scenario
    }

    private val myApps = listOf("ar.tvplayer.tv", "nl.uitzendinggemist", "com.google.android.youtube.tv", "org.jellyfin.androidtv", "org.xbmc.kodi", "com.tv.help")
    private val myInputs = listOf("hdmi1", "ps5", "switch")

    private fun key(s: ActivityScenario<HomeActivity>, action: Int, code: Int, downTime: Long, repeat: Int = 0, flags: Int = 0) {
        s.onActivity {
            val now = android.os.SystemClock.uptimeMillis()
            it.dispatchKeyEvent(KeyEvent(downTime, now, action, code, repeat, 0, -1, 0, flags))
        }
    }

    /** What a remote sends while OK is held: a press, then auto-repeats flagged as a long press. */
    private fun holdOk(s: ActivityScenario<HomeActivity>) {
        val down = android.os.SystemClock.uptimeMillis()
        key(s, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, down)
        pump(500)
        key(s, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, down, repeat = 1, flags = KeyEvent.FLAG_LONG_PRESS)
        pump(200)
        key(s, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, down, repeat = 2)
        pump(200)
        println("SHOT --- release")
        key(s, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, down)
        pump(400)
    }

    private fun stored(key: String): String =
        context.getSharedPreferences("orbit.favorites", Context.MODE_PRIVATE).getString(key, "").orEmpty().replace("\n", ",")

    @Test
    fun z1_holdOkOpensMenuAndReleaseDoesNothing() {
        favorites(myApps, myInputs)
        val s = launch()
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        holdOk(s)
        shot("z1-after-hold-release")
        println("SHOT --- now a real OK on Open")
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        println("SHOT --- end")
    }

    @Test
    fun z2_quickTapAfterHoldLaunches() {
        favorites(myApps, myInputs)
        val s = launch()
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        holdOk(s)
        press(s, KeyEvent.KEYCODE_BACK)
        println("SHOT --- quick tap")
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        println("SHOT --- end")
    }

    @Test
    fun z3_absentInputKeptAndSkippedByMove() {
        favorites(myApps, listOf("hdmi1", "ghost", "ps5"))
        val s = launch()
        println("SHOT stored after launch: " + stored(FavoriteStore.INPUTS))
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(s, KeyEvent.KEYCODE_MENU)
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        press(s, KeyEvent.KEYCODE_DPAD_LEFT)
        println("SHOT stored after one move left: " + stored(FavoriteStore.INPUTS))
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        shot("z3-input-moved-past-hidden")
        // Leave and come back, as after watching something: the absent one must still be kept.
        s.recreate()
        pump(1500)
        println("SHOT stored after return: " + stored(FavoriteStore.INPUTS))
    }

    @Test
    fun z4_moveThenRemoveFocusesNeighbour() {
        favorites(myApps, myInputs)
        val s = launch()
        press(s, KeyEvent.KEYCODE_MENU)
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        println("SHOT --- moved TiviMate to index 3, now remove it")
        press(s, KeyEvent.KEYCODE_MENU)
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER, settle = 800)
        shot("z4-after-move-then-remove")
        println("SHOT --- end")
    }

    @Test
    fun s01_noFavorites() {
        favorites(emptyList(), emptyList())
        launch()
        shot("01-no-favorites")
        pump(3500)
        shot("02-no-favorites-label-scrolled")
    }

    @Test
    fun s03_favorites() {
        favorites(myApps, myInputs)
        val s = launch()
        shot("03-favorites-first-focused")
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        shot("04-favorites-third-focused")
    }

    @Test
    fun s05_popOutMidAnimation() {
        favorites(myApps, myInputs)
        val s = launch()
        s.onActivity {
            val now = android.os.SystemClock.uptimeMillis()
            it.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0))
            it.dispatchKeyEvent(KeyEvent(now, now + 50, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0))
        }
        pump(64)
        shot("05-pop-out-mid-animation")
    }

    @Test
    fun s06_plusWrapLabel() {
        favorites(myApps, myInputs)
        val s = launch()
        press(s, KeyEvent.KEYCODE_DPAD_LEFT)
        shot("06-plus-after-wrap")
        pump(3500)
        shot("07-plus-label-scrolled")
    }

    @Test
    fun s08_menuOnApp() {
        favorites(myApps, myInputs)
        val s = launch()
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(s, KeyEvent.KEYCODE_MENU)
        shot("08-menu-on-app")
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        shot("09-menu-on-app-remove-focused")
        press(s, KeyEvent.KEYCODE_DPAD_CENTER, settle = 800)
        shot("10-after-remove")
    }

    @Test
    fun s11_menuOnInput() {
        favorites(myApps, myInputs)
        val s = launch()
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        shot("11-input-focused")
        press(s, KeyEvent.KEYCODE_MENU)
        shot("12-menu-on-input")
        press(s, KeyEvent.KEYCODE_BACK)
        shot("13-menu-closed-back")
    }

    @Test
    fun s14_longInputName() {
        favorites(myApps, myInputs)
        val s = launch()
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        shot("14-long-input-name-start")
        pump(3000)
        shot("15-long-input-name-scrolled")
    }

    @Test
    fun s16_topBar() {
        favorites(myApps, myInputs)
        val s = launch()
        press(s, KeyEvent.KEYCODE_DPAD_UP)
        shot("16-top-bar-focus")
    }

    @Test
    fun s17_notice() {
        ShotHooks.defaultHome = false
        favorites(myApps, myInputs)
        launch()
        shot("17-with-notice")
    }

    @Test
    fun s18_move() {
        favorites(myApps, myInputs)
        val s = launch()
        press(s, KeyEvent.KEYCODE_MENU)
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        shot("18-move-mode")
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        shot("19-move-mode-moved")
    }

    @Test
    fun s20_longAppLabel() {
        favorites(myApps + "com.plexapp.android", myInputs)
        val s = launch()
        repeat(6) { press(s, KeyEvent.KEYCODE_DPAD_RIGHT, settle = 200) }
        pump(400)
        shot("20-long-app-label-start")
        pump(3000)
        shot("21-long-app-label-scrolled")
    }
}
