package filehub.android.data

import filehub.android.core.model.DirectoryListing
import filehub.android.core.model.FileProperties
import filehub.android.core.model.FileSort
import filehub.android.core.model.SearchResult
import filehub.android.core.model.ServerProfile
import filehub.android.core.model.SortOrder
import filehub.android.data.api.DeletePreviewDto
import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException
import filehub.android.data.api.TextFileDto

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
    ): DirectoryListing = api(profile).list(
        rootId = rootId,
        path = path,
        sort = sort,
        order = order,
        cursor = cursor,
        limit = limit,
    )

    suspend fun mkdir(profile: ServerProfile, rootId: Long, path: String, name: String) =
        api(profile).mkdir(rootId, path, name)

    suspend fun touch(profile: ServerProfile, rootId: Long, path: String, name: String) =
        api(profile).touch(rootId, path, name)

    suspend fun rename(profile: ServerProfile, rootId: Long, path: String, newName: String) =
        api(profile).rename(rootId, path, newName)

    suspend fun copy(
        profile: ServerProfile,
        rootId: Long,
        paths: List<String>,
        destRootId: Long,
        destPath: String,
    ) = api(profile).copy(rootId, paths, destRootId, destPath)

    suspend fun move(
        profile: ServerProfile,
        rootId: Long,
        paths: List<String>,
        destRootId: Long,
        destPath: String,
    ) = api(profile).move(rootId, paths, destRootId, destPath)

    suspend fun deletePreview(
        profile: ServerProfile,
        rootId: Long,
        paths: List<String>,
    ): DeletePreviewDto = api(profile).deletePreview(rootId, paths)

    suspend fun deleteConfirm(
        profile: ServerProfile,
        token: String,
        confirmCode: String?,
    ) = api(profile).deleteConfirm(token, confirmCode)

    suspend fun searchAll(
        profile: ServerProfile,
        query: String,
        cursor: String?,
        kind: String? = null,
    ): Pair<List<SearchResult>, String?> = api(profile).searchAll(query, cursor, kind = kind)

    suspend fun search(
        profile: ServerProfile,
        rootId: Long,
        query: String,
        kind: String? = null,
    ): List<SearchResult> = api(profile).search(rootId, query, kind = kind)

    suspend fun archiveZip(profile: ServerProfile, rootId: Long, dir: String, paths: List<String>, name: String) =
        api(profile).archiveZip(rootId, dir, paths, name)

    suspend fun archiveUnzip(profile: ServerProfile, rootId: Long, path: String, target: String? = null) =
        api(profile).archiveUnzip(rootId, path, target)

    suspend fun properties(profile: ServerProfile, rootId: Long, path: String): FileProperties =
        api(profile).properties(rootId, path)

    suspend fun readText(
        profile: ServerProfile,
        rootId: Long,
        path: String,
    ): TextFileDto = api(profile).readText(rootId, path)

    private suspend fun api(profile: ServerProfile): FileHubApi {
        val token = session.tokenFor(profile)
            ?: throw FileHubApiException(401, "UNAUTHORIZED", "设备授权已失效，请重新登录")
        return FileHubApi(profile.baseUrl, token)
    }
}
