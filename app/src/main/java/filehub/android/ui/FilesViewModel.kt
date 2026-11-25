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
import filehub.android.R
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileKind
import filehub.android.core.model.FileProperties
import filehub.android.core.model.FileRoot
import filehub.android.core.model.FileSort
import filehub.android.core.model.ServerProfile
import filehub.android.core.model.SortOrder
import filehub.android.core.model.BrowserViewMode
import filehub.android.data.BrowserState
import filehub.android.data.BrowserStateRepository
import filehub.android.data.DirectoryCacheRepository
import filehub.android.data.FilesRepository
import filehub.android.data.ShareRepository
import filehub.android.core.model.ShareCreated
import filehub.android.data.api.DeletePreviewDto
import filehub.android.data.api.TextFileDto
import filehub.android.data.media.MediaPlayerFactory
import filehub.android.data.media.PreviewDownloader
import filehub.android.data.api.AudioInfoDto
import filehub.android.data.api.ThumbStatusDto
import filehub.android.data.playback.AudioPlayerController
import filehub.android.data.playback.AudioTrack
import filehub.android.data.media.ThumbnailLoaderFactory
import filehub.android.data.transfer.TransferKind
import filehub.android.data.transfer.TransferRepository
import filehub.android.data.transfer.TransferStatus
import filehub.android.data.api.FileHubApiException
import filehub.android.data.events.EventStreamClient
import filehub.android.data.events.JobUpdatedPayload
import filehub.android.data.events.ServerEventNames
import filehub.android.data.events.decodePayload
import filehub.android.data.toUserMessage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The server resolves move/copy paths relative to the source root. */
internal fun resolveTransferPaths(currentPath: String, names: List<String>): List<String> =
    names.map { name -> if (currentPath.isEmpty()) name else "$currentPath/$name" }

sealed interface DirSizeState {
    data object Loading : DirSizeState
    data class Ready(val size: Long, val partial: Boolean) : DirSizeState
    data object Failed : DirSizeState
}

data class AudioPickerState(
    val entry: FileEntry,
    val info: AudioInfoDto,
)

data class ScrollRestore(
    val view: BrowserViewMode,
    val index: Int,
    val offset: Int,
)

data class UnsupportedPreview(
    val entry: FileEntry,
    val message: String? = null,
    val allowText: Boolean = false,
)

data class ActiveJob(
    val id: String,
    val label: String,
    val status: String,
    val progress: Int,
    val total: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
)

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
    val view: BrowserViewMode = BrowserViewMode.List,
    val restore: ScrollRestore? = null,
    val error: String? = null,
    val loginRequired: Boolean = false,
    val selectedEntry: FileEntry? = null,
    val selected: Set<String> = emptySet(),
    val deletePreview: DeletePreviewDto? = null,
    val operationLoading: Boolean = false,
    val operationError: String? = null,
    val previewEntry: FileEntry? = null,
    val previewImages: List<FileEntry> = emptyList(),
    val previewImagesPinned: Boolean = false,
    val previewSession: Long = 0,
    val previewAsText: Boolean = false,
    val textPreview: TextFileDto? = null,
    val previewEncoding: String? = null,
    val unsupportedPreview: UnsupportedPreview? = null,
    val pdfPreviewFile: File? = null,
    val previewLoading: Boolean = false,
    val previewMediaReady: Int = 0,
    val previewError: String? = null,
    val shareEntries: List<FileEntry> = emptyList(),
    val shareCreated: ShareCreated? = null,
    val shareHasPassword: Boolean = false,
    val shareLoading: Boolean = false,
    val shareError: String? = null,
    val properties: FileProperties? = null,
    val propertiesLoading: Boolean = false,
    val propertiesError: String? = null,
    val dirSizes: Map<String, DirSizeState> = emptyMap(),
    val activeJobs: List<ActiveJob> = emptyList(),
    val audioPicker: AudioPickerState? = null,
    val thumbVersions: Map<String, Int> = emptyMap(),
    val thumbStatus: ThumbStatusDto? = null,
) {
    val isEmpty: Boolean get() = !loading && error == null && entries.isEmpty()
    val selectionMode: Boolean get() = selected.isNotEmpty()
    val offline: Boolean
        get() = root?.let { it.isRemote && it.online == false } == true
}

class FilesViewModel(
    private val application: Application,
    private val repository: FilesRepository,
    private val thumbnailLoaderFactory: ThumbnailLoaderFactory,
    private val mediaPlayerFactory: MediaPlayerFactory,
    private val previewDownloader: PreviewDownloader,
    private val transferRepository: TransferRepository,
    private val shareRepository: ShareRepository,
    private val sessionRepository: filehub.android.data.SessionRepository,
    private val directoryCache: DirectoryCacheRepository,
    private val browserStateRepository: BrowserStateRepository,
    private val audioPlayerController: AudioPlayerController,
    private val eventStreamClient: EventStreamClient,
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    private val _imageLoader = MutableStateFlow<ImageLoader?>(null)
    val imageLoader: StateFlow<ImageLoader?> = _imageLoader.asStateFlow()

    private var activeToken: String? = null

    private var loadJob: Job? = null
    private var navJob: Job? = null
    private var persistScrollJob: Job? = null
    private var uploadRefreshJob: Job? = null
    private var jobCompensationJob: Job? = null
    private val finishingJobs = mutableSetOf<String>()
    private val dirSizeJobs = mutableMapOf<String, Job>()
    private val dirSizeLimit = kotlinx.coroutines.sync.Semaphore(3)
    private val seenCompletedUploads = mutableSetOf<String>()
    private var uploadsPrimed = false

    private var listIndex = 0
    private var listOffset = 0
    private var gridIndex = 0
    private var gridOffset = 0

    init {
        viewModelScope.launch {
            transferRepository.items.collect { items ->
                val completed = items.filter {
                    it.kind == TransferKind.UPLOAD && it.status == TransferStatus.COMPLETED
                }
                if (!uploadsPrimed) {
                    seenCompletedUploads += completed.map { it.id }
                    uploadsPrimed = true
                    return@collect
                }
                val fresh = completed.filter { it.id !in seenCompletedUploads }
                if (fresh.isEmpty()) return@collect
                seenCompletedUploads += fresh.map { it.id }
                val current = _state.value
                val affectsCurrent = fresh.any { item ->
                    item.profileId == current.profile?.id &&
                        item.rootId == current.root?.id &&
                        item.remoteDir == current.path
                }
                if (affectsCurrent) scheduleUploadRefresh()
            }
        }
        viewModelScope.launch {
            sessionRepository.state.collect { session ->
                val profile = _state.value.profile ?: return@collect
                if (session.active?.id != profile.id || session.rootsLoading) return@collect
                val root = _state.value.root ?: return@collect
                val refreshed = session.roots.firstOrNull { it.id == root.id }
                if (refreshed == null) {
                    clear()
                } else if (refreshed != root) {
                    _state.update { it.copy(root = refreshed) }
                }
            }
        }
        viewModelScope.launch {
            eventStreamClient.events.collect { event ->
                val current = _state.value
                val profileId = current.profile?.id ?: return@collect
                if (event.profileId != profileId) return@collect
                when (event.name) {
                    ServerEventNames.JOB_UPDATED -> {
                        val payload = event.data
                            .decodePayload(JobUpdatedPayload.serializer())
                            ?: return@collect
                        applyJobUpdate(
                            jobId = payload.id,
                            status = payload.status,
                            progress = payload.progress,
                            total = payload.total,
                            error = payload.error,
                        )
                    }
                    ServerEventNames.RESYNC -> {
                        if (current.root != null) refresh()
                        compensateActiveJobs()
                    }
                }
            }
        }
    }

    private fun scheduleUploadRefresh() {
        uploadRefreshJob?.cancel()
        uploadRefreshJob = viewModelScope.launch {
            delay(400)
            refresh()
        }
    }

    fun open(profile: ServerProfile, root: FileRoot, path: String = "") {
        val normalized = normalizePath(path)
        persistCurrentState()
        loadJob?.cancel()
        navJob?.cancel()
        cancelDirSizes()
        resetScrollPosition()
        _state.value = FileBrowserState(
            profile = profile,
            root = root,
            path = normalized,
            loading = true,
        )
        viewModelScope.launch {
            repository.tokenFor(profile)?.let { token ->
                activeToken = token
                _imageLoader.value = thumbnailLoaderFactory.get(profile.id, token)
            }
        }
        navJob = viewModelScope.launch {
            val saved = browserStateRepository.get(profile.id, root.id, normalized)
                ?: BrowserState.Default
            val current = _state.value
            if (current.root?.id != root.id || current.path != normalized) return@launch
            applyBrowserState(saved)
            load(path = normalized, append = false)
        }
    }

    /**
     * Shows a cached directory level immediately, without a network round trip.
     * Column navigation uses this when the user steps back to an ancestor.
     */
    fun restoreColumn(
        profile: ServerProfile,
        root: FileRoot,
        path: String,
        entries: List<FileEntry>,
        nextCursor: String? = null,
        sort: FileSort,
        order: SortOrder,
        view: BrowserViewMode,
    ) {
        val normalized = normalizePath(path)
        persistCurrentState()
        loadJob?.cancel()
        navJob?.cancel()
        cancelDirSizes()
        resetScrollPosition()
        _state.value = FileBrowserState(
            profile = profile,
            root = root,
            path = normalized,
            entries = entries,
            loading = false,
            nextCursor = nextCursor,
            readOnly = root.readOnly,
            sort = sort,
            order = order,
            view = view,
        )
        viewModelScope.launch {
            repository.tokenFor(profile)?.let { token ->
                activeToken = token
                _imageLoader.value = thumbnailLoaderFactory.get(profile.id, token)
            }
        }
    }

    fun dirSizeState(entry: FileEntry): DirSizeState? = _state.value.dirSizes[dirSizeKey(entry)]

    fun requestDirSize(entry: FileEntry) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        if (!entry.isDir) return
        val key = dirSizeKey(entry)
        if (current.dirSizes[key] != null) return
        _state.update { it.copy(dirSizes = it.dirSizes + (key to DirSizeState.Loading)) }
        val path = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        dirSizeJobs[key] = viewModelScope.launch {
            var state: DirSizeState = DirSizeState.Failed
            var polls = 0
            while (true) {
                val result = dirSizeLimit.withPermit {
                    kotlinx.coroutines.withTimeoutOrNull(15_000) {
                        runCatching { repository.dirSize(profile, root.id, path) }.getOrNull()
                    }
                }
                if (result == null) break
                if (result.status.isEmpty() || result.status == "ready") {
                    state = DirSizeState.Ready(result.size ?: 0L, result.partial || result.error != null)
                    break
                }
                if (result.status == "error") break
                // Local roots answer "queued"/"running" while the server scans
                // the directory; keep polling until the cached value is ready.
                if (polls >= DIR_SIZE_MAX_POLLS) break
                polls++
                delay(if (polls <= 5) 1_000L else 2_000L)
            }
            _state.update { it.copy(dirSizes = it.dirSizes + (key to state)) }
            dirSizeJobs.remove(key)
        }
    }

    fun retryDirSize(entry: FileEntry) {
        _state.update { it.copy(dirSizes = it.dirSizes - dirSizeKey(entry)) }
        requestDirSize(entry)
    }

    private fun dirSizeKey(entry: FileEntry): String {
        val current = _state.value
        return "${current.root?.id ?: 0}:${current.path}:${entry.name}:${entry.modified}"
    }

    private fun cancelDirSizes() {
        dirSizeJobs.values.forEach { it.cancel() }
        dirSizeJobs.clear()
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
        val profile = _state.value.profile ?: return
        val root = _state.value.root ?: return
        persistCurrentState()
        loadJob?.cancel()
        navJob?.cancel()
        cancelDirSizes()
        resetScrollPosition()
        _state.update {
            it.copy(
                path = normalized,
                entries = emptyList(),
                nextCursor = null,
                loading = true,
                error = null,
                operationError = null,
                restore = null,
            )
        }
        navJob = viewModelScope.launch {
            val saved = browserStateRepository.get(profile.id, root.id, normalized)
                ?: BrowserState.Default
            val current = _state.value
            if (current.root?.id != root.id || current.path != normalized) return@launch
            applyBrowserState(saved)
            load(path = normalized, append = false)
        }
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
        _state.update {
            it.copy(
                selectedEntry = entry,
                thumbStatus = null,
                properties = null,
                propertiesError = null,
            )
        }
        if (entry.isDir && entry.size <= 0) requestDirSize(entry)
        if (entry.kind == FileKind.Image) loadProperties(entry)
        if (entry.hasPreview && (entry.kind == FileKind.Image || entry.kind == FileKind.Video)) {
            val current = _state.value
            val profile = current.profile ?: return
            val root = current.root ?: return
            val path = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
            viewModelScope.launch {
                runCatching { repository.thumbStatus(profile, root.id, path) }
                    .onSuccess { status -> _state.update { it.copy(thumbStatus = status) } }
            }
        }
    }

    fun dismissSelected() {
        _state.update { it.copy(selectedEntry = null, thumbStatus = null) }
    }

    fun retryThumbnail(entry: FileEntry) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        val path = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        val key = "$path:${entry.modified}"
        viewModelScope.launch {
            runCatching { repository.retryThumb(profile, root.id, path) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            thumbVersions = it.thumbVersions +
                                (key to (it.thumbVersions[key] ?: 0) + 1),
                            thumbStatus = ThumbStatusDto(state = "missing"),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(operationError = error.toUserMessage(application))
                    }
                }
        }
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
                        it.copy(propertiesLoading = false, propertiesError = error.toUserMessage(application))
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
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(operationLoading = false, operationError = error.toUserMessage(application))
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
                        it.copy(operationLoading = false, operationError = error.toUserMessage(application))
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

    fun showPreview(entry: FileEntry, pinImages: Boolean = false) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        if (!canPreview(entry)) {
            _state.update {
                it.copy(
                    unsupportedPreview = UnsupportedPreview(
                        entry = entry,
                        allowText = entry.kind == FileKind.File,
                    ),
                )
            }
            return
        }
        val needsText = entry.kind == FileKind.Text ||
            entry.kind == FileKind.Code ||
            entry.isMarkdown
        val needsPdf = entry.kind == FileKind.Pdf
        val needsMedia = entry.kind == FileKind.Video || entry.kind == FileKind.Audio
        val images = when {
            entry.kind != FileKind.Image -> emptyList()
            pinImages -> listOf(entry)
            else -> current.entries.filter { it.kind == FileKind.Image }
                .ifEmpty { listOf(entry) }
        }
        _state.update {
            it.copy(
                previewEntry = entry,
                previewImages = images,
                previewImagesPinned = pinImages,
                previewSession = it.previewSession + 1,
                previewAsText = false,
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
                        _state.update {
                            it.copy(
                                textPreview = text,
                                previewEncoding = null,
                                previewLoading = false,
                            )
                        }
                    }
                    .onFailure { error ->
                        showUnsupported(entry, error.toUserMessage(application))
                    }
            }
            needsPdf -> viewModelScope.launch {
                val token = ensureToken(profile)
                val url = downloadUrl(entry)
                if (token == null || url == null) {
                    _state.update { it.copy(previewLoading = false, previewError = application.getString(R.string.error_unauthorized)) }
                    return@launch
                }
                runCatching { previewDownloader.download(url, token, entry.name) }
                    .onSuccess { file ->
                        _state.update { it.copy(pdfPreviewFile = file, previewLoading = false) }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(previewLoading = false, previewError = error.toUserMessage(application))
                        }
                    }
            }
            needsMedia -> viewModelScope.launch {
                val token = ensureToken(profile)
                if (token == null) {
                    _state.update { it.copy(previewLoading = false, previewError = application.getString(R.string.error_unauthorized)) }
                } else {
                    _state.update {
                        it.copy(previewLoading = false, previewMediaReady = it.previewMediaReady + 1)
                    }
                }
            }
        }
    }

    fun openAsText(entry: FileEntry) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        val path = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        _state.update {
            it.copy(
                previewEntry = entry,
                previewImages = emptyList(),
                previewImagesPinned = true,
                previewSession = it.previewSession + 1,
                previewAsText = true,
                textPreview = null,
                pdfPreviewFile = null,
                previewLoading = true,
                previewMediaReady = 0,
                previewError = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.probeText(profile, root.id, path) }
                .onSuccess { probe ->
                    when {
                        !probe.isText -> showUnsupported(entry, probeErrorMessage(probe.reason))
                        probe.size > MAX_TEXT_EDIT_BYTES ->
                            showUnsupported(entry, application.getString(R.string.text_too_large))
                        else -> runCatching {
                            repository.readText(profile, root.id, path)
                        }.onSuccess { text ->
                            _state.update {
                                it.copy(textPreview = text, previewLoading = false)
                            }
                        }.onFailure { error ->
                            showUnsupported(entry, error.toUserMessage(application))
                        }
                    }
                }
                .onFailure { error ->
                    val message = if (error is FileHubApiException && error.statusCode == 404) {
                        application.getString(R.string.text_editor_disabled)
                    } else {
                        error.toUserMessage(application)
                    }
                    showUnsupported(entry, message)
                }
        }
    }

    fun reloadPreviewText(encoding: String?) {
        val current = _state.value
        val entry = current.previewEntry ?: return
        val profile = current.profile ?: return
        val root = current.root ?: return
        val path = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        _state.update {
            it.copy(previewEncoding = encoding, previewLoading = true, previewError = null)
        }
        viewModelScope.launch {
            runCatching { repository.readText(profile, root.id, path, encoding) }
                .onSuccess { text ->
                    _state.update { it.copy(textPreview = text, previewLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            previewLoading = false,
                            previewError = error.toUserMessage(application),
                        )
                    }
                }
        }
    }

    fun dismissUnsupported() {
        _state.update { it.copy(unsupportedPreview = null) }
    }

    private fun showUnsupported(entry: FileEntry, message: String?) {
        _state.update {
            it.copy(
                previewEntry = null,
                previewImages = emptyList(),
                textPreview = null,
                previewLoading = false,
                previewError = null,
                unsupportedPreview = UnsupportedPreview(entry, message),
            )
        }
    }

    private fun canPreview(entry: FileEntry): Boolean =
        entry.isMarkdown || when (entry.kind) {
            FileKind.Image,
            FileKind.Video,
            FileKind.Audio,
            FileKind.Pdf,
            FileKind.Text,
            FileKind.Code,
            -> true
            else -> false
        }

    private fun probeErrorMessage(reason: String): String = when (reason) {
        "too_large" -> application.getString(R.string.text_too_large)
        "binary" -> application.getString(R.string.text_not_text)
        "lossy" -> application.getString(R.string.text_lossy_hint)
        "not_utf8" -> application.getString(R.string.text_encoding_unsupported)
        else -> application.getString(R.string.text_not_text)
    }

    fun previewPage(entry: FileEntry) {
        _state.update { it.copy(previewEntry = entry) }
    }

    private suspend fun ensureToken(profile: ServerProfile): String? {
        activeToken?.let { return it }
        return repository.tokenFor(profile)?.also { activeToken = it }
    }

    fun closePreview() {
        _state.update {
            it.copy(
                previewEntry = null,
                previewImages = emptyList(),
                previewImagesPinned = false,
                previewAsText = false,
                textPreview = null,
                previewEncoding = null,
                pdfPreviewFile = null,
                previewError = null,
                previewLoading = false,
            )
        }
    }

    fun clearOperationError() {
        _state.update { it.copy(operationError = null) }
    }

    fun updateProfile(profile: ServerProfile) {
        val current = _state.value
        if (current.profile?.id != profile.id) return
        _state.update { it.copy(profile = profile) }
    }

    /**
     * Error-banner retry: re-probe every configured address before reloading.
     * A plain refresh would keep using the stale, unreachable base URL.
     */
    fun retry() {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        if (current.loading) return
        _state.update { it.copy(loading = true, error = null, loginRequired = false) }
        viewModelScope.launch {
            runCatching { sessionRepository.connect(profile.id) }
                .onSuccess {
                    val updated = sessionRepository.state.value.active ?: profile
                    _state.update { it.copy(profile = updated) }
                    repository.tokenFor(updated)?.let { token ->
                        activeToken = token
                        _imageLoader.value = thumbnailLoaderFactory.get(updated.id, token)
                    }
                    load(path = current.path, append = false)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.toUserMessage(application),
                        )
                    }
                }
        }
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
        val next = if (sort == current.sort) {
            current.copy(order = if (current.order == SortOrder.Asc) SortOrder.Desc else SortOrder.Asc)
        } else {
            current.copy(sort = sort, order = SortOrder.Asc)
        }
        listIndex = 0
        listOffset = 0
        gridIndex = 0
        gridOffset = 0
        _state.update {
            it.copy(
                sort = next.sort,
                order = next.order,
                restore = ScrollRestore(it.view, 0, 0),
            )
        }
        persistCurrentState()
        refresh()
    }

    fun setView(view: BrowserViewMode) {
        val current = _state.value
        if (current.view == view) return
        val target = if (view == BrowserViewMode.List) {
            ScrollRestore(view, listIndex, listOffset)
        } else {
            ScrollRestore(view, gridIndex, gridOffset)
        }
        _state.update { it.copy(view = view, restore = target) }
        persistCurrentState()
    }

    fun consumeRestore() {
        _state.update { it.copy(restore = null) }
    }

    fun reportScroll(
        path: String,
        view: BrowserViewMode,
        index: Int,
        offset: Int,
        persist: Boolean,
    ) {
        val current = _state.value
        if (current.path != path || current.restore != null) return
        if (view == BrowserViewMode.List) {
            listIndex = index
            listOffset = offset
        } else {
            gridIndex = index
            gridOffset = offset
        }
        if (!persist) return
        persistScrollJob?.cancel()
        persistScrollJob = viewModelScope.launch {
            delay(500)
            persistCurrentState()
        }
    }

    private fun resetScrollPosition() {
        listIndex = 0
        listOffset = 0
        gridIndex = 0
        gridOffset = 0
    }

    private fun applyBrowserState(saved: BrowserState) {
        listIndex = saved.listIndex
        listOffset = saved.listOffset
        gridIndex = saved.gridIndex
        gridOffset = saved.gridOffset
        val restore = if (saved.view == BrowserViewMode.List) {
            ScrollRestore(saved.view, saved.listIndex, saved.listOffset)
        } else {
            ScrollRestore(saved.view, saved.gridIndex, saved.gridOffset)
        }
        _state.update {
            it.copy(
                sort = saved.sort,
                order = saved.order,
                view = saved.view,
                restore = restore,
            )
        }
    }

    private fun persistCurrentState() {
        persistScrollJob?.cancel()
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        val snapshot = BrowserState(
            sort = current.sort,
            order = current.order,
            view = current.view,
            listIndex = listIndex,
            listOffset = listOffset,
            gridIndex = gridIndex,
            gridOffset = gridOffset,
        )
        viewModelScope.launch {
            browserStateRepository.put(profile.id, root.id, current.path, snapshot)
        }
    }

    fun openShare(entry: FileEntry) {
        openShare(listOf(entry))
    }

    fun openShareSelection() {
        val selected = _state.value.entries.filter { it.name in _state.value.selected }
        openShare(selected)
    }

    private fun openShare(entries: List<FileEntry>) {
        if (entries.isEmpty()) return
        _state.update {
            it.copy(
                shareEntries = entries,
                shareCreated = null,
                shareHasPassword = false,
                shareError = null,
            )
        }
    }

    fun dismissShare() {
        _state.update {
            it.copy(
                shareEntries = emptyList(),
                shareCreated = null,
                shareHasPassword = false,
                shareError = null,
                shareLoading = false,
            )
        }
    }

    fun createShare(password: String?, expiresAt: Long?, readOnly: Boolean) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        val entries = current.shareEntries
        if (entries.isEmpty() || current.shareLoading) return
        val collection = entries.size > 1
        val paths = entries.map { entry ->
            if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        }
        _state.update {
            it.copy(
                shareLoading = true,
                shareError = null,
                shareHasPassword = !password.isNullOrBlank(),
            )
        }
        viewModelScope.launch {
            runCatching {
                shareRepository.create(
                    profile = profile,
                    rootId = root.id,
                    paths = paths,
                    password = password?.takeIf { it.isNotBlank() },
                    expiresAt = expiresAt,
                    // A collection share is read-only on the server regardless
                    // of this flag; send the effective value to keep them in sync.
                    readOnly = if (collection) true else readOnly,
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
                    it.copy(shareLoading = false, shareError = error.toUserMessage(application))
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

    fun moveEntries(entries: List<FileEntry>, destRootId: Long, destPath: String) {
        transferPaths(entries.map { it.name }, destRootId, destPath, move = true)
    }

    fun copyEntries(entries: List<FileEntry>, destRootId: Long, destPath: String) {
        transferPaths(entries.map { it.name }, destRootId, destPath, move = false)
    }

    fun zipEntries(entries: List<FileEntry>, name: String) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        val names = entries.map { it.name }
        if (names.isEmpty()) return
        _state.update { it.copy(operationLoading = true, operationError = null) }
        viewModelScope.launch {
            runCatching { repository.archiveZip(profile, root.id, current.path, names, name) }
                .onSuccess { job ->
                    _state.update { it.copy(operationLoading = false, selected = emptySet()) }
                    trackJob(profile, job.jobId, name)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            operationLoading = false,
                            operationError = error.toUserMessage(application),
                        )
                    }
                }
        }
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
                .onSuccess { job ->
                    _state.update { it.copy(operationLoading = false, selected = emptySet()) }
                    trackJob(profile, job.jobId, name)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(operationLoading = false, operationError = error.toUserMessage(application))
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
                .onSuccess { job ->
                    _state.update { it.copy(operationLoading = false) }
                    trackJob(profile, job.jobId, entry.name)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(operationLoading = false, operationError = error.toUserMessage(application))
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
        val fullPaths = resolveTransferPaths(current.path, paths)
        _state.update { it.copy(operationLoading = true, operationError = null) }
        viewModelScope.launch {
            runCatching {
                if (move) {
                    repository.move(profile, root.id, fullPaths, destRootId, destPath)
                } else {
                    repository.copy(profile, root.id, fullPaths, destRootId, destPath)
                }
            }.onSuccess { job ->
                _state.update { it.copy(operationLoading = false, selected = emptySet()) }
                trackJob(
                    profile = profile,
                    jobId = job.jobId,
                    label = fullPaths.firstOrNull() ?: "",
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(operationLoading = false, operationError = error.toUserMessage(application))
                }
            }
        }
    }

    private fun trackJob(
        profile: ServerProfile,
        jobId: String,
        label: String,
    ) {
        if (jobId.isBlank()) {
            refresh()
            return
        }
        val initial = ActiveJob(
            id = jobId,
            label = label,
            status = "queued",
            progress = 0,
            total = 0,
            bytesDone = 0,
            bytesTotal = 0,
        )
        _state.update { it.copy(activeJobs = it.activeJobs + initial) }
        viewModelScope.launch {
            var wasConnected = false
            while (true) {
                if (_state.value.activeJobs.none { it.id == jobId }) break
                val connected = eventStreamClient.isConnected(profile.id)
                if (connected && !wasConnected) {
                    wasConnected = true
                } else {
                    delay(if (connected) JOB_POLL_CONNECTED_MS else JOB_POLL_FALLBACK_MS)
                }
                if (_state.value.activeJobs.none { it.id == jobId }) break
                if (!pollJobOnce(profile, jobId)) break
            }
        }
    }

    private suspend fun pollJobOnce(profile: ServerProfile, jobId: String): Boolean {
        val job = runCatching { repository.jobStatus(profile, jobId) }.getOrNull()
        if (job == null) {
            if (_state.value.activeJobs.any { it.id == jobId }) {
                _state.update {
                    it.copy(
                        activeJobs = it.activeJobs.filterNot { item -> item.id == jobId },
                        operationError = application.getString(R.string.operation_error),
                    )
                }
            }
            return false
        }
        if (_state.value.activeJobs.none { it.id == jobId }) return false
        applyJobUpdate(
            jobId = job.id,
            status = job.status,
            progress = job.progress,
            total = job.total,
            bytesDone = job.bytesDone,
            bytesTotal = job.bytesTotal,
            error = job.error,
        )
        return !isTerminalJobStatus(job.status)
    }

    private fun applyJobUpdate(
        jobId: String,
        status: String,
        progress: Int,
        total: Int,
        bytesDone: Long? = null,
        bytesTotal: Long? = null,
        error: String? = null,
    ) {
        val existing = _state.value.activeJobs.firstOrNull { it.id == jobId } ?: return
        val updated = mergeJobUpdate(
            existing = existing,
            status = status,
            progress = progress,
            total = total,
            bytesDone = bytesDone,
            bytesTotal = bytesTotal,
        )
        _state.update {
            it.copy(
                activeJobs = it.activeJobs.map { item ->
                    if (item.id == jobId) updated else item
                },
            )
        }
        if (isTerminalJobStatus(updated.status)) finishJob(jobId, updated.status, error)
    }

    private fun finishJob(jobId: String, status: String, error: String?) {
        if (!finishingJobs.add(jobId)) return
        when (status) {
            "completed" -> refresh()
            "failed" -> _state.update {
                it.copy(operationError = error ?: application.getString(R.string.operation_error))
            }
        }
        viewModelScope.launch {
            delay(1200)
            _state.update {
                it.copy(activeJobs = it.activeJobs.filterNot { job -> job.id == jobId })
            }
            finishingJobs.remove(jobId)
        }
    }

    private fun compensateActiveJobs() {
        if (jobCompensationJob?.isActive == true) return
        val profile = _state.value.profile ?: return
        val jobs = _state.value.activeJobs
        if (jobs.isEmpty()) return
        jobCompensationJob = viewModelScope.launch {
            for (job in jobs) {
                if (_state.value.profile?.id != profile.id) break
                if (_state.value.activeJobs.none { it.id == job.id }) continue
                pollJobOnce(profile, job.id)
            }
        }
    }

    fun cancelJob(jobId: String) {
        val current = _state.value
        val profile = current.profile ?: return
        viewModelScope.launch {
            runCatching { repository.cancelJob(profile, jobId) }
                .onFailure { error ->
                    _state.update {
                        it.copy(operationError = error.toUserMessage(application))
                    }
                }
        }
    }

    fun requestAudioPlay(entry: FileEntry) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        if (entry.kind != FileKind.Audio) return
        val path = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        _state.update { it.copy(operationLoading = true, operationError = null) }
        viewModelScope.launch {
            val info = runCatching { repository.audioInfo(profile, root.id, path) }.getOrNull()
            _state.update { it.copy(operationLoading = false) }
            when {
                info == null -> playAudio(profile, root.id, entry, path, info, 0)
                info.tracks.size > 1 -> _state.update {
                    it.copy(audioPicker = AudioPickerState(entry, info))
                }
                else -> {
                    val track = info.defaultTrack
                        ?: info.tracks.firstOrNull()?.index
                        ?: 0
                    playAudio(profile, root.id, entry, path, info, track)
                }
            }
        }
    }

    fun chooseAudioTrack(trackIndex: Int) {
        val picker = _state.value.audioPicker ?: return
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        val path = if (current.path.isEmpty()) {
            picker.entry.name
        } else {
            "${current.path}/${picker.entry.name}"
        }
        _state.update { it.copy(audioPicker = null) }
        playAudio(profile, root.id, picker.entry, path, picker.info, trackIndex)
    }

    fun dismissAudioPicker() {
        _state.update { it.copy(audioPicker = null) }
    }

    private fun playAudio(
        profile: ServerProfile,
        rootId: Long,
        entry: FileEntry,
        path: String,
        info: AudioInfoDto?,
        trackIndex: Int,
    ) {
        viewModelScope.launch {
            val track = info?.tracks?.firstOrNull { it.index == trackIndex }
            var url = downloadUrl(entry)
            if (track != null && !track.supported) {
                if (info?.ffmpeg != true) {
                    _state.update {
                        it.copy(
                            operationError = application.getString(R.string.audio_ffmpeg_missing),
                        )
                    }
                    return@launch
                }
                _state.update { it.copy(operationLoading = true) }
                val result = runCatching {
                    var response = repository.transcode(profile, rootId, path, trackIndex)
                    if (!response.ready) {
                        val jobId = response.jobId
                        if (jobId != null) {
                            repeat(120) {
                                kotlinx.coroutines.delay(1000)
                                val job = runCatching {
                                    repository.jobStatus(profile, jobId)
                                }.getOrNull()
                                if (job == null) return@runCatching null
                                if (job.status == "completed") {
                                    return@runCatching repository.transcodedUrl(
                                        profile,
                                        rootId,
                                        path,
                                        trackIndex,
                                    )
                                }
                                if (job.status == "failed" || job.status == "cancelled") {
                                    return@runCatching null
                                }
                            }
                            return@runCatching null
                        }
                    }
                    repository.transcodedUrl(profile, rootId, path, trackIndex)
                }.getOrNull()
                _state.update { it.copy(operationLoading = false) }
                if (result == null) {
                    _state.update {
                        it.copy(
                            operationError = application.getString(R.string.audio_transcode_failed),
                        )
                    }
                    return@launch
                }
                url = result
            }
            if (url == null) return@launch
            val queue = audioTracks(entry) ?: return@launch
            val tracks = queue.first.map { item ->
                if (item.path == path) item.copy(url = url) else item
            }
            audioPlayerController.playQueue(profile.id, tracks, queue.second)
        }
    }

    fun audioTracks(entry: FileEntry): Pair<List<AudioTrack>, Int>? {
        val current = _state.value
        val profile = current.profile ?: return null
        val root = current.root ?: return null
        if (entry.kind != FileKind.Audio) return null
        val tracks = current.entries
            .filter { it.kind == FileKind.Audio }
            .mapNotNull { audio ->
                val path = if (current.path.isEmpty()) audio.name else "${current.path}/${audio.name}"
                val url = downloadUrl(audio) ?: return@mapNotNull null
                AudioTrack(
                    id = path,
                    title = audio.name,
                    url = url,
                    mimeType = audio.mimeType,
                    rootId = root.id,
                    path = path,
                )
            }
        if (tracks.isEmpty()) return null
        val index = tracks.indexOfFirst { it.path == (if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}") }
        return tracks to index.coerceAtLeast(0)
    }

    fun enqueueUploads(
        uris: List<Uri>,
        directory: String? = null,
        mtime: Long? = null,
    ) {
        val current = _state.value
        val profile = current.profile ?: return
        val root = current.root ?: return
        if (uris.isEmpty()) return
        val targetDir = directory ?: current.path
        viewModelScope.launch {
            uris.forEach { uri ->
                transferRepository.enqueueUpload(
                    profile = profile,
                    rootId = root.id,
                    remoteDir = targetDir,
                    uri = uri,
                    mtimeOverride = mtime,
                )
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

    fun previewImageUrl(entry: FileEntry): String? {
        val current = _state.value
        val profile = current.profile ?: return null
        val root = current.root ?: return null
        val base = profile.baseUrl.toHttpUrlOrNull() ?: return null
        val remotePath = if (current.path.isEmpty()) entry.name else "${current.path}/${entry.name}"
        return base.newBuilder()
            .addPathSegments("api/v1/thumb")
            .addQueryParameter("root", root.id.toString())
            .addQueryParameter("path", remotePath)
            .addQueryParameter("edge", "2048")
            .addQueryParameter("wait", "1")
            .addQueryParameter("v", entry.modified.toString())
            .addQueryParameter(
                "r",
                (_state.value.thumbVersions["$remotePath:${entry.modified}"] ?: 0).toString(),
            )
            .build()
            .toString()
    }

    fun thumbnailUrl(entry: FileEntry, width: Int = 512): String? {
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
            .addQueryParameter(
                "r",
                (_state.value.thumbVersions["$guestPath:${entry.modified}"] ?: 0).toString(),
            )
            .build()
            .toString()
    }

    fun clear() {
        persistCurrentState()
        loadJob?.cancel()
        navJob?.cancel()
        cancelDirSizes()
        resetScrollPosition()
        _state.value = FileBrowserState()
    }

    fun release() {
        persistCurrentState()
        loadJob?.cancel()
        navJob?.cancel()
        cancelDirSizes()
        resetScrollPosition()
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
                        it.copy(operationLoading = false, operationError = error.toUserMessage(application))
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
            if (!append && cursor == null) {
                directoryCache.get(profile.id, root.id, path, current.sort, current.order)?.let { cached ->
                    if (cached.listing.entries.isNotEmpty()) {
                        _state.update {
                            it.copy(
                                path = path,
                                entries = cached.listing.entries,
                                readOnly = cached.listing.readOnly,
                                readOnlyReason = cached.listing.readOnlyReason,
                                loading = false,
                                stale = true,
                                error = null,
                            )
                        }
                    }
                }
            }
            runCatching {
                repository.list(
                    profile = profile,
                    rootId = root.id,
                    path = path,
                    sort = current.sort,
                    order = current.order,
                    cursor = cursor,
                    includeSizes = true,
                )
            }.onSuccess { listing ->
                sessionRepository.clearConnectionFailure(profile.id)
                if (!append && cursor == null) {
                    directoryCache.put(
                        profile.id, root.id, path, current.sort, current.order, listing,
                    )
                }
                _state.update {
                    val previewEntry = it.previewEntry
                    val previewImages = if (
                        !append && cursor == null && !it.previewImagesPinned &&
                        previewEntry?.kind == FileKind.Image && it.previewImages.size <= 1
                    ) {
                        val images = listing.entries.filter { entry -> entry.kind == FileKind.Image }
                        if (images.any { entry ->
                                entry.name == previewEntry.name && entry.modified == previewEntry.modified
                            }
                        ) {
                            images
                        } else {
                            it.previewImages
                        }
                    } else {
                        it.previewImages
                    }
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
                        previewImages = previewImages,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                val apiError = error as? FileHubApiException
                val needsLogin = apiError?.statusCode == 401
                if (apiError != null && (apiError.statusCode == 0 || apiError.code == "NETWORK_ERROR")) {
                    sessionRepository.reportConnectionFailure(
                        profile.id,
                        error.toUserMessage(application),
                    )
                }
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        loginRequired = needsLogin,
                        error = if (needsLogin) null else error.toUserMessage(application),
                    )
                }
            }
        }
    }

    private fun normalizePath(path: String): String =
        path.trim('/').replace(Regex("/+"), "/")

    companion object {
        const val MAX_TEXT_EDIT_BYTES = 2L * 1024 * 1024

        /**
         * Local roots scan the directory on demand; poll for up to roughly a
         * minute before giving up so huge folders still resolve eventually.
         */
        private const val DIR_SIZE_MAX_POLLS = 40
        private const val JOB_POLL_CONNECTED_MS = 15_000L
        private const val JOB_POLL_FALLBACK_MS = 1_500L

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as FileHubApplication
                FilesViewModel(
                    app,
                    app.container.filesRepository,
                    app.container.thumbnailLoaderFactory,
                    app.container.mediaPlayerFactory,
                    app.container.previewDownloader,
                    app.container.transferRepository,
                    app.container.shareRepository,
                    app.container.sessionRepository,
                    app.container.directoryCacheRepository,
                    app.container.browserStateRepository,
                    app.container.audioPlayerController,
                    app.container.eventStreamClient,
                )
            }
        }
    }
}
