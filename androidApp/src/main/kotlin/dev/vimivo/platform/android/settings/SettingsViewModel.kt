package dev.vimivo.platform.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vimivo.settings.AppSettings
import dev.vimivo.settings.HomeSection
import dev.vimivo.settings.SettingsRepository
import dev.vimivo.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val state: StateFlow<AppSettings> = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }
    fun setResume(enabled: Boolean) = update { it.copy(resumePlayback = enabled) }
    fun setSectionVisible(section: HomeSection, visible: Boolean) = update { settings ->
        val sections = settings.homeSections.toMutableList().apply {
            if (visible && section !in this) add(section) else if (!visible) remove(section)
        }
        settings.copy(homeSections = sections)
    }
    fun moveSection(section: HomeSection, direction: Int) = update { settings ->
        val sections = settings.homeSections.toMutableList()
        val from = sections.indexOf(section)
        val to = (from + direction).coerceIn(sections.indices)
        if (from >= 0 && from != to) sections.add(to, sections.removeAt(from))
        settings.copy(homeSections = sections)
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
