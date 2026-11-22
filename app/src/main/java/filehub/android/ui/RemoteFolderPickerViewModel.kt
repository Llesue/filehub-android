package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileRoot
import filehub.android.core.model.FileSort
import filehub.android.core.model.SortOrder
import filehub.android.data.FilesRepository
import filehub.android.data.SessionRepository
import filehub.android.data.api.FileHubApiException
import filehub.android.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteFolderPickerState(
    val visible: Boolean = false,
    val rootId: Long? = null,
    val rootAlias: String = "",
    val path: String = "",
    val directories: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
)

class RemoteFolderPickerViewModel(
    private val application: Application,
    private val filesRepository: FilesRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteFolderPickerState())
    val state: StateFlow<RemoteFolderPickerState> = _state.asStateFlow()

    fun open(root: FileRoot, initialPath: String) {
        val path = normalize(initialPath)
        _state.value = RemoteFolderPickerState(
            visible = true,
            rootId = root.id,
            rootAlias = root.alias,
            path = path,
            loading = true,
        )
        load(path, fallbackToRoot = true)
    }

    fun navigate(path: String) {
        val rootId = _state.value.rootId ?: return
        val normalized = normalize(path)
        _state.update {
            it.copy(
                path = normalized,
                directories = emptyList(),
                nextCursor = null,
                loading = true,
                error = null,
            )
        }
        load(rootId, normalized, fallbackToRoot = false, requestedPath = normalized)
    }

    fun up() {
        val current = _state.value
        if (current.path.isEmpty()) return
        navigate(current.path.substringBeforeLast('/', ""))
    }

    fun close() {
        _state.value = RemoteFolderPickerState()
    }

    private fun load(path: String, fallbackToRoot: Boolean) {
        val rootId = _state.value.rootId ?: return
        load(rootId, path, fallbackToRoot, path)
    }

    private fun load(rootId: Long, path: String, fallbackToRoot: Boolean, requestedPath: String) {
        val profile = sessionRepository.state.value.active ?: run {
            _state.update { it.copy(loading = false, error = application.getString(filehub.android.R.string.error_unauthorized)) }
            return
        }
        viewModelScope.launch {
            runCatching {
                filesRepository.list(
                    profile = profile,
                    rootId = rootId,
                    path = path,
                    sort = FileSort.Name,
                    order = SortOrder.Asc,
                    cursor = null,
                    limit = 200,
                    includeSizes = false,
                )
            }.onSuccess { listing ->
                _state.update {
                    it.copy(
                        path = path,
                        directories = listing.entries.filter { entry -> entry.isDir }
                            .sortedBy { entry -> entry.name.lowercase() },
                        loading = false,
                        nextCursor = listing.nextCursor,
                        error = null,
                    )
                }
            }.onFailure { error ->
                val missing = error is FileHubApiException && error.statusCode == 404
                if (fallbackToRoot && path.isNotEmpty() && missing) {
                    load(rootId, "", fallbackToRoot = false, requestedPath = "")
                } else {
                    _state.update {
                        it.copy(
                            path = requestedPath,
                            loading = false,
                            error = error.toUserMessage(application),
                        )
                    }
                }
            }
        }
    }

    fun createFolder(name: String) {
        val current = _state.value
        val rootId = current.rootId ?: return
        val profile = sessionRepository.state.value.active ?: return
        val folder = name.trim()
        if (folder.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                filesRepository.mkdir(profile, rootId, current.path, folder)
            }.onSuccess {
                load(rootId, current.path, fallbackToRoot = false, requestedPath = current.path)
            }.onFailure { error ->
                _state.update { it.copy(error = error.toUserMessage(application)) }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val rootId = current.rootId ?: return
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        val profile = sessionRepository.state.value.active ?: return
        _state.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            runCatching {
                filesRepository.list(
                    profile = profile,
                    rootId = rootId,
                    path = current.path,
                    sort = FileSort.Name,
                    order = SortOrder.Asc,
                    cursor = cursor,
                    limit = 200,
                    includeSizes = false,
                )
            }.onSuccess { listing ->
                _state.update {
                    it.copy(
                        directories = (it.directories + listing.entries.filter { entry -> entry.isDir })
                            .distinctBy { entry -> entry.name }
                            .sortedBy { entry -> entry.name.lowercase() },
                        nextCursor = listing.nextCursor,
                        loadingMore = false,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loadingMore = false,
                        error = error.toUserMessage(application),
                    )
                }
            }
        }
    }

    private fun normalize(path: String): String =
        path.trim('/').replace(Regex("/+"), "/")

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as FileHubApplication
                RemoteFolderPickerViewModel(
                    app,
                    app.container.filesRepository,
                    app.container.sessionRepository,
                )
            }
        }
    }
}
