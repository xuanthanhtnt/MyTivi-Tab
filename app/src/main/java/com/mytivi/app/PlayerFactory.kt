@file:OptIn(UnstableApi::class)

package com.mytivi.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

fun buildPlayer(context: Context): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent("MyTivi/1.0")
        .setAllowCrossProtocolRedirects(true)   // nhiều server IPTV redirect http -> https
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(http))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,                                // tự quản lý audio focus
        )
        .build()
        .apply { playWhenReady = true }
}

/** Bấm Home / TV tắt màn hình thì dừng phát (tránh còn tiếng chạy ngầm); quay lại thì phát tiếp. */
@Composable
fun PauseWhenBackground(player: ExoPlayer) {
    val activity = LocalContext.current as? ComponentActivity
    if (activity != null) {
        DisposableEffect(player) {
            var wasPlaying = false
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        wasPlaying = player.playWhenReady
                        player.playWhenReady = false
                    }
                    Lifecycle.Event.ON_START -> if (wasPlaying) {
                        player.seekToDefaultPosition()   // kênh live: nhảy về điểm trực tiếp
                        player.prepare()
                        player.playWhenReady = true
                        wasPlaying = false
                    }
                    else -> Unit
                }
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
    }
}
