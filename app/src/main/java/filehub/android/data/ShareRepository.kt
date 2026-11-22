package filehub.android.data

import filehub.android.core.model.ShareCreated
import filehub.android.core.model.ShareInfo
import filehub.android.core.model.ServerProfile
import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException

class ShareRepository(
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

    suspend fun rotate(profile: ServerProfile, id: Long): ShareCreated = api(profile).rotateShare(id)

    suspend fun revoke(profile: ServerProfile, id: Long) = api(profile).revokeShare(id)

    private suspend fun api(profile: ServerProfile): FileHubApi {
        val token = session.tokenFor(profile)
            ?: throw FileHubApiException(401, "UNAUTHORIZED", "设备授权已失效，请重新登录")
        return FileHubApi(profile.baseUrl, token)
    }
}
