@file:Suppress("ImportOrdering")

package dev.vimivo.platform.android.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.vimivo.settings.AppSettings
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.EqualizerBand
import dev.vimivo.playback.EqualizerPreset
import dev.vimivo.settings.AccentColor
import dev.vimivo.settings.DecoderPreference
import dev.vimivo.settings.HiddenMediaBehavior
import dev.vimivo.settings.HomeSection
import dev.vimivo.settings.LibraryViewMode
import dev.vimivo.settings.PreferredEngine
import dev.vimivo.settings.RepeatBehavior
import dev.vimivo.settings.ResumeBehavior
import dev.vimivo.settings.ShuffleBehavior
import dev.vimivo.settings.SettingsRepository
import dev.vimivo.settings.SubtitleAppearance
import dev.vimivo.settings.SubtitleEdgeStyle
import dev.vimivo.settings.ThemeMode
import dev.vimivo.settings.VideoAspectPreference
import dev.vimivo.settings.VideoRotationBehavior
import dev.vimivo.core.model.LibrarySort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("vimivo_settings")

class AndroidSettingsRepository(private val context: Context) : SettingsRepository {
    override val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            themeMode = preferences[Keys.theme]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            // "LIBVLC" was stored before libmpv replaced LibVLC as the fallback engine (ADR-005).
            preferredEngine = preferences[Keys.engine]
                ?.let { runCatching { PreferredEngine.valueOf(it.replace("LIBVLC", "LIBMPV")) }.getOrNull() }
                ?: PreferredEngine.AUTOMATIC,
            resumePlayback = preferences[Keys.resume] ?: true,
            dynamicColor = preferences[Keys.dynamicColor] ?: true,
            accentColor = preferences.enum(Keys.accentColor, AccentColor.AURORA),
            artworkBasedAccents = preferences[Keys.artworkBasedAccents] ?: true,
            reduceAnimations = preferences[Keys.reduceAnimations] ?: false,
            reduceTransparency = preferences[Keys.reduceTransparency] ?: false,
            highContrast = preferences[Keys.highContrast] ?: false,
            largerTouchTargets = preferences[Keys.largerTouchTargets] ?: false,
            shuffleBehavior = preferences.enum(Keys.shuffleBehavior, ShuffleBehavior.REMEMBER),
            repeatBehavior = preferences.enum(Keys.repeatBehavior, RepeatBehavior.REMEMBER),
            musicResumeBehavior = preferences.enum(Keys.musicResumeBehavior, ResumeBehavior.ALWAYS),
            defaultLibrarySort = preferences.enum(Keys.defaultLibrarySort, LibrarySort.TITLE),
            libraryViewMode = preferences.enum(Keys.libraryViewMode, LibraryViewMode.LIST),
            restoreQueue = preferences[Keys.restoreQueue] ?: true,
            defaultVideoSpeed = preferences[Keys.defaultVideoSpeed] ?: 1f,
            doubleTapSeekSeconds = preferences[Keys.doubleTapSeekSeconds] ?: 10,
            videoGestureSensitivity = preferences[Keys.videoGestureSensitivity] ?: 1f,
            videoRotationBehavior = preferences.enum(Keys.videoRotationBehavior, VideoRotationBehavior.FOLLOW_SYSTEM),
            videoResumeBehavior = preferences.enum(Keys.videoResumeBehavior, ResumeBehavior.ALWAYS),
            videoAspectPreference = preferences.enum(Keys.videoAspectPreference, VideoAspectPreference.FIT),
            decoderPreference = preferences.enum(Keys.decoderPreference, DecoderPreference.AUTOMATIC),
            preferredSubtitleLanguage = preferences[Keys.preferredSubtitleLanguage].orEmpty(),
            preferredAudioLanguage = preferences[Keys.preferredAudioLanguage].orEmpty(),
            subtitlesEnabled = preferences[Keys.subtitlesEnabled] ?: false,
            subtitleDelayMillis = preferences[Keys.subtitleDelayMillis] ?: 0,
            audioDelayMillis = preferences[Keys.audioDelayMillis] ?: 0,
            audioEnhancements = AudioEnhancementSettings(
                enabled = preferences[Keys.eqEnabled] ?: false,
                preset = preferences.enum(Keys.eqPreset, EqualizerPreset.FLAT),
                bands = preferences[Keys.eqBands].toBands(),
                bassStrength = preferences[Keys.bassStrength] ?: 0f,
                trebleGainDb = preferences[Keys.trebleGain] ?: 0f,
                preampDb = preferences[Keys.preamp] ?: 0f,
            ),
            audioVisualizerEnabled = preferences[Keys.audioVisualizer] ?: true,
            subtitleAppearance = SubtitleAppearance(
                textSizeSp = preferences[Keys.subtitleTextSize] ?: 20f,
                positionFraction = preferences[Keys.subtitlePosition] ?: 0.08f,
                textColorArgb = preferences[Keys.subtitleTextColor] ?: 0xFFFFFFFF,
                backgroundColorArgb = preferences[Keys.subtitleBackgroundColor] ?: 0x66000000,
                edgeStyle = preferences[Keys.subtitleEdgeStyle]
                    ?.let { runCatching { SubtitleEdgeStyle.valueOf(it) }.getOrNull() }
                    ?: SubtitleEdgeStyle.OUTLINE,
                edgeColorArgb = preferences[Keys.subtitleEdgeColor] ?: 0xFF000000,
            ),
            hiddenMediaBehavior = preferences.enum(Keys.hiddenMediaBehavior, HiddenMediaBehavior.HIDE),
            autoReconnectStreams = preferences[Keys.autoReconnectStreams] ?: true,
            homeSections = preferences[Keys.homeSections]
                ?.split(',')
                ?.mapNotNull { runCatching { HomeSection.valueOf(it) }.getOrNull() }
                ?.takeIf(List<HomeSection>::isNotEmpty)
                ?: HomeSection.entries,
        )
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(settings.first())
        context.dataStore.edit { preferences ->
            preferences[Keys.theme] = updated.themeMode.name
            preferences[Keys.engine] = updated.preferredEngine.name
            preferences[Keys.resume] = updated.resumePlayback
            preferences[Keys.dynamicColor] = updated.dynamicColor
            preferences[Keys.accentColor] = updated.accentColor.name
            preferences[Keys.artworkBasedAccents] = updated.artworkBasedAccents
            preferences[Keys.reduceAnimations] = updated.reduceAnimations
            preferences[Keys.reduceTransparency] = updated.reduceTransparency
            preferences[Keys.highContrast] = updated.highContrast
            preferences[Keys.largerTouchTargets] = updated.largerTouchTargets
            preferences[Keys.shuffleBehavior] = updated.shuffleBehavior.name
            preferences[Keys.repeatBehavior] = updated.repeatBehavior.name
            preferences[Keys.musicResumeBehavior] = updated.musicResumeBehavior.name
            preferences[Keys.defaultLibrarySort] = updated.defaultLibrarySort.name
            preferences[Keys.libraryViewMode] = updated.libraryViewMode.name
            preferences[Keys.restoreQueue] = updated.restoreQueue
            preferences[Keys.defaultVideoSpeed] = updated.defaultVideoSpeed
            preferences[Keys.doubleTapSeekSeconds] = updated.doubleTapSeekSeconds
            preferences[Keys.videoGestureSensitivity] = updated.videoGestureSensitivity
            preferences[Keys.videoRotationBehavior] = updated.videoRotationBehavior.name
            preferences[Keys.videoResumeBehavior] = updated.videoResumeBehavior.name
            preferences[Keys.videoAspectPreference] = updated.videoAspectPreference.name
            preferences[Keys.decoderPreference] = updated.decoderPreference.name
            preferences[Keys.preferredSubtitleLanguage] = updated.preferredSubtitleLanguage
            preferences[Keys.preferredAudioLanguage] = updated.preferredAudioLanguage
            preferences[Keys.subtitlesEnabled] = updated.subtitlesEnabled
            preferences[Keys.subtitleDelayMillis] = updated.subtitleDelayMillis
            preferences[Keys.audioDelayMillis] = updated.audioDelayMillis
            preferences[Keys.eqEnabled] = updated.audioEnhancements.enabled
            preferences[Keys.eqPreset] = updated.audioEnhancements.preset.name
            preferences[Keys.eqBands] = updated.audioEnhancements.bands.joinToString(";") {
                "${it.centerFrequencyHz}:${it.gainDb}"
            }
            preferences[Keys.bassStrength] = updated.audioEnhancements.bassStrength
            preferences[Keys.trebleGain] = updated.audioEnhancements.trebleGainDb
            preferences[Keys.preamp] = updated.audioEnhancements.preampDb
            preferences[Keys.audioVisualizer] = updated.audioVisualizerEnabled
            preferences[Keys.subtitleTextSize] = updated.subtitleAppearance.textSizeSp
            preferences[Keys.subtitlePosition] = updated.subtitleAppearance.positionFraction
            preferences[Keys.subtitleTextColor] = updated.subtitleAppearance.textColorArgb
            preferences[Keys.subtitleBackgroundColor] = updated.subtitleAppearance.backgroundColorArgb
            preferences[Keys.subtitleEdgeStyle] = updated.subtitleAppearance.edgeStyle.name
            preferences[Keys.subtitleEdgeColor] = updated.subtitleAppearance.edgeColorArgb
            preferences[Keys.homeSections] = updated.homeSections.joinToString(",", transform = HomeSection::name)
            preferences[Keys.hiddenMediaBehavior] = updated.hiddenMediaBehavior.name
            preferences[Keys.autoReconnectStreams] = updated.autoReconnectStreams
        }
    }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val engine = stringPreferencesKey("engine")
        val resume = booleanPreferencesKey("resume_playback")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val accentColor = stringPreferencesKey("accent_color")
        val artworkBasedAccents = booleanPreferencesKey("artwork_based_accents")
        val reduceAnimations = booleanPreferencesKey("reduce_animations")
        val reduceTransparency = booleanPreferencesKey("reduce_transparency")
        val highContrast = booleanPreferencesKey("high_contrast")
        val largerTouchTargets = booleanPreferencesKey("larger_touch_targets")
        val shuffleBehavior = stringPreferencesKey("shuffle_behavior")
        val repeatBehavior = stringPreferencesKey("repeat_behavior")
        val musicResumeBehavior = stringPreferencesKey("music_resume_behavior")
        val defaultLibrarySort = stringPreferencesKey("default_library_sort")
        val libraryViewMode = stringPreferencesKey("library_view_mode")
        val restoreQueue = booleanPreferencesKey("restore_queue")
        val defaultVideoSpeed = floatPreferencesKey("default_video_speed")
        val doubleTapSeekSeconds = intPreferencesKey("double_tap_seek_seconds")
        val videoGestureSensitivity = floatPreferencesKey("video_gesture_sensitivity")
        val videoRotationBehavior = stringPreferencesKey("video_rotation_behavior")
        val videoResumeBehavior = stringPreferencesKey("video_resume_behavior")
        val videoAspectPreference = stringPreferencesKey("video_aspect_preference")
        val decoderPreference = stringPreferencesKey("decoder_preference")
        val preferredSubtitleLanguage = stringPreferencesKey("preferred_subtitle_language")
        val preferredAudioLanguage = stringPreferencesKey("preferred_audio_language")
        val subtitlesEnabled = booleanPreferencesKey("subtitles_enabled")
        val subtitleDelayMillis = longPreferencesKey("subtitle_delay_millis")
        val audioDelayMillis = longPreferencesKey("audio_delay_millis")
        val eqEnabled = booleanPreferencesKey("audio_eq_enabled")
        val eqPreset = stringPreferencesKey("audio_eq_preset")
        val eqBands = stringPreferencesKey("audio_eq_bands")
        val bassStrength = floatPreferencesKey("audio_bass_strength")
        val trebleGain = floatPreferencesKey("audio_treble_gain")
        val preamp = floatPreferencesKey("audio_preamp")
        val audioVisualizer = booleanPreferencesKey("audio_visualizer")
        val subtitleTextSize = floatPreferencesKey("subtitle_text_size_sp")
        val subtitlePosition = floatPreferencesKey("subtitle_position_fraction")
        val subtitleTextColor = longPreferencesKey("subtitle_text_color_argb")
        val subtitleBackgroundColor = longPreferencesKey("subtitle_background_color_argb")
        val subtitleEdgeStyle = stringPreferencesKey("subtitle_edge_style")
        val subtitleEdgeColor = longPreferencesKey("subtitle_edge_color_argb")
        val homeSections = stringPreferencesKey("home_sections")
        val hiddenMediaBehavior = stringPreferencesKey("hidden_media_behavior")
        val autoReconnectStreams = booleanPreferencesKey("auto_reconnect_streams")
    }
}

private fun String?.toBands(): List<EqualizerBand> = orEmpty().split(';').mapNotNull { encoded ->
    val (frequency, gain) = encoded.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
    EqualizerBand(frequency.toIntOrNull() ?: return@mapNotNull null, gain.toFloatOrNull() ?: return@mapNotNull null)
}

private inline fun <reified T : Enum<T>> androidx.datastore.preferences.core.Preferences.enum(
    key: androidx.datastore.preferences.core.Preferences.Key<String>,
    fallback: T,
): T = get(key)?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback
