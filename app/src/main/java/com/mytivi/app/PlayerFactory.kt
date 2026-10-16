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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

fun buildPlayer(context: Context): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent("MyTivi/1.0")
        .setAllowCrossProtocolRedirects(true)   // nhiều server IPTV redirect http -> https
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
    // Tablet giá rẻ (chip MediaTek/Unisoc...) đôi khi khởi tạo bộ giải mã phần cứng lỗi:
    // cho phép tự chuyển sang bộ giải mã khác thay vì báo lỗi kênh.
    val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(http))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,                                // tự quản lý audio focus (có cuộc gọi thì tạm dừng)
        )
        .setHandleAudioBecomingNoisy(true)       // rút tai nghe / ngắt loa Bluetooth thì tạm dừng
        .build()
        .apply { playWhenReady = true }
}

/**
 * Về màn hình chính / tắt màn hình thì dừng phát (tránh còn tiếng chạy ngầm); quay lại thì phát tiếp.
 * Lưu ý: khi đang ở cửa sổ nổi (PiP) hoặc chia đôi màn hình, Activity chỉ bị "pause" chứ không "stop",
 * nên video vẫn chạy bình thường.
 */
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
                        // Kênh live: nhảy về điểm trực tiếp. Phim/VOD: phát tiếp từ chỗ đang xem.
                        if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
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
