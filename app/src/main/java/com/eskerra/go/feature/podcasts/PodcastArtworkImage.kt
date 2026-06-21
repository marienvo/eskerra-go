package com.eskerra.go.feature.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadPodcastArtwork
import java.io.File

@Composable
fun PodcastArtworkImage(
    rssFeedUrl: String?,
    config: WorkspaceConfig,
    filesDir: File,
    loadPodcastArtwork: LoadPodcastArtwork,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    iconSize: Dp = 20.dp,
    placeholderBackground: Color = Color(0xFF3A3A3A),
    placeholderIconTint: Color = Color(0xFF8F8F8F),
    allowNetworkFetch: Boolean = true
) {
    var displayUri by remember(rssFeedUrl) {
        mutableStateOf(loadPodcastArtwork.peek(config, filesDir, rssFeedUrl))
    }
    LaunchedEffect(rssFeedUrl, allowNetworkFetch) {
        val url = rssFeedUrl?.trim().orEmpty()
        if (url.isEmpty()) {
            displayUri = null
            return@LaunchedEffect
        }
        displayUri = loadPodcastArtwork.peek(config, filesDir, url)
            ?: loadPodcastArtwork.resolve(config, filesDir, url, allowNetworkFetch)
    }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(placeholderBackground),
        contentAlignment = Alignment.Center
    ) {
        if (displayUri.isNullOrBlank()) {
            PlaceholderIcon(iconSize, placeholderIconTint)
        } else {
            SubcomposeAsyncImage(
                model = displayUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { PlaceholderIcon(iconSize, placeholderIconTint) },
                error = { PlaceholderIcon(iconSize, placeholderIconTint) }
            )
        }
    }
}

@Composable
private fun PlaceholderIcon(size: Dp, tint: Color) {
    Icon(
        imageVector = Icons.Outlined.MusicNote,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size)
    )
}
