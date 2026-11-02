package org.monogram.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileBackgroundFormatTest {
    @Test
    fun recognizesSupportedFileSignatures() {
        assertEquals(
            ProfileBackgroundFormat.Png,
            detectProfileBackgroundFormat(bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)),
        )
        assertEquals(ProfileBackgroundFormat.Jpeg, detectProfileBackgroundFormat(bytes(0xff, 0xd8, 0xff, 0x00)))
        assertEquals(ProfileBackgroundFormat.Webp, detectProfileBackgroundFormat(ascii("RIFF0000WEBP")))
        assertEquals(ProfileBackgroundFormat.Gif, detectProfileBackgroundFormat(ascii("GIF89a")))
        assertEquals(ProfileBackgroundFormat.Mp4, detectProfileBackgroundFormat(ascii("0000ftypisom")))
    }

    @Test
    fun rejectsUnknownAndTruncatedFileSignatures() {
        assertNull(detectProfileBackgroundFormat(ascii("not an image")))
        assertNull(detectProfileBackgroundFormat(bytes(0x89, 0x50, 0x4e)))
        assertNull(detectProfileBackgroundFormat(ascii("RIFF0000NOPE")))
    }

    @Test
    fun transparencyIsBoundedAndNonFiniteValuesUseTheDefault() {
        assertEquals(0f, normalizeProfileBackgroundTransparency(-0.2f))
        assertEquals(1f, normalizeProfileBackgroundTransparency(1.4f))
        assertEquals(
            DEFAULT_PROFILE_BACKGROUND_TRANSPARENCY,
            normalizeProfileBackgroundTransparency(Float.NaN),
        )
        assertEquals(
            DEFAULT_PROFILE_BACKGROUND_TRANSPARENCY,
            normalizeProfileBackgroundTransparency(Float.POSITIVE_INFINITY),
        )
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun ascii(value: String) = value.toByteArray(Charsets.ISO_8859_1)
}
