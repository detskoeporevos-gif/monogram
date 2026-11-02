package org.monogram.feature.profile

internal enum class ProfileBackgroundFormat(val extension: String) {
    Png("png"),
    Jpeg("jpg"),
    Webp("webp"),
    Gif("gif"),
    Mp4("mp4"),
}

internal fun detectProfileBackgroundFormat(header: ByteArray): ProfileBackgroundFormat? = when {
    header.startsWithBytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) ->
        ProfileBackgroundFormat.Png

    header.startsWithBytes(0xff, 0xd8, 0xff) -> ProfileBackgroundFormat.Jpeg
    header.matchesAscii(0, "GIF87a") || header.matchesAscii(0, "GIF89a") ->
        ProfileBackgroundFormat.Gif

    header.matchesAscii(0, "RIFF") && header.matchesAscii(8, "WEBP") ->
        ProfileBackgroundFormat.Webp

    header.matchesAscii(4, "ftyp") -> ProfileBackgroundFormat.Mp4
    else -> null
}

internal fun normalizeProfileBackgroundTransparency(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else DEFAULT_PROFILE_BACKGROUND_TRANSPARENCY

internal const val DEFAULT_PROFILE_BACKGROUND_TRANSPARENCY = 0.28f

private fun ByteArray.startsWithBytes(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { this[it] == expected[it].toByte() }

private fun ByteArray.matchesAscii(offset: Int, expected: String): Boolean =
    offset >= 0 && size >= offset + expected.length && expected.indices.all {
        this[offset + it] == expected[it].code.toByte()
    }
