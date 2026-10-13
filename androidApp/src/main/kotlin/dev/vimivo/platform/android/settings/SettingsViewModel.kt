@file:Suppress("ImportOrdering", "TooManyFunctions")

package dev.vimivo.platform.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import dev.vimivo.settings.AppSettings
import dev.vimivo.settings.AccentColor
import dev.vimivo.settings.DecoderPreference
import dev.vimivo.settings.HiddenMediaBehavior
import dev.vimivo.settings.HomeSection
import dev.vimivo.settings.SettingsRepository
import dev.vimivo.settings.SubtitleAppearance
import dev.vimivo.settings.ThemeMode
import dev.vimivo.settings.ShuffleBehavior
import dev.vimivo.settings.RepeatBehavior
import dev.vimivo.settings.ResumeBehavior
import dev.vimivo.settings.VideoAspectPreference
import dev.vimivo.settings.VideoRotationBehavior
import dev.vimivo.core.model.LibrarySort
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.vimivo.domain.repository.MediaRepository
import dev.vimivo.platform.android.database.LibraryDao
import dev.vimivo.platform.android.library.AndroidThumbnailRepository

data class SettingsOperationState(
    val exclusions: List<String> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val mediaRepository: MediaRepository,
    private val libraryDao: LibraryDao,
    private val thumbnails: AndroidThumbnailRepository,
    private val backup: LocalBackupManager,
) : ViewModel() {
    val state: StateFlow<AppSettings> = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )
    private val mutableOperations = MutableStateFlow(SettingsOperationState())
    val operations = mutableOperations.asStateFlow()

    init { refreshExclusions() }

    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }
    fun setAccentColor(value: AccentColor) = update { it.copy(accentColor = value) }
    fun setArtworkAccents(enabled: Boolean) = update { it.copy(artworkBasedAccents = enabled) }
    fun setReduceAnimations(enabled: Boolean) = update { it.copy(reduceAnimations = enabled) }
    fun setHighContrast(enabled: Boolean) = update { it.copy(highContrast = enabled) }
    fun setLargerTouchTargets(enabled: Boolean) = update { it.copy(largerTouchTargets = enabled) }
    fun setResume(enabled: Boolean) = update { it.copy(resumePlayback = enabled) }
    fun setShuffleBehavior(value: ShuffleBehavior) = update { it.copy(shuffleBehavior = value) }
    fun setRepeatBehavior(value: RepeatBehavior) = update { it.copy(repeatBehavior = value) }
    fun setMusicResume(value: ResumeBehavior) = update { it.copy(musicResumeBehavior = value) }
    fun setDefaultSort(value: LibrarySort) = update { it.copy(defaultLibrarySort = value) }
    fun setQueueRestoration(enabled: Boolean) = update { it.copy(restoreQueue = enabled) }
    fun setDefaultVideoSpeed(value: Float) = update { it.copy(defaultVideoSpeed = value.coerceIn(0.5f, 2f)) }
    fun setDoubleTapSeekSeconds(value: Int) = update { it.copy(doubleTapSeekSeconds = value.coerceIn(5, 60)) }
    fun setVideoGestureSensitivity(value: Float) = update {
        it.copy(videoGestureSensitivity = value.coerceIn(0.5f, 2f))
    }
    fun setVideoRotation(value: VideoRotationBehavior) = update { it.copy(videoRotationBehavior = value) }
    fun setVideoResume(value: ResumeBehavior) = update { it.copy(videoResumeBehavior = value) }
    fun setVideoAspect(value: VideoAspectPreference) = update { it.copy(videoAspectPreference = value) }
    fun setDecoder(value: DecoderPreference) = update { it.copy(decoderPreference = value) }
    fun setHiddenMedia(value: HiddenMediaBehavior) = update { it.copy(hiddenMediaBehavior = value) }
    fun setAutoReconnect(enabled: Boolean) = update { it.copy(autoReconnectStreams = enabled) }
    fun setPreferredSubtitleLanguage(language: String) = update { it.copy(preferredSubtitleLanguage = language) }
    fun setPreferredAudioLanguage(language: String) = update { it.copy(preferredAudioLanguage = language) }
    fun setSubtitlesEnabled(enabled: Boolean) = update { it.copy(subtitlesEnabled = enabled) }
    fun setSubtitleDelay(delayMillis: Long) = update {
        it.copy(subtitleDelayMillis = delayMillis.coerceIn(-10_000, 10_000))
    }
    fun setAudioDelay(delayMillis: Long) = update { it.copy(audioDelayMillis = delayMillis.coerceIn(-10_000, 10_000)) }
    fun setSubtitleAppearance(appearance: SubtitleAppearance) = update { it.copy(subtitleAppearance = appearance) }
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

    fun reset() = update { AppSettings() }
    fun restore(settings: AppSettings) = update { settings }

    fun rescan() = operation("Library scan completed") { mediaRepository.refresh().getOrThrow() }
    fun cleanMissing() = operation("Missing media cleaned") { mediaRepository.removeMissing().getOrThrow() }
    fun excludeFolder(path: String) = operation("Folder excluded") {
        mediaRepository.excludeFolder(path).getOrThrow()
        refreshExclusionsNow()
    }
    fun includeFolder(path: String) = operation("Folder included") {
        mediaRepository.includeFolder(path).getOrThrow()
        refreshExclusionsNow()
    }
    fun clearPlaybackHistory() = operation("Playback history cleared") { libraryDao.clearPlaybackHistory() }
    fun clearArtworkCache() {
        thumbnails.clear()
        mutableOperations.value = operations.value.copy(message = "Artwork cache cleared")
    }
    fun exportBackup(uri: Uri) = operation("Backup exported") { backup.export(uri).getOrThrow() }
    fun restoreBackup(uri: Uri) = operation("Backup restored") {
        val report = backup.restore(uri).getOrThrow()
        mutableOperations.value = operations.value.copy(
            message = "Restored ${report.playlists} playlists, ${report.favorites} favorites, " +
                "${report.bookmarks} bookmarks and ${report.history} history entries; " +
                "${report.missingReferences} missing references skipped",
        )
    }
    fun clearMessage() { mutableOperations.value = operations.value.copy(message = null) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    private fun refreshExclusions() = viewModelScope.launch { refreshExclusionsNow() }
    private suspend fun refreshExclusionsNow() {
        mutableOperations.value = operations.value.copy(exclusions = libraryDao.exclusions())
    }

    private fun operation(successMessage: String, block: suspend () -> Unit) = viewModelScope.launch {
        mutableOperations.value = operations.value.copy(busy = true, message = null)
        runCatching { block() }.fold(
            onSuccess = {
                if (operations.value.message == null) {
                    mutableOperations.value = operations.value.copy(busy = false, message = successMessage)
                } else {
                    mutableOperations.value = operations.value.copy(busy = false)
                }
            },
            onFailure = { mutableOperations.value = operations.value.copy(busy = false, message = it.message) },
        )
    }
}
