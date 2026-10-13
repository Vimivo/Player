package dev.vimivo.settings

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class PreferredEngine { AUTOMATIC, MEDIA3, LIBVLC }
enum class HomeSection { CONTINUE_WATCHING, RECENTLY_PLAYED, RECENT_MUSIC, RECENT_VIDEOS, FAVORITES, PLAYLISTS, OPEN_STREAM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val preferredEngine: PreferredEngine = PreferredEngine.AUTOMATIC,
    val resumePlayback: Boolean = true,
    val dynamicColor: Boolean = true,
    val homeSections: List<HomeSection> = HomeSection.entries,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
