package com.eskerra.go.data.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastPlaybackState
import com.eskerra.go.core.player.PodcastNativePlaybackState
import com.eskerra.go.core.player.PodcastPlayerEvent
import com.eskerra.go.core.player.PodcastPlayerMachine
import com.eskerra.go.core.repository.PodcastPlayerDriver
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class Media3PodcastPlayerDriver(context: Context) : PodcastPlayerDriver {

    private val appContext = context.applicationContext
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PodcastPlaybackState())
    override val state: StateFlow<PodcastPlaybackState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var progressJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            publishNativeSnapshot()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            publishNativeSnapshot()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishNativeSnapshot()
        }

        override fun onPlayerError(error: PlaybackException) {
            reduce(PodcastPlayerEvent.PlaybackError(error.message))
        }
    }

    init {
        connect()
    }

    override fun play(episode: PodcastEpisode, startPositionMs: Long) {
        reduce(PodcastPlayerEvent.EpisodePlayRequested(episode))
        withController { mediaController ->
            val item = MediaItem.Builder()
                .setMediaId(episode.id)
                .setUri(episode.mp3Url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(episode.title)
                        .setArtist(episode.seriesName)
                        .build()
                )
                .build()
            mediaController.setMediaItem(item)
            mediaController.prepare()
            if (startPositionMs > 0L) {
                mediaController.seekTo(startPositionMs)
            }
            mediaController.play()
            publishNativeSnapshot(mediaController)
        }
    }

    override fun hydrate(episode: PodcastEpisode, positionMs: Long, durationMs: Long?) {
        reduce(PodcastPlayerEvent.PlaylistHydrated(episode, positionMs, durationMs))
    }

    override fun pause() {
        reduce(PodcastPlayerEvent.PauseRequested)
        withController { mediaController ->
            mediaController.pause()
            publishNativeSnapshot(mediaController)
        }
    }

    override fun resume() {
        reduce(PodcastPlayerEvent.ResumeRequested)
        withController { mediaController ->
            mediaController.play()
            publishNativeSnapshot(mediaController)
        }
    }

    override fun stop() {
        reduce(PodcastPlayerEvent.StopRequested)
        withController { mediaController ->
            mediaController.stop()
            mediaController.clearMediaItems()
            publishNativeSnapshot(mediaController)
        }
    }

    override fun seekBy(deltaMs: Long) {
        withController { mediaController ->
            val target = (mediaController.currentPosition + deltaMs).coerceAtLeast(0L)
            mediaController.seekTo(target)
            publishProgress(mediaController)
        }
    }

    override fun seekTo(positionMs: Long) {
        withController { mediaController ->
            mediaController.seekTo(positionMs.coerceAtLeast(0L))
            publishProgress(mediaController)
        }
    }

    override fun release() {
        progressJob?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controllerFuture?.let(MediaController::releaseFuture)
        controller = null
        controllerFuture = null
        pendingAction = null
        reduce(PodcastPlayerEvent.AppClosed)
    }

    private fun connect() {
        val serviceComponent = ComponentName(
            appContext,
            PodcastPlaybackService::class.java
        )
        val token = SessionToken(appContext, serviceComponent)
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val mediaController = future.get()
                    controller = mediaController
                    mediaController.addListener(listener)
                    publishNativeSnapshot(mediaController)
                    pendingAction?.invoke(mediaController)
                    pendingAction = null
                    startProgressTicker()
                }.onFailure { error ->
                    reduce(PodcastPlayerEvent.PlaybackError(error.message))
                }
            },
            mainExecutor
        )
    }

    private fun withController(action: (MediaController) -> Unit) {
        val mediaController = controller
        if (mediaController != null) {
            action(mediaController)
        } else {
            pendingAction = action
        }
    }

    private fun publishNativeSnapshot(mediaController: MediaController? = controller) {
        val current = mediaController ?: return
        reduce(
            PodcastPlayerEvent.NativeStateChanged(
                nativeState = current.playbackState.toNativeState(),
                playWhenReady = current.playWhenReady,
                positionMs = current.currentPosition,
                durationMs = current.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            )
        )
    }

    private fun publishProgress(mediaController: MediaController? = controller) {
        val current = mediaController ?: return
        reduce(
            PodcastPlayerEvent.ProgressChanged(
                positionMs = current.currentPosition,
                durationMs = current.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            )
        )
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                publishProgress()
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    private fun reduce(event: PodcastPlayerEvent) {
        _state.value = PodcastPlayerMachine.reduce(_state.value, event)
    }

    private fun Int.toNativeState(): PodcastNativePlaybackState = when (this) {
        Player.STATE_IDLE -> PodcastNativePlaybackState.IDLE
        Player.STATE_BUFFERING -> PodcastNativePlaybackState.BUFFERING
        Player.STATE_READY -> PodcastNativePlaybackState.READY
        Player.STATE_ENDED -> PodcastNativePlaybackState.ENDED
        else -> PodcastNativePlaybackState.ERROR
    }

    private companion object {
        const val PROGRESS_TICK_MS = 1_000L
    }
}
