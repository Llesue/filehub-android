package filehub.android.data

import filehub.android.core.model.FileRoot
import filehub.android.core.model.ServerProfile
import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException
import filehub.android.data.local.ProfileStore
import filehub.android.data.local.TokenVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    private val store: ProfileStore,
    private val vault: TokenVault,
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

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
        if (active != null) {
            refreshRoots(active)
        }
    }

    suspend fun login(baseUrl: String, username: String, password: String): Result<ServerProfile> =
        runCatching {
            val result = FileHubApi.loginAndCreateToken(
                baseUrl = baseUrl,
                username = username,
                password = password,
            )
            val encrypted = vault.encrypt(result.token)
            store.saveToken(result.profile.id, encrypted)

            val profiles = _state.value.profiles
                .filterNot {
                    it.baseUrl == result.profile.baseUrl && it.username == result.profile.username
                } + result.profile
            store.saveProfiles(profiles)
            store.setActive(result.profile.id)

            _state.update {
                it.copy(
                    loading = false,
                    profiles = profiles,
                    active = result.profile,
                    roots = result.roots,
                    rootsLoading = false,
                    needsLogin = false,
                    error = null,
                )
            }
            result.profile
        }

    suspend fun select(profileId: String) {
        val profile = _state.value.profiles.firstOrNull { it.id == profileId } ?: return
        store.setActive(profileId)
        _state.update { it.copy(active = profile, roots = emptyList(), error = null, needsLogin = false) }
        refreshRoots(profile)
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
        try {
            val roots = FileHubApi(profile.baseUrl, token).roots()
            _state.update {
                it.copy(roots = roots, rootsLoading = false, needsLogin = false, error = null)
            }
        } catch (error: FileHubApiException) {
            val needsLogin = error.statusCode == 401
            _state.update {
                it.copy(
                    rootsLoading = false,
                    needsLogin = needsLogin,
                    error = if (needsLogin) null else error.toUserMessage(),
                )
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(rootsLoading = false, error = error.toUserMessage())
            }
        }
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
}

fun Throwable.toUserMessage(): String = when (this) {
    is FileHubApiException -> when (code) {
        "SETUP_REQUIRED" -> "服务器尚未初始化，请先在浏览器中完成初始化"
        "UNAUTHORIZED" -> "用户名或密码错误"
        "RATE_LIMITED" -> "尝试过于频繁，请稍后再试"
        "NETWORK_ERROR" -> "无法连接服务器，请检查地址、网络或证书"
        else -> message.ifBlank { "操作失败" }
    }
    else -> message?.takeIf { it.isNotBlank() } ?: "操作失败"
}
