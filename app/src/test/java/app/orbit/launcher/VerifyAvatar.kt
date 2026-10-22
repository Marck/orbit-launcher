package app.orbit.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class VerifyAvatar : TvTest() {

    private fun photo(name: String, w: Int, h: Int, orientation: Int? = null): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.BLUE)
        // A red band along the top edge, so rotation can be seen.
        c.drawRect(0f, 0f, w.toFloat(), h / 10f, Paint().apply { color = Color.RED })
        val f = File(context.cacheDir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (orientation != null) ExifInterface(f.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes()
        }
        return f
    }

    @Test
    fun bigRotatedPhotoIsStoredSmallAndUpright() {
        val f = photo("big.jpg", 4000, 3000, ExifInterface.ORIENTATION_ROTATE_90)
        val ok = Profile.save(context, Uri.fromFile(f), 256)
        assertTrue(ok)
        val stored = File(context.getExternalFilesDir(null), "avatar.png")
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(stored.absolutePath, b)
        println("TV stored ${b.outWidth}x${b.outHeight}, ${stored.length()} bytes")
        assertTrue("stored copy must be small", maxOf(b.outWidth, b.outHeight) <= 512)
        assertTrue("rotated: portrait now", b.outHeight > b.outWidth)
        assertTrue(Profile.hasAvatar(context))
    }

    @Test
    fun hugeFileDroppedInLoadsDownsampled() {
        val dir = context.getExternalFilesDir(null)!!
        photo("huge.jpg", 6000, 4000).copyTo(File(dir, "avatar.jpg"), overwrite = true)
        val loaded = Profile.avatar(context)
        assertNotNull(loaded)
        println("TV loaded ${loaded!!.width}x${loaded.height}")
        assertTrue("must be downsampled", maxOf(loaded.width, loaded.height) <= 256)
    }

    @Test
    fun missingOrEmptyFileMeansNoAvatar() {
        val dir = context.getExternalFilesDir(null)!!
        File(dir, "avatar.png").writeBytes(ByteArray(0))
        assertEquals(false, Profile.hasAvatar(context))
        assertEquals(null, Profile.avatar(context))
    }
}
