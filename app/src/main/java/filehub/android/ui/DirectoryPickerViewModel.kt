package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.R
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileRoot
import filehub.android.core.model.FileSort
import filehub.android.core.model.SortOrder
import filehub.android.data.FilesRepository
import filehub.android.data.SessionRepository
import filehub.android.data.api.FileHubApiException
import filehub.android.data.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DirectoryPickerMode {
    MoveCopy,
    Sync,
}

data class DirectoryPickerState(
    val visible: Boolean = false,
    val roots: List<FileRoot> = emptyList(),
    val rootId: Long? = null,
    val rootAlias: String = "",
    val rootReadOnly: Boolean = false,
    val rootOnline: Boolean? = null,
    val path: String = "",
    val directories: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val checking: Boolean = false,
    val creating: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
    val mode: DirectoryPickerMode = DirectoryPickerMode.Sync,
    val excludeRootId: Long? = null,
    val excludePaths: List<String> = emptyList(),
    val missingPath: String? = null,
    val manualOpen: Boolean = false,
    val manualPath: String = "",
    val pickedRootId: Long? = null,
    val pickedPath: String? = null,
)

class DirectoryPickerViewModel(
    private val application: Application,
    private val filesRepository: FilesRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DirectoryPickerState())
    val state: StateFlow<DirectoryPickerState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun open(
        roots: List<FileRoot>,
        initialRootId: Long?,
        initialPath: String,
        mode: DirectoryPickerMode,
        excludeRootId: Long? = null,
        excludePaths: List<String> = emptyList(),
    ) {
        val root = roots.firstOrNull { it.id == initialRootId } ?: roots.firstOrNull() ?: return
        val path = normalize(initialPath)
        loadJob?.cancel()
        _state.value = DirectoryPickerState(
            visible = true,
            roots = roots,
            rootId = root.id,
            rootAlias = root.alias,
            rootReadOnly = root.readOnly,
            rootOnline = root.online,
            path = path,
            loading = true,
            mode = mode,
            excludeRootId = excludeRootId,
            excludePaths = excludePaths.map(::normalize).filter { it.isNotEmpty() },
            manualPath = path,
        )
        load(root.id, path)
    }

    fun close() {
        loadJob?.cancel()
        _state.value = DirectoryPickerState()
    }

    fun switchRoot(rootId: Long) {
        val current = _state.value
        val root = current.roots.firstOrNull { it.id == rootId } ?: return
        if (root.id == current.rootId) return
        loadJob?.cancel()
        _state.update {
            it.copy(
                rootId = root.id,
                rootAlias = root.alias,
                rootReadOnly = root.readOnly,
                rootOnline = root.online,
                path = "",
                manualPath = "",
                directories = emptyList(),
                nextCursor = null,
                loading = true,
                error = null,
                missingPath = null,
            )
        }
        load(root.id, "")
    }

    fun navigate(path: String) {
        val normalized = normalizeOrNull(path)
        if (normalized == null) {
            _state.update { it.copy(error = application.getString(R.string.directory_picker_invalid_path)) }
            return
        }
        if (isExcluded(normalized)) {
            _state.update { it.copy(error = application.getString(R.string.directory_picker_self_target)) }
            return
        }
        val rootId = _state.value.rootId ?: return
        loadJob?.cancel()
        _state.update {
            it.copy(
                path = normalized,
                manualPath = normalized,
                directories = emptyList(),
                nextCursor = null,
                loading = true,
                error = null,
                missingPath = null,
            )
        }
        load(rootId, normalized)
    }

    fun navigateManual() {
        navigate(_state.value.manualPath)
        _state.update { it.copy(manualOpen = false) }
    }

    fun setManualPath(value: String) {
        _state.update { it.copy(manualPath = value) }
    }

    fun toggleManual() {
        _state.update { it.copy(manualOpen = !it.manualOpen, manualPath = it.path) }
    }

    fun up() {
        val path = _state.value.path
        if (path.isEmpty()) return
        navigate(path.substringBeforeLast('/', ""))
    }

    fun refresh() {
        val current = _state.value
        val rootId = current.rootId ?: return
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null, missingPath = null) }
        load(rootId, current.path)
    }

    fun loadMore() {
        val current = _state.value
        val rootId = current.rootId ?: return
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        val profile = sessionRepository.state.value.active ?: return
        _state.update { it.copy(loadingMore = true) }
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
                        directories = mergeDirectories(it.directories, listing.entries),
                        nextCursor = listing.nextCursor,
                        loadingMore = false,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(loadingMore = false, error = error.toUserMessage(application))
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
        if (current.rootReadOnly) {
            _state.update { it.copy(error = application.getString(R.string.directory_picker_read_only)) }
            return
        }
        viewModelScope.launch {
            runCatching { filesRepository.mkdir(profile, rootId, current.path, folder) }
                .onSuccess {
                    val path = if (current.path.isEmpty()) folder else "${current.path}/$folder"
                    _state.update { it.copy(manualPath = path) }
                    navigate(path)
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.toUserMessage(application)) }
                }
        }
    }

    /**
     * Checks the typed/current target before returning it. A 404 turns into the
     * missing-directory confirmation; any other failure is shown in the picker.
     */
    fun confirm(path: String = _state.value.path) {
        val current = _state.value
        val rootId = current.rootId ?: return
        val normalized = normalizeOrNull(path) ?: run {
            _state.update { it.copy(error = application.getString(R.string.directory_picker_invalid_path)) }
            return
        }
        if (isExcluded(normalized)) {
            _state.update { it.copy(error = application.getString(R.string.directory_picker_self_target)) }
            return
        }
        if (current.rootReadOnly) {
            _state.update { it.copy(error = application.getString(R.string.directory_picker_read_only)) }
            return
        }
        val profile = sessionRepository.state.value.active ?: return
        _state.update { it.copy(checking = true, error = null, missingPath = null) }
        viewModelScope.launch {
            runCatching { filesRepository.properties(profile, rootId, normalized) }
                .onSuccess { properties ->
                    if (properties.isDir) {
                        _state.update {
                            it.copy(
                                checking = false,
                                pickedRootId = rootId,
                                pickedPath = normalized,
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                checking = false,
                                error = application.getString(R.string.directory_picker_file_conflict),
                            )
                        }
                    }
                }
                .onFailure { error ->
                    val missing = isMissingPath(error)
                    _state.update {
                        it.copy(
                            checking = false,
                            missingPath = if (missing) normalized else null,
                            error = if (missing) null else error.toUserMessage(application),
                        )
                    }
                }
        }
    }

    fun createMissingAndPick() {
        val current = _state.value
        val rootId = current.rootId ?: return
        val path = current.missingPath ?: return
        val profile = sessionRepository.state.value.active ?: return
        if (current.rootReadOnly) {
            _state.update {
                it.copy(
                    missingPath = null,
                    error = application.getString(R.string.directory_picker_read_only),
                )
            }
            return
        }
        _state.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            runCatching { filesRepository.ensureDirectory(profile, rootId, path) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            creating = false,
                            missingPath = null,
                            pickedRootId = rootId,
                            pickedPath = path,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(creating = false, error = error.toUserMessage(application))
                    }
                }
        }
    }

    fun dismissMissing() {
        _state.update { it.copy(missingPath = null) }
    }

    private fun load(rootId: Long, path: String) {
        val profile = sessionRepository.state.value.active ?: run {
            _state.update {
                it.copy(
                    loading = false,
                    error = application.getString(R.string.error_unauthorized),
                )
            }
            return
        }
        val requested = path
        loadJob = viewModelScope.launch {
            runCatching {
                filesRepository.list(
                    profile = profile,
                    rootId = rootId,
                    path = requested,
                    sort = FileSort.Name,
                    order = SortOrder.Asc,
                    cursor = null,
                    limit = 200,
                    includeSizes = false,
                )
            }.onSuccess { listing ->
                if (_state.value.rootId != rootId) return@onSuccess
                _state.update {
                    it.copy(
                        path = requested,
                        manualPath = requested,
                        directories = mergeDirectories(emptyList(), listing.entries),
                        loading = false,
                        nextCursor = listing.nextCursor,
                        error = null,
                        missingPath = null,
                    )
                }
            }.onFailure { error ->
                if (_state.value.rootId != rootId) return@onFailure
                val missing = isMissingPath(error)
                _state.update {
                    it.copy(
                        loading = false,
                        directories = emptyList(),
                        nextCursor = null,
                        error = if (missing) null else error.toUserMessage(application),
                        missingPath = if (missing && requested.isNotEmpty()) requested else null,
                    )
                }
            }
        }
    }

    /**
     * FileHub answers "parent directory does not exist" with INVALID_PATH
     * (400) instead of 404 when more than the last segment is missing.
     */
    private fun isMissingPath(error: Throwable): Boolean {
        if (error !is FileHubApiException) return false
        if (error.statusCode == 404) return true
        return error.code == "INVALID_PATH" &&
            error.message.contains("parent directory does not exist", ignoreCase = true)
    }

    private fun mergeDirectories(
        existing: List<FileEntry>,
        incoming: List<FileEntry>,
    ): List<FileEntry> = (existing + incoming.filter { it.isDir })
        .distinctBy { it.name }
        .sortedBy { it.name.lowercase() }

    private fun isExcluded(path: String): Boolean {
        val current = _state.value
        if (current.excludeRootId != current.rootId) return false
        if (path.isEmpty()) return false
        return current.excludePaths.any { excluded ->
            path == excluded || path.startsWith("$excluded/")
        }
    }

    private fun normalize(path: String): String = normalizeOrNull(path) ?: ""

    private fun normalizeOrNull(path: String): String? {
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.any { it == ".." || it == "." }) return null
        return segments.joinToString("/")
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as FileHubApplication
                DirectoryPickerViewModel(
                    app,
                    app.container.filesRepository,
                    app.container.sessionRepository,
                )
            }
        }
    }
}
