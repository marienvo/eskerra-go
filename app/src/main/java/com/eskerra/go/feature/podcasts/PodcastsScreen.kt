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
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
}

/** Stateless Episodes list from vault catalog data. */
@Composable
fun PodcastsScreen(
    state: PodcastsUiState,
    onRetry: () -> Unit,
    onEpisodeClick: (PodcastEpisode) -> Unit,
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
                onEpisodeClick = onEpisodeClick
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
    onEpisodeClick: (PodcastEpisode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = shellScrollContentPadding()
    ) {
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
                    showBottomDivider = !isLastRow,
                    onClick = { onEpisodeClick(episode) }
                )
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
private fun EpisodeRow(episode: PodcastEpisode, showBottomDivider: Boolean, onClick: () -> Unit) {
    val dateLabel = RelativeCalendarLabel.formatFromIsoDate(episode.date)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                    text = "Tap to play",
                    color = PodcastUiTokens.StatusMuted,
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
