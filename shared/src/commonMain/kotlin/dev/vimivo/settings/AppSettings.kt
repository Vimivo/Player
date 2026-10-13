package dev.vimivo.settings

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class PreferredEngine { AUTOMATIC, MEDIA3, LIBVLC }
enum class SubtitleEdgeStyle { NONE, OUTLINE, DROP_SHADOW }

data class SubtitleAppearance(
    val textSizeSp: Float = 20f,
    val positionFraction: Float = 0.08f,
    val textColorArgb: Long = 0xFFFFFFFF,
    val backgroundColorArgb: Long = 0x66000000,
    val edgeStyle: SubtitleEdgeStyle = SubtitleEdgeStyle.OUTLINE,
    val edgeColorArgb: Long = 0xFF000000,
)
enum class HomeSection {
    CONTINUE_WATCHING,
    RECENTLY_PLAYED,
    RECENT_MUSIC,
    RECENT_VIDEOS,
    FAVORITES,
    PLAYLISTS,
    OPEN_STREAM,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val preferredEngine: PreferredEngine = PreferredEngine.AUTOMATIC,
    val resumePlayback: Boolean = true,
    val dynamicColor: Boolean = true,
    val videoGestureSensitivity: Float = 1f,
    val preferredSubtitleLanguage: String = "",
    val preferredAudioLanguage: String = "",
    val subtitlesEnabled: Boolean = false,
    val subtitleDelayMillis: Long = 0,
    val audioDelayMillis: Long = 0,
    val subtitleAppearance: SubtitleAppearance = SubtitleAppearance(),
    val homeSections: List<HomeSection> = HomeSection.entries,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
