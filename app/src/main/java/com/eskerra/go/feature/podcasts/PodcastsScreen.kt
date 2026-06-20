package com.eskerra.go.feature.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eskerra.go.app.shellScrollContentPadding
import com.eskerra.go.core.datetime.RelativeCalendarLabel
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.PodcastSection
import com.eskerra.go.ui.theme.DarkBackground

private object PodcastUiTokens {
    val ListBackground = DarkBackground
    val SectionLabel = Color(0xFF6A6A6A)
    val MutedMeta = Color(0xFFCFCFCF)
    val Title = Color.White
    val StatusMuted = Color(0xFF9A9A9A)
    val Divider = Color(0xFF333333)
    val ArtworkPlaceholder = Color(0xFF3A3A3A)
    val ArtworkIcon = Color(0xFF8F8F8F)
    val Accent = Color(0xFF4FAFE6)
}

/** Stateless Episodes list from vault catalog data. */
@Composable
fun PodcastsScreen(
    state: PodcastsUiState,
    onRetry: () -> Unit,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onStopPlayback: () -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PodcastUiTokens.ListBackground)
    ) {
        when (state) {
            PodcastsUiState.Loading -> LoadingContent()
            PodcastsUiState.Empty -> EmptyContent()
            is PodcastsUiState.Error -> ErrorContent(message = state.message, onRetry = onRetry)
            is PodcastsUiState.Content -> CatalogContent(
                sections = state.sections,
                playerState = state.playerState,
                onEpisodeClick = onEpisodeClick,
                onPausePlayback = onPausePlayback,
                onResumePlayback = onResumePlayback,
                onStopPlayback = onStopPlayback,
                onSeekBy = onSeekBy
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(shellScrollContentPadding()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No unplayed podcast episodes found in vault root.",
            color = PodcastUiTokens.MutedMeta,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(shellScrollContentPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(text = message, color = PodcastUiTokens.MutedMeta, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("Retry")
        }
    }
}

@Composable
private fun CatalogContent(
    sections: List<PodcastSection>,
    playerState: PodcastPlaybackState,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onStopPlayback: () -> Unit,
    onSeekBy: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = shellScrollContentPadding()
    ) {
        if (playerState.hasActiveEpisode) {
            item(key = "now-playing") {
                NowPlayingCard(
                    playerState = playerState,
                    onPausePlayback = onPausePlayback,
                    onResumePlayback = onResumePlayback,
                    onStopPlayback = onStopPlayback,
                    onSeekBy = onSeekBy
                )
            }
        }
        sections.forEachIndexed { sectionIndex, section ->
            item(key = "header-${section.title}") {
                SectionHeader(title = section.title)
            }
            items(
                items = section.episodes,
                key = { episode -> episode.id }
            ) { episode ->
                val isLastRow = sectionIndex == sections.lastIndex &&
                    episode == section.episodes.lastOrNull()
                EpisodeRow(
                    episode = episode,
                    playerState = playerState,
                    showBottomDivider = !isLastRow,
                    onClick = { onEpisodeClick(episode) }
                )
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    playerState: PodcastPlaybackState,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onStopPlayback: () -> Unit,
    onSeekBy: (Long) -> Unit
) {
    val episode = playerState.activeEpisode ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1D1D1D))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = episode.title,
            color = PodcastUiTokens.Title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${episode.seriesName} - ${playerStatusText(playerState)}",
            color = PodcastUiTokens.MutedMeta,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatElapsed(playerState.positionMs),
                color = PodcastUiTokens.MutedMeta,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onSeekBy(-10_000L) }, enabled = !playerState.transportBusy) {
                Icon(Icons.Outlined.Replay10, contentDescription = "Back 10 seconds")
            }
            IconButton(
                onClick = if (playerState.isPlaying) onPausePlayback else onResumePlayback,
                enabled = !playerState.transportBusy
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) {
                        Icons.Outlined.Pause
                    } else {
                        Icons.Outlined.PlayArrow
                    },
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    tint = PodcastUiTokens.Accent
                )
            }
            IconButton(onClick = { onSeekBy(10_000L) }, enabled = !playerState.transportBusy) {
                Icon(Icons.Outlined.Forward10, contentDescription = "Forward 10 seconds")
            }
            IconButton(onClick = onStopPlayback, enabled = !playerState.transportBusy) {
                Icon(Icons.Outlined.Stop, contentDescription = "Stop")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = PodcastUiTokens.Divider)
        Text(
            text = title.uppercase(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            color = PodcastUiTokens.SectionLabel,
            fontSize = 10.sp,
            letterSpacing = 0.9.sp,
            textAlign = TextAlign.Center
        )
        HorizontalDivider(color = PodcastUiTokens.Divider)
    }
}

@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    playerState: PodcastPlaybackState,
    showBottomDivider: Boolean,
    onClick: () -> Unit
) {
    val dateLabel = RelativeCalendarLabel.formatFromIsoDate(episode.date)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !playerState.transportBusy, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PodcastUiTokens.ArtworkPlaceholder),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = PodcastUiTokens.ArtworkIcon,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = episode.title,
                    color = PodcastUiTokens.Title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${episode.seriesName} - $dateLabel",
                    color = PodcastUiTokens.MutedMeta,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = rowStatusText(episode, playerState),
                    color = if (playerState.isActiveEpisode(episode)) {
                        PodcastUiTokens.Accent
                    } else {
                        PodcastUiTokens.StatusMuted
                    },
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (showBottomDivider) {
            HorizontalDivider(color = PodcastUiTokens.Divider)
        }
    }
}

private fun rowStatusText(episode: PodcastEpisode, playerState: PodcastPlaybackState): String {
    if (!playerState.isActiveEpisode(episode)) return "Tap to play"
    return playerStatusText(playerState)
}

private fun playerStatusText(playerState: PodcastPlaybackState): String = when (playerState.phase) {
    PodcastPlaybackPhase.LOADING -> when {
        playerState.positionMs >= 10_000L -> "Resuming..."
        else -> "Starting..."
    }
    PodcastPlaybackPhase.PLAYING -> "Playing"
    PodcastPlaybackPhase.PAUSED -> "Paused"
    PodcastPlaybackPhase.NEAR_END_PLAYING,
    PodcastPlaybackPhase.NEAR_END_PAUSED -> "Almost done"
    PodcastPlaybackPhase.ENDED -> "Ended"
    PodcastPlaybackPhase.STOPPED -> "Stopped"
    PodcastPlaybackPhase.ERROR -> playerState.errorMessage ?: "Playback error"
    PodcastPlaybackPhase.IDLE -> "Tap to play"
}

private fun formatElapsed(positionMs: Long): String {
    val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
