package dev.vimivo.platform.android.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.vimivo.settings.AppSettings
import dev.vimivo.settings.HomeSection
import dev.vimivo.settings.PreferredEngine
import dev.vimivo.settings.SettingsRepository
import dev.vimivo.settings.SubtitleAppearance
import dev.vimivo.settings.SubtitleEdgeStyle
import dev.vimivo.settings.ThemeMode
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
            preferredEngine = preferences[Keys.engine]?.let { runCatching { PreferredEngine.valueOf(it) }.getOrNull() }
                ?: PreferredEngine.AUTOMATIC,
            resumePlayback = preferences[Keys.resume] ?: true,
            dynamicColor = preferences[Keys.dynamicColor] ?: true,
            videoGestureSensitivity = preferences[Keys.videoGestureSensitivity] ?: 1f,
            preferredSubtitleLanguage = preferences[Keys.preferredSubtitleLanguage].orEmpty(),
            preferredAudioLanguage = preferences[Keys.preferredAudioLanguage].orEmpty(),
            subtitlesEnabled = preferences[Keys.subtitlesEnabled] ?: false,
            subtitleDelayMillis = preferences[Keys.subtitleDelayMillis] ?: 0,
            audioDelayMillis = preferences[Keys.audioDelayMillis] ?: 0,
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
            preferences[Keys.videoGestureSensitivity] = updated.videoGestureSensitivity
            preferences[Keys.preferredSubtitleLanguage] = updated.preferredSubtitleLanguage
            preferences[Keys.preferredAudioLanguage] = updated.preferredAudioLanguage
            preferences[Keys.subtitlesEnabled] = updated.subtitlesEnabled
            preferences[Keys.subtitleDelayMillis] = updated.subtitleDelayMillis
            preferences[Keys.audioDelayMillis] = updated.audioDelayMillis
            preferences[Keys.subtitleTextSize] = updated.subtitleAppearance.textSizeSp
            preferences[Keys.subtitlePosition] = updated.subtitleAppearance.positionFraction
            preferences[Keys.subtitleTextColor] = updated.subtitleAppearance.textColorArgb
            preferences[Keys.subtitleBackgroundColor] = updated.subtitleAppearance.backgroundColorArgb
            preferences[Keys.subtitleEdgeStyle] = updated.subtitleAppearance.edgeStyle.name
            preferences[Keys.subtitleEdgeColor] = updated.subtitleAppearance.edgeColorArgb
            preferences[Keys.homeSections] = updated.homeSections.joinToString(",", transform = HomeSection::name)
        }
    }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val engine = stringPreferencesKey("engine")
        val resume = booleanPreferencesKey("resume_playback")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val videoGestureSensitivity = floatPreferencesKey("video_gesture_sensitivity")
        val preferredSubtitleLanguage = stringPreferencesKey("preferred_subtitle_language")
        val preferredAudioLanguage = stringPreferencesKey("preferred_audio_language")
        val subtitlesEnabled = booleanPreferencesKey("subtitles_enabled")
        val subtitleDelayMillis = longPreferencesKey("subtitle_delay_millis")
        val audioDelayMillis = longPreferencesKey("audio_delay_millis")
        val subtitleTextSize = floatPreferencesKey("subtitle_text_size_sp")
        val subtitlePosition = floatPreferencesKey("subtitle_position_fraction")
        val subtitleTextColor = longPreferencesKey("subtitle_text_color_argb")
        val subtitleBackgroundColor = longPreferencesKey("subtitle_background_color_argb")
        val subtitleEdgeStyle = stringPreferencesKey("subtitle_edge_style")
        val subtitleEdgeColor = longPreferencesKey("subtitle_edge_color_argb")
        val homeSections = stringPreferencesKey("home_sections")
    }
}
