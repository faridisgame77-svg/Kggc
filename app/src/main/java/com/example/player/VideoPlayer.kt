package com.example.player

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.viewmodel.PlaybackCommand
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    modifier: Modifier = Modifier,
    playUrl: String,
    commands: SharedFlow<PlaybackCommand>,
    onPlaybackEnded: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current

    // Keep screen on for video streaming
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Handle initial and modified URL sources
    LaunchedEffect(playUrl) {
         if (playUrl.isNotBlank()) {
             Log.d("VideoPlayer", "Loading media HLS/DASH/MP4/MKV stream: $playUrl")
             val mediaItem = MediaItem.fromUri(playUrl)
             exoPlayer.setMediaItem(mediaItem)
             exoPlayer.prepare()
             exoPlayer.play()
         }
    }

    // Register Player Listener to handle events and errors
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val message = "Playback error: ${error.localizedMessage ?: "Unknown"}"
                Log.e("VideoPlayer", message, error)
                onError(message)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    onPlaybackEnded()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Listen to network synchronization commands and map them directly into our playback state
    LaunchedEffect(commands) {
        commands.collectLatest { command ->
            Log.d("VideoPlayer", "Received synchronization command: $command")
            when (command) {
                is PlaybackCommand.Play -> {
                    val mediaItem = MediaItem.fromUri(command.url)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
                is PlaybackCommand.Pause -> {
                    exoPlayer.pause()
                }
                is PlaybackCommand.Resume -> {
                    exoPlayer.play()
                }
                is PlaybackCommand.Stop -> {
                    exoPlayer.stop()
                    onPlaybackEnded()
                }
                is PlaybackCommand.SetVolume -> {
                    // level ranges from 0.0 to 1.0
                    exoPlayer.volume = command.level.coerceIn(0f, 1f)
                }
                is PlaybackCommand.SeekTo -> {
                    exoPlayer.seekTo(command.positionMs)
                }
            }
        }
    }

    // Release player resources when leaving screen scope
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Render underlying native PlayerView
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
