package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
    val writable: Boolean = false,
    val readOnlyReason: String? = null,
    val lossy: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
    val conflict: Boolean = false,
)

class TextEditorViewModel(
    private val application: Application,
    private val filesRepository: filehub.android.data.FilesRepository,
    private val sessionRepository: filehub.android.data.SessionRepository,
    private val profileId: String,
    private val rootId: Long,
    private val path: String,
    private val markdown: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(TextEditorState())
    val state: StateFlow<TextEditorState> = _state.asStateFlow()

    val isMarkdown: Boolean get() = markdown

    fun pathLabel(): String = path.substringAfterLast('/').ifBlank { path }

    init {
        load()
    }

    fun load(encoding: String? = _state.value.encoding) {
        val profile = sessionRepository.state.value.profiles.firstOrNull { it.id == profileId }
        if (profile == null) {
            _state.update {
                it.copy(loading = false, error = application.getString(R.string.error_unauthorized))
            }
            return
        }
        _state.update { it.copy(loading = true, error = null, saveError = null, conflict = false) }
        viewModelScope.launch {
            runCatching { filesRepository.readText(profile, rootId, path, encoding) }
                .onSuccess { text -> applyText(text) }
                .onFailure { error ->
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
            it.copy(content = value, dirty = value != it.original, saveError = null)
        }
    }

    fun save(force: Boolean = false) {
        val current = _state.value
        if (current.saving || !current.writable || current.etag == null) return
        val profile = sessionRepository.state.value.profiles.firstOrNull { it.id == profileId } ?: return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val payload = restoreLineEndings(current.content, current.lineEnding)
            runCatching {
                filesRepository.saveText(
                    profile = profile,
                    rootId = rootId,
                    path = path,
                    content = payload,
                    encoding = current.encoding,
                    bom = current.bom,
                    etag = if (force) null else current.etag,
                    force = force,
                )
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        saving = false,
                        etag = result.etag.takeIf { value -> value.isNotBlank() } ?: it.etag,
                        size = result.size.takeIf { value -> value > 0 } ?: it.size,
                        modified = System.currentTimeMillis() / 1000,
                        original = it.content,
                        dirty = false,
                        conflict = false,
                        saveError = null,
                    )
                }
            }.onFailure { error ->
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
                )
            }
        }
    }
}
