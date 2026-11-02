package org.monogram.feature.profile

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Movie
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal data class ProfileBackgroundSettings(
    val file: File?,
    val transparency: Float,
)

internal object ProfileBackgroundStorage {
    private const val PREFERENCES = "monogram_profile_backgrounds"
    private const val BACKGROUND_KEY_PREFIX = "background_"
    private const val TRANSPARENCY_KEY_PREFIX = "transparency_"
    private const val MAX_FILE_BYTES = 100L * 1024L * 1024L
    private const val MAX_IMAGE_PIXELS = 120_000_000L
    private const val MAX_GIF_PIXELS = 32_000_000L

    fun load(context: Context, accountId: Long): ProfileBackgroundSettings {
        val preferences = preferences(context)
        val key = backgroundKey(accountId)
        val file = resolveFile(context, accountId, preferences.getString(key, null))
        if (preferences.contains(key) && file == null) {
            preferences.edit().remove(key).apply()
        }
        return ProfileBackgroundSettings(
            file = file,
            transparency = normalizeProfileBackgroundTransparency(
                preferences.getFloat(transparencyKey(accountId), DEFAULT_PROFILE_BACKGROUND_TRANSPARENCY),
            ),
        )
    }

    /** Saves a new local file and returns the previous one for cleanup. */
    fun select(context: Context, accountId: Long, file: File): File? {
        val directory = backgroundDirectory(context, accountId)
        require(file.isFile && file.length() > 0L && file.parentFile?.absoluteFile == directory.absoluteFile)
        val preferences = preferences(context)
        val previous = resolveFile(context, accountId, preferences.getString(backgroundKey(accountId), null))
        preferences.edit().putString(backgroundKey(accountId), file.name).apply()
        return previous?.takeUnless { it == file }
    }

    fun setTransparency(context: Context, accountId: Long, transparency: Float) {
        preferences(context).edit()
            .putFloat(
                transparencyKey(accountId),
                normalizeProfileBackgroundTransparency(transparency),
            )
            .apply()
    }

    /** Removes the preference first and returns its old file for asynchronous deletion. */
    fun clear(context: Context, accountId: Long): File? {
        val preferences = preferences(context)
        val previous = resolveFile(context, accountId, preferences.getString(backgroundKey(accountId), null))
        preferences.edit()
            .remove(backgroundKey(accountId))
            .remove(transparencyKey(accountId))
            .apply()
        return previous
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun backgroundKey(accountId: Long) = "$BACKGROUND_KEY_PREFIX$accountId"
    private fun transparencyKey(accountId: Long) = "$TRANSPARENCY_KEY_PREFIX$accountId"

    private fun backgroundDirectory(context: Context, accountId: Long) =
        File(context.filesDir, "profile-backgrounds/$accountId")

    private fun resolveFile(context: Context, accountId: Long, name: String?): File? {
        if (name.isNullOrBlank() || File(name).name != name || !name.startsWith("background-")) {
            return null
        }
        val extension = name.substringAfterLast('.', "").lowercase()
        if (ProfileBackgroundFormat.entries.none { it.extension == extension }) return null
        return File(backgroundDirectory(context, accountId), name)
            .takeIf { it.isFile && it.length() > 0L }
    }

    suspend fun importBackground(context: Context, accountId: Long, uri: Uri): File =
        withContext(Dispatchers.IO) {
            val temporary = File.createTempFile("profile-background-import-", ".tmp", context.cacheDir)
            var destination: File? = null
            try {
                val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
                    "Unable to read the selected file"
                }
                input.use { source -> copyBounded(source, temporary) }
                val format = detectProfileBackgroundFormat(temporary.readHeader())
                    ?: throw IllegalArgumentException("Unsupported profile background")
                when (format) {
                    ProfileBackgroundFormat.Mp4 -> validateVideo(temporary)
                    ProfileBackgroundFormat.Gif -> validateGif(temporary)
                    else -> validateImage(temporary)
                }

                val directory = backgroundDirectory(context, accountId)
                if (!directory.isDirectory && !directory.mkdirs()) {
                    throw IllegalStateException("Cannot create profile background directory")
                }
                destination = File(directory, "background-${UUID.randomUUID()}.${format.extension}")
                if (!temporary.renameTo(destination!!)) {
                    temporary.inputStream().use { source -> copyBounded(source, destination!!) }
                    temporary.delete()
                }
                destination!!
            } catch (error: Exception) {
                destination?.delete()
                throw error
            } finally {
                temporary.delete()
            }
        }

    private suspend fun copyBounded(source: java.io.InputStream, destination: File) {
        destination.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                coroutineContext.ensureActive()
                val count = source.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    val byte = source.read()
                    if (byte < 0) break
                    total++
                    require(total <= MAX_FILE_BYTES) { "Profile background is too large" }
                    output.write(byte)
                } else {
                    total += count
                    require(total <= MAX_FILE_BYTES) { "Profile background is too large" }
                    output.write(buffer, 0, count)
                }
            }
            require(total > 0L) { "Profile background is empty" }
        }
    }

    private fun File.readHeader(): ByteArray {
        val header = ByteArray(12)
        inputStream().use { input ->
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count <= 0) break
                offset += count
            }
            return header.copyOf(offset)
        }
    }

    private fun validateGif(file: File) {
        val header = file.readHeader()
        require(header.size >= 10) { "Invalid profile GIF" }
        val width = (header[6].toInt() and 0xff) or ((header[7].toInt() and 0xff) shl 8)
        val height = (header[8].toInt() and 0xff) or ((header[9].toInt() and 0xff) shl 8)
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_GIF_PIXELS) {
            "Invalid or oversized profile GIF"
        }
        val movie = Movie.decodeFile(file.absolutePath)
            ?: throw IllegalArgumentException("Invalid profile GIF")
        require(movie.width() > 0 && movie.height() > 0) { "Invalid profile GIF" }
    }

    private fun validateImage(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_IMAGE_PIXELS) {
            "Invalid or oversized profile image"
        }
        var sample = 1
        while (maxOf(width, height) / sample > 2048) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw IllegalArgumentException("Invalid profile image")
        bitmap.recycle()
    }

    private fun validateVideo(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val hasVideoTrack = (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            }
            require(hasVideoTrack) { "The selected MP4 has no video track" }
        } finally {
            extractor.release()
        }
    }
}
