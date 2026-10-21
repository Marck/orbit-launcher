package app.orbit.launcher

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * A picture that can be chosen. [key] tells apart files that share a name in different folders,
 * and is the same whether the file was found through the media index or by walking the folders.
 */
data class PickerImage(val uri: Uri, val name: String, val key: String = name)

/**
 * Finds pictures on the TV so one can be chosen with the remote.
 *
 * The system document picker is a stub on Android TV and refuses the intent, so the scan is our
 * own. It looks at the media index and at the usual directories directly, because a file copied
 * over adb or dropped on a USB stick may not have been indexed yet.
 */
object AvatarPicker {

    private val EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp")
    private const val MAX_DEPTH = 3
    private const val MAX_RESULTS = 120

    /** A USB stick can hold tens of thousands of files; the walk gives up rather than hang. */
    private const val WALK_MILLIS = 3_000L

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_IMAGES"
        else "android.permission.READ_EXTERNAL_STORAGE"

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    fun scan(context: Context): List<PickerImage> {
        val found = LinkedHashMap<String, PickerImage>()
        requestIndex(context)
        scanMediaStore(context, found)
        scanDirectories(context, found)
        return found.values.take(MAX_RESULTS)
    }

    /**
     * Asks the media scanner to look at the public folders.
     *
     * A picture the browser downloaded is indexed already, but one copied in over adb is not, and
     * on a device that enforces scoped storage the index is the only way to see it at all. The
     * scan is asynchronous, so it mainly helps the next time the picker is opened.
     */
    private fun requestIndex(context: Context) {
        val paths = publicDirectories().map { it.absolutePath }.toTypedArray()
        runCatching { MediaScannerConnection.scanFile(context, paths, null, null) }
    }

    private fun publicDirectories(): List<File> = listOf(
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_DCIM,
    ).map { Environment.getExternalStoragePublicDirectory(it) }

    private fun scanMediaStore(context: Context, into: MutableMap<String, PickerImage>) {
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                columns,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(columns[0])
                val nameColumn = cursor.getColumnIndexOrThrow(columns[1])
                val folderColumn = cursor.getColumnIndex(columns[2])
                while (cursor.moveToNext() && into.size < MAX_RESULTS) {
                    val name = cursor.getString(nameColumn) ?: continue
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idColumn),
                    )
                    val folder = if (folderColumn >= 0) cursor.getString(folderColumn).orEmpty() else ""
                    val key = folder + name
                    into.putIfAbsent(key, PickerImage(uri, name, key))
                }
            }
        }
    }

    private fun scanDirectories(context: Context, into: MutableMap<String, PickerImage>) {
        val roots = publicDirectories().toMutableList()
        // Removable media, where a USB stick turns up.
        runCatching { File("/storage").listFiles() }.getOrNull()
            ?.filter { it.isDirectory && it.name !in setOf("self", "emulated") }
            ?.let { roots += it }
        context.getExternalFilesDir(null)?.let { roots += it }

        val deadline = android.os.SystemClock.elapsedRealtime() + WALK_MILLIS
        val own = Profile.ownFiles(context)
        roots.forEach { root -> collect(root, 0, into, deadline, own) }
    }

    /**
     * The same key the media index gives a file: its folder below the storage root, then its
     * name. A file elsewhere, on a USB stick, is keyed by its full path.
     */
    private fun keyOf(file: File): String {
        val storage = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull()
        val path = file.absolutePath
        return if (storage != null && path.startsWith("$storage/")) path.removePrefix("$storage/") else path
    }

    private fun collect(
        dir: File,
        depth: Int,
        into: MutableMap<String, PickerImage>,
        deadline: Long,
        own: Set<File>,
    ) {
        if (depth > MAX_DEPTH || into.size >= MAX_RESULTS) return
        if (android.os.SystemClock.elapsedRealtime() > deadline) return
        val entries = runCatching { dir.listFiles() }.getOrNull() ?: return
        entries.forEach { entry ->
            if (into.size >= MAX_RESULTS) return
            when {
                // Hidden folders and other apps' data hold thumbnails and caches, not pictures
                // anyone would pick, and can be very large.
                entry.isDirectory && (entry.name.startsWith(".") || entry.name == "Android") -> Unit
                entry.isDirectory -> collect(entry, depth + 1, into, deadline, own)
                // Orbit's own stored picture is not one of the owner's pictures.
                entry in own -> Unit
                entry.extension.lowercase() in EXTENSIONS && entry.length() > 0 ->
                    keyOf(entry).let { key -> into.putIfAbsent(key, PickerImage(Uri.fromFile(entry), entry.name, key)) }
            }
        }
    }

    /** Decoded small, because a full photo is far larger than a tile and there may be many. */
    fun thumbnail(context: Context, image: PickerImage, maxPixels: Int = 256): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(image.uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }.getOrNull()
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        if (largest <= 0) return null
        var sample = 1
        while (largest / sample > maxPixels) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching {
            context.contentResolver.openInputStream(image.uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }
}
