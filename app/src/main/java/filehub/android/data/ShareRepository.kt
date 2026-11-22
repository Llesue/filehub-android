package filehub.android.data

import android.content.Context
import filehub.android.R
import filehub.android.core.model.ShareCreated
import filehub.android.core.model.ShareInfo
import filehub.android.core.model.ServerProfile
import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException

class ShareRepository(
    private val context: Context,
    private val session: SessionRepository,
) {
    suspend fun list(profile: ServerProfile, cursor: String? = null): Pair<List<ShareInfo>, String?> =
        api(profile).listShares(cursor = cursor)

    suspend fun create(
        profile: ServerProfile,
        rootId: Long,
        paths: List<String>,
        password: String?,
        expiresAt: Long?,
        readOnly: Boolean,
    ): ShareCreated = api(profile).createShare(rootId, paths, password, expiresAt, readOnly)

    suspend fun url(profile: ServerProfile, id: Long): ShareCreated = api(profile).shareUrl(id)

    suspend fun update(
        profile: ServerProfile,
        id: Long,
        readOnly: Boolean? = null,
        expiresAt: Long? = null,
        clearExpires: Boolean = false,
        password: String? = null,
        clearPassword: Boolean = false,
        revoked: Boolean? = null,
    ) = api(profile).updateShare(
        id = id,
        readOnly = readOnly,
        expiresAt = expiresAt,
        clearExpires = clearExpires,
        password = password,
        clearPassword = clearPassword,
        revoked = revoked,
    )

    suspend fun rotate(profile: ServerProfile, id: Long): ShareCreated = api(profile).rotateShare(id)

    suspend fun revoke(profile: ServerProfile, id: Long) = api(profile).revokeShare(id)

    private suspend fun api(profile: ServerProfile): FileHubApi {
        val token = session.tokenFor(profile)
            ?: throw FileHubApiException(401, "UNAUTHORIZED", context.getString(R.string.error_unauthorized))
        return FileHubApi(profile.baseUrl, token)
    }
}
