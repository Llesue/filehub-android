package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import filehub.android.FileHubApplication
import filehub.android.R
import filehub.android.data.api.FileHubApiException
import filehub.android.data.api.TextFileDto
import filehub.android.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TextEditorState(
    val loading: Boolean = true,
    val error: String? = null,
    val content: String = "",
    val original: String = "",
    val etag: String? = null,
    val size: Long = 0,
    val modified: Long = 0,
    val encoding: String? = null,
    val bom: Boolean = false,
    val lineEnding: String = "lf",
    val originalLineEnding: String = "lf",
    val writable: Boolean = false,
    val readOnlyReason: String? = null,
    val lossy: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
    val conflict: Boolean = false,
    val justSaved: Boolean = false,
)

class TextEditorViewModel(
    private val application: Application,
    private val filesRepository: filehub.android.data.FilesRepository,
    private val sessionRepository: filehub.android.data.SessionRepository,
    private var profileId: String,
    private var rootId: Long,
    private var path: String,
    private var markdown: Boolean,
    initialText: TextFileDto? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(TextEditorState())
    val state: StateFlow<TextEditorState> = _state.asStateFlow()

    val isMarkdown: Boolean get() = markdown

    private var generation = 0
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var autoSaveJob: Job? = null

    fun pathLabel(): String = path.substringAfterLast('/').ifBlank { path }

    fun fullPath(): String = path

    fun preferenceKey(): String = "$profileId:$rootId:$path"

    init {
        if (path.isNotEmpty()) {
            if (initialText != null) applyText(initialText) else load()
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000)
                val current = _state.value
                if (current.dirty &&
                    !current.saving &&
                    current.writable &&
                    !current.conflict &&
                    current.saveError == null
                ) {
                    save(auto = true)
                }
            }
        }
    }

    /**
     * Reuses one document ViewModel for every preview in the browser. The
     * generation counter makes late responses from the previous document
     * harmless.
     */
    fun open(
        profileId: String,
        rootId: Long,
        path: String,
        markdown: Boolean,
        initialText: TextFileDto?,
    ) {
        generation++
        loadJob?.cancel()
        saveJob?.cancel()
        autoSaveJob?.cancel()
        this.profileId = profileId
        this.rootId = rootId
        this.path = path
        this.markdown = markdown
        _state.value = TextEditorState(loading = initialText == null)
        if (initialText != null) applyText(initialText) else load()
    }

    fun load(encoding: String? = _state.value.encoding) {
        val profile = sessionRepository.state.value.profiles.firstOrNull { it.id == profileId }
        if (profile == null) {
            _state.update {
                it.copy(loading = false, error = application.getString(R.string.error_unauthorized))
            }
            return
        }
        val gen = generation
        _state.update { it.copy(loading = true, error = null, saveError = null, conflict = false) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            runCatching { filesRepository.readText(profile, rootId, path, encoding) }
                .onSuccess { text ->
                    if (gen == generation) applyText(text)
                }
                .onFailure { error ->
                    if (gen != generation) return@onFailure
                    _state.update {
                        it.copy(
                            loading = false,
                            error = if (error is FileHubApiException && error.statusCode == 413) {
                                application.getString(R.string.text_too_large)
                            } else {
                                error.toUserMessage(application)
                            },
                        )
                    }
                }
        }
    }

    private fun applyText(text: TextFileDto) {
        val normalized = normalizeLineEndings(text.content, text.lineEnding)
        _state.value = TextEditorState(
            loading = false,
            content = normalized,
            original = normalized,
            etag = text.etag,
            size = text.size,
            modified = text.modified,
            encoding = text.encoding,
            bom = text.bom,
            lineEnding = text.lineEnding,
            originalLineEnding = text.lineEnding,
            writable = text.writable && !text.lossy,
            readOnlyReason = if (text.lossy) {
                application.getString(R.string.text_encoding_unsupported)
            } else {
                text.readOnlyReason
            },
            lossy = text.lossy,
        )
    }

    fun setContent(value: String) {
        _state.update {
            it.copy(
                content = value,
                dirty = value != it.original || it.lineEnding != it.originalLineEnding,
                saveError = null,
                justSaved = false,
            )
        }
        scheduleAutoSave()
    }

    fun setLineEnding(value: String) {
        if (value != "lf" && value != "crlf") return
        _state.update {
            it.copy(
                lineEnding = value,
                dirty = it.content != it.original || value != it.originalLineEnding,
                justSaved = false,
            )
        }
        scheduleAutoSave()
    }

    fun clearJustSaved() {
        _state.update { it.copy(justSaved = false) }
    }

    fun save(force: Boolean = false, auto: Boolean = false) {
        val current = _state.value
        if (current.saving || !current.writable) return
        if (auto && (!current.dirty || current.conflict || current.saveError != null)) return
        val profile = sessionRepository.state.value.profiles.firstOrNull { it.id == profileId } ?: return
        autoSaveJob?.cancel()
        _state.update { it.copy(saving = true, saveError = null) }
        val gen = generation
        val savedContent = current.content
        val savedLineEnding = current.lineEnding
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val payload = restoreLineEndings(savedContent, savedLineEnding)
            runCatching {
                filesRepository.saveText(
                    profile = profile,
                    rootId = rootId,
                    path = path,
                    content = payload,
                    encoding = current.encoding,
                    bom = current.bom,
                    etag = if (force || current.etag == null) null else current.etag,
                    force = force || current.etag == null,
                )
            }.onSuccess { result ->
                if (gen != generation) return@onSuccess
                _state.update {
                    it.copy(
                        saving = false,
                        etag = result.etag.takeIf { value -> value.isNotBlank() } ?: it.etag,
                        size = result.size.takeIf { value -> value > 0 } ?: it.size,
                        modified = System.currentTimeMillis() / 1000,
                        original = savedContent,
                        originalLineEnding = savedLineEnding,
                        dirty = it.content != savedContent ||
                            it.lineEnding != savedLineEnding,
                        conflict = false,
                        saveError = null,
                        justSaved = !auto,
                    )
                }
                if (auto && _state.value.dirty) scheduleAutoSave()
            }.onFailure { error ->
                if (gen != generation) return@onFailure
                val api = error as? FileHubApiException
                _state.update {
                    it.copy(
                        saving = false,
                        conflict = api?.statusCode == 412,
                        saveError = when (api?.statusCode) {
                            412 -> application.getString(R.string.editor_conflict)
                            413 -> application.getString(R.string.text_too_large)
                            415 -> application.getString(R.string.text_not_text)
                            422 -> application.getString(R.string.editor_encoding_unrepresentable)
                            403 -> application.getString(R.string.editor_read_only)
                            else -> error.toUserMessage(application)
                        },
                    )
                }
            }
        }
    }

    fun useUtf8() {
        _state.update { it.copy(encoding = "utf-8", bom = false, dirty = true) }
    }

    fun clearSaveError() {
        _state.update { it.copy(saveError = null) }
    }

    fun dismissConflict() {
        _state.update { it.copy(conflict = false) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun scheduleAutoSave() {
        val current = _state.value
        if (!current.writable || current.conflict) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1_500)
            save(auto = true)
        }
    }

    private fun normalizeLineEndings(content: String, lineEnding: String): String =
        if (lineEnding == "crlf") content.replace("\r\n", "\n") else content.replace("\r\n", "\n")

    private fun restoreLineEndings(content: String, lineEnding: String): String =
        if (lineEnding == "crlf") content.replace("\r\n", "\n").replace("\n", "\r\n") else content

    companion object {
        fun factory(
            application: Application,
            profileId: String,
            rootId: Long,
            path: String,
            markdown: Boolean,
            initialText: TextFileDto? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as FileHubApplication
                TextEditorViewModel(
                    application,
                    app.container.filesRepository,
                    app.container.sessionRepository,
                    profileId,
                    rootId,
                    path,
                    markdown,
                    initialText,
                )
            }
        }
    }
}
