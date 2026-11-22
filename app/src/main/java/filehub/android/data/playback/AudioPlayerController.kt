package filehub.android.data.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import filehub.android.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioPlayerState(
    val title: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val expanded: Boolean = false,
    val hasQueue: Boolean = false,
    val currentIndex: Int = 0,
    val count: Int = 0,
)

class AudioPlayerController(
    private val context: Context,
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var pollJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = error.message, loading = false) }
        }
    }

    fun playQueue(profileId: String, tracks: List<AudioTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        scope.launch {
            val profile = sessionRepository.state.value.profiles.firstOrNull { it.id == profileId }
                ?: return@launch
            val token = sessionRepository.tokenFor(profile) ?: return@launch
            AudioPlaybackConfig.token = token
            val items = tracks.map { track ->
                MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(track.url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .build(),
                    )
                    .build()
            }
            withController { current ->
                current.setMediaItems(items, index, 0)
                current.prepare()
                current.play()
            }
            _state.update {
                it.copy(
                    title = tracks[index].title,
                    expanded = true,
                    hasQueue = true,
                    error = null,
                    currentIndex = index,
                    count = tracks.size,
                )
            }
        }
    }

    fun togglePlayPause() {
        val current = controller ?: return
        if (current.isPlaying) current.pause() else current.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun setExpanded(expanded: Boolean) {
        _state.update { it.copy(expanded = expanded) }
    }

    fun stop() {
        val current = controller
        if (current != null) {
            current.stop()
            current.clearMediaItems()
        }
        _state.value = AudioPlayerState()
        AudioPlaybackConfig.token = null
    }

    fun release() {
        pollJob?.cancel()
        pollJob = null
        pendingAction = null
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        controllerFuture?.let { future ->
            runCatching { MediaController.releaseFuture(future) }
        }
        controllerFuture = null
        AudioPlaybackConfig.token = null
        scope.cancel()
    }

    private fun withController(action: (MediaController) -> Unit) {
        val current = controller
        if (current != null) {
            action(current)
            return
        }
        pendingAction = action
        ensureController()
    }

    private fun ensureController() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { current ->
                        controller = current
                        current.addListener(listener)
                        current.repeatMode = Player.REPEAT_MODE_ALL
                        sync(current)
                        startPolling()
                        val action = pendingAction
                        pendingAction = null
                        action?.invoke(current)
                    }
                    .onFailure { error ->
                        controllerFuture = null
                        _state.update {
                            it.copy(error = error.message, loading = false)
                        }
                    }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun startPolling() {
        if (pollJob != null) return
        pollJob = scope.launch {
            while (isActive) {
                controller?.let(::sync)
                delay(500)
            }
        }
    }

    private fun sync(player: Player) {
        val duration = player.duration
        _state.update {
            it.copy(
                title = player.currentMediaItem?.mediaMetadata?.title?.toString()
                    ?.takeIf { title -> title.isNotBlank() }
                    ?: it.title,
                playing = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = if (duration == C.TIME_UNSET) 0 else duration.coerceAtLeast(0),
                bufferedMs = player.bufferedPosition.coerceAtLeast(0),
                loading = player.playbackState == Player.STATE_BUFFERING,
                hasQueue = player.mediaItemCount > 0,
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                count = player.mediaItemCount,
                error = player.playerError?.message,
            )
        }
    }
}
