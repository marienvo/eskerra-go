package com.eskerra.go.app

import com.eskerra.go.feature.podcasts.PodcastItem

/** Placeholder content for PoC-only surfaces (podcasts list, overflow menu). Not vault data. */

internal val fakePodcasts: List<PodcastItem> = listOf(
    PodcastItem(title = "Note-taking, deeply", author = "Eskerra FM"),
    PodcastItem(title = "Plain text forever", author = "Markdown Weekly"),
    PodcastItem(title = "Compose in practice", author = "Android Cafe")
)

internal const val MENU_SYNC = "Sync"
internal const val MENU_SETTINGS = "Settings"
internal const val MENU_WORKSPACES = "Workspaces"
internal const val MENU_ABOUT = "About"

internal val menuItems: List<String> = listOf(
    MENU_SYNC,
    MENU_SETTINGS,
    MENU_WORKSPACES,
    MENU_ABOUT
)
