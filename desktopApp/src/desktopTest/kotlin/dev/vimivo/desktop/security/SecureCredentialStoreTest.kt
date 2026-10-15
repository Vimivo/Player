package dev.vimivo.desktop.security

import dev.vimivo.desktop.platform.DesktopPlatform
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureCredentialStoreTest {
    @Test
    fun memoryCredentialStoreLifecycle() {
        val store = MemoryCredentialStore()
        val key = "stream_auth_1"
        val secret = "super-secret-password-123".toCharArray()

        // 1. Initial lookup is null
        val initial = store.getSecret(key).getOrThrow()
        assertNull(initial)

        // 2. Save secret
        store.saveSecret(key, secret).getOrThrow()
        val retrieved = store.getSecret(key).getOrThrow()
        assertNotNull(retrieved)
        assertContentEquals(secret, retrieved)

        // 3. Update secret
        val updatedSecret = "new-password-456".toCharArray()
        store.saveSecret(key, updatedSecret).getOrThrow()
        val retrievedUpdated = store.getSecret(key).getOrThrow()
        assertNotNull(retrievedUpdated)
        assertContentEquals(updatedSecret, retrievedUpdated)

        // 4. Delete secret
        store.deleteSecret(key).getOrThrow()
        val afterDelete = store.getSecret(key).getOrThrow()
        assertNull(afterDelete)

        // 5. Delete non-existent key succeeds cleanly
        store.deleteSecret("non_existent").getOrThrow()

        // 6. Close clears all
        store.saveSecret("temp", "token".toCharArray()).getOrThrow()
        store.close()
        assertNull(store.getSecret("temp").getOrThrow())
    }

    @Test
    fun blankKeysAreRejected() {
        val store = MemoryCredentialStore()
        assertTrue(store.saveSecret("", "pass".toCharArray()).isFailure)
        assertTrue(store.saveSecret("   ", "pass".toCharArray()).isFailure)
        assertTrue(store.getSecret("").isFailure)
        assertTrue(store.deleteSecret("").isFailure)
    }

    @Test
    fun factoryProducesFunctionalStoreForAnyPlatform() {
        val windowsStore = SecureCredentialStoreFactory.create(DesktopPlatform.WINDOWS)
        assertNotNull(windowsStore)
        windowsStore.saveSecret("test_key", "secret_val".toCharArray()).getOrThrow()
        val read = windowsStore.getSecret("test_key").getOrThrow()
        assertNotNull(read)
        assertContentEquals("secret_val".toCharArray(), read)
        windowsStore.deleteSecret("test_key").getOrThrow()
        windowsStore.close()

        val macStore = SecureCredentialStoreFactory.create(DesktopPlatform.MACOS)
        assertNotNull(macStore)
        macStore.close()

        val linuxStore = SecureCredentialStoreFactory.create(DesktopPlatform.LINUX)
        assertNotNull(linuxStore)
        linuxStore.saveSecret("linux_key", "linux_secret".toCharArray()).getOrThrow()
        val linuxRead = linuxStore.getSecret("linux_key").getOrThrow()
        assertNotNull(linuxRead)
        assertContentEquals("linux_secret".toCharArray(), linuxRead)
        linuxStore.deleteSecret("linux_key").getOrThrow()
        linuxStore.close()
    }
}
