package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.core.model.PublicShareEntry
import filehub.android.core.model.PublicShareMeta
import filehub.android.data.api.FileHubApiException
import filehub.android.data.api.PublicLink
import filehub.android.data.api.PublicShareApi
import filehub.android.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PublicShareState(
    val meta: PublicShareMeta? = null,
    val path: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val ticket: String? = null,
    val needsPassword: Boolean = false,
)

class PublicShareViewModel(
    private val application: Application,
    private val link: PublicLink,
) : ViewModel() {
    private val api = PublicShareApi(link.baseUrl)
    private val _state = MutableStateFlow(PublicShareState())
    val state: StateFlow<PublicShareState> = _state.asStateFlow()

    init {
        load("")
    }

    fun load(path: String) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null, path = path)
        viewModelScope.launch {
            runCatching {
                api.meta(link.token, path, _state.value.ticket)
            }.onSuccess { meta ->
                _state.value = _state.value.copy(
                    meta = meta,
                    loading = false,
                    needsPassword = false,
                    error = null,
                )
            }.onFailure { error ->
                val needsPassword = (error as? FileHubApiException)?.statusCode == 401
                _state.value = _state.value.copy(
                    loading = false,
                    needsPassword = needsPassword,
                    error = if (needsPassword) null else error.toUserMessage(application),
                )
            }
        }
    }

    fun unlock(password: String) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { api.unlock(link.token, password) }
                .onSuccess { response ->
                    _state.value = _state.value.copy(ticket = response.ticket, loading = false)
                    load(_state.value.path)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = error.toUserMessage(application),
                    )
                }
        }
    }

    fun openDir(entry: PublicShareEntry) {
        val path = if (_state.value.path.isEmpty()) entry.name else "${_state.value.path}/${entry.name}"
        load(path)
    }

    fun goUp() {
        val current = _state.value.path
        if (current.isEmpty()) return
        load(current.substringBeforeLast('/', ""))
    }

    fun downloadUrl(entry: PublicShareEntry): String {
        val path = if (_state.value.path.isEmpty()) entry.name else "${_state.value.path}/${entry.name}"
        return api.downloadUrl(link.token, path, _state.value.ticket)
    }

    fun thumbUrl(entry: PublicShareEntry): String {
        val path = if (_state.value.path.isEmpty()) entry.name else "${_state.value.path}/${entry.name}"
        return api.thumbUrl(link.token, path, _state.value.ticket)
    }

    companion object {
        fun factory(application: Application, link: PublicLink): ViewModelProvider.Factory = viewModelFactory {
            initializer { PublicShareViewModel(application, link) }
        }
    }
}
