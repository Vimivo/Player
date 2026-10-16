@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.core.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VimivoErrorTest {

    @Test
    fun testAllTwentyErrorCategoriesExist() {
        val categories = VimivoErrorCategory.values()
        assertEquals(20, categories.size)
        assertTrue(categories.contains(VimivoErrorCategory.APPLICATION_STARTUP))
        assertTrue(categories.contains(VimivoErrorCategory.MEDIA_NOT_FOUND))
        assertTrue(categories.contains(VimivoErrorCategory.MEDIA_UNSUPPORTED))
        assertTrue(categories.contains(VimivoErrorCategory.MEDIA_CORRUPT))
        assertTrue(categories.contains(VimivoErrorCategory.PLAYBACK_INITIALIZATION))
        assertTrue(categories.contains(VimivoErrorCategory.AUDIO_OUTPUT))
        assertTrue(categories.contains(VimivoErrorCategory.VIDEO_RENDERING))
        assertTrue(categories.contains(VimivoErrorCategory.PLAYBACK_INTERRUPTED))
        assertTrue(categories.contains(VimivoErrorCategory.DATABASE))
        assertTrue(categories.contains(VimivoErrorCategory.DATABASE_MIGRATION))
        assertTrue(categories.contains(VimivoErrorCategory.QUEUE_PERSISTENCE))
        assertTrue(categories.contains(VimivoErrorCategory.FILE_PERMISSION))
        assertTrue(categories.contains(VimivoErrorCategory.NETWORK_TIMEOUT))
        assertTrue(categories.contains(VimivoErrorCategory.NETWORK_AUTHENTICATION))
        assertTrue(categories.contains(VimivoErrorCategory.NATIVE_DEPENDENCY))
        assertTrue(categories.contains(VimivoErrorCategory.UPDATE_CHECK))
        assertTrue(categories.contains(VimivoErrorCategory.UPDATE_VERIFICATION))
        assertTrue(categories.contains(VimivoErrorCategory.UPDATE_INSTALLATION))
        assertTrue(categories.contains(VimivoErrorCategory.BACKUP_RESTORE))
        assertTrue(categories.contains(VimivoErrorCategory.UNKNOWN))
    }

    @Test
    fun testMediaNotFoundRedactsUserPath() {
        val error = VimivoError.mediaNotFound("/Users/johndoe/Music/private_album/song.mp3")
        assertEquals(VimivoErrorCategory.MEDIA_NOT_FOUND, error.category)
        assertTrue(error.isRecoverable)
        assertFalse(error.toDiagnosticString().contains("johndoe"))
        assertTrue(error.toDiagnosticString().contains("~/user/Music/private_album/song.mp3"))
    }

    @Test
    fun testNetworkAuthRedactsCredentialsAndTokens() {
        val sensitiveUrl = "https://stream.internal.net/audio.m3u8?token=SECRET_AUTH_TOKEN_123&user=alice"
        val error = VimivoError.networkAuthFailed(sensitiveUrl)
        assertEquals(VimivoErrorCategory.NETWORK_AUTHENTICATION, error.category)
        assertTrue(error.isRecoverable)
        assertFalse(error.toDiagnosticString().contains("SECRET_AUTH_TOKEN_123"))
        assertTrue(error.toDiagnosticString().contains("[REDACTED]"))
    }

    @Test
    fun testDatabaseMigrationErrorIsNonRecoverable() {
        val error = VimivoError.databaseMigrationFailed(fromVersion = 2, toVersion = 3, RuntimeException("Disk full"))
        assertEquals(VimivoErrorCategory.DATABASE_MIGRATION, error.category)
        assertFalse(error.isRecoverable)
        assertTrue(error.userMessage.contains("preserved safely"))
    }

    @Test
    fun testNativeDependencyMissingError() {
        val error = VimivoError.nativeDependencyMissing("libmpv.2.dylib")
        assertEquals(VimivoErrorCategory.NATIVE_DEPENDENCY, error.category)
        assertFalse(error.isRecoverable)
        assertEquals("ERR_NATIVE_DEP_MISSING", error.code)
    }
}
