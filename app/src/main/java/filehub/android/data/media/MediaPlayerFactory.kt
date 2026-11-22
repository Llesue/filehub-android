package filehub.android.data.media

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

@androidx.annotation.OptIn(UnstableApi::class)
class MediaPlayerFactory(private val context: Context) {

    fun create(token: String): ExoPlayer {
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
        val dataSourceFactory = OkHttpDataSource.Factory(client)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }
}
