package filehub.android.data

import android.content.Context
import filehub.android.R
import filehub.android.core.model.DirectoryListing
import filehub.android.core.model.FileProperties
import filehub.android.core.model.FileSort
import filehub.android.core.model.SearchResult
import filehub.android.core.model.ServerProfile
import filehub.android.core.model.SortOrder
import filehub.android.data.api.DeletePreviewDto
import filehub.android.data.api.FileHubApi
import filehub.android.data.api.FileHubApiException
import filehub.android.data.api.DirSizeDto
import filehub.android.data.api.JobAcceptedDto
import filehub.android.data.api.AudioInfoDto
import filehub.android.data.api.IndexStatusDto
import filehub.android.data.api.JobDto
import filehub.android.data.api.VersionDto
import filehub.android.data.api.UsageDto
import filehub.android.data.api.SaveTextResultDto
import filehub.android.data.api.ThumbStatusDto
import filehub.android.data.api.TranscodeDto
import filehub.android.data.api.TextFileDto
import filehub.android.data.api.TextProbeDto

class FilesRepository(
    private val context: Context,
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
        includeSizes: Boolean = true,
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
    ): JobAcceptedDto = api(profile).copy(rootId, paths, destRootId, destPath)

    suspend fun move(
        profile: ServerProfile,
        rootId: Long,
        paths: List<String>,
        destRootId: Long,
        destPath: String,
    ): JobAcceptedDto = api(profile).move(rootId, paths, destRootId, destPath)

    suspend fun thumbStatus(
        profile: ServerProfile,
        rootId: Long,
        path: String,
    ): ThumbStatusDto = api(profile).thumbStatus(rootId, path)

    suspend fun retryThumb(profile: ServerProfile, rootId: Long, path: String) =
        api(profile).retryThumb(rootId, path)

    suspend fun audioInfo(
        profile: ServerProfile,
        rootId: Long,
        path: String,
    ): AudioInfoDto = api(profile).audioInfo(rootId, path)

    suspend fun transcode(
        profile: ServerProfile,
        rootId: Long,
        path: String,
        track: Int,
    ): TranscodeDto = api(profile).transcode(rootId, path, track)

    fun transcodedUrl(profile: ServerProfile, rootId: Long, path: String, track: Int): String? =
        FileHubApi(profile.baseUrl, null).transcodedUrl(rootId, path, track)

    suspend fun usage(profile: ServerProfile): UsageDto = api(profile).usage()

    suspend fun version(profile: ServerProfile): VersionDto = api(profile).version()

    suspend fun indexStatus(
        profile: ServerProfile,
        rootId: Long,
        path: String,
    ): IndexStatusDto = api(profile).indexStatus(rootId, path)

    suspend fun startIndex(
        profile: ServerProfile,
        rootId: Long,
        path: String,
        recursive: Boolean,
    ): JobAcceptedDto = api(profile).startIndex(rootId, path, recursive)

    suspend fun jobStatus(profile: ServerProfile, id: String): JobDto =
        api(profile).jobStatus(id)

    suspend fun cancelJob(profile: ServerProfile, id: String): JobDto =
        api(profile).cancelJob(id)

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

    suspend fun archiveZip(
        profile: ServerProfile,
        rootId: Long,
        dir: String,
        paths: List<String>,
        name: String,
    ): JobAcceptedDto = api(profile).archiveZip(rootId, dir, paths, name)

    suspend fun archiveUnzip(
        profile: ServerProfile,
        rootId: Long,
        path: String,
        target: String? = null,
    ): JobAcceptedDto = api(profile).archiveUnzip(rootId, path, target)

    suspend fun dirSize(profile: ServerProfile, rootId: Long, path: String): DirSizeDto =
        api(profile).dirSize(rootId, path)

    suspend fun properties(profile: ServerProfile, rootId: Long, path: String): FileProperties =
        api(profile).properties(rootId, path)

    suspend fun readText(
        profile: ServerProfile,
        rootId: Long,
        path: String,
        encoding: String? = null,
    ): TextFileDto = api(profile).readText(rootId, path, encoding)

    suspend fun probeText(
        profile: ServerProfile,
        rootId: Long,
        path: String,
    ): TextProbeDto = api(profile).probeText(rootId, path)

    suspend fun saveText(
        profile: ServerProfile,
        rootId: Long,
        path: String,
        content: String,
        encoding: String?,
        bom: Boolean,
        etag: String?,
        force: Boolean,
    ): SaveTextResultDto = api(profile).saveText(
        rootId = rootId,
        path = path,
        content = content,
        encoding = encoding,
        bom = bom,
        etag = etag,
        force = force,
    )

    private suspend fun api(profile: ServerProfile): FileHubApi {
        val token = session.tokenFor(profile)
            ?: throw FileHubApiException(401, "UNAUTHORIZED", context.getString(R.string.error_unauthorized))
        return FileHubApi(profile.baseUrl, token)
    }
}
