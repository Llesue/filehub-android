package filehub.android.data

import android.content.Context
import filehub.android.R
import filehub.android.core.model.Branding
import filehub.android.core.model.FileRoot
import filehub.android.core.model.ServerProfile
import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException
import filehub.android.data.local.ProfileStore
import filehub.android.data.local.TokenVault
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ServerConnectionStatus { Connecting, Success, Failed }

data class ServerConnection(
    val profileId: String,
    val attempt: Int,
    val total: Int,
    val status: ServerConnectionStatus,
    val message: String? = null,
)

data class SessionState(
    val loading: Boolean = true,
    val profiles: List<ServerProfile> = emptyList(),
    val active: ServerProfile? = null,
    val roots: List<FileRoot> = emptyList(),
    val rootsLoading: Boolean = false,
    val needsLogin: Boolean = false,
    val error: String? = null,
)

class SessionRepository(
    private val context: Context,
    private val store: ProfileStore,
    private val vault: TokenVault,
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _connection = MutableStateFlow<ServerConnection?>(null)
    val connection: StateFlow<ServerConnection?> = _connection.asStateFlow()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun load() {
        val stored = store.read()
        val active = stored.profiles.firstOrNull { it.id == stored.activeId }
        _state.update {
            it.copy(
                loading = false,
                profiles = stored.profiles,
                active = active,
            )
        }
        // Root probing is owned by connect(), so a cold start can show the
        // per-address connection progress without probing twice.
    }

    suspend fun login(addresses: List<String>, username: String, password: String): Result<ServerProfile> =
        runCatching {
            val normalized = addresses
                .map { FileHubApi.normalizeBaseUrl(it) }
                .distinct()
            require(normalized.isNotEmpty()) { context.getString(R.string.login_error_url) }
            var lastError: Throwable? = null
            var result: filehub.android.core.model.LoginResult? = null
            var workingAddress: String? = null
            for (address in normalized) {
                try {
                    result = FileHubApi.loginAndCreateToken(
                        baseUrl = address,
                        username = username,
                        password = password,
                    )
                    workingAddress = address
                    break
                } catch (error: Throwable) {
                    lastError = error
                }
            }
            val login = result ?: throw lastError ?: error(context.getString(R.string.login_error_generic))
            val profile = login.profile.copy(
                baseUrl = workingAddress ?: normalized.first(),
                addresses = normalized,
            )
            val encrypted = vault.encrypt(login.token)
            store.saveToken(profile.id, encrypted)

            val profiles = _state.value.profiles
                .filterNot {
                    it.baseUrl == profile.baseUrl && it.username == profile.username
                } + profile
            store.saveProfiles(profiles)
            store.setActive(profile.id)

            _state.update {
                it.copy(
                    loading = false,
                    profiles = profiles,
                    active = profile,
                    roots = login.roots,
                    rootsLoading = false,
                    needsLogin = false,
                    error = null,
                )
            }
            profile
        }

    /**
     * Switches the active server immediately, without waiting for a probe.
     * Used when a sync notification points at a server that is not active;
     * callers refresh roots afterwards.
     */
    suspend fun activate(profileId: String) {
        val profile = _state.value.profiles.firstOrNull { it.id == profileId } ?: return
        if (_state.value.active?.id == profileId) return
        store.setActive(profileId)
        _state.update {
            it.copy(active = profile, roots = emptyList(), rootsLoading = true, error = null)
        }
    }

    /**
     * Activates a profile only after one of its addresses answers. The current
     * address is tried first; the rest are fallbacks. This keeps the server
     * page visible and shows per-address progress instead of blocking Browse.
     */
    suspend fun connect(profileId: String): Result<Unit> = runCatching {
        val profile = _state.value.profiles.firstOrNull { it.id == profileId }
            ?: error(context.getString(R.string.server_missing))
        val token = store.readToken(profile.id)?.let(vault::decrypt)
        if (token == null) {
            val message = context.getString(R.string.error_unauthorized)
            _connection.value = ServerConnection(
                profileId, 0, 0, ServerConnectionStatus.Failed, message,
            )
            _state.update { it.copy(needsLogin = true, error = message) }
            error(message)
        }

        // Always probe in the user-configured order. The last working address
        // stays as baseUrl for API calls, but must not jump the queue: a LAN
        // address placed first should win whenever the device is at home.
        val addresses = normalizedAddresses(profile)
        var lastError: Throwable? = null
        for ((index, address) in addresses.withIndex()) {
            _connection.value = ServerConnection(
                profile.id, index + 1, addresses.size, ServerConnectionStatus.Connecting,
            )
            val api = FileHubApi(address, token, probeClient)
            val roots = try {
                api.roots()
            } catch (error: FileHubApiException) {
                if (error.statusCode == 401) {
                    val message = context.getString(R.string.error_unauthorized)
                    _connection.value = ServerConnection(
                        profile.id, index + 1, addresses.size,
                        ServerConnectionStatus.Failed, message,
                    )
                    _state.update { it.copy(needsLogin = true, error = message) }
                    throw error
                }
                lastError = error
                null
            } catch (error: Throwable) {
                lastError = error
                null
            }
            if (roots != null) {
                val updated = profile.copy(baseUrl = address, addresses = addresses)
                val profiles = _state.value.profiles.map {
                    if (it.id == profile.id) updated else it
                }
                store.saveProfiles(profiles)
                store.setActive(profile.id)
                _state.update {
                    it.copy(
                        profiles = profiles,
                        active = updated,
                        roots = roots,
                        rootsLoading = false,
                        needsLogin = false,
                        error = null,
                    )
                }
                _connection.value = ServerConnection(
                    profile.id, index + 1, addresses.size, ServerConnectionStatus.Success,
                )
                return@runCatching
            }
        }
        val message = lastError?.toUserMessage(context)
            ?: context.getString(R.string.server_unreachable)
        _connection.value = ServerConnection(
            profile.id, addresses.size, addresses.size,
            ServerConnectionStatus.Failed, message,
        )
        _state.update { it.copy(error = message) }
        throw lastError ?: IllegalStateException(message)
    }

    fun clearConnection() {
        _connection.value = null
    }

    suspend fun updateProfile(
        profileId: String,
        displayName: String,
        addresses: List<String>,
    ): Result<ServerProfile> = runCatching {
        val current = _state.value.profiles.firstOrNull { it.id == profileId }
            ?: error(context.getString(R.string.server_missing))
        val normalized = addresses
            .mapNotNull { raw -> runCatching { FileHubApi.normalizeBaseUrl(raw) }.getOrNull() }
            .distinct()
        require(normalized.isNotEmpty()) { context.getString(R.string.server_address_required) }
        val base = current.baseUrl.takeIf { it in normalized } ?: normalized.first()
        val updated = current.copy(
            displayName = displayName.trim().ifEmpty { current.displayName },
            addresses = normalized,
            baseUrl = base,
        )
        val profiles = _state.value.profiles.map { if (it.id == profileId) updated else it }
        store.saveProfiles(profiles)
        _state.update {
            it.copy(
                profiles = profiles,
                active = if (it.active?.id == profileId) updated else it.active,
            )
        }
        if (_state.value.active?.id == profileId) refreshRoots(updated)
        updated
    }

    suspend fun refreshRoots() {
        val active = _state.value.active ?: return
        refreshRoots(active)
    }

    suspend fun refreshRoots(profile: ServerProfile) {
        val token = store.readToken(profile.id)?.let(vault::decrypt)
        if (token == null) {
            _state.update { it.copy(rootsLoading = false, needsLogin = true) }
            return
        }
        _state.update { it.copy(rootsLoading = true, error = null) }
        val reachable = probe(profile, token)
        if (reachable == null) {
            _state.update {
                it.copy(rootsLoading = false, error = context.getString(R.string.server_unreachable))
            }
            return
        }
        if (reachable.unauthorized) {
            _state.update { it.copy(rootsLoading = false, needsLogin = true, error = null) }
            return
        }
        val working = reachable.working ?: run {
            _state.update {
                it.copy(rootsLoading = false, error = reachable.error?.toUserMessage(context) ?: context.getString(R.string.error_network))
            }
            return
        }
        val addresses = normalizedAddresses(profile)
        val iconUrl = resolveIcon(working.address, reachable.branding?.iconUrl)
        val updated = profile.copy(
            baseUrl = working.address,
            addresses = addresses,
            iconUrl = iconUrl ?: profile.iconUrl,
            avatarVersion = reachable.avatarVersion ?: profile.avatarVersion,
        )
        val profiles = _state.value.profiles.map { if (it.id == profile.id) updated else it }
        store.saveProfiles(profiles)
        _state.update {
            it.copy(
                profiles = profiles,
                active = if (it.active?.id == profile.id) updated else it.active,
                roots = working.roots,
                rootsLoading = false,
                needsLogin = false,
                error = null,
            )
        }
    }

    private data class WorkingAddress(
        val address: String,
        val roots: List<FileRoot>,
    )

    private data class ProbeResult(
        val working: WorkingAddress?,
        val branding: Branding?,
        val avatarVersion: Long?,
        val unauthorized: Boolean,
        val error: Throwable?,
    )

    private suspend fun probe(profile: ServerProfile, token: String): ProbeResult? {
        var lastError: Throwable? = null
        var unauthorized = false
        for (address in normalizedAddresses(profile)) {
            val api = FileHubApi(address, token, probeClient)
            val roots = runCatching { api.roots() }.getOrElse { error ->
                lastError = error
                if (error is FileHubApiException && error.statusCode == 401) unauthorized = true
                null
            } ?: continue
            val branding = runCatching { api.branding() }.getOrNull()?.let { Branding(it.name, it.iconUrl) }
            val avatarVersion = runCatching { api.avatarVersion() }.getOrNull()
            return ProbeResult(
                working = WorkingAddress(address, roots),
                branding = branding,
                avatarVersion = avatarVersion,
                unauthorized = false,
                error = null,
            )
        }
        return ProbeResult(null, null, null, unauthorized, lastError)
    }

    private fun normalizedAddresses(profile: ServerProfile): List<String> =
        (profile.addresses.ifEmpty { listOf(profile.baseUrl) })
            .mapNotNull { raw -> runCatching { FileHubApi.normalizeBaseUrl(raw) }.getOrNull() }
            .distinct()


    private fun resolveIcon(baseUrl: String, raw: String?): String? {
        if (raw.isNullOrBlank()) {
            return "${baseUrl.trimEnd('/')}/favicon.ico"
        }
        return raw.toHttpUrlOrNull()?.toString()
            ?: baseUrl.toHttpUrlOrNull()?.resolve(raw)?.toString()
    }

    suspend fun logout(removeProfile: Boolean) {
        val active = _state.value.active ?: return
        val token = store.readToken(active.id)?.let(vault::decrypt)
        if (token != null) {
            runCatching { FileHubApi(active.baseUrl, token).deleteToken(active.tokenId) }
        }
        store.removeToken(active.id)

        val profiles = if (removeProfile) {
            _state.value.profiles.filterNot { it.id == active.id }
        } else {
            _state.value.profiles
        }
        if (removeProfile) store.saveProfiles(profiles)
        store.setActive(null)
        _state.update {
            it.copy(
                profiles = profiles,
                active = null,
                roots = emptyList(),
                needsLogin = false,
                error = null,
            )
        }
    }

    suspend fun removeProfile(profileId: String) {
        val active = _state.value.active
        if (active?.id == profileId) {
            logout(removeProfile = true)
            return
        }
        store.removeToken(profileId)
        val profiles = _state.value.profiles.filterNot { it.id == profileId }
        store.saveProfiles(profiles)
        _state.update { it.copy(profiles = profiles) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    suspend fun tokenFor(profile: ServerProfile): String? =
        store.readToken(profile.id)?.let(vault::decrypt)
}

fun Throwable.toUserMessage(context: Context): String = when (this) {
    is FileHubApiException -> when (code) {
        "SETUP_REQUIRED" -> context.getString(R.string.error_server_setup_required)
        "UNAUTHORIZED" -> context.getString(R.string.error_unauthorized)
        "RATE_LIMITED" -> context.getString(R.string.login_error_rate_limited)
        "NETWORK_ERROR" -> context.getString(R.string.login_error_network)
        "INVALID_URL" -> context.getString(R.string.error_invalid_url)
        "INVALID_RESPONSE" -> context.getString(R.string.error_invalid_response)
        "REMOTE_OFFLINE" -> context.getString(R.string.error_remote_offline)
        "REMOTE_TOKEN_INVALID", "REMOTE_GRANT_REVOKED" -> context.getString(R.string.sync_remote_auth)
        "NOT_FOUND" -> context.getString(R.string.error_not_found)
        "FORBIDDEN" -> context.getString(R.string.error_forbidden)
        else -> message.ifBlank { context.getString(R.string.operation_error) }
    }
    else -> message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.operation_error)
}
