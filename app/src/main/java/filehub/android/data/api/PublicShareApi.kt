package filehub.android.data.api

import filehub.android.core.model.PublicShareEntry
import filehub.android.core.model.PublicShareMeta
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
internal data class PublicEntryDto(
    @SerialName("item_id") val itemId: Long? = null,
    val name: String,
    @SerialName("is_dir") val isDir: Boolean = false,
    val size: Long = 0,
    val modified: Long = 0,
    @SerialName("mime_type") val mimeType: String = "",
    @SerialName("has_preview") val hasPreview: Boolean = false,
)

@Serializable
internal data class PublicMetaDto(
    val collection: Boolean = false,
    val name: String = "",
    val path: String = "",
    @SerialName("is_dir") val isDir: Boolean = false,
    @SerialName("read_only") val readOnly: Boolean = true,
    val entries: List<PublicEntryDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
internal data class UnlockRequest(val password: String)

@Serializable
data class UnlockResponse(
    val ticket: String,
    @SerialName("expires_at") val expiresAt: Long = 0,
)

data class PublicLink(
    val baseUrl: String,
    val token: String,
)

class PublicShareApi(baseUrl: String) {
    private val baseUrl = FileHubApi.normalizeBaseUrl(baseUrl)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun unlock(token: String, password: String): UnlockResponse {
        val body = JSON.encodeToString(UnlockRequest.serializer(), UnlockRequest(password))
            .toByteArray(Charsets.UTF_8)
        val request = Request.Builder()
            .url(apiUrl("public/shares/$token/unlock"))
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        val response = execute(request)
        return decode(response, UnlockResponse.serializer())
    }

    suspend fun meta(
        token: String,
        path: String = "",
        ticket: String? = null,
        cursor: String? = null,
    ): PublicShareMeta {
        val url = apiUrl("public/shares/$token").newBuilder()
            .addQueryParameter("path", path)
            .addQueryParameter("limit", "200")
            .apply { cursor?.takeIf { it.isNotBlank() }?.let { addQueryParameter("cursor", it) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .apply { ticket?.let { header("X-Share-Ticket", it) } }
            .build()
        val response = decode(execute(request), PublicMetaDto.serializer())
        return PublicShareMeta(
            name = response.name,
            path = response.path,
            isDir = response.isDir,
            readOnly = response.readOnly,
            entries = response.entries.map {
                PublicShareEntry(
                    name = it.name,
                    isDir = it.isDir,
                    size = it.size,
                    modified = it.modified,
                    mimeType = it.mimeType,
                    hasPreview = it.hasPreview,
                )
            },
            nextCursor = response.nextCursor,
            collection = response.collection,
        )
    }

    fun downloadUrl(token: String, path: String, ticket: String?): String {
        val url = apiUrl("public/shares/$token/download").newBuilder()
            .addQueryParameter("path", path)
            .apply { ticket?.let { addQueryParameter("ticket", it) } }
            .build()
        return url.toString()
    }

    fun thumbUrl(token: String, path: String, ticket: String?): String {
        val url = apiUrl("public/shares/$token/thumb").newBuilder()
            .addQueryParameter("path", path)
            .addQueryParameter("w", "256")
            .apply { ticket?.let { addQueryParameter("ticket", it) } }
            .build()
        return url.toString()
    }

    private fun apiUrl(path: String): HttpUrl {
        val base = baseUrl.toHttpUrlOrNull() ?: error("invalid base url")
        return base.newBuilder().addPathSegments("api/v1").addPathSegments(path).build()
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    val parsed = runCatching {
                        JSON.decodeFromString(ErrorEnvelope.serializer(), body)
                    }.getOrNull()
                    throw FileHubApiException(
                        statusCode = response.code,
                        code = parsed?.error?.code ?: "HTTP_${response.code}",
                        message = parsed?.error?.message ?: "HTTP ${response.code}",
                    )
                }
                body
            }
        } catch (error: FileHubApiException) {
            throw error
        } catch (error: IOException) {
            throw FileHubApiException(0, "NETWORK_ERROR", "无法连接服务器", error)
        }
    }

    private fun <T> decode(body: String, deserializer: kotlinx.serialization.DeserializationStrategy<T>): T =
        runCatching { JSON.decodeFromString(deserializer, body) }
            .getOrElse { throw FileHubApiException(200, "INVALID_RESPONSE", "无法解析分享数据", it) }

    companion object {
        private const val USER_AGENT = "FileHub-Android/0.1.0"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun parseLink(input: String): PublicLink? {
            val url = input.trim().toHttpUrlOrNull() ?: return null
            val segments = url.pathSegments
            val index = segments.indexOf("s")
            if (index < 0 || index + 1 >= segments.size) return null
            val token = segments[index + 1]
            if (token.isBlank()) return null
            val base = url.newBuilder()
                .encodedPath("/")
                .query(null)
                .fragment(null)
                .build()
                .toString()
                .trimEnd('/')
            return PublicLink(base, token)
        }
    }
}
