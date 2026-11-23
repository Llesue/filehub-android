package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.core.model.FileKind
import filehub.android.core.model.SearchResult
import filehub.android.data.FilesRepository
import filehub.android.data.api.IndexStatusDto
import filehub.android.data.SessionRepository
import filehub.android.data.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class SearchState(
    val query: String = "",
    val scopeRootId: Long? = null,
    val kind: String? = null,
    val results: List<SearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val searched: Boolean = false,
    val indexStatus: IndexStatusDto? = null,
    val indexLoading: Boolean = false,
    val indexError: String? = null,
)

class SearchViewModel(
    private val application: Application,
    private val filesRepository: FilesRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var job: Job? = null

    fun setScope(rootId: Long?) {
        _state.update { it.copy(scopeRootId = rootId) }
        if (_state.value.query.isNotBlank()) {
            search(_state.value.query)
        }
    }

    fun setKind(kind: String?) {
        _state.update { it.copy(kind = kind) }
        if (_state.value.query.isNotBlank()) {
            search(_state.value.query)
        }
    }

    fun search(rawQuery: String) {
        val query = rawQuery.trim()
        _state.update { it.copy(query = rawQuery) }
        if (!isValid(query)) {
            job?.cancel()
            _state.update {
                it.copy(results = emptyList(), loading = false, error = null, nextCursor = null, searched = false)
            }
            return
        }
        val profile = sessionRepository.state.value.active ?: return
        job?.cancel()
        _state.update { it.copy(loading = true, error = null, searched = true) }
        job = viewModelScope.launch {
            runCatching {
                val scope = _state.value.scopeRootId
                if (scope == null) {
                    filesRepository.searchAll(
                        profile = profile,
                        query = query,
                        cursor = null,
                        kind = _state.value.kind,
                    )
                } else {
                    filesRepository.search(
                        profile = profile,
                        rootId = scope,
                        query = query,
                        kind = _state.value.kind,
                    ) to null
                }
            }.onSuccess { (results, cursor) ->
                _state.update {
                    it.copy(
                        results = results,
                        nextCursor = cursor,
                        loading = false,
                        error = null,
                    )
                }
                if (results.isEmpty()) checkIndex()
            }.onFailure { error ->
                _state.update {
                    it.copy(loading = false, error = error.toUserMessage(application))
                }
            }
        }
    }

    fun thumbnailUrl(result: SearchResult): String? {
        if (!result.hasPreview) return null
        val kind = result.toEntry().kind
        if (kind != FileKind.Image && kind != FileKind.Video) return null
        val profile = sessionRepository.state.value.active ?: return null
        val base = profile.baseUrl.toHttpUrlOrNull() ?: return null
        return base.newBuilder()
            .addPathSegments("api/v1/thumb")
            .addQueryParameter("root", result.rootId.toString())
            .addQueryParameter("path", result.path)
            .addQueryParameter("w", "128")
            .addQueryParameter("wait", "1")
            .addQueryParameter("v", result.modified.toString())
            .build()
            .toString()
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.scopeRootId != null) return
        val profile = sessionRepository.state.value.active ?: return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching {
                filesRepository.searchAll(
                    profile = profile,
                    query = current.query.trim(),
                    cursor = cursor,
                    kind = current.kind,
                )
            }.onSuccess { (results, next) ->
                _state.update {
                    it.copy(
                        results = it.results + results,
                        nextCursor = next,
                        loading = false,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.toUserMessage(application)) }
            }
        }
    }

    fun checkIndex() {
        val profile = sessionRepository.state.value.active ?: return
        val rootId = _state.value.scopeRootId
            ?: sessionRepository.state.value.roots.firstOrNull()?.id
            ?: return
        _state.update { it.copy(indexLoading = true, indexError = null) }
        viewModelScope.launch {
            runCatching { filesRepository.indexStatus(profile, rootId, "") }
                .onSuccess { status ->
                    _state.update { it.copy(indexStatus = status, indexLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(indexLoading = false, indexError = error.toUserMessage(application))
                    }
                }
        }
    }

    fun startIndex() {
        val profile = sessionRepository.state.value.active ?: return
        val rootId = _state.value.scopeRootId
            ?: sessionRepository.state.value.roots.firstOrNull()?.id
            ?: return
        _state.update { it.copy(indexLoading = true, indexError = null) }
        viewModelScope.launch {
            runCatching { filesRepository.startIndex(profile, rootId, "", recursive = true) }
                .onSuccess {
                    repeat(60) {
                        val status = runCatching {
                            filesRepository.indexStatus(profile, rootId, "")
                        }.getOrNull()
                        if (status != null) {
                            _state.update { state -> state.copy(indexStatus = status) }
                            val running = status.job != null &&
                                status.job.status != "completed" &&
                                status.job.status != "failed" &&
                                status.job.status != "cancelled"
                            if (!running && status.status == "ready") {
                                _state.update { state -> state.copy(indexLoading = false) }
                                return@launch
                            }
                        }
                        kotlinx.coroutines.delay(2000)
                    }
                    _state.update { it.copy(indexLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(indexLoading = false, indexError = error.toUserMessage(application))
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun reset() {
        job?.cancel()
        _state.value = SearchState()
    }

    private fun isValid(query: String): Boolean {
        val length = query.codePointCount(0, query.length)
        if (length >= 3) return true
        return length == 2 && query.all(::isCjk)
    }

    private fun isCjk(character: Char): Boolean {
        val code = character.code
        return code in 0x3400..0x4DBF ||
            code in 0x4E00..0x9FFF ||
            code in 0xF900..0xFAFF
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as FileHubApplication
                SearchViewModel(app, app.container.filesRepository, app.container.sessionRepository)
            }
        }
    }
}
