package dev.vimivo.desktop.security

import dev.vimivo.desktop.platform.DesktopPlatform

/**
 * Factory for creating platform-appropriate secure credential storage.
 */
internal object SecureCredentialStoreFactory {
    fun create(platform: DesktopPlatform = DesktopPlatform.detect()): SecureCredentialStore {
        return when (platform) {
            DesktopPlatform.WINDOWS -> runCatching { WindowsCredentialStore() }.getOrElse { MemoryCredentialStore() }
            DesktopPlatform.LINUX -> runCatching { LinuxCredentialStore() }.getOrElse { MemoryCredentialStore() }
            else -> MemoryCredentialStore()
        }
    }
}
