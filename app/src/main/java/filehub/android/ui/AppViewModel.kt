package filehub.android.ui

import android.app.Application
import coil3.ImageLoader
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.R
import filehub.android.data.ServerConnection
import filehub.android.data.SessionRepository
import filehub.android.data.SessionState
import filehub.android.data.local.SettingsStore
import filehub.android.data.media.ThumbnailLoaderFactory
import filehub.android.data.sync.FolderSyncRepository
import filehub.android.data.sync.PhotoSyncRepository
import filehub.android.data.api.FileHubApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

class AppViewModel(
    private val application: Application,
    private val repository: SessionRepository,
    private val settingsStore: SettingsStore,
    private val thumbnailLoaderFactory: ThumbnailLoaderFactory,
    private val photoSyncRepository: PhotoSyncRepository,
    private val folderSyncRepository: FolderSyncRepository,
) : ViewModel() {

    val session: StateFlow<SessionState> = repository.state
    val connection: StateFlow<ServerConnection?> = repository.connection
    val theme: StateFlow<String> = settingsStore.theme.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
        initialValue = "system",
    )

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    private var connectJob: Job? = null

    private val _imageLoader = MutableStateFlow<ImageLoader?>(null)
    val imageLoader: StateFlow<ImageLoader?> = _imageLoader.asStateFlow()

    init {
        viewModelScope.launch {
            repository.load()
            catchUpSyncs()
        }
        viewModelScope.launch {
            var loadedProfileId: String? = null
            repository.state.collect { state ->
                val profile = state.active
                if (profile?.id == loadedProfileId && _imageLoader.value != null) return@collect
                loadedProfileId = profile?.id
                val token = profile?.let { repository.tokenFor(it) }
                _imageLoader.value = if (profile != null && token != null) {
                    thumbnailLoaderFactory.get(profile.id, token)
                } else {
                    null
                }
            }
        }
    }

    fun login(addresses: List<String>, username: String, password: String) {
        if (_login.value.loading) return
        if (addresses.none { it.isNotBlank() }) {
            _login.value = LoginUiState(error = application.getString(R.string.login_error_url))
            return
        }
        if (username.isBlank() || password.isEmpty()) {
            _login.value = LoginUiState(
                error = application.getString(R.string.login_error_credentials),
            )
            return
        }
        _login.value = LoginUiState(loading = true)
        viewModelScope.launch {
            repository.login(addresses.filter { it.isNotBlank() }, username, password)
                .onSuccess { _login.value = LoginUiState(success = true) }
                .onFailure { error ->
                    _login.value = LoginUiState(error = loginErrorMessage(error))
                }
        }
    }

    private fun loginErrorMessage(error: Throwable): String = when (error) {
        is FileHubApiException -> when (error.code) {
            "UNAUTHORIZED" -> application.getString(R.string.login_error_bad_credentials)
            "RATE_LIMITED" -> application.getString(R.string.login_error_rate_limited)
            "NETWORK_ERROR" -> application.getString(R.string.login_error_network)
            "INVALID_URL" -> application.getString(R.string.error_invalid_url)
            "INVALID_RESPONSE" -> application.getString(R.string.error_invalid_response)
            "SETUP_REQUIRED" -> application.getString(R.string.error_server_setup_required)
            else -> error.message.ifBlank { application.getString(R.string.login_error_generic) }
        }
        else -> error.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.login_error_generic)
    }

    fun resetLogin() {
        _login.value = LoginUiState()
    }

    fun activateProfile(profileId: String) {
        if (session.value.active?.id == profileId) return
        viewModelScope.launch {
            repository.activate(profileId)
            repository.refreshRoots()
        }
    }

    fun connectProfile(profileId: String) {
        connectJob?.cancel()
        connectJob = viewModelScope.launch { repository.connect(profileId) }
    }

    fun clearConnection() {
        repository.clearConnection()
    }

    fun refreshRoots() {
        viewModelScope.launch { repository.refreshRoots() }
    }

    fun logout() {
        val profileId = session.value.active?.id
        viewModelScope.launch {
            profileId?.let { id ->
                photoSyncRepository.disableAndCancel(id)
                folderSyncRepository.disableAndCancel(id)
            }
            repository.logout(removeProfile = false)
        }
    }

    fun removeProfile(profileId: String) {
        viewModelScope.launch {
            photoSyncRepository.disableAndCancel(profileId)
            folderSyncRepository.disableAndCancel(profileId)
            repository.removeProfile(profileId)
        }
    }

    private suspend fun catchUpSyncs() {
        val profile = session.value.active ?: return
        val now = System.currentTimeMillis() / 1000
        photoSyncRepository.config(profile.id)?.let { config ->
            if (config.enabled && isOverdue(config.lastRunAt, config.schedule, now)) {
                photoSyncRepository.catchUp(profile.id)
            }
        }
        folderSyncRepository.config(profile.id)?.let { config ->
            if (config.enabled && isOverdue(config.lastRunAt, config.schedule, now)) {
                folderSyncRepository.catchUp(profile.id)
            }
        }
    }

    private fun isOverdue(lastRunAt: Long?, schedule: String, now: Long): Boolean {
        if (lastRunAt == null) return false
        val interval = when (schedule) {
            "15m" -> 15 * 60L
            "hourly" -> 60 * 60L
            "weekly" -> 7 * 24 * 60 * 60L
            else -> 24 * 60 * 60L
        }
        return now - lastRunAt > interval
    }

    fun updateProfile(profileId: String, displayName: String, addresses: List<String>) {
        viewModelScope.launch { repository.updateProfile(profileId, displayName, addresses) }
    }

    fun setTheme(value: String) {
        viewModelScope.launch { settingsStore.setTheme(value) }
    }

    fun clearSessionError() {
        repository.clearError()
    }

    fun consumeLoginError() {
        _login.update { it.copy(error = null) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as FileHubApplication
                AppViewModel(
                    app,
                    app.container.sessionRepository,
                    app.container.settingsStore,
                    app.container.thumbnailLoaderFactory,
                    app.container.photoSyncRepository,
                    app.container.folderSyncRepository,
                )
            }
        }
    }
}
