package filehub.android.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerProfile(
    val id: String,
    val baseUrl: String,
    val displayName: String,
    val username: String,
    val userId: Long,
    val tokenId: Long,
    val tokenExpiresAt: Long?,
    val createdAt: Long,
    val lastRootId: Long? = null,
    val lastPath: String = "",
)

data class FileRoot(
    val id: Long,
    val alias: String,
    val readOnly: Boolean,
    val kind: String?,
    val mountId: Long?,
) {
    val isRemote: Boolean get() = kind == "remote"
}

data class Branding(
    val name: String,
    val iconUrl: String?,
)

data class LoginResult(
    val profile: ServerProfile,
    val token: String,
    val roots: List<FileRoot>,
)
