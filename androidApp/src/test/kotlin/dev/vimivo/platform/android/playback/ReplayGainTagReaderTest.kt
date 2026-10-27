@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress("UnstableApiUsage")

package dev.vimivo.platform.android.playback

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainTagReaderTest {
    @Test
    fun id3TxxxFramesAreRead() {
        val tags = ReplayGainTagReader.read(
            Metadata(
                TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", ImmutableList.of("-7.20 dB")),
                TextInformationFrame("TXXX", "replaygain_album_gain", ImmutableList.of("-9.00 dB")),
                TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_PEAK", ImmutableList.of("0.95")),
                TextInformationFrame("TIT2", null, ImmutableList.of("A title")),
            ),
        )
        assertEquals(-7.2f, tags.trackGainDb)
        assertEquals(-9f, tags.albumGainDb)
        assertEquals(0.95f, tags.trackPeak)
        assertNull(tags.albumPeak)
    }

    @Test
    fun vorbisCommentsAreRead() {
        val tags = ReplayGainTagReader.read(
            Metadata(VorbisComment("REPLAYGAIN_TRACK_GAIN", "+2.5 dB"), VorbisComment("ARTIST", "Somebody")),
        )
        assertEquals(2.5f, tags.trackGainDb)
    }

    @Test
    fun itunesAtomsAreRead() {
        val tags = ReplayGainTagReader.read(
            Metadata(InternalFrame("com.apple.iTunes", "REPLAYGAIN_ALBUM_GAIN", "-4.1 dB")),
        )
        assertEquals(-4.1f, tags.albumGainDb)
    }

    @Test
    fun filesWithoutTagsGiveNothing() {
        assertEquals(ReplayGainTags(), ReplayGainTagReader.read(null))
        assertEquals(ReplayGainTags(), ReplayGainTagReader.read(Metadata(VorbisComment("TITLE", "x"))))
    }
}
