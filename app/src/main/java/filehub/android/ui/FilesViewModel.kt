package filehub.android.ui

import android.app.Application
import coil3.ImageLoader
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileRoot
import filehub.android.core.model.FileSort
import filehub.android.core.model.ServerProfile
import filehub.android.core.model.SortOrder
import filehub.android.data.FilesRepository
import filehub.android.data.media.ThumbnailLoaderFactory
import filehub.android.data.api.FileHubApiException
import filehub.android.data.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class FileBrowserState(
    val profile: ServerProfile? = null,
    val root: FileRoot? = null,
    val path: String = "",
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val readOnly: Boolean = false,
    val readOnlyReason: String? = null,
    val stale: Boolean = false,
    val sort: FileSort = FileSort.Name,
    val order: SortOrder = SortOrder.Asc,
    val error: String? = null,
    val loginRequired: Boolean = false,
    val selectedEntry: FileEntry? = null,
) {
    val isEmpty: Boolean get() = !loading && error == null && entries.isEmpty()
}

class FilesViewModel(
    private val repository: FilesRepository,
    private val thumbnailLoaderFactory: ThumbnailLoaderFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    private val _imageLoader = MutableStateFlow<ImageLoader?>(null)
    val imageLoader: StateFlow<ImageLoader?> = _imageLoader.asStateFlow()

    private var loadJob: Job? = null

    fun open(profile: ServerProfile, root: FileRoot, path: String = "") {
        loadJob?.cancel()
        _state.value = FileBrowserState(
            profile = profile,
            root = root,
            path = path,
            loading = true,
        )
        viewModelScope.launch {
            repository.tokenFor(profile)?.let { token ->
                _imageLoader.value = thumbnailLoaderFactory.get(profile.id, token)
            }
        }
        load(path = path, append = false)
    }

    fun refresh() {
        val current = _state.value
        if (current.root == null || current.profile == null) return
        loadJob?.cancel()
        _state.update { it.copy(loading = it.entries.isEmpty(), error = null, stale = false) }
        load(path = current.path, append = false)
    }

    fun navigateTo(path: String) {
        val normalized = normalizePath(path)
        if (normalized == _state.value.path) return
        loadJob?.cancel()
        _state.update {
            it.copy(path = normalized, entries = emptyList(), nextCursor = null, loading = true, error = null)
        }
        load(path = normalized, append = false)
    }

    fun enter(entry: FileEntry) {
        if (!entry.isDir) {
            _state.update { it.copy(selectedEntry = entry) }
            return
        }
        val current = _state.value
        val next = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        navigateTo(next)
    }

    fun goUp() {
        val current = _state.value
        if (current.path.isEmpty()) return
        navigateTo(current.path.substringBeforeLast('/', ""))
    }

    fun openPathSegment(index: Int) {
        val current = _state.value
        val segments = current.path.split('/').filter { it.isNotEmpty() }
        if (index < 0) {
            navigateTo("")
            return
        }
        navigateTo(segments.take(index + 1).joinToString("/"))
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        load(path = current.path, append = true, cursor = cursor)
    }

    fun setSort(sort: FileSort) {
        val current = _state.value
        if (sort == current.sort) {
            _state.update { it.copy(order = if (it.order == SortOrder.Asc) SortOrder.Desc else SortOrder.Asc) }
        } else {
            _state.update { it.copy(sort = sort, order = SortOrder.Asc) }
        }
        refresh()
    }

    fun dismissSelected() {
        _state.update { it.copy(selectedEntry = null) }
    }

    fun thumbnailUrl(entry: FileEntry, width: Int = 128): String? {
        if (!entry.hasPreview) return null
        val current = _state.value
        val profile = current.profile ?: return null
        val root = current.root ?: return null
        val base = profile.baseUrl.toHttpUrlOrNull() ?: return null
        val guestPath = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        return base.newBuilder()
            .addPathSegments("api/v1/thumb")
            .addQueryParameter("root", root.id.toString())
            .addQueryParameter("path", guestPath)
            .addQueryParameter("w", width.toString())
            .addQueryParameter("wait", "1")
            .addQueryParameter("v", entry.modified.toString())
            .build()
            .toString()
    }

    fun clear() {
        loadJob?.cancel()
        _state.value = FileBrowserState()
    }

    private fun load(path: String, append: Boolean, cursor: String? = null) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        if (append) {
            _state.update { it.copy(loadingMore = true, error = null) }
        } else {
            _state.update { it.copy(loading = it.entries.isEmpty(), error = null, loginRequired = false) }
        }
        loadJob = viewModelScope.launch {
            runCatching {
                repository.list(
                    profile = profile,
                    rootId = root.id,
                    path = path,
                    sort = current.sort,
                    order = current.order,
                    cursor = cursor,
                )
            }.onSuccess { listing ->
                _state.update {
                    it.copy(
                        path = path,
                        entries = if (append) it.entries + listing.entries else listing.entries,
                        nextCursor = listing.nextCursor,
                        readOnly = listing.readOnly,
                        readOnlyReason = listing.readOnlyReason,
                        stale = listing.stale,
                        loading = false,
                        loadingMore = false,
                        loginRequired = false,
                        error = null,
                    )
                }
            }.onFailure { error ->
                val needsLogin = (error as? FileHubApiException)?.statusCode == 401
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        loginRequired = needsLogin,
                        error = if (needsLogin) null else error.toUserMessage(),
                    )
                }
            }
        }
    }

    private fun normalizePath(path: String): String =
        path.trim('/').replace(Regex("/+"), "/")

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as FileHubApplication
                FilesViewModel(app.container.filesRepository, app.container.thumbnailLoaderFactory)
            }
        }
    }
}
