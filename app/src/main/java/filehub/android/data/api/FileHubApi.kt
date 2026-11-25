package filehub.android.data.api

import android.os.Build
import filehub.android.core.model.DirectoryListing
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileProperties
import filehub.android.core.model.FileRoot
import filehub.android.core.model.ImageProperties
import filehub.android.core.model.FileSort
import filehub.android.core.model.SortOrder
import filehub.android.core.model.SearchResult
import filehub.android.core.model.ShareCreated
import filehub.android.core.model.ShareInfo
import filehub.android.core.model.LoginResult
import filehub.android.core.model.ServerProfile
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class FileHubApiException(
    val statusCode: Int,
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

private val JSON = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

@Serializable
data class SetupStatusDto(val initialized: Boolean)

@Serializable
data class BrandingDto(
    val name: String,
    val version: Long? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
)

@Serializable
// exposed through AuthResponseDto
data class UserDto(
    val id: Long,
    val username: String,
    val role: String = "user",
    @SerialName("quota_bytes") val quotaBytes: Long? = null,
    @SerialName("must_change_password") val mustChangePassword: Boolean = false,
)

/** Raised when the server requires a web password change before tokens can be issued. */
class PasswordChangeRequiredException(val serverUrl: String) : Exception("password change required")

@Serializable
data class AuthResponseDto(
    val user: UserDto,
    @SerialName("csrf_token") val csrfToken: String? = null,
    val auth: String? = null,
    val capabilities: ServerCapabilitiesDto? = null,
)

@Serializable
data class ServerCapabilitiesDto(
    val hash: Boolean = false,
    @SerialName("hash_batch") val hashBatch: Boolean = false,
    @SerialName("hash_upload_verify") val hashUploadVerify: Boolean = false,
    @SerialName("hash_verify_max_bytes") val hashVerifyMaxBytes: Long = 0,
    @SerialName("hash_algo") val hashAlgo: String = "sha256",
)

@Serializable
internal data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
internal data class ChangePasswordRequest(
    @SerialName("new_password") val newPassword: String,
)

@Serializable
internal data class CreateTokenRequest(
    val name: String,
    val scope: String,
    @SerialName("expires_at") val expiresAt: Long?,
)

@Serializable
internal data class TokenResponseDto(
    val id: Long,
    val token: String,
    val name: String = "",
    val prefix: String = "",
    val scope: String = "write",
    @SerialName("expires_at") val expiresAt: Long? = null,
)

@Serializable
internal data class RootDto(
    val id: Long,
    val alias: String,
    @SerialName("read_only") val readOnly: Boolean = false,
    val kind: String? = null,
    @SerialName("mount_id") val mountId: Long? = null,
    val online: Boolean? = null,
)

@Serializable
internal data class RootsResponseDto(val roots: List<RootDto>)

@Serializable
internal data class RemoteMountDto(
    val id: Long = 0,
    @SerialName("root_id") val rootId: Long = 0,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("avatar_version") val avatarVersion: Long = 0,
)

@Serializable
internal data class RemoteMountsDto(val mounts: List<RemoteMountDto> = emptyList())

@Serializable
internal data class EntryDto(
    @SerialName("item_id") val itemId: Long? = null,
    val name: String,
    @SerialName("is_dir") val isDir: Boolean = false,
    @SerialName("is_symlink") val isSymlink: Boolean = false,
    val size: Long = 0,
    val modified: Long = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("mime_type") val mimeType: String = "",
    @SerialName("has_preview") val hasPreview: Boolean = false,
    @SerialName("symlink_state") val symlinkState: String? = null,
)

@Serializable
internal data class ListResponseDto(
    val root: RootDto? = null,
    val path: String = "",
    @SerialName("read_only") val readOnly: Boolean = false,
    @SerialName("read_only_reason") val readOnlyReason: String? = null,
    val entries: List<EntryDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    val stale: Boolean = false,
)

@Serializable
internal data class MkdirRequest(val root: Long, val path: String, val name: String)

@Serializable
internal data class TouchRequest(val root: Long, val path: String, val name: String)

@Serializable
internal data class RenameRequest(
    val root: Long,
    val path: String,
    @SerialName("new_name") val newName: String,
)

@Serializable
internal data class TransferRequest(
    @SerialName("source_root") val sourceRoot: Long,
    val paths: List<String>,
    @SerialName("dest_root") val destRoot: Long,
    @SerialName("dest_path") val destPath: String?,
)

@Serializable
internal data class DeletePreviewRequest(val root: Long, val paths: List<String>)

@Serializable
internal data class DeleteConfirmRequest(
    val token: String,
    @SerialName("confirm_code") val confirmCode: String?,
)

@Serializable
data class DeleteItemDto(
    val relative: String = "",
    @SerialName("is_dir") val isDir: Boolean = false,
    val size: Long = 0,
)

@Serializable
data class DeletePreviewDto(
    val token: String,
    @SerialName("expires_at") val expiresAt: Long = 0,
    val items: List<DeleteItemDto> = emptyList(),
    @SerialName("confirm_code") val confirmCode: String = "",
    @SerialName("requires_code") val requiresCode: Boolean = false,
)

@Serializable
internal data class SearchEntryDto(
    @SerialName("root_id") val rootId: Long? = null,
    val name: String,
    val path: String,
    @SerialName("is_dir") val isDir: Boolean = false,
    val size: Long = 0,
    val modified: Long = 0,
    @SerialName("mime_type") val mimeType: String = "",
    @SerialName("has_preview") val hasPreview: Boolean = false,
)

@Serializable
internal data class SearchResponseDto(
    val entries: List<SearchEntryDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class TextFileDto(
    val content: String = "",
    val etag: String = "",
    val size: Long = 0,
    val modified: Long = 0,
    @SerialName("line_ending") val lineEnding: String = "lf",
    val writable: Boolean = false,
    @SerialName("read_only_reason") val readOnlyReason: String? = null,
    val encoding: String? = null,
    val bom: Boolean = false,
    val lossy: Boolean = false,
)

@Serializable
data class TextProbeDto(
    @SerialName("is_text") val isText: Boolean = false,
    val reason: String = "",
    val size: Long = 0,
    val encoding: String? = null,
    val bom: Boolean = false,
    val lossy: Boolean = false,
)

@Serializable
internal data class SaveTextRequest(
    val content: String,
    val encoding: String? = null,
    val bom: Boolean = false,
    val force: Boolean = false,
)

@Serializable
data class VersionDto(
    val version: String = "",
    val latest: String? = null,
    @SerialName("update_available") val updateAvailable: Boolean = false,
    @SerialName("check_enabled") val checkEnabled: Boolean = true,
)

@Serializable
data class IndexStatusDto(
    val status: String = "",
    @SerialName("entry_count") val entryCount: Long = 0,
    @SerialName("indexed_at") val indexedAt: Long? = null,
    @SerialName("total_size_bytes") val totalSizeBytes: Long = 0,
    val partial: Boolean = false,
    val job: JobDto? = null,
)

@Serializable
internal data class IndexRequest(
    val root: Long,
    val path: String? = null,
    val recursive: Boolean? = null,
)

@Serializable
data class ThumbStatusDto(
    val state: String = "",
    val reason: String? = null,
    val message: String? = null,
)

@Serializable
internal data class ThumbRetryRequest(
    val root: Long,
    val path: String,
    val edge: Int,
)

@Serializable
data class AudioTrackDto(
    val index: Int = 0,
    val codec: String = "",
    val channels: Int = 0,
    val language: String? = null,
    val default: Boolean = false,
    val supported: Boolean = false,
)

@Serializable
data class AudioInfoDto(
    val ffmpeg: Boolean = false,
    val tracks: List<AudioTrackDto> = emptyList(),
    @SerialName("default_track") val defaultTrack: Int? = null,
    @SerialName("has_supported_track") val hasSupportedTrack: Boolean = true,
)

@Serializable
internal data class TranscodeRequest(
    val root: Long,
    val path: String,
    val track: Int,
)

@Serializable
data class TranscodeDto(
    @SerialName("job_id") val jobId: String? = null,
    val ready: Boolean = false,
    val path: String = "",
    val track: Int = 0,
)

@Serializable
data class UsageDto(
    @SerialName("quota_bytes") val quotaBytes: Long? = null,
    @SerialName("used_bytes") val usedBytes: Long? = null,
    @SerialName("entry_count") val entryCount: Long? = null,
    @SerialName("max_entries") val maxEntries: Long? = null,
    val disks: List<JsonElement> = emptyList(),
    @SerialName("computed_at") val computedAt: Long? = null,
    val stale: Boolean = false,
    val computing: Boolean = false,
)

@Serializable
data class JobDto(
    val id: String = "",
    val kind: String = "",
    val status: String = "",
    val progress: Int = 0,
    val total: Int = 0,
    @SerialName("bytes_done") val bytesDone: Long = 0,
    @SerialName("bytes_total") val bytesTotal: Long = 0,
    val result: JsonElement? = null,
    val error: String? = null,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("started_at") val startedAt: Long? = null,
    @SerialName("finished_at") val finishedAt: Long? = null,
)

@Serializable
data class JobListDto(val jobs: List<JobDto> = emptyList())

@Serializable
data class JobAcceptedDto(
    @SerialName("job_id") val jobId: String = "",
    val status: String = "",
)

@Serializable
data class DirSizeDto(
    val status: String = "",
    /** Null while a local-root scan is queued or running. */
    val size: Long? = null,
    val partial: Boolean = false,
    val error: String? = null,
)

@Serializable
data class SaveTextResultDto(
    val etag: String = "",
    val size: Long = 0,
)

@Serializable
internal data class CreateUploadRequest(
    val root: Long,
    val path: String,
    val filename: String,
    @SerialName("total_size") val totalSize: Long,
    @SerialName("chunk_size") val chunkSize: Long? = null,
    val mtime: Long? = null,
    val sha256: String? = null,
    val verify: String? = null,
)

@Serializable
data class UploadCreatedDto(
    @SerialName("upload_id") val uploadId: String,
    @SerialName("chunk_size") val chunkSize: Long,
    val chunks: Int,
    @SerialName("expires_at") val expiresAt: Long = 0,
)

@Serializable
data class UploadStatusDto(
    @SerialName("upload_id") val uploadId: String,
    @SerialName("total_size") val totalSize: Long = 0,
    @SerialName("chunk_size") val chunkSize: Long = 0,
    val received: Long = 0,
    @SerialName("received_chunks") val receivedChunks: List<Int> = emptyList(),
    @SerialName("expires_at") val expiresAt: Long = 0,
    @SerialName("sha256_expected") val sha256Expected: String? = null,
    val verify: String? = null,
    @SerialName("hash_status") val hashStatus: String? = null,
    @SerialName("hash_error") val hashError: String? = null,
)

@Serializable
data class UploadCompleteDto(
    val path: String = "",
    val size: Long = 0,
    val verified: Boolean = false,
    @SerialName("hash_status") val hashStatus: String? = null,
    val sha256: String? = null,
)

@Serializable
internal data class HashDto(
    val path: String = "",
    val algo: String = "sha256",
    val status: String = "pending",
    val sha256: String? = null,
    val size: Long = 0,
    val mtime: Long = 0,
    val cached: Boolean = false,
    @SerialName("computed_at") val computedAt: Long = 0,
    @SerialName("retry_after_ms") val retryAfterMs: Long = 0,
    val error: ErrorDto? = null,
)

@Serializable
internal data class HashesRequest(
    val root: Long,
    val paths: List<String>,
    val force: Boolean = false,
)

@Serializable
internal data class HashesResponse(
    val hashes: List<HashDto> = emptyList(),
)

@Serializable
internal data class ShareInfoDto(
    val id: Long,
    val kind: String = "single",
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("real_path") val realPath: String = "",
    @SerialName("is_dir") val isDir: Boolean = false,
    @SerialName("has_password") val hasPassword: Boolean = false,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val expired: Boolean = false,
    @SerialName("read_only") val readOnly: Boolean = true,
    val revoked: Boolean = false,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("visit_count") val visitCount: Int = 0,
)

@Serializable
internal data class ShareListDto(
    val shares: List<ShareInfoDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
internal data class CreateShareRequest(
    val root: Long,
    val path: String? = null,
    val paths: List<String>? = null,
    val password: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("read_only") val readOnly: Boolean = true,
)

@Serializable
internal data class ShareCreatedDto(
    val id: Long,
    val token: String = "",
    val url: String = "",
    @SerialName("expires_at") val expiresAt: Long? = null,
    val kind: String = "single",
    @SerialName("item_count") val itemCount: Int = 0,
)

@Serializable
internal data class ShareUrlDto(
    val token: String = "",
    val url: String = "",
)

@Serializable
internal data class UpdateShareRequest(
    @SerialName("read_only") val readOnly: Boolean? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("clear_expires") val clearExpires: Boolean? = null,
    val password: String? = null,
    @SerialName("clear_password") val clearPassword: Boolean? = null,
    val revoked: Boolean? = null,
)

@Serializable
internal data class ArchiveZipRequest(
    val root: Long,
    val dir: String,
    val paths: List<String>,
    val name: String,
)

@Serializable
internal data class ArchiveUnzipRequest(
    val root: Long,
    val path: String,
    val target: String? = null,
)

@Serializable
internal data class ImagePropertiesDto(
    val width: Int? = null,
    val height: Int? = null,
    val format: String? = null,
    val exif: Map<String, JsonElement>? = null,
)

@Serializable
internal data class PropertiesDto(
    val name: String = "",
    val path: String = "",
    @SerialName("is_dir") val isDir: Boolean = false,
    val size: Long = 0,
    @SerialName("mime_type") val mimeType: String = "",
    val created: Long? = null,
    val modified: Long? = null,
    val mode: String? = null,
    val image: ImagePropertiesDto? = null,
)

@Serializable
internal data class PreferencesResponseDto(
    val preferences: PreferencesDto? = null,
)

@Serializable
internal data class PreferencesDto(
    @SerialName("avatar_version") val avatarVersion: Long = 0,
)

@Serializable
internal data class ErrorEnvelope(
    val error: ErrorDto? = null,
)

@Serializable
internal data class ErrorDto(
    val code: String? = null,
    val message: String? = null,
)

/**
 * A small, typed client for the FileHub HTTP API. Streaming uploads/downloads
 * and Range requests are added in the transfer milestone; this class keeps the
 * JSON/auth path explicit and does not hide OkHttp behind Retrofit.
 */
class FileHubApi(
    baseUrl: String,
    private val token: String? = null,
    private val client: OkHttpClient = buildClient(),
) {
    private val baseUrl = normalizeBaseUrl(baseUrl)

    suspend fun setupStatus(): SetupStatusDto = getJson(
        path = "setup/status",
        deserializer = SetupStatusDto.serializer(),
    )

    suspend fun branding(): BrandingDto = getJson(
        path = "branding",
        deserializer = BrandingDto.serializer(),
    )

    suspend fun me(): AuthResponseDto = getJson(
        path = "me",
        deserializer = AuthResponseDto.serializer(),
    )

    suspend fun roots(): List<FileRoot> {
        val response = getJson(path = "roots", deserializer = RootsResponseDto.serializer())
        return response.roots.map {
            FileRoot(
                id = it.id,
                alias = it.alias,
                readOnly = it.readOnly,
                kind = it.kind,
                mountId = it.mountId,
                online = it.online,
            )
        }
    }

    internal suspend fun remoteMounts(): List<RemoteMountDto> =
        getJson(path = "remote/mounts", deserializer = RemoteMountsDto.serializer()).mounts

    /** Adds the sharer name/avatar version used by remote root cards. */
    internal suspend fun rootsWithRemoteInfo(): List<FileRoot> {
        val roots = roots()
        if (roots.none { it.isRemote }) return roots
        val mounts = runCatching { remoteMounts() }.getOrDefault(emptyList())
        if (mounts.isEmpty()) return roots
        val byRoot = mounts.associateBy { it.rootId }
        return roots.map { root ->
            val mount = byRoot[root.id]
            if (root.isRemote && mount != null) {
                root.copy(
                    remoteOwnerName = mount.ownerName,
                    remoteAvatarVersion = mount.avatarVersion,
                )
            } else {
                root
            }
        }
    }

    suspend fun list(
        rootId: Long,
        path: String,
        sort: FileSort = FileSort.Name,
        order: SortOrder = SortOrder.Asc,
        cursor: String? = null,
        limit: Int = 200,
        includeSizes: Boolean = true,
    ): DirectoryListing {
        val query = buildMap {
            put("root", rootId.toString())
            put("path", path)
            put("sort", sort.apiValue)
            put("order", order.apiValue)
            put("limit", limit.toString())
            put("sizes", includeSizes.toString())
            cursor?.takeIf { it.isNotBlank() }?.let { put("cursor", it) }
        }
        val response = getJson(
            path = "list",
            deserializer = ListResponseDto.serializer(),
            query = query,
        )
        return DirectoryListing(
            entries = response.entries.map {
                FileEntry(
                    name = it.name,
                    isDir = it.isDir,
                    isSymlink = it.isSymlink,
                    size = it.size,
                    modified = it.modified,
                    mimeType = it.mimeType,
                    hasPreview = it.hasPreview,
                    symlinkState = it.symlinkState,
                )
            },
            nextCursor = response.nextCursor,
            readOnly = response.readOnly,
            readOnlyReason = response.readOnlyReason,
            stale = response.stale,
        )
    }

    suspend fun mkdir(rootId: Long, path: String, name: String) {
        postJson(
            path = "fs/mkdir",
            body = MkdirRequest(rootId, path, name),
            serializer = MkdirRequest.serializer(),
        )
    }

    suspend fun touch(rootId: Long, path: String, name: String) {
        postJson(
            path = "fs/touch",
            body = TouchRequest(rootId, path, name),
            serializer = TouchRequest.serializer(),
        )
    }

    suspend fun rename(rootId: Long, path: String, newName: String) {
        postJson(
            path = "fs/rename",
            body = RenameRequest(rootId, path, newName),
            serializer = RenameRequest.serializer(),
        )
    }

    suspend fun copy(
        rootId: Long,
        paths: List<String>,
        destRoot: Long,
        destPath: String,
    ): JobAcceptedDto = postJson(
        path = "fs/copy",
        body = TransferRequest(rootId, paths, destRoot, destPath),
        serializer = TransferRequest.serializer(),
        deserializer = JobAcceptedDto.serializer(),
    )

    suspend fun move(
        rootId: Long,
        paths: List<String>,
        destRoot: Long,
        destPath: String,
    ): JobAcceptedDto = postJson(
        path = "fs/move",
        body = TransferRequest(rootId, paths, destRoot, destPath),
        serializer = TransferRequest.serializer(),
        deserializer = JobAcceptedDto.serializer(),
    )

    suspend fun deletePreview(rootId: Long, paths: List<String>): DeletePreviewDto =
        postJson(
            path = "fs/delete/preview",
            body = DeletePreviewRequest(rootId, paths),
            serializer = DeletePreviewRequest.serializer(),
            deserializer = DeletePreviewDto.serializer(),
        )

    suspend fun deleteConfirm(token: String, confirmCode: String?) {
        postJson(
            path = "fs/delete",
            body = DeleteConfirmRequest(token, confirmCode),
            serializer = DeleteConfirmRequest.serializer(),
        )
    }

    suspend fun searchAll(
        query: String,
        cursor: String? = null,
        limit: Int = 100,
        kind: String? = null,
    ): Pair<List<SearchResult>, String?> {
        val params = buildMap {
            put("q", query)
            put("limit", limit.toString())
            cursor?.takeIf { it.isNotBlank() }?.let { put("cursor", it) }
            kind?.takeIf { it.isNotBlank() }?.let { put("kind", it) }
        }
        val response = getJson("search/all", SearchResponseDto.serializer(), params)
        return response.entries.map { it.toSearchResult(null) } to response.nextCursor
    }

    suspend fun search(
        rootId: Long,
        query: String,
        limit: Int = 100,
        kind: String? = null,
    ): List<SearchResult> {
        val params = buildMap {
            put("root", rootId.toString())
            put("q", query)
            put("limit", limit.toString())
            kind?.takeIf { it.isNotBlank() }?.let { put("kind", it) }
        }
        val response = getJson("search", SearchResponseDto.serializer(), params)
        return response.entries.map { it.toSearchResult(rootId) }
    }

    suspend fun probeText(rootId: Long, path: String): TextProbeDto = getJson(
        path = "file/probe",
        deserializer = TextProbeDto.serializer(),
        query = mapOf("root" to rootId.toString(), "path" to path),
    )

    suspend fun readText(
        rootId: Long,
        path: String,
        encoding: String? = null,
    ): TextFileDto = getJson(
        path = "file",
        deserializer = TextFileDto.serializer(),
        query = buildMap {
            put("root", rootId.toString())
            put("path", path)
            encoding?.takeIf { it.isNotBlank() }?.let { put("encoding", it) }
        },
    )

    suspend fun saveText(
        rootId: Long,
        path: String,
        content: String,
        encoding: String?,
        bom: Boolean,
        etag: String?,
        force: Boolean,
    ): SaveTextResultDto {
        val body = JSON.encodeToString(
            SaveTextRequest.serializer(),
            SaveTextRequest(content, encoding, bom, force),
        ).toByteArray(Charsets.UTF_8)
        return requestJson(
            path = "file",
            method = "PUT",
            body = body,
            csrfToken = null,
            deserializer = SaveTextResultDto.serializer(),
            query = mapOf("root" to rootId.toString(), "path" to path),
            headers = etag?.takeIf { it.isNotBlank() }
                ?.let { mapOf("If-Match" to it) }
                .orEmpty(),
        )
    }

    suspend fun createUpload(
        rootId: Long,
        path: String,
        filename: String,
        totalSize: Long,
        chunkSize: Long? = null,
        mtime: Long? = null,
        sha256: String? = null,
        verify: String? = null,
    ): UploadCreatedDto = postJson(
        path = "uploads",
        body = CreateUploadRequest(
            rootId,
            path,
            filename,
            totalSize,
            chunkSize,
            mtime,
            sha256,
            verify,
        ),
        serializer = CreateUploadRequest.serializer(),
        deserializer = UploadCreatedDto.serializer(),
    )

    internal suspend fun getHash(
        rootId: Long,
        path: String,
        wait: Boolean = true,
        timeoutMs: Long = 5000,
    ): HashDto = getJson(
        path = "hash",
        deserializer = HashDto.serializer(),
        query = mapOf(
            "root" to rootId.toString(),
            "path" to path,
            "wait" to wait.toString(),
            "timeout_ms" to timeoutMs.coerceIn(1, 5000).toString(),
        ),
    )

    internal suspend fun hashes(
        rootId: Long,
        paths: List<String>,
        force: Boolean = false,
    ): List<HashDto> {
        if (paths.isEmpty()) return emptyList()
        val response = postJson(
            path = "hashes",
            body = HashesRequest(rootId, paths, force),
            serializer = HashesRequest.serializer(),
            deserializer = HashesResponse.serializer(),
        )
        return response.hashes
    }

    suspend fun uploadStatus(uploadId: String): UploadStatusDto = getJson(
        path = "uploads/$uploadId",
        deserializer = UploadStatusDto.serializer(),
    )

    suspend fun uploadChunk(uploadId: String, index: Int, data: ByteArray) {
        requestUnit(
            path = "uploads/$uploadId/chunks/$index",
            method = "PUT",
            body = data,
            csrfToken = null,
            contentType = OCTET_STREAM,
        )
    }

    suspend fun completeUpload(uploadId: String): UploadCompleteDto = postJsonEmpty(
        path = "uploads/$uploadId/complete",
        deserializer = UploadCompleteDto.serializer(),
    )

    suspend fun cancelUpload(uploadId: String) {
        requestUnit(
            path = "uploads/$uploadId",
            method = "DELETE",
            body = null,
            csrfToken = null,
        )
    }

    suspend fun listShares(limit: Int = 100, cursor: String? = null): Pair<List<ShareInfo>, String?> {
        val params = buildMap {
            put("limit", limit.toString())
            cursor?.takeIf { it.isNotBlank() }?.let { put("cursor", it) }
        }
        val response = getJson("shares", ShareListDto.serializer(), params)
        return response.shares.map {
            ShareInfo(
                id = it.id,
                kind = it.kind,
                itemCount = it.itemCount,
                realPath = it.realPath,
                isDir = it.isDir,
                hasPassword = it.hasPassword,
                expiresAt = it.expiresAt,
                expired = it.expired,
                readOnly = it.readOnly,
                revoked = it.revoked,
                createdAt = it.createdAt,
                visitCount = it.visitCount,
            )
        } to response.nextCursor
    }

    suspend fun createShare(
        rootId: Long,
        paths: List<String>,
        password: String? = null,
        expiresAt: Long? = null,
        readOnly: Boolean = true,
    ): ShareCreated {
        val body = CreateShareRequest(
            root = rootId,
            path = paths.singleOrNull(),
            paths = paths.takeIf { it.size > 1 },
            password = password,
            expiresAt = expiresAt,
            readOnly = readOnly,
        )
        val response = postJson(
            path = "shares",
            body = body,
            serializer = CreateShareRequest.serializer(),
            deserializer = ShareCreatedDto.serializer(),
        )
        return ShareCreated(response.id, response.token, response.url, response.expiresAt)
    }

    suspend fun shareUrl(id: Long): ShareCreated {
        val response = getJson("shares/$id/url", ShareUrlDto.serializer())
        return ShareCreated(id, response.token, response.url, null)
    }

    suspend fun updateShare(
        id: Long,
        readOnly: Boolean? = null,
        expiresAt: Long? = null,
        clearExpires: Boolean = false,
        password: String? = null,
        clearPassword: Boolean = false,
        revoked: Boolean? = null,
    ) {
        val body = UpdateShareRequest(
            readOnly = readOnly,
            expiresAt = expiresAt,
            clearExpires = clearExpires.takeIf { it },
            password = password,
            clearPassword = clearPassword.takeIf { it },
            revoked = revoked,
        )
        val bytes = JSON.encodeToString(UpdateShareRequest.serializer(), body)
            .toByteArray(Charsets.UTF_8)
        requestUnit(path = "shares/$id", method = "PUT", body = bytes, csrfToken = null)
    }

    suspend fun rotateShare(id: Long): ShareCreated {
        val response = postJsonEmpty("shares/$id/rotate", ShareUrlDto.serializer())
        return ShareCreated(id, response.token, response.url, null)
    }

    suspend fun revokeShare(id: Long) {
        requestUnit("shares/$id", "DELETE", body = null, csrfToken = null)
    }

    suspend fun purgeShare(id: Long) {
        requestUnit("shares/$id/purge", "DELETE", body = null, csrfToken = null)
    }

    suspend fun archiveZip(
        rootId: Long,
        dir: String,
        paths: List<String>,
        name: String,
    ): JobAcceptedDto = postJson(
        path = "archive/zip",
        body = ArchiveZipRequest(rootId, dir, paths, name),
        serializer = ArchiveZipRequest.serializer(),
        deserializer = JobAcceptedDto.serializer(),
    )

    suspend fun archiveUnzip(
        rootId: Long,
        path: String,
        target: String? = null,
    ): JobAcceptedDto = postJson(
        path = "archive/unzip",
        body = ArchiveUnzipRequest(rootId, path, target),
        serializer = ArchiveUnzipRequest.serializer(),
        deserializer = JobAcceptedDto.serializer(),
    )

    suspend fun thumbStatus(rootId: Long, path: String, edge: Int = 320): ThumbStatusDto = getJson(
        path = "thumb/status",
        deserializer = ThumbStatusDto.serializer(),
        query = mapOf(
            "root" to rootId.toString(),
            "path" to path,
            "edge" to edge.toString(),
        ),
    )

    suspend fun retryThumb(rootId: Long, path: String, edge: Int = 320) {
        postJson(
            path = "thumb/retry",
            body = ThumbRetryRequest(rootId, path, edge),
            serializer = ThumbRetryRequest.serializer(),
        )
    }

    suspend fun audioInfo(rootId: Long, path: String): AudioInfoDto = getJson(
        path = "media/audio-info",
        deserializer = AudioInfoDto.serializer(),
        query = mapOf("root" to rootId.toString(), "path" to path),
    )

    suspend fun transcode(rootId: Long, path: String, track: Int): TranscodeDto = postJson(
        path = "media/transcode",
        body = TranscodeRequest(rootId, path, track),
        serializer = TranscodeRequest.serializer(),
        deserializer = TranscodeDto.serializer(),
    )

    fun transcodedUrl(rootId: Long, path: String, track: Int): String? {
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        return base.newBuilder()
            .addPathSegments("api/v1/media/transcoded")
            .addQueryParameter("root", rootId.toString())
            .addQueryParameter("path", path)
            .addQueryParameter("track", track.toString())
            .build()
            .toString()
    }

    suspend fun version(): VersionDto = getJson(
        path = "version",
        deserializer = VersionDto.serializer(),
    )

    suspend fun indexStatus(rootId: Long, path: String): IndexStatusDto = getJson(
        path = "index/status",
        deserializer = IndexStatusDto.serializer(),
        query = mapOf("root" to rootId.toString(), "path" to path),
    )

    suspend fun startIndex(rootId: Long, path: String, recursive: Boolean): JobAcceptedDto =
        postJson(
            path = "index",
            body = IndexRequest(rootId, path, recursive),
            serializer = IndexRequest.serializer(),
            deserializer = JobAcceptedDto.serializer(),
        )

    suspend fun usage(): UsageDto = getJson(
        path = "usage",
        deserializer = UsageDto.serializer(),
    )

    suspend fun listJobs(since: Long = 0): List<JobDto> = getJson(
        path = "jobs",
        deserializer = JobListDto.serializer(),
        query = mapOf("since" to since.toString()),
    ).jobs

    suspend fun jobStatus(id: String): JobDto = getJson(
        path = "jobs/$id",
        deserializer = JobDto.serializer(),
    )

    suspend fun cancelJob(id: String): JobDto = requestJson(
        path = "jobs/$id",
        method = "DELETE",
        body = null,
        csrfToken = null,
        deserializer = JobDto.serializer(),
    )

    suspend fun dirSize(rootId: Long, path: String): DirSizeDto = getJson(
        path = "fs/dir-size",
        deserializer = DirSizeDto.serializer(),
        query = mapOf("root" to rootId.toString(), "path" to path),
    )

    suspend fun properties(rootId: Long, path: String): FileProperties {
        val response = getJson(
            path = "properties",
            deserializer = PropertiesDto.serializer(),
            query = mapOf("root" to rootId.toString(), "path" to path),
        )
        return FileProperties(
            name = response.name,
            path = response.path,
            isDir = response.isDir,
            size = response.size,
            mimeType = response.mimeType,
            created = response.created,
            modified = response.modified,
            mode = response.mode,
            image = response.image?.let { image ->
                ImageProperties(
                    width = image.width,
                    height = image.height,
                    format = image.format,
                    exif = image.exif.orEmpty().mapValues { (_, value) ->
                        (value as? JsonPrimitive)?.content ?: value.toString()
                    },
                )
            },
        )
    }

    suspend fun avatarVersion(): Long? {
        val response = getJson("me/preferences", PreferencesResponseDto.serializer())
        return response.preferences?.avatarVersion
    }

    suspend fun deleteToken(tokenId: Long) {
        requestUnit(
            path = "auth/tokens/$tokenId",
            method = "DELETE",
            body = null,
            csrfToken = null,
        )
    }

    private suspend fun login(username: String, password: String): AuthResponseDto {
        val body = JSON.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(username = username, password = password),
        ).toByteArray(Charsets.UTF_8)
        return requestJson(
            path = "login",
            method = "POST",
            body = body,
            csrfToken = null,
            deserializer = AuthResponseDto.serializer(),
        )
    }

    private suspend fun createToken(csrfToken: String, deviceName: String): TokenResponseDto {
        val body = JSON.encodeToString(
            CreateTokenRequest.serializer(),
            CreateTokenRequest(
                name = deviceName.take(64),
                scope = "write",
                expiresAt = System.currentTimeMillis() / 1000 + TOKEN_LIFETIME_SECONDS,
            ),
        ).toByteArray(Charsets.UTF_8)
        return requestJson(
            path = "auth/tokens",
            method = "POST",
            body = body,
            csrfToken = csrfToken,
            deserializer = TokenResponseDto.serializer(),
        )
    }

    private suspend fun changePasswordForced(csrfToken: String, newPassword: String) {
        val body = JSON.encodeToString(
            ChangePasswordRequest.serializer(),
            ChangePasswordRequest(newPassword),
        ).toByteArray(Charsets.UTF_8)
        requestUnit(
            path = "me/password/forced",
            method = "POST",
            body = body,
            csrfToken = csrfToken,
        )
    }

    private suspend fun logout(csrfToken: String) {
        requestUnit(
            path = "logout",
            method = "POST",
            body = null,
            csrfToken = csrfToken,
        )
    }

    private suspend fun <B> postJson(
        path: String,
        body: B,
        serializer: SerializationStrategy<B>,
    ) {
        val bytes = JSON.encodeToString(serializer, body).toByteArray(Charsets.UTF_8)
        requestUnit(path = path, method = "POST", body = bytes, csrfToken = null)
    }

    private suspend fun <B, T> postJson(
        path: String,
        body: B,
        serializer: SerializationStrategy<B>,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val bytes = JSON.encodeToString(serializer, body).toByteArray(Charsets.UTF_8)
        return requestJson(
            path = path,
            method = "POST",
            body = bytes,
            csrfToken = null,
            deserializer = deserializer,
        )
    }

    private suspend fun <T> postJsonEmpty(
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): T = requestJson(
        path = path,
        method = "POST",
        body = null,
        csrfToken = null,
        deserializer = deserializer,
    )

    private suspend fun <T> getJson(
        path: String,
        deserializer: DeserializationStrategy<T>,
        query: Map<String, String> = emptyMap(),
    ): T = requestJson(
        path = path,
        method = "GET",
        body = null,
        csrfToken = null,
        deserializer = deserializer,
        query = query,
    )

    private suspend fun <T> requestJson(
        path: String,
        method: String,
        body: ByteArray?,
        csrfToken: String?,
        deserializer: DeserializationStrategy<T>,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): T {
        val responseBody = execute(buildRequest(path, method, body, csrfToken, query, headers))
        return runCatching {
            JSON.decodeFromString(deserializer, responseBody)
        }.getOrElse { error ->
            throw FileHubApiException(
                statusCode = 200,
                code = "INVALID_RESPONSE",
                message = "服务器返回了无法解析的数据",
                cause = error,
            )
        }
    }

    private suspend fun requestUnit(
        path: String,
        method: String,
        body: ByteArray?,
        csrfToken: String?,
        contentType: MediaType? = JSON_MEDIA_TYPE,
    ) {
        execute(buildRequest(path, method, body, csrfToken, contentType = contentType))
    }

    private fun buildRequest(
        path: String,
        method: String,
        body: ByteArray?,
        csrfToken: String?,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        contentType: MediaType? = JSON_MEDIA_TYPE,
    ): Request {
        val requestBody = when {
            body != null -> body.toRequestBody(contentType)
            method == "POST" || method == "PUT" || method == "PATCH" -> {
                ByteArray(0).toRequestBody(null)
            }
            else -> null
        }
        return Request.Builder()
            .url(apiUrl(path, query))
            .method(method, requestBody)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .apply {
                token?.let { header("Authorization", "Bearer $it") }
                csrfToken?.let { header("X-CSRF-Token", it) }
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build()
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throw parseError(response.code, body)
                body
            }
        } catch (error: FileHubApiException) {
            throw error
        } catch (error: IOException) {
            throw FileHubApiException(
                statusCode = 0,
                code = "NETWORK_ERROR",
                message = "无法连接服务器，请检查地址、网络或证书",
                cause = error,
            )
        }
    }

    private fun parseError(status: Int, body: String): FileHubApiException {
        val parsed = runCatching { JSON.decodeFromString(ErrorEnvelope.serializer(), body) }.getOrNull()
        return FileHubApiException(
            statusCode = status,
            code = parsed?.error?.code ?: "HTTP_$status",
            message = parsed?.error?.message?.takeIf { it.isNotBlank() } ?: "HTTP $status",
        )
    }

    private fun apiUrl(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val base = baseUrl.toHttpUrlOrNull()
            ?: throw FileHubApiException(0, "INVALID_URL", "服务器地址无效")
        val builder = base.newBuilder()
            .addPathSegments("api/v1")
            .addPathSegments(path.trimStart('/'))
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private fun SearchEntryDto.toSearchResult(rootHint: Long?): SearchResult = SearchResult(
        rootId = rootId ?: rootHint ?: 0,
        name = name,
        path = path,
        isDir = isDir,
        size = size,
        modified = modified,
        mimeType = mimeType,
        hasPreview = hasPreview,
    )

    companion object {
        private const val TOKEN_LIFETIME_SECONDS = 365L * 24 * 60 * 60
        private const val USER_AGENT = "FileHub-Android/0.1.0"
        private val OCTET_STREAM = "application/octet-stream".toMediaType()

        fun normalizeBaseUrl(input: String): String {
            var raw = input.trim()
            if (raw.isEmpty()) throw FileHubApiException(0, "INVALID_URL", "请输入服务器地址")
            if (!raw.startsWith("http://", ignoreCase = true) &&
                !raw.startsWith("https://", ignoreCase = true)
            ) {
                raw = "http://$raw"
            }
            val url = raw.toHttpUrlOrNull()
                ?: throw FileHubApiException(0, "INVALID_URL", "服务器地址格式不正确")
            var path = url.encodedPath.trimEnd('/')
            if (path.endsWith("/api/v1")) path = path.removeSuffix("/api/v1")
            return url.newBuilder()
                .encodedPath(if (path.isEmpty()) "/" else path)
                .query(null)
                .fragment(null)
                .build()
                .toString()
                .trimEnd('/')
        }

        fun deviceName(): String {
            val name = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Android" }
            return "FileHub Android · $name".take(64)
        }

        /**
         * Logs in with the password only to mint a device token, then discards
         * the session. The returned token is the only credential the app keeps.
         */
        suspend fun loginAndCreateToken(
            baseUrl: String,
            username: String,
            password: String,
            newPassword: String? = null,
            deviceName: String = deviceName(),
        ): LoginResult {
            val normalized = normalizeBaseUrl(baseUrl)
            val cookieJar = MemoryCookieJar()
            val loginClient = buildClient(cookieJar)
            val sessionApi = FileHubApi(normalized, token = null, client = loginClient)

            val setup = sessionApi.setupStatus()
            if (!setup.initialized) {
                throw FileHubApiException(
                    statusCode = 409,
                    code = "SETUP_REQUIRED",
                    message = "服务器尚未初始化，请先在浏览器中完成初始化",
                )
            }
            val branding = runCatching { sessionApi.branding() }.getOrNull()
            val auth = sessionApi.login(username, password)
            val csrf = auth.csrfToken?.takeIf { it.isNotBlank() }
                ?: throw FileHubApiException(0, "INVALID_RESPONSE", "登录响应缺少 CSRF token")
            if (auth.user.mustChangePassword) {
                if (newPassword == null) {
                    runCatching { sessionApi.logout(csrf) }
                    throw PasswordChangeRequiredException(normalized)
                }
                sessionApi.changePasswordForced(csrf, newPassword)
            }

            val tokenResponse = try {
                sessionApi.createToken(csrf, deviceName)
            } catch (error: FileHubApiException) {
                if (error.code == "PASSWORD_CHANGE_REQUIRED") {
                    if (newPassword == null) {
                        runCatching { sessionApi.logout(csrf) }
                        throw PasswordChangeRequiredException(normalized)
                    }
                    sessionApi.changePasswordForced(csrf, newPassword)
                    sessionApi.createToken(csrf, deviceName)
                } else {
                    throw error
                }
            }
            runCatching { sessionApi.logout(csrf) }

            val tokenApi = FileHubApi(normalized, tokenResponse.token)
            val me = tokenApi.me()
            val roots = tokenApi.rootsWithRemoteInfo()
            val avatarVersion = runCatching { tokenApi.avatarVersion() }.getOrNull()
            val serverVersion = runCatching { tokenApi.version() }.getOrNull()?.version
            val host = normalized.toHttpUrlOrNull()?.host ?: normalized
            val displayName = branding?.name?.takeIf { it.isNotBlank() } ?: host
            val iconUrl = branding?.iconUrl?.let { raw ->
                raw.toHttpUrlOrNull()?.toString()
                    ?: normalized.toHttpUrlOrNull()?.resolve(raw)?.toString()
            } ?: "$normalized/favicon.ico"
            val profile = ServerProfile(
                id = UUID.randomUUID().toString(),
                baseUrl = normalized,
                displayName = displayName,
                username = me.user.username,
                userId = me.user.id,
                tokenId = tokenResponse.id,
                tokenExpiresAt = tokenResponse.expiresAt,
                createdAt = System.currentTimeMillis() / 1000,
                addresses = listOf(normalized),
                iconUrl = iconUrl,
                avatarVersion = avatarVersion,
                serverVersion = serverVersion,
            )
            return LoginResult(
                profile = profile,
                token = tokenResponse.token,
                roots = roots,
                serverVersion = serverVersion,
            )
        }

        private fun buildClient(
            cookieJar: CookieJar = CookieJar.NO_COOKIES,
        ): OkHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

private class MemoryCookieJar : CookieJar {
    private val cookies = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this) {
            val bucket = this.cookies.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { cookie ->
                bucket.removeAll { it.name == cookie.name && it.path == cookie.path }
                bucket += cookie
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(this) {
        cookies[url.host].orEmpty().filter { it.matches(url) }
    }
}
