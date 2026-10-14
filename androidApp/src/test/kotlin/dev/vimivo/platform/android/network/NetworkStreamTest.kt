package dev.vimivo.platform.android.network

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStreamTest {
    @Test
    fun acceptsAuthorizedDirectAdaptiveAndLocalNetworkUrls() {
        listOf(
            "https://cdn.example.test/audio.mp3",
            "https://cdn.example.test/video.mp4?token=temporary",
            "https://cdn.example.test/live/index.m3u8",
            "https://cdn.example.test/manifest.mpd",
            "http://192.168.1.20:8080/radio",
            "http://localhost:8000/media",
            "rtsp://camera.example.test:8554/live",
            "rtsps://camera.example.test/live",
        ).forEach { url -> assertEquals(url, validateNetworkStreamUrl(url).getOrThrow()) }
    }

    @Test
    fun rejectsInvalidSchemesMissingHostsAndEmbeddedCredentials() {
        listOf(
            "ftp://example.test/media.mp3",
            "smb://user:secret@example.test/share/movie.mkv",
            "https:///missing-host.mp3",
            "https://user:secret@example.test/media.mp3",
            "not a url",
        ).forEach { value -> assertTrue(value, validateNetworkStreamUrl(value).isFailure) }
    }

    @Test
    fun infersAdaptiveManifestTypesWithoutGuessingDirectMedia() {
        assertEquals("application/x-mpegURL", inferredStreamMimeType("https://example.test/live.M3U8?token=one"))
        assertEquals("application/dash+xml", inferredStreamMimeType("https://example.test/live.mpd"))
        assertEquals(null, inferredStreamMimeType("https://example.test/audio.mp3"))
    }

    @Test
    fun reconnectIsLimitedToTransportFailures() {
        assertTrue(isReconnectableErrorCode(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(isReconnectableErrorCode(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertFalse(isReconnectableErrorCode(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(isReconnectableErrorCode(PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED))
    }
}
