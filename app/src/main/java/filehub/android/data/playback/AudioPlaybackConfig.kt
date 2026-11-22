package filehub.android.data.playback

/**
 * The token lives in memory only. The playback service's OkHttp interceptor
 * reads it per request, so switching accounts does not require rebuilding the
 * player, and the token never enters MediaItem metadata or notifications.
 */
object AudioPlaybackConfig {
    @Volatile
    var token: String? = null
}
