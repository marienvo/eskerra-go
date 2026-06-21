package com.eskerra.go.feature.podcasts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eskerra.go.R
import com.eskerra.go.core.model.PodcastCatalogError

@Composable
internal fun podcastCatalogErrorText(error: PodcastCatalogError): String = when (error) {
    PodcastCatalogError.InvalidWorkspacePath ->
        stringResource(R.string.podcasts_error_workspace_unavailable)
    PodcastCatalogError.WorkspaceMissing ->
        stringResource(R.string.podcasts_error_workspace_missing)
    is PodcastCatalogError.LoadFailed ->
        stringResource(R.string.podcasts_error_load_failed)
}

@Composable
internal fun podcastsActionErrorText(error: PodcastsActionError): String = when (error) {
    PodcastsActionError.RefreshFailed ->
        stringResource(R.string.podcasts_error_refresh_failed)
    PodcastsActionError.MarkSelectedFailed ->
        stringResource(R.string.podcasts_error_mark_selected_failed)
}
