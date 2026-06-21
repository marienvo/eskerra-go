package com.eskerra.go.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellMiniPlayerMountTest {

    @Test
    fun visible_onlyWhenActiveEpisodeAndPodcastMode() {
        assertFalse(shouldShowShellMiniPlayer(hasActiveEpisode = false, inPodcastMode = false))
        assertFalse(shouldShowShellMiniPlayer(hasActiveEpisode = false, inPodcastMode = true))
        assertFalse(shouldShowShellMiniPlayer(hasActiveEpisode = true, inPodcastMode = false))
        assertTrue(shouldShowShellMiniPlayer(hasActiveEpisode = true, inPodcastMode = true))
    }
}
