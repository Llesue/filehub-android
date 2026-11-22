package filehub.android.data.api

import android.os.Build
import filehub.android.core.model.FileRoot
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

    private suspend fun <T> getJson(
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): T = requestJson(
        path = path,
        method = "GET",
        body = null,
        csrfToken = null,
        deserializer = deserializer,
    )

    private suspend fun <T> requestJson(
        path: String,
        method: String,
        body: ByteArray?,
        csrfToken: String?,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val responseBody = execute(buildRequest(path, method, body, csrfToken))
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
    ): Request {
        val requestBody = when {
            body != null -> body.toRequestBody(JSON_MEDIA_TYPE)
            method == "POST" || method == "PUT" || method == "PATCH" -> {
                ByteArray(0).toRequestBody(null)
            }
            else -> null
        }
        return Request.Builder()
            .url(apiUrl(path))
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

    private fun apiUrl(path: String): HttpUrl {
        val base = baseUrl.toHttpUrlOrNull()
            ?: throw FileHubApiException(0, "INVALID_URL", "服务器地址无效")
        return base.newBuilder()
            .addPathSegments("api/v1")
            .addPathSegments(path.trimStart('/'))
            .build()
    }

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
