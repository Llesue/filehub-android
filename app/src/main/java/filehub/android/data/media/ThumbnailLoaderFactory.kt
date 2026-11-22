package filehub.android.data.media

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Coil image loader bound to one FileHub profile. Thumbnails require the same
 * bearer token as the API, so the token is injected by an interceptor instead
 * of being appended to the URL.
 */
class ThumbnailLoaderFactory(private val context: Context) {

    private data class Entry(val token: String, val loader: ImageLoader)

    private val loaders = mutableMapOf<String, Entry>()

    @Synchronized
    fun get(profileId: String, token: String): ImageLoader {
        val cached = loaders[profileId]
        if (cached != null && cached.token == token) return cached.loader
        cached?.let { runCatching { it.loader.shutdown() } }
        return create(token).also { loaders[profileId] = Entry(token, it) }
    }

    @Synchronized
    fun close(profileId: String) {
        loaders.remove(profileId)?.let { entry ->
            runCatching { entry.loader.shutdown() }
        }
    }

    private fun create(token: String): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "FileHub-Android/0.1.0")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
            }
            .build()
    }
}
