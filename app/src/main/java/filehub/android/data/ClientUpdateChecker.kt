package filehub.android.data

import android.content.Context
import filehub.android.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class ClientRelease(
    val version: String,
    val url: String,
    val checkedAt: Long,
)

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

/**
 * Reads the stable Android release from GitHub. The cached result is used for
 * 24 hours; a manual refresh bypasses the TTL.
 */
class ClientUpdateChecker(private val context: Context) {
    private val preferences =
        context.getSharedPreferences("client_update", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun cached(): ClientRelease? {
        val version = preferences.getString(KEY_VERSION, null) ?: return null
        val url = preferences.getString(KEY_URL, null) ?: return null
        val checkedAt = preferences.getLong(KEY_CHECKED_AT, 0)
        return ClientRelease(version, url, checkedAt)
    }

    fun isFresh(release: ClientRelease): Boolean =
        System.currentTimeMillis() - release.checkedAt < TTL_MS

    suspend fun check(): ClientRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPOSITORY/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FileHub-Android/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body.string()
            val release = json.decodeFromString<GitHubRelease>(body)
            if (release.draft || release.prerelease) error("not a stable release")
            val version = release.tagName.trim().removePrefix("v").trim()
            if (version.isEmpty() || release.htmlUrl.isEmpty()) error("invalid release")
            val result = ClientRelease(
                version = version,
                url = release.htmlUrl,
                checkedAt = System.currentTimeMillis(),
            )
            preferences.edit()
                .putString(KEY_VERSION, result.version)
                .putString(KEY_URL, result.url)
                .putLong(KEY_CHECKED_AT, result.checkedAt)
                .apply()
            result
        }
    }

    companion object {
        private const val REPOSITORY = "Llesue/filehub-android"
        private const val KEY_VERSION = "version"
        private const val KEY_URL = "url"
        private const val KEY_CHECKED_AT = "checked_at"
        private const val TTL_MS = 24L * 60 * 60 * 1000

        /**
         * Numeric segment comparison: 0.1.2 > 0.1.1, 0.10.0 > 0.9.9.
         * Non-numeric suffixes are ignored so `1.2.3-debug` compares as 1.2.3.
         */
        fun isNewer(candidate: String, current: String): Boolean {
            val left = parse(candidate)
            val right = parse(current)
            val size = maxOf(left.size, right.size)
            for (index in 0 until size) {
                val a = left.getOrElse(index) { 0 }
                val b = right.getOrElse(index) { 0 }
                if (a != b) return a > b
            }
            return false
        }

        private fun parse(value: String): List<Int> =
            value.trim()
                .removePrefix("v")
                .substringBefore('-')
                .substringBefore('+')
                .split('.')
                .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
    }
}
