package dev.vimivo.settings

import dev.vimivo.core.model.LibrarySort
import dev.vimivo.playback.AudioEnhancementSettings
import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class PreferredEngine { AUTOMATIC, MEDIA3, LIBVLC }
enum class SubtitleEdgeStyle { NONE, OUTLINE, DROP_SHADOW }
enum class AccentColor { AURORA, VIOLET, BLUE, GREEN, ORANGE, PINK }
enum class ShuffleBehavior { OFF, ON, REMEMBER }
enum class RepeatBehavior { OFF, ALL, ONE, REMEMBER }
enum class ResumeBehavior { NEVER, ALWAYS }
enum class VideoRotationBehavior { FOLLOW_SYSTEM, SENSOR, LOCK_CURRENT }
enum class VideoAspectPreference { FIT, FILL, ORIGINAL, RATIO_16_9, RATIO_4_3, CROP }
enum class DecoderPreference { AUTOMATIC, HARDWARE, SOFTWARE }
enum class HiddenMediaBehavior { HIDE, SHOW }

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
    val accentColor: AccentColor = AccentColor.AURORA,
    val artworkBasedAccents: Boolean = true,
    val reduceAnimations: Boolean = false,
    val reduceTransparency: Boolean = false,
    val highContrast: Boolean = false,
    val largerTouchTargets: Boolean = false,
    val shuffleBehavior: ShuffleBehavior = ShuffleBehavior.REMEMBER,
    val repeatBehavior: RepeatBehavior = RepeatBehavior.REMEMBER,
    val musicResumeBehavior: ResumeBehavior = ResumeBehavior.ALWAYS,
    val defaultLibrarySort: LibrarySort = LibrarySort.TITLE,
    val restoreQueue: Boolean = true,
    val defaultVideoSpeed: Float = 1f,
    val doubleTapSeekSeconds: Int = 10,
    val videoGestureSensitivity: Float = 1f,
    val videoRotationBehavior: VideoRotationBehavior = VideoRotationBehavior.FOLLOW_SYSTEM,
    val videoResumeBehavior: ResumeBehavior = ResumeBehavior.ALWAYS,
    val videoAspectPreference: VideoAspectPreference = VideoAspectPreference.FIT,
    val decoderPreference: DecoderPreference = DecoderPreference.AUTOMATIC,
    val preferredSubtitleLanguage: String = "",
    val preferredAudioLanguage: String = "",
    val subtitlesEnabled: Boolean = false,
    val subtitleDelayMillis: Long = 0,
    val audioDelayMillis: Long = 0,
    val audioEnhancements: AudioEnhancementSettings = AudioEnhancementSettings(),
    val audioVisualizerEnabled: Boolean = true,
    val subtitleAppearance: SubtitleAppearance = SubtitleAppearance(),
    val hiddenMediaBehavior: HiddenMediaBehavior = HiddenMediaBehavior.HIDE,
    val autoReconnectStreams: Boolean = true,
    val homeSections: List<HomeSection> = HomeSection.entries,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
