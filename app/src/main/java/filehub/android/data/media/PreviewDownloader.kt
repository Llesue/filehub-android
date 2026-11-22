package filehub.android.data.media

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PreviewDownloader(private val cacheDir: File) {

    suspend fun download(url: String, token: String, name: String): File = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "previews").apply { mkdirs() }
        val target = File(dir, digest(url) + "-" + sanitize(name))
        if (target.isFile && target.length() > 0) return@withContext target

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .header("User-Agent", "FileHub-Android/0.1.0")
                        .build(),
                )
            }
            .build()
        val response = client.newCall(Request.Builder().url(url).get().build()).execute()
        response.use {
            if (!it.isSuccessful) error("preview download failed: HTTP ${it.code}")
            val body = it.body
            val tmp = File(dir, target.name + ".part")
            body.byteStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                error("failed to store preview")
            }
        }
        target
    }

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifBlank { "preview" }
}
