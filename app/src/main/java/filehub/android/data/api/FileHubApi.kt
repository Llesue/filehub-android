package filehub.android.data.api

import android.os.Build
import filehub.android.core.model.DirectoryListing
import filehub.android.core.model.FileEntry
import filehub.android.core.model.FileRoot
import filehub.android.core.model.FileSort
import filehub.android.core.model.SortOrder
import filehub.android.core.model.SearchResult
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
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
)

@Serializable
data class AuthResponseDto(
    val user: UserDto,
    @SerialName("csrf_token") val csrfToken: String? = null,
    val auth: String? = null,
)

@Serializable
internal data class LoginRequest(
    val username: String,
    val password: String,
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
)

@Serializable
internal data class RootsResponseDto(val roots: List<RootDto>)

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
            )
        }
    }

    suspend fun list(
        rootId: Long,
        path: String,
        sort: FileSort = FileSort.Name,
        order: SortOrder = SortOrder.Asc,
        cursor: String? = null,
        limit: Int = 200,
    ): DirectoryListing {
        val query = buildMap {
            put("root", rootId.toString())
            put("path", path)
            put("sort", sort.apiValue)
            put("order", order.apiValue)
            put("limit", limit.toString())
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

    suspend fun copy(rootId: Long, paths: List<String>, destRoot: Long, destPath: String) {
        postJson(
            path = "fs/copy",
            body = TransferRequest(rootId, paths, destRoot, destPath),
            serializer = TransferRequest.serializer(),
        )
    }

    suspend fun move(rootId: Long, paths: List<String>, destRoot: Long, destPath: String) {
        postJson(
            path = "fs/move",
            body = TransferRequest(rootId, paths, destRoot, destPath),
            serializer = TransferRequest.serializer(),
        )
    }

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

    suspend fun readText(rootId: Long, path: String): TextFileDto = getJson(
        path = "file",
        deserializer = TextFileDto.serializer(),
        query = mapOf("root" to rootId.toString(), "path" to path),
    )

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
    ): T {
        val responseBody = execute(buildRequest(path, method, body, csrfToken, query))
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
    ) {
        execute(buildRequest(path, method, body, csrfToken))
    }

    private fun buildRequest(
        path: String,
        method: String,
        body: ByteArray?,
        csrfToken: String?,
        query: Map<String, String> = emptyMap(),
    ): Request {
        val requestBody = when {
            body != null -> body.toRequestBody(JSON_MEDIA_TYPE)
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

            val tokenResponse = sessionApi.createToken(csrf, deviceName)
            runCatching { sessionApi.logout(csrf) }

            val tokenApi = FileHubApi(normalized, tokenResponse.token)
            val me = tokenApi.me()
            val roots = tokenApi.roots()
            val host = normalized.toHttpUrlOrNull()?.host ?: normalized
            val displayName = branding?.name?.takeIf { it.isNotBlank() } ?: host
            val profile = ServerProfile(
                id = UUID.randomUUID().toString(),
                baseUrl = normalized,
                displayName = displayName,
                username = me.user.username,
                userId = me.user.id,
                tokenId = tokenResponse.id,
                tokenExpiresAt = tokenResponse.expiresAt,
                createdAt = System.currentTimeMillis() / 1000,
            )
            return LoginResult(profile = profile, token = tokenResponse.token, roots = roots)
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
