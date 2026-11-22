package filehub.android.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import filehub.android.FileHubApplication
import filehub.android.R
import filehub.android.data.SessionRepository
import filehub.android.data.SessionState
import filehub.android.data.local.SettingsStore
import filehub.android.data.api.FileHubApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
) : ViewModel() {

    val session: StateFlow<SessionState> = repository.state
    val theme: StateFlow<String> = settingsStore.theme.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
        initialValue = "system",
    )

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    init {
        viewModelScope.launch { repository.load() }
    }

    fun login(baseUrl: String, username: String, password: String) {
        if (_login.value.loading) return
        if (baseUrl.isBlank()) {
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
            repository.login(baseUrl, username, password)
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
            else -> error.message.ifBlank { application.getString(R.string.login_error_generic) }
        }
        else -> error.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.login_error_generic)
    }

    fun resetLogin() {
        _login.value = LoginUiState()
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch { repository.select(profileId) }
    }

    fun refreshRoots() {
        viewModelScope.launch { repository.refreshRoots() }
    }

    fun logout() {
        viewModelScope.launch { repository.logout(removeProfile = false) }
    }

    fun removeProfile(profileId: String) {
        viewModelScope.launch { repository.removeProfile(profileId) }
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
                AppViewModel(app, app.container.sessionRepository, app.container.settingsStore)
            }
        }
    }
}
