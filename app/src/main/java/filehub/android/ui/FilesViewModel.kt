package filehub.android.ui

import android.app.Application
import android.net.Uri
import coil3.ImageLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileKind
import filehub.android.core.model.FileProperties
import filehub.android.core.model.FileRoot
import filehub.android.core.model.FileSort
import filehub.android.core.model.ServerProfile
import filehub.android.core.model.SortOrder
import filehub.android.data.FilesRepository
import filehub.android.data.ShareRepository
import filehub.android.core.model.ShareCreated
import filehub.android.data.api.DeletePreviewDto
import filehub.android.data.api.TextFileDto
import filehub.android.data.media.MediaPlayerFactory
import filehub.android.data.media.PreviewDownloader
import filehub.android.data.media.ThumbnailLoaderFactory
import filehub.android.data.transfer.TransferRepository
import filehub.android.data.api.FileHubApiException
import filehub.android.data.toUserMessage
import java.io.File
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
    val selected: Set<String> = emptySet(),
    val deletePreview: DeletePreviewDto? = null,
    val operationLoading: Boolean = false,
    val operationError: String? = null,
    val previewEntry: FileEntry? = null,
    val textPreview: TextFileDto? = null,
    val pdfPreviewFile: File? = null,
    val previewLoading: Boolean = false,
    val previewMediaReady: Int = 0,
    val previewError: String? = null,
    val shareEntry: FileEntry? = null,
    val shareCreated: ShareCreated? = null,
    val shareLoading: Boolean = false,
    val shareError: String? = null,
    val properties: FileProperties? = null,
    val propertiesLoading: Boolean = false,
    val propertiesError: String? = null,
) {
    val isEmpty: Boolean get() = !loading && error == null && entries.isEmpty()
    val selectionMode: Boolean get() = selected.isNotEmpty()
}

class FilesViewModel(
    private val repository: FilesRepository,
    private val thumbnailLoaderFactory: ThumbnailLoaderFactory,
    private val mediaPlayerFactory: MediaPlayerFactory,
    private val previewDownloader: PreviewDownloader,
    private val transferRepository: TransferRepository,
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    private val _imageLoader = MutableStateFlow<ImageLoader?>(null)
    val imageLoader: StateFlow<ImageLoader?> = _imageLoader.asStateFlow()

    private var activeToken: String? = null

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
                activeToken = token
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
            showPreview(entry)
            return
        }
        val current = _state.value
        val next = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        navigateTo(next)
    }

    fun showDetails(entry: FileEntry) {
        _state.update { it.copy(selectedEntry = entry) }
    }

    fun toggleSelection(entry: FileEntry) {
        _state.update {
            val next = it.selected.toMutableSet()
            if (!next.add(entry.name)) next.remove(entry.name)
            it.copy(selected = next)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    fun selectAll() {
        _state.update { it.copy(selected = it.entries.map(FileEntry::name).toSet()) }
    }

    fun loadProperties(entry: FileEntry) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        val path = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        _state.update { it.copy(properties = null, propertiesLoading = true, propertiesError = null) }
        viewModelScope.launch {
            runCatching { repository.properties(profile, root.id, path) }
                .onSuccess { properties ->
                    _state.update {
                        it.copy(properties = properties, propertiesLoading = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(propertiesLoading = false, propertiesError = error.toUserMessage())
                    }
                }
        }
    }

    fun clearProperties() {
        _state.update { it.copy(properties = null, propertiesError = null, propertiesLoading = false) }
    }

    fun requestDeleteSelected() {
        val current = _state.value
        requestDelete(current.entries.filter { it.name in current.selected })
    }

    fun requestDelete(entries: List<FileEntry>) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        if (entries.isEmpty()) return
        val paths = entries.map { entry ->
            if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        }
        _state.update {
            it.copy(
                selected = entries.map(FileEntry::name).toSet(),
                operationLoading = true,
                operationError = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.deletePreview(profile, root.id, paths) }
                .onSuccess { preview ->
                    _state.update {
                        it.copy(operationLoading = false, deletePreview = preview, operationError = null)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(operationLoading = false, operationError = error.toUserMessage())
                    }
                }
        }
    }

    fun confirmDelete(confirmCode: String?) {
        val current = _state.value
        val profile = current.profile ?: return
        val preview = current.deletePreview ?: return
        _state.update { it.copy(operationLoading = true, operationError = null) }
        viewModelScope.launch {
            runCatching { repository.deleteConfirm(profile, preview.token, confirmCode) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            operationLoading = false,
                            deletePreview = null,
                            selected = emptySet(),
                            operationError = null,
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(operationLoading = false, operationError = error.toUserMessage())
                    }
                }
        }
    }

    fun dismissDeletePreview() {
        _state.update { it.copy(deletePreview = null, operationError = null) }
    }

    fun createFolder(name: String) {
        mutate { profile, root ->
            repository.mkdir(profile, root.id, _state.value.path, name)
        }
    }

    fun createFile(name: String) {
        mutate { profile, root ->
            repository.touch(profile, root.id, _state.value.path, name)
        }
    }

    fun rename(entry: FileEntry, newName: String) {
        mutate { profile, root ->
            val path = if (_state.value.path.isEmpty()) entry.name else "${_state.value.path}/${entry.name}"
            repository.rename(profile, root.id, path, newName)
        }
    }

    fun showPreview(entry: FileEntry) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        val needsText = entry.kind == FileKind.Text || entry.kind == FileKind.Code
        val needsPdf = entry.kind == FileKind.Pdf
        val needsMedia = entry.kind == FileKind.Video || entry.kind == FileKind.Audio
        _state.update {
            it.copy(
                previewEntry = entry,
                textPreview = null,
                pdfPreviewFile = null,
                previewLoading = needsText || needsPdf || needsMedia,
                previewMediaReady = 0,
                previewError = null,
            )
        }
        val path = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        when {
            needsText -> viewModelScope.launch {
                runCatching { repository.readText(profile, root.id, path) }
                    .onSuccess { text ->
                        _state.update { it.copy(textPreview = text, previewLoading = false) }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(previewLoading = false, previewError = error.toUserMessage())
                        }
                    }
            }
            needsPdf -> viewModelScope.launch {
                val token = ensureToken(profile)
                val url = downloadUrl(entry)
                if (token == null || url == null) {
                    _state.update { it.copy(previewLoading = false, previewError = "设备授权已失效") }
                    return@launch
                }
                runCatching { previewDownloader.download(url, token, entry.name) }
                    .onSuccess { file ->
                        _state.update { it.copy(pdfPreviewFile = file, previewLoading = false) }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(previewLoading = false, previewError = error.toUserMessage())
                        }
                    }
            }
            needsMedia -> viewModelScope.launch {
                val token = ensureToken(profile)
                if (token == null) {
                    _state.update { it.copy(previewLoading = false, previewError = "设备授权已失效") }
                } else {
                    _state.update {
                        it.copy(previewLoading = false, previewMediaReady = it.previewMediaReady + 1)
                    }
                }
            }
        }
    }

    private suspend fun ensureToken(profile: ServerProfile): String? {
        activeToken?.let { return it }
        return repository.tokenFor(profile)?.also { activeToken = it }
    }

    fun closePreview() {
        _state.update {
            it.copy(
                previewEntry = null,
                textPreview = null,
                pdfPreviewFile = null,
                previewError = null,
                previewLoading = false,
            )
        }
    }

    fun clearOperationError() {
        _state.update { it.copy(operationError = null) }
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

    fun openShare(entry: FileEntry) {
        _state.update {
            it.copy(shareEntry = entry, shareCreated = null, shareError = null)
        }
    }

    fun dismissShare() {
        _state.update {
            it.copy(shareEntry = null, shareCreated = null, shareError = null, shareLoading = false)
        }
    }

    fun createShare(password: String?, expiresAt: Long?, readOnly: Boolean) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        val entry = current.shareEntry ?: return
        if (current.shareLoading) return
        val path = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        _state.update { it.copy(shareLoading = true, shareError = null) }
        viewModelScope.launch {
            runCatching {
                shareRepository.create(
                    profile = profile,
                    rootId = root.id,
                    paths = listOf(path),
                    password = password?.takeIf { it.isNotBlank() },
                    expiresAt = expiresAt,
                    readOnly = readOnly,
                )
            }.onSuccess { created ->
                _state.update {
                    it.copy(
                        shareLoading = false,
                        shareCreated = created.copy(
                            url = ShareViewModel.absoluteUrl(profile.baseUrl, created.url),
                        ),
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(shareLoading = false, shareError = error.toUserMessage())
                }
            }
        }
    }

    fun moveEntry(entry: FileEntry, destRootId: Long, destPath: String) {
        transferPaths(listOf(entry.name), destRootId, destPath, move = true)
    }

    fun copyEntry(entry: FileEntry, destRootId: Long, destPath: String) {
        transferPaths(listOf(entry.name), destRootId, destPath, move = false)
    }

    fun moveSelected(destRootId: Long, destPath: String) {
        val current = _state.value
        transferPaths(
            current.entries.filter { it.name in current.selected }.map { it.name },
            destRootId,
            destPath,
            move = true,
        )
    }

    fun copySelected(destRootId: Long, destPath: String) {
        val current = _state.value
        transferPaths(
            current.entries.filter { it.name in current.selected }.map { it.name },
            destRootId,
            destPath,
            move = false,
        )
    }

    fun zipSelected(name: String) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        val names = current.entries.filter { it.name in current.selected }.map { it.name }
        if (names.isEmpty()) return
        _state.update { it.copy(operationLoading = true, operationError = null) }
        viewModelScope.launch {
            runCatching { repository.archiveZip(profile, root.id, current.path, names, name) }
                .onSuccess {
                    _state.update { it.copy(operationLoading = false, selected = emptySet()) }
                    refresh()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(operationLoading = false, operationError = error.toUserMessage())
                    }
                }
        }
    }

    fun unzip(entry: FileEntry) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        val path = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        _state.update { it.copy(operationLoading = true, operationError = null) }
        viewModelScope.launch {
            runCatching { repository.archiveUnzip(profile, root.id, path) }
                .onSuccess { _state.update { it.copy(operationLoading = false) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(operationLoading = false, operationError = error.toUserMessage())
                    }
                }
        }
    }

    private fun transferPaths(
        paths: List<String>,
        destRootId: Long,
        destPath: String,
        move: Boolean,
    ) {
        if (paths.isEmpty()) return
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        _state.update { it.copy(operationLoading = true, operationError = null) }
        viewModelScope.launch {
            runCatching {
                if (move) {
                    repository.move(profile, root.id, paths, destRootId, destPath)
                } else {
                    repository.copy(profile, root.id, paths, destRootId, destPath)
                }
            }.onSuccess {
                _state.update { it.copy(operationLoading = false, selected = emptySet()) }
                refresh()
            }.onFailure { error ->
                _state.update {
                    it.copy(operationLoading = false, operationError = error.toUserMessage())
                }
            }
        }
    }

    fun enqueueUploads(uris: List<Uri>) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uris.forEach { uri ->
                transferRepository.enqueueUpload(profile, root.id, current.path, uri)
            }
        }
    }

    fun enqueueDownload(entry: FileEntry) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        if (entry.isDir) return
        val path = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        viewModelScope.launch {
            transferRepository.enqueueDownload(
                profile = profile,
                rootId = root.id,
                remotePath = path,
                name = entry.name,
                size = entry.size,
                mimeType = entry.mimeType,
            )
        }
    }

    fun downloadSelected() {
        val current = _state.value
        current.entries.filter { it.name in current.selected && !it.isDir }.forEach(::enqueueDownload)
    }

    fun createPlayer(): ExoPlayer? = activeToken?.let(mediaPlayerFactory::create)

    fun downloadUrl(entry: FileEntry, inline: Boolean = true): String? {
        val current = _state.value
        val profile = current.profile ?: return null
        val root = current.root ?: return null
        val base = profile.baseUrl.toHttpUrlOrNull() ?: return null
        val remotePath = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        return base.newBuilder()
            .addPathSegments("api/v1/download")
            .addQueryParameter("root", root.id.toString())
            .addQueryParameter("path", remotePath)
            .addQueryParameter("inline", inline.toString())
            .build()
            .toString()
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

    fun release() {
        loadJob?.cancel()
        val profileId = _state.value.profile?.id
        if (profileId != null) thumbnailLoaderFactory.close(profileId)
        activeToken = null
        _imageLoader.value = null
        _state.value = FileBrowserState()
    }

    private fun mutate(block: suspend (ServerProfile, FileRoot) -> Unit) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        _state.update { it.copy(operationLoading = true, operationError = null) }
        viewModelScope.launch {
            runCatching { block(profile, root) }
                .onSuccess {
                    _state.update { it.copy(operationLoading = false, selected = emptySet()) }
                    refresh()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(operationLoading = false, operationError = error.toUserMessage())
                    }
                }
        }
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
                FilesViewModel(
                    app.container.filesRepository,
                    app.container.thumbnailLoaderFactory,
                    app.container.mediaPlayerFactory,
                    app.container.previewDownloader,
                    app.container.transferRepository,
                    app.container.shareRepository,
                )
            }
        }
    }
}
