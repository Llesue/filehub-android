package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.R
import filehub.android.core.model.ShareCreated
import filehub.android.core.model.ShareInfo
import filehub.android.data.SessionRepository
import filehub.android.data.ShareRepository
import filehub.android.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal fun ShareInfo.isExpired(nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
    expired || (expiresAt != null && expiresAt <= nowSeconds)

internal fun ShareInfo.isStopped(nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
    revoked || isExpired(nowSeconds)

/** Expired shares need a future expiry before the server will re-enable them. */
internal fun ShareInfo.enableExpiry(nowSeconds: Long = System.currentTimeMillis() / 1000): Long? =
    if (isExpired(nowSeconds)) nowSeconds + 86_400 else null

data class ShareUiState(
    val shares: List<ShareInfo> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val actionLink: String? = null,
    val actionError: String? = null,
    val editing: ShareInfo? = null,
    val editLoading: Boolean = false,
    val editError: String? = null,
    val openLink: String? = null,
    val cardShare: ShareInfo? = null,
    val cardLink: String? = null,
    val cardLoading: Boolean = false,
    val deleting: ShareInfo? = null,
    val deleteLoading: Boolean = false,
    val deleteError: String? = null,
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
                        error = error.toUserMessage(application),
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
                        error = error.toUserMessage(application),
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
                    _state.value = _state.value.copy(actionError = error.toUserMessage(application))
                }
        }
    }

    fun beginEdit(share: ShareInfo) {
        _state.value = _state.value.copy(editing = share, editError = null)
    }

    fun dismissEdit() {
        _state.value = _state.value.copy(editing = null, editLoading = false, editError = null)
    }

    fun update(
        share: ShareInfo,
        readOnly: Boolean,
        expiresAt: Long?,
        clearExpires: Boolean,
        password: String?,
        clearPassword: Boolean,
    ) {
        val profile = sessionRepository.state.value.active ?: return
        val readOnlyChange = readOnly.takeIf { it != share.readOnly }
        val expiresChange = expiresAt.takeIf { it != null && it != share.expiresAt }
        val hasChange = readOnlyChange != null ||
            clearExpires || expiresChange != null || !password.isNullOrBlank() || clearPassword
        if (!hasChange) {
            dismissEdit()
            return
        }
        _state.value = _state.value.copy(editLoading = true, editError = null)
        viewModelScope.launch {
            runCatching {
                shareRepository.update(
                    profile = profile,
                    id = share.id,
                    readOnly = readOnlyChange,
                    expiresAt = expiresChange,
                    clearExpires = clearExpires,
                    password = password?.takeIf { it.isNotBlank() },
                    clearPassword = clearPassword,
                )
            }.onSuccess {
                _state.value = _state.value.copy(editing = null, editLoading = false)
                load()
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    editLoading = false,
                    editError = error.toUserMessage(application),
                )
            }
        }
    }

    fun open(share: ShareInfo) {
        val profile = sessionRepository.state.value.active ?: return
        viewModelScope.launch {
            runCatching { shareRepository.url(profile, share.id) }
                .onSuccess { created ->
                    _state.value = _state.value.copy(
                        openLink = absoluteUrl(profile.baseUrl, created.url),
                        actionError = null,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(actionError = error.toUserMessage(application))
                }
        }
    }

    fun dismissOpenLink() {
        _state.value = _state.value.copy(openLink = null)
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
                    _state.value = _state.value.copy(actionError = error.toUserMessage(application))
                }
        }
    }

    fun stop(share: ShareInfo) {
        val profile = sessionRepository.state.value.active ?: return
        viewModelScope.launch {
            runCatching { shareRepository.revoke(profile, share.id) }
                .onSuccess { load() }
                .onFailure { error ->
                    _state.value = _state.value.copy(actionError = error.toUserMessage(application))
                }
        }
    }

    fun enable(share: ShareInfo) {
        val profile = sessionRepository.state.value.active ?: return
        val now = System.currentTimeMillis() / 1000
        // An expired share cannot be re-enabled without a future expiry; the
        // web client gives it one more day, so match that behaviour here.
        val expiresAt = share.enableExpiry(now)
        viewModelScope.launch {
            runCatching {
                shareRepository.update(
                    profile = profile,
                    id = share.id,
                    revoked = false,
                    expiresAt = expiresAt,
                )
            }.onSuccess {
                load()
            }.onFailure { error ->
                _state.value = _state.value.copy(actionError = error.toUserMessage(application))
            }
        }
    }

    fun showCard(share: ShareInfo) {
        if (share.expired || share.revoked) return
        val profile = sessionRepository.state.value.active ?: return
        _state.value = _state.value.copy(cardLoading = true, actionError = null)
        viewModelScope.launch {
            runCatching { shareRepository.url(profile, share.id) }
                .onSuccess { created ->
                    _state.value = _state.value.copy(
                        cardShare = share,
                        cardLink = absoluteUrl(profile.baseUrl, created.url),
                        cardLoading = false,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        cardLoading = false,
                        actionError = error.toUserMessage(application),
                    )
                }
        }
    }

    fun dismissCard() {
        _state.value = _state.value.copy(cardShare = null, cardLink = null, cardLoading = false)
    }

    fun requestDelete(share: ShareInfo) {
        _state.value = _state.value.copy(deleting = share, deleteError = null)
    }

    fun dismissDelete() {
        if (_state.value.deleteLoading) return
        _state.value = _state.value.copy(deleting = null, deleteError = null)
    }

    fun confirmDelete() {
        val share = _state.value.deleting ?: return
        val profile = sessionRepository.state.value.active ?: return
        _state.value = _state.value.copy(deleteLoading = true, deleteError = null)
        viewModelScope.launch {
            runCatching { shareRepository.purge(profile, share.id) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        shares = _state.value.shares.filterNot { it.id == share.id },
                        deleting = null,
                        deleteLoading = false,
                        deleteError = null,
                    )
                }
                .onFailure { error ->
                    val alreadyGone = error is filehub.android.data.api.FileHubApiException &&
                        error.statusCode == 404
                    if (alreadyGone) {
                        _state.value = _state.value.copy(
                            shares = _state.value.shares.filterNot { it.id == share.id },
                            deleting = null,
                            deleteLoading = false,
                        )
                    } else {
                        _state.value = _state.value.copy(
                            deleteLoading = false,
                            deleteError = application.getString(R.string.share_delete_failed),
                        )
                    }
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
