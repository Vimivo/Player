@file:Suppress("ArgumentListWrapping", "ImportOrdering", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.platform.android.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vimivo.core.model.LibraryQuery
import dev.vimivo.core.model.LibraryCategory
import dev.vimivo.core.model.LibraryCategoryField
import dev.vimivo.core.model.LibrarySort
import dev.vimivo.core.model.LibraryStatus
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaPermissionAccess
import dev.vimivo.core.model.MediaPermissionState
import dev.vimivo.domain.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val items: List<MediaItem> = emptyList(),
    val query: LibraryQuery = LibraryQuery(kind = MediaKind.AUDIO),
    val totalCount: Int = 0,
    val categories: List<LibraryCategory>? = null,
    val categoryField: LibraryCategoryField? = null,
    val hasMore: Boolean = false,
    val isLoading: Boolean = true,
    val status: LibraryStatus = LibraryStatus.Idle,
    val permissions: MediaPermissionState = MediaPermissionState(
        MediaPermissionAccess.DENIED,
        MediaPermissionAccess.DENIED,
    ),
    val errorMessage: String? = null,
)

class LibraryViewModel(private val repository: MediaRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    private var loadJob: Job? = null
    val state: StateFlow<LibraryUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { repository.status.collect { status -> mutableState.update { it.copy(status = status) } } }
        viewModelScope.launch { repository.changes.collect { load(reset = true) } }
        viewModelScope.launch {
            val permissions = repository.permissionState()
            mutableState.update { it.copy(permissions = permissions) }
            repository.refresh()
        }
    }

    fun selectKind(kind: MediaKind) {
        mutableState.update {
            it.copy(query = LibraryQuery(kind = kind), categories = null, categoryField = null)
        }
        load(reset = true)
    }

    fun showCategories(field: LibraryCategoryField) = viewModelScope.launch {
        val kind = state.value.query.kind ?: MediaKind.AUDIO
        mutableState.update { it.copy(isLoading = true, categoryField = field, errorMessage = null) }
        repository.categories(kind, field).fold(
            onSuccess = { categories -> mutableState.update { it.copy(categories = categories, isLoading = false) } },
            onFailure = ::showError,
        )
    }

    fun selectCategory(category: LibraryCategory) {
        val current = state.value
        val query = LibraryQuery(kind = current.query.kind).let { base ->
            when (current.categoryField) {
                LibraryCategoryField.ALBUM -> base.copy(album = category.name)
                LibraryCategoryField.ARTIST -> base.copy(artist = category.name)
                LibraryCategoryField.GENRE -> base.copy(genre = category.name)
                LibraryCategoryField.FOLDER -> base.copy(folderPath = category.name)
                null -> base
            }
        }
        mutableState.update { it.copy(query = query, categories = null, categoryField = null) }
        load(reset = true)
    }

    fun recentlyAdded() {
        mutableState.update {
            it.copy(
                query = LibraryQuery(kind = MediaKind.VIDEO, sort = LibrarySort.DATE_ADDED, descending = true),
                categories = null,
                categoryField = null,
            )
        }
        load(reset = true)
    }

    fun recentlyWatched() {
        mutableState.update {
            it.copy(
                query = LibraryQuery(
                    kind = MediaKind.VIDEO,
                    recentlyWatchedOnly = true,
                    sort = LibrarySort.LAST_PLAYED,
                    descending = true,
                ),
                categories = null,
                categoryField = null,
            )
        }
        load(reset = true)
    }

    fun search(text: String) {
        mutableState.update { it.copy(query = it.query.copy(text = text, offset = 0), categories = null) }
        load(reset = true)
    }

    fun loadMore() {
        if (!state.value.hasMore || state.value.isLoading) return
        load(reset = false)
    }

    fun rescan() = viewModelScope.launch { repository.refresh().onFailure(::showError) }

    fun permissionsChanged() = viewModelScope.launch {
        mutableState.update { it.copy(permissions = repository.permissionState()) }
        repository.refresh().onFailure(::showError)
    }

    fun importFile(uri: String) = viewModelScope.launch { repository.importFile(uri).onFailure(::showError) }
    fun importFolder(uri: String) = viewModelScope.launch { repository.importFolder(uri).onFailure(::showError) }

    fun toggleFavorite(item: MediaItem) = viewModelScope.launch {
        repository.setFavorite(item.id, !item.isFavorite).onFailure(::showError)
        load(reset = true)
    }

    private fun load(reset: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val current = mutableState.value
            val offset = if (reset) 0 else current.items.size
            mutableState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.page(current.query.copy(offset = offset)).fold(
                onSuccess = { page ->
                    mutableState.update {
                        it.copy(
                            items = if (reset) page.items else it.items + page.items,
                            totalCount = page.totalCount,
                            hasMore = page.hasMore,
                            isLoading = false,
                        )
                    }
                },
                onFailure = { error -> mutableState.update { it.copy(isLoading = false, errorMessage = error.message) } },
            )
        }
    }

    private fun showError(error: Throwable) = mutableState.update { it.copy(errorMessage = error.message) }
}
