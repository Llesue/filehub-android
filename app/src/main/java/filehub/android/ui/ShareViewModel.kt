package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.core.model.ShareCreated
import filehub.android.core.model.ShareInfo
import filehub.android.data.SessionRepository
import filehub.android.data.ShareRepository
import filehub.android.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShareUiState(
    val shares: List<ShareInfo> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val actionLink: String? = null,
    val actionError: String? = null,
)

class ShareViewModel(
    private val application: Application,
    private val sessionRepository: SessionRepository,
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    fun load() {
        val profile = sessionRepository.state.value.active ?: return
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { shareRepository.list(profile) }
                .onSuccess { (shares, cursor) ->
                    _state.value = _state.value.copy(
                        shares = shares,
                        nextCursor = cursor,
                        loading = false,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = error.toUserMessage(),
                    )
                }
        }
    }

    fun loadMore() {
        val profile = sessionRepository.state.value.active ?: return
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            runCatching { shareRepository.list(profile, cursor) }
                .onSuccess { (shares, next) ->
                    _state.value = _state.value.copy(
                        shares = _state.value.shares + shares,
                        nextCursor = next,
                        loading = false,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = error.toUserMessage(),
                    )
                }
        }
    }

    fun copyLink(share: ShareInfo) {
        val profile = sessionRepository.state.value.active ?: return
        viewModelScope.launch {
            runCatching { shareRepository.url(profile, share.id) }
                .onSuccess { created ->
                    val absolute = absoluteUrl(profile.baseUrl, created.url)
                    _state.value = _state.value.copy(actionLink = absolute, actionError = null)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(actionError = error.toUserMessage())
                }
        }
    }

    fun rotate(share: ShareInfo) {
        val profile = sessionRepository.state.value.active ?: return
        viewModelScope.launch {
            runCatching { shareRepository.rotate(profile, share.id) }
                .onSuccess {
                    load()
                    _state.value = _state.value.copy(actionError = null)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(actionError = error.toUserMessage())
                }
        }
    }

    fun revoke(share: ShareInfo) {
        val profile = sessionRepository.state.value.active ?: return
        viewModelScope.launch {
            runCatching { shareRepository.revoke(profile, share.id) }
                .onSuccess { load() }
                .onFailure { error ->
                    _state.value = _state.value.copy(actionError = error.toUserMessage())
                }
        }
    }

    fun dismissAction() {
        _state.value = _state.value.copy(actionLink = null, actionError = null)
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as FileHubApplication
                ShareViewModel(app, app.container.sessionRepository, app.container.shareRepository)
            }
        }

        fun absoluteUrl(baseUrl: String, url: String): String {
            if (url.startsWith("http://") || url.startsWith("https://")) return url
            return baseUrl.trimEnd('/') + "/" + url.trimStart('/')
        }
    }
}
