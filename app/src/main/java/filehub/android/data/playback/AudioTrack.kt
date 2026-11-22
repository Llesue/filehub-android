package filehub.android.data.playback

data class AudioTrack(
    val id: String,
    val title: String,
    val url: String,
    val mimeType: String,
    val rootId: Long,
    val path: String,
)
