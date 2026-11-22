package filehub.android.data

import filehub.android.core.model.DirectoryListing
import filehub.android.core.model.FileSort
import filehub.android.core.model.ServerProfile
import filehub.android.core.model.SortOrder
import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException

class FilesRepository(
    private val session: SessionRepository,
) {
    suspend fun tokenFor(profile: ServerProfile): String? = session.tokenFor(profile)

    suspend fun list(
        profile: ServerProfile,
        rootId: Long,
        path: String,
        sort: FileSort,
        order: SortOrder,
        cursor: String?,
        limit: Int = 200,
    ): DirectoryListing {
        val token = session.tokenFor(profile)
            ?: throw FileHubApiException(401, "UNAUTHORIZED", "设备授权已失效，请重新登录")
        return FileHubApi(profile.baseUrl, token).list(
            rootId = rootId,
            path = path,
            sort = sort,
            order = order,
            cursor = cursor,
            limit = limit,
        )
    }
}
