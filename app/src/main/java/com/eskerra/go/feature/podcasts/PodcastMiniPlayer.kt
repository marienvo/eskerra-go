package com.eskerra.go.feature.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eskerra.go.core.datetime.RelativeCalendarLabel
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadPodcastArtwork
import java.io.File

private object MiniPlayerTokens {
    val Background = Color(0xFF1D1D1D)
    val Border = Color(0xFF2D2D2D)
    val Title = Color.White
    val Muted = Color(0xB8FFFFFF)
    val Track = Color(0xFF383838)
    val Accent = Color(0xFF4FAFE6)
    val Placeholder = Color(0xFF3A3A3A)
}

/** Global now-playing chrome rendered above the bottom taskbar (spec §13). */
@Composable
fun PodcastMiniPlayer(
    playerState: PodcastPlaybackState,
    config: WorkspaceConfig,
    filesDir: File,
    loadPodcastArtwork: LoadPodcastArtwork,
    artworkSelectionMode: Boolean,
    onArtworkSelectionToggle: () -> Unit,
    onArchiveActiveEpisode: () -> Unit,
    onDismissActiveEpisode: () -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    markInFlight: Boolean = false,
    markError: PodcastsActionError? = null,
    modifier: Modifier = Modifier
) {
    val episode = playerState.activeEpisode ?: return
    var sliderPosition by remember(playerState.activeEpisode?.id) {
        mutableFloatStateOf(positionToSlider(playerState))
    }
    var seeking by remember { mutableStateOf(false) }
    if (!seeking) {
        sliderPosition = positionToSlider(playerState)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MiniPlayerTokens.Background)
            .border(width = 1.dp, color = MiniPlayerTokens.Border)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val artworkModifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (artworkSelectionMode) {
                        Modifier.border(2.dp, MiniPlayerTokens.Accent, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onArtworkSelectionToggle)
            PodcastArtworkImage(
                rssFeedUrl = episode.rssFeedUrl,
                config = config,
                filesDir = filesDir,
                loadPodcastArtwork = loadPodcastArtwork,
                modifier = artworkModifier,
                cornerRadius = 8.dp,
                iconSize = 28.dp,
                placeholderBackground = MiniPlayerTokens.Placeholder
            )
            if (artworkSelectionMode) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onArchiveActiveEpisode, enabled = !markInFlight) {
                        if (markInFlight) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Archive, contentDescription = "Archive episode")
                        }
                    }
                    IconButton(onClick = onDismissActiveEpisode, enabled = !markInFlight) {
                        Icon(Icons.Outlined.Close, contentDescription = "Stop playback")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = episode.title,
                        color = MiniPlayerTokens.Title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = episode.seriesName,
                        color = MiniPlayerTokens.Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleFor(playerState),
                        color = MiniPlayerTokens.Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        markError?.let { error ->
            Text(
                text = podcastsActionErrorText(error),
                color = MiniPlayerTokens.Muted,
                fontSize = 11.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Slider(
            value = sliderPosition,
            onValueChange = {
                seeking = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                seeking = false
                onSeekTo(sliderToPosition(playerState, sliderPosition))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MiniPlayerTokens.Accent,
                activeTrackColor = MiniPlayerTokens.Accent,
                inactiveTrackColor = MiniPlayerTokens.Track
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatElapsed(playerState.positionMs),
                color = MiniPlayerTokens.Muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onSeekBy(-10_000L) },
                enabled = !playerState.transportBusy || seeking
            ) {
                Icon(Icons.Outlined.Replay10, contentDescription = "Back 10 seconds")
            }
            IconButton(
                onClick = if (playerState.isPlaying) onPausePlayback else onResumePlayback,
                enabled = !playerState.transportBusy || seeking,
                modifier = Modifier.size(52.dp)
            ) {
                if (playerState.transportBusy && !seeking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (playerState.isPlaying) {
                            Icons.Outlined.Pause
                        } else {
                            Icons.Outlined.PlayArrow
                        },
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = MiniPlayerTokens.Accent,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            IconButton(
                onClick = { onSeekBy(10_000L) },
                enabled = !playerState.transportBusy || seeking
            ) {
                Icon(Icons.Outlined.Forward10, contentDescription = "Forward 10 seconds")
            }
            Text(
                text = playerState.durationMs?.let(::formatElapsed) ?: "--:--",
                color = MiniPlayerTokens.Muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun subtitleFor(playerState: PodcastPlaybackState): String {
    val episode = playerState.activeEpisode ?: return ""
    return when (playerState.phase) {
        PodcastPlaybackPhase.NEAR_END_PLAYING,
        PodcastPlaybackPhase.NEAR_END_PAUSED -> "Almost done"
        PodcastPlaybackPhase.LOADING -> when {
            playerState.positionMs >= 10_000L -> "Resuming..."
            playerState.positionMs < 10_000L -> "Buffering..."
            playerState.transportBusy -> "Starting..."
            else -> "Buffering..."
        }
        PodcastPlaybackPhase.PAUSED ->
            if (playerState.transportBusy) {
                "Starting..."
            } else {
                RelativeCalendarLabel.formatFromIsoDate(episode.date)
            }
        else -> RelativeCalendarLabel.formatFromIsoDate(episode.date)
    }
}

private fun positionToSlider(playerState: PodcastPlaybackState): Float {
    val duration = playerState.durationMs ?: return 0f
    if (duration <= 0L) return 0f
    return (playerState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

private fun sliderToPosition(playerState: PodcastPlaybackState, slider: Float): Long {
    val duration = playerState.durationMs ?: return playerState.positionMs
    return (duration * slider).toLong().coerceIn(0L, duration)
}

private fun formatElapsed(positionMs: Long): String {
    val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
