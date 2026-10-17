package dev.vimivo.core.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeLoggerTest {

    @Test
    fun testRedactMacUserPath() {
        val path = "/Users/secretuser/Music/Vimivo/album/song.mp3"
        val redacted = SafeLogger.redactPath(path)
        assertFalse(redacted.contains("secretuser"))
        assertTrue(redacted.contains("~/user/Music/Vimivo/album/song.mp3"))
    }

    @Test
    fun testRedactLinuxUserPath() {
        val path = "/home/john_doe/Downloads/video.mp4"
        val redacted = SafeLogger.redactPath(path)
        assertFalse(redacted.contains("john_doe"))
        assertTrue(redacted.contains("~/user/Downloads/video.mp4"))
    }

    @Test
    fun testRedactWindowsUserPath() {
        val path = """C:\Users\Administrator\Videos\movie.mkv"""
        val redacted = SafeLogger.redactPath(path)
        assertFalse(redacted.contains("Administrator"))
        assertTrue(redacted.contains("""C:\Users\user\Videos\movie.mkv"""))
    }

    @Test
    fun testRedactSensitiveUrlParams() {
        val url = "https://stream.example.com/live.m3u8?token=SECRET12345&sig=abcdef98765&format=hls"
        val redacted = SafeLogger.redactUrl(url)
        assertFalse(redacted.contains("SECRET12345"))
        assertFalse(redacted.contains("abcdef98765"))
        assertTrue(redacted.contains("token=[REDACTED]"))
        assertTrue(redacted.contains("sig=[REDACTED]"))
        assertTrue(redacted.contains("format=hls"))
    }

    @Test
    fun testRedactEmbeddedUrlCredentials() {
        val url = "https://admin:mySecretPassword123@stream.example.com/live.m3u8?jwt=xyz987"
        val redacted = SafeLogger.redactUrl(url)
        assertFalse(redacted.contains("admin"))
        assertFalse(redacted.contains("mySecretPassword123"))
        assertFalse(redacted.contains("xyz987"))
        assertTrue(redacted.contains("https://[REDACTED]@stream.example.com/live.m3u8?jwt=[REDACTED]"))
    }

    @Test
    fun testSanitizeErrorMessage() {
        val raw = "Failed to open /Users/alice/Library/test.flac with stream " +
            "https://user:pass@server.com?key=SUPERSECRET"
        val sanitized = SafeLogger.sanitize(raw)
        assertFalse(sanitized.contains("alice"))
        assertFalse(sanitized.contains("user:pass"))
        assertFalse(sanitized.contains("SUPERSECRET"))
        assertTrue(sanitized.contains("~/user/Library/test.flac"))
        assertTrue(sanitized.contains("https://[REDACTED]@server.com?key=[REDACTED]"))
    }
}
