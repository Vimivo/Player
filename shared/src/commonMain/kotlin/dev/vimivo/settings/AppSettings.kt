package dev.vimivo.settings

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class PreferredEngine { AUTOMATIC, MEDIA3, LIBVLC }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val preferredEngine: PreferredEngine = PreferredEngine.AUTOMATIC,
    val resumePlayback: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
