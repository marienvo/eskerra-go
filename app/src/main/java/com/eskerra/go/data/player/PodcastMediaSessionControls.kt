package com.eskerra.go.data.player

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton

internal const val PODCAST_SEEK_INCREMENT_MS = 10_000L

@UnstableApi
internal fun podcastMediaButtonPreferences(): List<CommandButton> = listOf(
    CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
        .setDisplayName("Back 10 seconds")
        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
        .setSlots(CommandButton.SLOT_BACK)
        .build(),
    CommandButton.Builder(CommandButton.ICON_PLAY)
        .setDisplayName("Play or pause")
        .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
        .setSlots(CommandButton.SLOT_CENTRAL)
        .build(),
    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
        .setDisplayName("Forward 10 seconds")
        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
        .setSlots(CommandButton.SLOT_FORWARD)
        .build()
)
