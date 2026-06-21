package com.eskerra.go.feature.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eskerra.go.R
import com.eskerra.go.app.LocalShellChromeInsets
import com.eskerra.go.core.datetime.RelativeCalendarLabel
import com.eskerra.go.core.model.PodcastCatalogError
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.model.PodcastSection
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadPodcastArtwork
import com.eskerra.go.ui.theme.DarkBackground
import java.io.File

private object PodcastUiTokens {
    val ListBackground = DarkBackground
    val SectionLabel = Color(0xFF6A6A6A)
    val MutedMeta = Color(0xFFCFCFCF)
    val Title = Color.White
    val StatusMuted = Color(0xFF9A9A9A)
    val Divider = Color(0xFF333333)
    val ArtworkPlaceholder = Color(0xFF3A3A3A)
    val Accent = Color(0xFF4FAFE6)
    val StripTrack = Color(0x1F4FAFE6)
    val SelectionBorder = Color(0xFF4FAFE6)
}

private val RefreshStripHeight = 3.dp

/** Stateless Episodes list from vault catalog data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastsScreen(
    state: PodcastsUiState,
    refreshState: PodcastRefreshState,
    config: WorkspaceConfig,
    filesDir: File,
    loadPodcastArtwork: LoadPodcastArtwork,
    showPauseToSwitchHint: Boolean = false,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onEpisodeArtworkClick: (PodcastEpisode) -> Unit,
    onClearSelection: () -> Unit,
    onMarkSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PodcastUiTokens.ListBackground)
    ) {
        RefreshStrip(refreshState)
        if (showPauseToSwitchHint) {
            Text(
                text = stringResource(R.string.podcasts_hint_pause_to_switch),
                color = PodcastUiTokens.Accent,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        val chrome = LocalShellChromeInsets.current
        val pullRefreshState = rememberPullToRefreshState()
        val listState = rememberLazyListState()
        val fillItemModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalConfiguration.current.screenHeightDp.dp)
        PullToRefreshBox(
            isRefreshing = refreshState.active,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            indicator = {
                // Visible pull/refresh affordance dropped below the floating top chrome so the
                // gesture has feedback (the prior strip-only versions showed nothing while pulling).
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = refreshState.active,
                    color = PodcastUiTokens.Accent,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = chrome.top)
                )
            }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item(key = "top-chrome") {
                    Spacer(Modifier.height(chrome.top))
                }
                when (state) {
                    PodcastsUiState.Loading -> item(key = "loading") {
                        LoadingContent(modifier = fillItemModifier)
                    }
                    PodcastsUiState.Empty -> item(key = "empty") {
                        EmptyContent(modifier = fillItemModifier)
                    }
                    is PodcastsUiState.Error -> item(key = "error") {
                        ErrorContent(
                            error = state.error,
                            onRetry = onRetry,
                            modifier = fillItemModifier
                        )
                    }
                    is PodcastsUiState.Content -> podcastCatalogItems(
                        sections = state.sections,
                        playerState = state.playerState,
                        selectedEpisodeIds = state.selectedEpisodeIds,
                        markInFlight = state.markInFlight,
                        markError = state.markError,
                        refreshError = refreshState.error,
                        config = config,
                        filesDir = filesDir,
                        loadPodcastArtwork = loadPodcastArtwork,
                        onEpisodeClick = onEpisodeClick,
                        onEpisodeArtworkClick = onEpisodeArtworkClick,
                        onClearSelection = onClearSelection,
                        onMarkSelected = onMarkSelected
                    )
                }
                item(key = "bottom-chrome") {
                    Spacer(Modifier.height(chrome.bottom))
                }
            }
        }
    }
}

@Composable
private fun RefreshStrip(refreshState: PodcastRefreshState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RefreshStripHeight)
            .background(if (refreshState.active) PodcastUiTokens.StripTrack else Color.Transparent)
    ) {
        if (!refreshState.active) return@Box
        val percent = refreshState.percent
        if (percent != null) {
            LinearProgressIndicator(
                progress = { (percent.coerceIn(0, 100)) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RefreshStripHeight),
                color = PodcastUiTokens.Accent,
                trackColor = PodcastUiTokens.StripTrack
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RefreshStripHeight),
                color = PodcastUiTokens.Accent,
                trackColor = PodcastUiTokens.StripTrack
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
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
private fun ErrorContent(
    error: PodcastCatalogError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = podcastCatalogErrorText(error),
            color = PodcastUiTokens.MutedMeta,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("Retry")
        }
    }
}

private fun LazyListScope.podcastCatalogItems(
    sections: List<PodcastSection>,
    playerState: PodcastPlaybackState,
    selectedEpisodeIds: Set<String>,
    markInFlight: Boolean,
    markError: PodcastsActionError?,
    refreshError: PodcastsActionError?,
    config: WorkspaceConfig,
    filesDir: File,
    loadPodcastArtwork: LoadPodcastArtwork,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onEpisodeArtworkClick: (PodcastEpisode) -> Unit,
    onClearSelection: () -> Unit,
    onMarkSelected: () -> Unit
) {
    val hasSelection = selectedEpisodeIds.isNotEmpty()
    item(key = "header-bar") {
        PodcastsHeaderBar(
            hasSelection = hasSelection,
            selectedCount = selectedEpisodeIds.size,
            markInFlight = markInFlight,
            onClearSelection = onClearSelection,
            onMarkSelected = onMarkSelected
        )
    }
    markError?.let { error ->
        item(key = "mark-error") {
            Text(
                text = podcastsActionErrorText(error),
                color = PodcastUiTokens.StatusMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    }
    if (refreshError != null) {
        item(key = "refresh-error") {
            Text(
                text = podcastsActionErrorText(refreshError),
                color = PodcastUiTokens.StatusMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
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
                sectionRssFeedUrl = section.rssFeedUrl,
                config = config,
                filesDir = filesDir,
                loadPodcastArtwork = loadPodcastArtwork,
                playerState = playerState,
                isSelected = episode.id in selectedEpisodeIds,
                selectionActive = hasSelection,
                markInFlight = markInFlight,
                showBottomDivider = !isLastRow,
                onArtworkClick = { onEpisodeArtworkClick(episode) },
                onRowClick = { onEpisodeClick(episode) }
            )
        }
    }
}

@Composable
private fun PodcastsHeaderBar(
    hasSelection: Boolean,
    selectedCount: Int,
    markInFlight: Boolean,
    onClearSelection: () -> Unit,
    onMarkSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasSelection) {
            IconButton(onClick = onClearSelection, enabled = !markInFlight) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Clear selection",
                    tint = Color.White
                )
            }
            Text(
                text = "$selectedCount selected",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onMarkSelected, enabled = !markInFlight) {
                if (markInFlight) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = "Archive selected episodes",
                        tint = Color.White
                    )
                }
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
    sectionRssFeedUrl: String?,
    config: WorkspaceConfig,
    filesDir: File,
    loadPodcastArtwork: LoadPodcastArtwork,
    playerState: PodcastPlaybackState,
    isSelected: Boolean,
    selectionActive: Boolean,
    markInFlight: Boolean,
    showBottomDivider: Boolean,
    onArtworkClick: () -> Unit,
    onRowClick: () -> Unit
) {
    val dateLabel = RelativeCalendarLabel.formatFromIsoDate(episode.date)
    val artworkFeedUrl = episode.rssFeedUrl ?: sectionRssFeedUrl
    val isActiveEpisode = playerState.isActiveEpisode(episode)
    val switchBlocked = playerState.locksEpisodeSwitch && !isActiveEpisode
    val rowEnabled = episodeRowEnabled(
        markInFlight = markInFlight,
        selectionActive = selectionActive,
        switchBlocked = switchBlocked,
        transportBusy = playerState.transportBusy
    )
    val rowAlpha = if (switchBlocked) 0.45f else 1f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (rowEnabled) {
                        Modifier.clickable(onClick = onRowClick)
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val artworkModifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = PodcastUiTokens.SelectionBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(enabled = !markInFlight, onClick = onArtworkClick)
            PodcastArtworkImage(
                rssFeedUrl = artworkFeedUrl,
                config = config,
                filesDir = filesDir,
                loadPodcastArtwork = loadPodcastArtwork,
                modifier = artworkModifier,
                cornerRadius = 8.dp
            )
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
                    color = if (isActiveEpisode) {
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

private fun episodeRowEnabled(
    markInFlight: Boolean,
    selectionActive: Boolean,
    switchBlocked: Boolean,
    transportBusy: Boolean
): Boolean = !markInFlight && !selectionActive && (switchBlocked || !transportBusy)

private fun rowStatusText(episode: PodcastEpisode, playerState: PodcastPlaybackState): String {
    if (!playerState.isActiveEpisode(episode)) {
        return if (playerState.locksEpisodeSwitch) {
            "Pause current episode to switch"
        } else {
            "Tap to play"
        }
    }
    return playerStatusText(playerState)
}

private fun playerStatusText(playerState: PodcastPlaybackState): String = when (playerState.phase) {
    PodcastPlaybackPhase.PRIMED,
    PodcastPlaybackPhase.PAUSED -> "Paused"
    PodcastPlaybackPhase.LOADING -> when {
        playerState.positionMs >= 10_000L -> "Resuming..."
        else -> "Starting..."
    }
    PodcastPlaybackPhase.PLAYING -> "Playing"
    PodcastPlaybackPhase.NEAR_END_PLAYING,
    PodcastPlaybackPhase.NEAR_END_PAUSED -> "Almost done"
    PodcastPlaybackPhase.ENDED -> "Ended"
    PodcastPlaybackPhase.STOPPED -> "Stopped"
    PodcastPlaybackPhase.ERROR -> playerState.errorMessage ?: "Playback error"
    PodcastPlaybackPhase.IDLE -> "Tap to play"
}
