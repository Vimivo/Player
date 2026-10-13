package dev.vimivo.platform.android.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.vimivo.settings.AppSettings
import dev.vimivo.settings.PreferredEngine
import dev.vimivo.settings.SettingsRepository
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
        )
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(settings.first())
        context.dataStore.edit { preferences ->
            preferences[Keys.theme] = updated.themeMode.name
            preferences[Keys.engine] = updated.preferredEngine.name
            preferences[Keys.resume] = updated.resumePlayback
        }
    }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val engine = stringPreferencesKey("engine")
        val resume = booleanPreferencesKey("resume_playback")
    }
}
