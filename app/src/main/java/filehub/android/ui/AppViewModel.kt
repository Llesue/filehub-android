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
import filehub.android.data.BrowserStateRepository
import filehub.android.data.ClientRelease
import filehub.android.data.ClientUpdateChecker
import filehub.android.data.ServerConnection
import filehub.android.data.FilesRepository
import filehub.android.data.toUserMessage
import filehub.android.data.SessionRepository
import filehub.android.data.SessionState
import filehub.android.data.api.UsageDto
import filehub.android.data.api.VersionDto
import filehub.android.data.events.EventStreamClient
import filehub.android.data.events.RootsChangedPayload
import filehub.android.data.events.ServerEvent
import filehub.android.data.events.ServerEventNames
import filehub.android.data.events.UsageUpdatedPayload
import filehub.android.data.events.decodePayload
import filehub.android.data.local.SettingsStore
import filehub.android.data.media.ThumbnailLoaderFactory
import filehub.android.data.sync.FolderSyncRepository
import filehub.android.data.sync.PhotoSyncRepository
import filehub.android.data.sync.isScheduleOverdue
import filehub.android.data.api.FileHubApiException
import filehub.android.data.api.PasswordChangeRequiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UsageUiState(
    val loading: Boolean = false,
    val usage: UsageDto? = null,
    val error: String? = null,
)

internal fun mergeUsageEvent(current: UsageDto?, payload: UsageUpdatedPayload): UsageDto =
    (current ?: UsageDto()).copy(
        usedBytes = payload.usedBytes ?: current?.usedBytes,
        quotaBytes = payload.quotaBytes ?: current?.quotaBytes,
        computedAt = payload.computedAt ?: current?.computedAt,
        computing = false,
        stale = false,
    )

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val passwordChangeRequired: Boolean = false,
    val passwordChangeError: String? = null,
)

data class ClientUpdateUiState(
    val release: ClientRelease? = null,
    val checking: Boolean = false,
    val error: String? = null,
)

class AppViewModel(
    private val application: Application,
    private val repository: SessionRepository,
    private val filesRepository: FilesRepository,
    private val settingsStore: SettingsStore,
    private val thumbnailLoaderFactory: ThumbnailLoaderFactory,
    private val photoSyncRepository: PhotoSyncRepository,
    private val folderSyncRepository: FolderSyncRepository,
    private val clientUpdateChecker: ClientUpdateChecker,
    private val browserStateRepository: BrowserStateRepository,
    private val eventStreamClient: EventStreamClient,
) : ViewModel() {

    val session: StateFlow<SessionState> = repository.state
    val connection: StateFlow<ServerConnection?> = repository.connection
    val theme: StateFlow<String> = settingsStore.theme.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
        initialValue = "system",
    )
    val appLockEnabled: StateFlow<Boolean?> = settingsStore.appLockEnabled
        .map<Boolean, Boolean?> { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    private val _appUnlocked = MutableStateFlow(false)
    val appUnlocked: StateFlow<Boolean> = _appUnlocked.asStateFlow()

    private val _usage = MutableStateFlow(UsageUiState())
    val usage: StateFlow<UsageUiState> = _usage.asStateFlow()

    private val _serverVersion = MutableStateFlow<VersionDto?>(null)
    val serverVersion: StateFlow<VersionDto?> = _serverVersion.asStateFlow()
    private val _serverVersionLoading = MutableStateFlow(false)
    val serverVersionLoading: StateFlow<Boolean> = _serverVersionLoading.asStateFlow()

    private val _clientUpdate = MutableStateFlow(ClientUpdateUiState())
    val clientUpdate: StateFlow<ClientUpdateUiState> = _clientUpdate.asStateFlow()
    private val _versionError = MutableStateFlow<String?>(null)
    val versionError: StateFlow<String?> = _versionError.asStateFlow()

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    private var connectJob: Job? = null
    private var rootsRefreshJob: Job? = null
    private var versionRefreshJob: Job? = null

    private val _imageLoader = MutableStateFlow<ImageLoader?>(null)
    val imageLoader: StateFlow<ImageLoader?> = _imageLoader.asStateFlow()

    init {
        viewModelScope.launch {
            repository.load()
            val active = session.value.active
            if (active != null && NotificationNavigator.destination.value == null) {
                connectProfile(active.id)
            }
            catchUpSyncs()
        }
        viewModelScope.launch {
            var loadedProfileId: String? = null
            var loadedToken: String? = null
            repository.state.collect { state ->
                val profile = state.active
                val token = profile?.let { repository.tokenFor(it) }
                if (profile?.id == loadedProfileId &&
                    token == loadedToken &&
                    _imageLoader.value != null
                ) {
                    return@collect
                }
                loadedProfileId = profile?.id
                loadedToken = token
                _imageLoader.value = if (profile != null && token != null) {
                    thumbnailLoaderFactory.get(profile.id, token)
                } else {
                    null
                }
            }
        }
        viewModelScope.launch {
            eventStreamClient.events.collect(::handleServerEvent)
        }
    }

    private fun handleServerEvent(event: ServerEvent) {
        val active = session.value.active ?: return
        if (event.profileId != active.id) return
        when (event.name) {
            ServerEventNames.USAGE_UPDATED -> applyUsageEvent(event)
            ServerEventNames.ROOTS_CHANGED -> {
                val payload = event.data.decodePayload(RootsChangedPayload.serializer())
                val rootId = payload?.rootId
                val online = payload?.online
                if (rootId != null && online != null) {
                    repository.applyRootOnline(rootId, online)
                }
                scheduleRootsRefresh()
            }
            ServerEventNames.UPDATES_CHANGED -> scheduleVersionRefresh()
            ServerEventNames.SETTINGS_CHANGED,
            ServerEventNames.USER_FEATURES_CHANGED -> scheduleRootsRefresh()
            ServerEventNames.RESYNC -> {
                rootsRefreshJob?.cancel()
                refreshRoots()
                loadUsage()
                loadServerVersion()
            }
        }
    }

    private fun applyUsageEvent(event: ServerEvent) {
        val payload = event.data.decodePayload(UsageUpdatedPayload.serializer()) ?: return
        _usage.value = UsageUiState(
            loading = false,
            usage = mergeUsageEvent(_usage.value.usage, payload),
            error = null,
        )
    }

    private fun scheduleRootsRefresh() {
        rootsRefreshJob?.cancel()
        rootsRefreshJob = viewModelScope.launch {
            delay(300)
            repository.refreshRoots()
        }
    }

    private fun scheduleVersionRefresh() {
        versionRefreshJob?.cancel()
        versionRefreshJob = viewModelScope.launch {
            delay(300)
            loadServerVersion()
        }
    }

    fun login(
        addresses: List<String>,
        username: String,
        password: String,
        newPassword: String? = null,
    ) {
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
            repository.login(
                addresses.filter { it.isNotBlank() },
                username,
                password,
                newPassword = newPassword,
            )
                .onSuccess { _login.value = LoginUiState(success = true) }
                .onFailure { error ->
                    val changing = newPassword != null
                    _login.value = when {
                        error is PasswordChangeRequiredException -> LoginUiState(
                            passwordChangeRequired = true,
                        )
                        changing -> LoginUiState(
                            passwordChangeRequired = true,
                            passwordChangeError = loginErrorMessage(error),
                        )
                        else -> LoginUiState(error = loginErrorMessage(error))
                    }
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
            "PASSWORD_CHANGE_REQUIRED" -> application.getString(R.string.password_change_required_body)
            "WEAK_PASSWORD" -> application.getString(R.string.password_change_weak)
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

    fun moveProfile(from: Int, to: Int) {
        viewModelScope.launch { repository.moveProfile(from, to) }
    }

    fun connectProfile(profileId: String) {
        connectJob?.cancel()
        connectJob = viewModelScope.launch { repository.connect(profileId) }
    }

    fun clearConnection() {
        repository.clearConnection()
    }

    fun loadUsage() {
        val profile = session.value.active ?: return
        if (_usage.value.loading) return
        _usage.value = UsageUiState(loading = true)
        viewModelScope.launch {
            runCatching { filesRepository.usage(profile) }
                .onSuccess { first ->
                    val settled = if ((first.computing || first.usedBytes == null) && first.usedBytes == null) {
                        kotlinx.coroutines.delay(1500)
                        runCatching { filesRepository.usage(profile) }.getOrDefault(first)
                    } else {
                        first
                    }
                    _usage.value = UsageUiState(loading = false, usage = settled)
                }
                .onFailure { error ->
                    _usage.value = UsageUiState(
                        loading = false,
                        error = error.toUserMessage(application),
                    )
                }
        }
    }

    fun refreshServerVersions() {
        viewModelScope.launch { repository.refreshServerVersions() }
    }

    fun loadServerVersion() {
        val profile = session.value.active ?: return
        _serverVersionLoading.value = true
        viewModelScope.launch {
            runCatching { filesRepository.version(profile) }
                .onSuccess {
                    _serverVersion.value = it
                    _versionError.value = null
                    if (it.version.isNotBlank()) {
                        repository.updateServerVersion(profile.id, it.version)
                    }
                }
                .onFailure {
                    _versionError.value = application.getString(R.string.version_check_failed)
                }
            _serverVersionLoading.value = false
        }
    }

    fun loadClientUpdate(force: Boolean = false, reportError: Boolean = false) {
        val cached = clientUpdateChecker.cached()
        if (cached != null) {
            _clientUpdate.update { it.copy(release = cached) }
        }
        if (!force && cached != null && clientUpdateChecker.isFresh(cached)) {
            return
        }
        if (_clientUpdate.value.checking) return
        _clientUpdate.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            runCatching { clientUpdateChecker.check() }
                .onSuccess { release ->
                    _clientUpdate.value = ClientUpdateUiState(release = release)
                    _versionError.value = null
                }
                .onFailure {
                    _clientUpdate.update { state ->
                        state.copy(
                            checking = false,
                            error = if (reportError) {
                                application.getString(R.string.version_check_failed)
                            } else {
                                state.error
                            },
                        )
                    }
                    if (reportError) {
                        _versionError.value = application.getString(R.string.version_check_failed)
                    }
                    return@launch
                }
            _clientUpdate.update { it.copy(checking = false) }
        }
    }

    fun refreshVersions() {
        _versionError.value = null
        loadServerVersion()
        loadClientUpdate(force = true, reportError = true)
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
            profileId?.let { browserStateRepository.clearForProfile(it) }
        }
    }

    fun removeProfile(profileId: String) {
        viewModelScope.launch {
            browserStateRepository.clearForProfile(profileId)
            photoSyncRepository.disableAndCancel(profileId)
            folderSyncRepository.disableAndCancel(profileId)
            repository.removeProfile(profileId)
        }
    }

    private suspend fun catchUpSyncs() {
        photoSyncRepository.reconcileSchedules()
        folderSyncRepository.reconcileSchedules()
        val profile = session.value.active ?: return
        val now = System.currentTimeMillis() / 1000
        photoSyncRepository.config(profile.id)?.let { config ->
            if (config.enabled && isScheduleOverdue(config.lastRunAt, config.schedule, now)) {
                photoSyncRepository.catchUp(profile.id)
            }
        }
        folderSyncRepository.config(profile.id)?.let { config ->
            if (config.enabled && isScheduleOverdue(config.lastRunAt, config.schedule, now)) {
                folderSyncRepository.catchUp(profile.id)
            }
        }
    }


    fun updateProfile(profileId: String, displayName: String, addresses: List<String>) {
        viewModelScope.launch { repository.updateProfile(profileId, displayName, addresses) }
    }

    fun setTheme(value: String) {
        viewModelScope.launch { settingsStore.setTheme(value) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAppLockEnabled(enabled) }
    }

    fun setAppUnlocked(value: Boolean) {
        _appUnlocked.value = value
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
                    app.container.filesRepository,
                    app.container.settingsStore,
                    app.container.thumbnailLoaderFactory,
                    app.container.photoSyncRepository,
                    app.container.folderSyncRepository,
                    app.container.clientUpdateChecker,
                    app.container.browserStateRepository,
                    app.container.eventStreamClient,
                )
            }
        }
    }
}
