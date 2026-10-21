package app.orbit.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * The avatar in the top bar.
 *
 * The signed-in account's picture belongs to the account provider and is not readable by a
 * third-party app: no provider on an Android TV exposes it. So Orbit shows a picture the owner
 * drops in instead, and falls back to a plain glyph when there is none.
 */
object Profile {

    private val FILE_NAMES = listOf("avatar.png", "avatar.jpg", "avatar.jpeg", "avatar.webp")

    /**
     * Bumped whenever the picture changes, so the home screen picks a new one up on return
     * instead of holding the copy it read when it was composed.
     */
    var revision by mutableIntStateOf(0)
        private set

    /** Where to put the picture: the app's own external files directory, writable over adb. */
    fun avatarDirectory(context: Context): String? = context.getExternalFilesDir(null)?.absolutePath

    /**
     * The picture, decoded no larger than [LoadPixels].
     *
     * Bounded even though Orbit stores a small copy itself: a file dropped in over adb can be a
     * full-size photo, and drawing one of those can take the home screen down with it.
     */
    fun avatar(context: Context): Bitmap? {
        val file = file(context) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        if (largest <= 0) return null
        var sample = 1
        while (largest / (sample * 2) >= LoadPixels) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
    }

    /**
     * Stores a small copy of the chosen picture, so it survives the original being moved or
     * deleted and costs next to nothing to show. Reads the file, so call it off the main thread.
     */
    fun save(context: Context, uri: Uri, pixels: Int): Boolean {
        val decoded = AvatarPicker.thumbnail(context, PickerImage(uri, ""), pixels) ?: return false
        return save(context, upright(context, uri, decoded))
    }

    /** Saves a picture that is already small, such as one of the built-in ones. */
    fun save(context: Context, bitmap: Bitmap): Boolean {
        val dir = context.getExternalFilesDir(null) ?: return false
        val target = File(dir, FILE_NAMES.first())
        // Written beside the old one and moved over it, so a failed write leaves the old
        // picture as it was rather than half overwritten.
        val partial = File(dir, "${target.name}.part")
        val written = runCatching {
            partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.getOrDefault(false)
        if (!written || !partial.renameTo(target)) {
            runCatching { partial.delete() }
            return false
        }
        // Drop any other spelling, so the lookup order cannot resurrect an older picture.
        FILE_NAMES.drop(1).forEach { runCatching { File(dir, it).delete() } }
        revision++
        return true
    }

    /**
     * Turns a photo the right way up. Cameras store pictures sideways and note the rotation
     * separately, and decoding ignores that note.
     */
    private fun upright(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: return bitmap
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    /** Every file Orbit keeps its picture in, including one half written, so a scan can skip them. */
    fun ownFiles(context: Context): Set<File> {
        val dir = context.getExternalFilesDir(null) ?: return emptySet()
        return FILE_NAMES.flatMap { listOf(File(dir, it), File(dir, "$it.part")) }.toSet()
    }

    private fun file(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: return null
        return FILE_NAMES.asSequence()
            .map { File(dir, it) }
            .firstOrNull { it.isFile && it.length() > 0 }
    }

    fun clear(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: return
        FILE_NAMES.forEach { runCatching { File(dir, it).delete() } }
        revision++
    }

    /** Whether there is a picture to clear. Looks at the file only; decoding it is not needed. */
    fun hasAvatar(context: Context): Boolean = file(context) != null

    /** A little over the most it is drawn at: 21dp, grown on focus, on a 4K panel. */
    private const val LoadPixels = 128

    /**
     * Where the avatar leads.
     *
     * The account screens resolve but finish immediately when launched without an account extra,
     * which a launcher cannot supply, so this opens the settings root where accounts live.
     */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_SETTINGS)
}
