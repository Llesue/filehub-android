package filehub.android.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import filehub.android.R
import filehub.android.data.playback.AudioPlayerState

@Composable
fun AudioPlayerBar(
    state: AudioPlayerState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
) {
    if (!state.hasQueue) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
        ) {
            if (state.expanded) {
                ExpandedPlayer(
                    state = state,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onCollapse = onCollapse,
                    onClose = onClose,
                )
            } else {
                CollapsedPlayer(
                    state = state,
                    onTogglePlay = onTogglePlay,
                    onExpand = onExpand,
                    onClose = onClose,
                )
            }
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CollapsedPlayer(
    state: AudioPlayerState,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onTogglePlay) {
            Icon(
                painter = painterResource(
                    if (state.playing) R.drawable.ic_pause else R.drawable.ic_play,
                ),
                contentDescription = stringResource(
                    if (state.playing) R.string.audio_pause else R.string.audio_play,
                ),
            )
        }
        IconButton(onClick = onExpand) {
            Icon(
                painter = painterResource(R.drawable.ic_expand_less),
                contentDescription = stringResource(R.string.audio_expand),
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.ic_close_player),
                contentDescription = stringResource(R.string.audio_close),
            )
        }
    }
}

@Composable
private fun ExpandedPlayer(
    state: AudioPlayerState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.count > 1) {
                    Text(
                        text = "${state.currentIndex + 1} / ${state.count}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onCollapse) {
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = stringResource(R.string.audio_collapse),
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_player),
                    contentDescription = stringResource(R.string.audio_close),
                )
            }
        }
        val duration = state.durationMs.coerceAtLeast(1)
        Slider(
            value = state.positionMs.coerceIn(0, duration).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            enabled = state.durationMs > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatPlaybackTime(state.positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatPlaybackTime(state.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_previous),
                    contentDescription = stringResource(R.string.audio_previous),
                )
            }
            Spacer(Modifier.size(12.dp))
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        painter = painterResource(
                            if (state.playing) R.drawable.ic_pause else R.drawable.ic_play,
                        ),
                        contentDescription = stringResource(
                            if (state.playing) R.string.audio_pause else R.string.audio_play,
                        ),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            IconButton(onClick = onNext) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next),
                    contentDescription = stringResource(R.string.audio_next),
                )
            }
        }
        state.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatPlaybackTime(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0) / 1000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
