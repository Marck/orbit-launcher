package app.orbit.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    fun avatar(context: Context): Bitmap? {
        val dir = context.getExternalFilesDir(null) ?: return null
        val file = FILE_NAMES.asSequence()
            .map { File(dir, it) }
            .firstOrNull { it.isFile && it.length() > 0 } ?: return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /** Copies the chosen picture in, so it survives the original being moved or deleted. */
    fun save(context: Context, uri: Uri): Boolean {
        val dir = context.getExternalFilesDir(null) ?: return false
        val target = File(dir, FILE_NAMES.first())
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
        if (!copied) return false
        // Drop any other spelling, so the lookup order cannot resurrect an older picture.
        FILE_NAMES.drop(1).forEach { runCatching { File(dir, it).delete() } }
        revision++
        return true
    }

    /** Saves one of the built-in pictures, which are drawn rather than stored. */
    fun save(context: Context, bitmap: android.graphics.Bitmap): Boolean {
        val dir = context.getExternalFilesDir(null) ?: return false
        val target = File(dir, FILE_NAMES.first())
        val written = runCatching {
            target.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }.getOrDefault(false)
        if (!written) return false
        FILE_NAMES.drop(1).forEach { runCatching { File(dir, it).delete() } }
        revision++
        return true
    }

    fun clear(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: return
        FILE_NAMES.forEach { runCatching { File(dir, it).delete() } }
        revision++
    }

    fun hasAvatar(context: Context): Boolean = avatar(context) != null

    /**
     * Where the avatar leads.
     *
     * The account screens resolve but finish immediately when launched without an account extra,
     * which a launcher cannot supply, so this opens the settings root where accounts live.
     */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_SETTINGS)
}
