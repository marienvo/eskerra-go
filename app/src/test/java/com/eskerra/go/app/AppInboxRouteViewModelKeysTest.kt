package com.eskerra.go.app

import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppInboxRouteViewModelKeysTest {

    @Test
    fun inboxAndTodayHubKeys_differForSameRemoteUri() {
        val remote = "https://git.example.com/vault.git"
        assertNotEquals(inboxViewModelKey(remote), todayHubViewModelKey(remote))
    }

    @Test
    fun inboxAndTodayHubKeys_differWhenRemoteUriNull() {
        assertNotEquals(inboxViewModelKey(null), todayHubViewModelKey(null))
    }
}
