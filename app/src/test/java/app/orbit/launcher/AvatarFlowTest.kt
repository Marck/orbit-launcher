package app.orbit.launcher

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A stand-in for the system's MediaStore: lists the files it is given, serves their bytes. */
class FakeMediaProvider : ContentProvider() {
    companion object {
        val rows = mutableListOf<Pair<Long, File>>()
        var queries = 0
        var delayMs = 0L
    }

    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        queries++
        if (delayMs > 0) Thread.sleep(delayMs)
        val c = MatrixCursor(arrayOf("_id", "_display_name"))
        rows.forEach { (id, f) -> c.addRow(arrayOf<Any>(id, f.name)) }
        return c
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment!!.toLong()
        val f = rows.first { it.first == id }.second
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class AvatarFlowTest : TvTest() {

    override val outDir = "build/tv-shots/verify-avatar"

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        Profile.clear(context)
        FakeMediaProvider.rows.clear()
        Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media")
        favorites(myApps, myInputs)
        while (nextStarted() != null) Unit
    }

    // ---------- helpers ----------

    private fun pictures(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).apply { mkdirs() }

    private fun jpeg(file: File, w: Int, h: Int, color: Int) {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(color)
        c.drawCircle(w / 2f, h / 2f, minOf(w, h) / 3f, Paint().apply { this.color = Color.WHITE })
        file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        b.recycle()
    }

    private fun grant() = shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
    private fun deny() = shadowOf(app).denyPermissions(Manifest.permission.READ_MEDIA_IMAGES)

    private fun avatarFile() = File(context.getExternalFilesDir(null), "avatar.png")

    /** Arrows the remote to the picture labelled [label] under YOUR PICTURES: down twice, then right. */
    private fun focusTile(s: ActivityScenario<AvatarPickerActivity>, label: String) {
        // The pictures are read on Dispatchers.IO, on a real thread, while the clock here is a
        // fake one. Wait for the list to land rather than trust a single pump, or the
        // "Looking for pictures" placeholder is what gets read, at random.
        var names: List<String> = emptyList()
        var i = -1
        for (attempt in 0 until 150) {
            val t = texts()
            names = t.drop(t.indexOf("YOUR PICTURES") + 1).takeWhile { it != "CURRENT" }
            i = names.indexOf(label)
            if (i in 0..5) break
            Thread.sleep(100)
            pump(100)
        }
        check(i in 0..5) { "$label not in first picture row: $names" }
        // From Orbit: down to Bloom, down to the first picture, then right.
        repeat(2) { press(s, KeyEvent.KEYCODE_DPAD_DOWN) }
        repeat(i) { press(s, KeyEvent.KEYCODE_DPAD_RIGHT) }
        println("TV on picture '$label' (#$i), focused=${focused()}")
    }

    private fun texts(): List<String> =
        rule.onAllNodes(androidx.compose.ui.test.hasTextExactly(*arrayOf<String>(), includeEditableText = false).not(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }

    private fun walk(s: ActivityScenario<*>, code: Int, n: Int): List<String?> {
        @Suppress("UNCHECKED_CAST")
        val sc = s as ActivityScenario<android.app.Activity>
        return (1..n).map { press(sc, code); focused() }
    }

    // ---------- the picker ----------

    @Test
    fun a1_initialFocusAndDpadOverBuiltIns() {
        deny()
        val s = launch(AvatarPickerActivity::class.java)
        println("TV initial focus: ${focused()}")
        shot("a1-picker-initial")
        println("TV right x6: ${walk(s, KeyEvent.KEYCODE_DPAD_RIGHT, 6)}")
        println("TV down x3: ${walk(s, KeyEvent.KEYCODE_DPAD_DOWN, 3)}")
        shot("a1-picker-after-down")
        println("TV up x3: ${walk(s, KeyEvent.KEYCODE_DPAD_UP, 3)}")
        println("TV left x6: ${walk(s, KeyEvent.KEYCODE_DPAD_LEFT, 6)}")
        assertEquals("Orbit", focused())
    }

    @Test
    fun a2_chooseBuiltInStoresItAndHomeUpdates() {
        deny()
        val home = launch(HomeActivity::class.java)
        // The avatar is the rightmost button of the top bar.
        toTopBar(home, "Profile")
        println("TV home top focus after right: ${focused()}")
        shot("a2-home-before")
        press(home, KeyEvent.KEYCODE_DPAD_CENTER)
        val started = nextStarted()
        println("TV avatar OK started: $started")
        assertEquals(AvatarPickerActivity::class.java.name, started?.component?.className)
        home.moveToState(Lifecycle.State.CREATED)

        val before = Profile.revision
        val picker = launch(AvatarPickerActivity::class.java)
        press(picker, KeyEvent.KEYCODE_DPAD_RIGHT)
        println("TV picker focus: ${focused()}")
        press(picker, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        var finishing = false
        picker.onActivity { finishing = it.isFinishing }
        println("TV picker finishing=$finishing revision ${before} -> ${Profile.revision} file=${avatarFile().length()}")
        assertTrue(finishing)
        assertTrue(Profile.revision > before)
        val saved = Profile.avatar(context)
        assertNotNull(saved)
        println("TV saved ${saved!!.width}x${saved.height}")
        picker.close()

        home.moveToState(Lifecycle.State.RESUMED)
        pump(1500)
        println("TV home focus after return: ${focused()}")
        shot("a2-home-after")
    }

    @Test
    fun a3_backLeavesProfileAlone() {
        deny()
        Profile.save(context, BuiltInAvatars.render(BuiltInAvatars.all[2], 256))
        val bytes = avatarFile().readBytes()
        val rev = Profile.revision
        val s = launch(AvatarPickerActivity::class.java)
        shot("a3-picker-with-current")
        press(s, KeyEvent.KEYCODE_BACK)
        var finishing = false
        s.onActivity { finishing = it.isFinishing }
        assertTrue(finishing)
        assertEquals(rev, Profile.revision)
        assertTrue(bytes.contentEquals(avatarFile().readBytes()))
    }

    @Test
    fun a5_picturesNormalHugeCorrupt() {
        grant()
        val dir = pictures()
        val normal = File(dir, "normal.jpg").also { jpeg(it, 640, 480, Color.rgb(200, 60, 60)) }
        val huge = File(dir, "huge.jpg").also { jpeg(it, 8000, 6000, Color.rgb(40, 140, 60)) }
        val corrupt = File(dir, "corrupt.jpg").also { it.writeBytes(ByteArray(5000) { i -> (i * 31).toByte() }) }
        // The same three through the media index, under other names.
        FakeMediaProvider.rows += 1L to File(dir, "m-normal.jpg").also { normal.copyTo(it) }
        println("TV files: ${dir.listFiles()?.map { it.name + ":" + it.length() }}")

        val s = launch(AvatarPickerActivity::class.java, settle = 2500).also { settleIo() }
        println("TV media queries=${FakeMediaProvider.queries} texts: ${texts()}")
        shot("a5-picker-pictures")

        focusTile(s, "m-normal.jpg")
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        val viaMedia = Profile.avatar(context)
        println("TV via media: ${viaMedia?.width}x${viaMedia?.height}")
        assertNotNull(viaMedia)
        s.close()

        val s2 = launch(AvatarPickerActivity::class.java, settle = 2500).also { settleIo() }
        focusTile(s2, "huge.jpg")
        press(s2, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        val big = Profile.avatar(context)
        println("TV huge saved file=${avatarFile().length()} decoded ${big?.width}x${big?.height} bytes=${big?.byteCount}")
        s2.close()
        val home = launch(HomeActivity::class.java)
        shot("a5-home-huge")
        home.close()
        assertTrue(
            "the top bar decodes the whole photo: ${big?.byteCount} bytes, over the 100 MB a Canvas will draw",
            (big?.byteCount ?: 0) < 100 * 1024 * 1024,
        )
    }

    @Test
    fun a6_corruptPictureReplacesGoodOne() {
        grant()
        Profile.save(context, BuiltInAvatars.render(BuiltInAvatars.all[0], 256))
        assertNotNull(Profile.avatar(context))
        File(pictures(), "corrupt.jpg").writeBytes(ByteArray(5000) { i -> (i * 31).toByte() })
        val s = launch(AvatarPickerActivity::class.java, settle = 2500).also { settleIo() }
        shot("a6-corrupt-tile")
        focusTile(s, "corrupt.jpg")
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        var finishing = false
        s.onActivity { finishing = it.isFinishing }
        println("TV corrupt chosen: finishing=$finishing file=${avatarFile().length()} avatar=${Profile.avatar(context)}")
        assertNotNull("a picture that does not decode should not replace the current one", Profile.avatar(context))
    }

    @Test
    fun a7_ownSavedPictureListedAndChoosingItWipesIt() {
        grant()
        jpeg(File(pictures(), "normal.jpg"), 640, 480, Color.RED)
        val s = launch(AvatarPickerActivity::class.java, settle = 2500).also { settleIo() }
        focusTile(s, "normal.jpg")
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        println("TV first choice file=${avatarFile().length()}")
        s.close()
        val s2 = launch(AvatarPickerActivity::class.java, settle = 2500).also { settleIo() }
        val t = texts()
        println("TV picker texts second time: $t")
        shot("a7-own-avatar-listed")
        // Choosing the same picture again is fine.
        focusTile(s2, "normal.jpg")
        press(s2, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        println("TV same again file=${avatarFile().length()} ok=${Profile.avatar(context) != null}")
        assertNotNull(Profile.avatar(context))
        s2.close()
        val s3 = launch(AvatarPickerActivity::class.java, settle = 2500).also { settleIo() }
        if (texts().contains("avatar.png")) {
            focusTile(s3, "avatar.png")
            press(s3, KeyEvent.KEYCODE_DPAD_CENTER)
            println("TV chose avatar.png: file=${avatarFile().length()} avatar=${Profile.avatar(context)}")
        }
        assertFalse("Orbit's own stored copy shows up as one of 'your pictures'", t.contains("avatar.png"))
        assertNotNull(Profile.avatar(context))
    }

    @Test
    fun a8_missingOrCorruptProfileOnStartup() {
        deny()
        val dir = context.getExternalFilesDir(null)!!
        // Zero bytes.
        File(dir, "avatar.png").writeBytes(ByteArray(0))
        var s = launch(HomeActivity::class.java)
        println("TV empty file -> avatar=${Profile.avatar(context)}")
        s.close()
        // Garbage.
        File(dir, "avatar.png").writeBytes("not a picture".toByteArray())
        s = launch(HomeActivity::class.java)
        println("TV garbage -> avatar=${Profile.avatar(context)} focus=${focused()}")
        shot("a8-home-garbage-avatar")
        s.close()
        // A good jpg under the png's name: decoded by content, fine.
        jpeg(File(dir, "avatar.png"), 100, 100, Color.YELLOW)
        println("TV jpeg-in-png -> ${Profile.avatar(context)?.width}")
        // Garbage in the picker: is there a way to clear it?
        File(dir, "avatar.png").writeBytes("not a picture".toByteArray())
        val p = launch(AvatarPickerActivity::class.java)
        println("TV picker with garbage file texts: ${texts()}")
        p.close()
    }

    @Test
    fun a9_exifRotatedPhoto() {
        grant()
        val f = File(pictures(), "portrait.jpg")
        jpeg(f, 400, 200, Color.BLUE) // stored sideways, as a camera does
        android.media.ExifInterface(f.absolutePath).apply {
            setAttribute(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        println("TV exif orientation now: ${android.media.ExifInterface(f.absolutePath).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 0)}")
        val s = launch(AvatarPickerActivity::class.java, settle = 2500).also { settleIo() }
        focusTile(s, "portrait.jpg")
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        val a = Profile.avatar(context)
        println("TV exif-rotated avatar decoded ${a?.width}x${a?.height}")
        assertTrue("portrait photo should come out portrait", a != null && a.height > a.width)
    }

    private fun toAllow(s: ActivityScenario<AvatarPickerActivity>): org.robolectric.shadows.ShadowActivity.PermissionsRequest {
        repeat(2) { press(s, KeyEvent.KEYCODE_DPAD_DOWN) }
        assertEquals("Allow access", focused())
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        println("TV focus right after OK on Allow access (dialog would now be up): ${focused()}")
        var req: org.robolectric.shadows.ShadowActivity.PermissionsRequest? = null
        s.onActivity { req = shadowOf(it).lastRequestedPermission; println("TV view focus: ${it.currentFocus} hasWindowFocus=${it.hasWindowFocus()}") }
        return req!!
    }

    /** Lets work on Dispatchers.IO finish in real time, then flushes frames. */
    private fun settleIo() { repeat(5) { Thread.sleep(200); pump(200) } }

    @Test
    fun c1_pauseResumeAloneKeepsFocus() {
        deny()
        val s = launch(AvatarPickerActivity::class.java)
        repeat(2) { press(s, KeyEvent.KEYCODE_DPAD_DOWN) }
        println("TV c1 before pause: ${focused()}")
        s.moveToState(Lifecycle.State.STARTED)
        pump(300)
        s.moveToState(Lifecycle.State.RESUMED)
        pump(800)
        println("TV c1 after resume: ${focused()}")
    }

    @Test
    fun c3_grantResultListsPicturesAndKeepsRemoteAlive() {
        deny()
        jpeg(File(pictures(), "holiday.jpg"), 640, 480, Color.BLUE)
        val s = launch(AvatarPickerActivity::class.java)
        val req = toAllow(s)
        grant()
        s.onActivity { it.onRequestPermissionsResult(req.requestCode, req.requestedPermissions, intArrayOf(0)) }
        pump(1500)
        println("TV c3 texts right after grant: ${texts()}")
        settleIo()
        val f = focused()
        println("TV c3 after grant: focus=$f texts=${texts()}")
        shot("c3-after-grant")
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        println("TV c3 OK after grant: started=${nextStarted()} finishing=${run { var x = false; s.onActivity { x = it.isFinishing }; x }}")
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        println("TV c3 DOWN after grant: ${focused()}")
        assertTrue(texts().contains("holiday.jpg"))
        assertNotNull("after granting, something must have focus so OK/arrows work", f)
    }

    @Test
    fun c4_contentUriPath() {
        grant()
        val f = File(pictures(), "m.jpg").also { jpeg(it, 640, 480, Color.GREEN) }
        FakeMediaProvider.rows += 7L to f
        val uri = Uri.parse("content://media/external/images/media/7")
        val th = AvatarPicker.thumbnail(context, PickerImage(uri, "m.jpg"))
        println("TV c4 thumbnail via content: ${th?.width}")
        val ok = Profile.save(context, uri, 256)
        println("TV c4 save via content: $ok file=${avatarFile().length()}")
        val hugeF = File(pictures(), "h.jpg").also { jpeg(it, 8000, 6000, Color.GREEN) }
        val t0 = System.currentTimeMillis()
        val th2 = AvatarPicker.thumbnail(context, PickerImage(Uri.fromFile(hugeF), "h.jpg"))
        println("TV c4 huge thumb ${th2?.width}x${th2?.height} in ${System.currentTimeMillis() - t0}ms")
    }

    @Test
    fun d1_slowScanClaimsThereAreNoPictures() {
        grant()
        jpeg(File(pictures(), "holiday.jpg"), 640, 480, Color.BLUE)
        // A media index (or a USB stick) that takes a few seconds to answer.
        FakeMediaProvider.delayMs = 3000
        try {
            val s = launch(AvatarPickerActivity::class.java)
            val during = texts()
            println("TV d1 while scanning: $during")
            shot("d1-while-scanning")
            settleIo(); Thread.sleep(2500); settleIo()
            println("TV d1 after scan: ${texts()}")
            assertFalse(
                "while the scan runs the picker tells the owner there are no pictures on the TV",
                during.contains("No pictures on this TV yet. Add one by:"),
            )
        } finally {
            FakeMediaProvider.delayMs = 0
        }
    }

    @Test
    fun d2_noPictureOptionByRemote() {
        deny()
        Profile.save(context, BuiltInAvatars.render(BuiltInAvatars.all[3], 256))
        val s = launch(AvatarPickerActivity::class.java)
        val path = walk(s, KeyEvent.KEYCODE_DPAD_DOWN, 4)
        println("TV d2 downs: $path")
        shot("d2-no-picture-focused")
        val rev = Profile.revision
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        println("TV d2 after OK: avatar=${Profile.avatar(context)} rev $rev -> ${Profile.revision}")
        assertNull(Profile.avatar(context))
    }

    @Test
    fun d3_sameFileNameInTwoFolders() {
        grant()
        jpeg(File(pictures(), "IMG_0001.jpg"), 640, 480, Color.BLUE)
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        jpeg(File(dl, "IMG_0001.jpg"), 640, 480, Color.RED)
        val found = AvatarPicker.scan(context)
        println("TV d3 scan: ${found.map { it.uri }}")
        assertEquals("two different pictures, both called IMG_0001.jpg", 2, found.size)
    }

    @Test
    fun d4_afterRefusalNoWayForward() {
        deny()
        val s = launch(AvatarPickerActivity::class.java)
        val req = toAllow(s)
        s.onActivity { it.onRequestPermissionsResult(req.requestCode, req.requestedPermissions, intArrayOf(-1)) }
        pump(800)
        // Refused a second time: from now on Android answers "denied" without showing anything.
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        var again: org.robolectric.shadows.ShadowActivity.PermissionsRequest? = null
        s.onActivity { again = shadowOf(it).lastRequestedPermission }
        s.onActivity { it.onRequestPermissionsResult(again!!.requestCode, again!!.requestedPermissions, intArrayOf(-1)) }
        pump(800)
        val t = texts()
        println("TV d4 after second refusal: $t")
        shot("d4-after-refusals")
        assertTrue(
            "after a refusal the screen still only offers 'Allow access', which Android now answers silently",
            t.any { it.contains("Settings", ignoreCase = true) } || !t.contains("Allow access"),
        )
    }

    @Test
    fun d5_currentPictureNotMarked() {
        deny()
        Profile.save(context, BuiltInAvatars.render(BuiltInAvatars.all[5], 256)) // Tide
        launch(AvatarPickerActivity::class.java)
        println("TV d5 initial focus with Tide current: ${focused()}")
    }

    @Test
    fun e1_hugeAvatarCannotBeDrawnByAHardwareCanvas() {
        grant()
        File(pictures(), "huge.jpg").also { jpeg(it, 8000, 6000, Color.rgb(40, 140, 60)) }
        val s = launch(AvatarPickerActivity::class.java, settle = 2500).also { settleIo() }
        focusTile(s, "huge.jpg")
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        settleIo()
        s.close()
        // Exactly what the top bar gets to draw.
        val shown = Profile.avatar(context)!!
        val error = runCatching {
            val node = android.graphics.RenderNode("avatar")
            node.setPosition(0, 0, 42, 42)
            val canvas = node.beginRecording()
            canvas.drawBitmap(shown, null, android.graphics.Rect(0, 0, 42, 42), null)
            node.endRecording()
        }.exceptionOrNull()
        println("TV e1 drawing the ${shown.width}x${shown.height} avatar on a RecordingCanvas: $error")
        assertNull("the home screen's hardware canvas refuses this bitmap", error)
    }

    // ---------- the home button service ----------

    private fun windowEvent(pkg: String?): AccessibilityEvent =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply { packageName = pkg }

    @Test
    fun b1_homeButtonService() {
        val service = Robolectric.buildService(HomeButtonService::class.java).create().get()
        val ss = shadowOf(service)
        println("TV clock at start: ${SystemClock.elapsedRealtime()}")
        service.onAccessibilityEvent(windowEvent("com.google.android.tvlauncher"))
        val veryFirst = ss.nextStartedActivity
        println("TV very first event at boot-ish clock: $veryFirst")
        // A device has been up far longer than 700ms.
        SystemClock.setCurrentTimeMillis(SystemClock.currentThreadTimeMillis())
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
        service.onAccessibilityEvent(windowEvent("com.google.android.tvlauncher"))
        val first = ss.nextStartedActivity
        println("TV first: $first flags=0x${Integer.toHexString(first?.flags ?: 0)}")
        assertEquals(HomeActivity::class.java.name, first?.component?.className)
        assertTrue(first!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
        service.onAccessibilityEvent(windowEvent("com.google.android.tvlauncher"))
        assertNull("debounced within 700ms", ss.nextStartedActivity)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        service.onAccessibilityEvent(windowEvent("com.google.android.tvlauncher"))
        assertNotNull("after 800ms it fires again", ss.nextStartedActivity)

        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        service.onAccessibilityEvent(windowEvent("com.netflix.ninja"))
        service.onAccessibilityEvent(windowEvent(null))
        service.onAccessibilityEvent(null)
        service.onAccessibilityEvent(
            AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED).apply { packageName = "com.google.android.tvlauncher" }
        )
        assertNull("other packages, null package, other types ignored", ss.nextStartedActivity)
    }

    @Test
    fun b3_stockInputsPanelIsYankedAway() {
        // A TV whose only VIEW_INPUTS handler is the stock launcher's own panel.
        ShotHooks.allInputs = null
        val panel = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.google.android.tvlauncher"
                name = "com.google.android.tvlauncher.inputs.InputsPanelActivity"
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = "com.google.android.tvlauncher" }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent("com.android.tv.action.VIEW_INPUTS"), panel)
        val intent = InputRepository.allInputsIntent(context)
        println("TV b3 All inputs opens: ${intent?.component}")
        assertEquals("com.google.android.tvlauncher", intent?.component?.packageName)

        val service = Robolectric.buildService(HomeButtonService::class.java).create().get()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
        // The owner pressed "All inputs" in Orbit's top bar; the panel's window comes up.
        service.onAccessibilityEvent(windowEvent("com.google.android.tvlauncher").apply { className = intent!!.component!!.className })
        val yanked = shadowOf(service).nextStartedActivity
        println("TV b3 service reaction to the inputs panel: $yanked")
        assertNull("the inputs panel Orbit itself opened is covered by Orbit again", yanked)
    }

    @Test
    fun b2_googleTvLauncherCovered() {
        val service = Robolectric.buildService(HomeButtonService::class.java).create().get()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
        val seen = mutableMapOf<String, Boolean>()
        listOf(
            "com.google.android.apps.tv.launcherx", // Google TV
            "com.google.android.leanbacklauncher", // Android TV before 8.0 / AOSP
            "com.google.android.tvlauncher", // Android TV Home
        ).forEach { pkg ->
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
            service.onAccessibilityEvent(windowEvent(pkg))
            seen[pkg] = shadowOf(service).nextStartedActivity != null
        }
        println("TV launcher coverage: $seen")
        assertTrue("Google TV's launcher (launcherx) should be taken over", seen["com.google.android.apps.tv.launcherx"] == true)
    }
}
