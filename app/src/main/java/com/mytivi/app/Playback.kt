@file:OptIn(UnstableApi::class)

package com.mytivi.app

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MỘT trình phát dùng chung cho khung xem trước và màn hình toàn màn hình.
 * Chạm vào khung xem trước để phóng to thì hình chạy tiếp ngay, không phải tải lại luồng.
 *  - Ở màn hình chính: giới hạn 720p cho nhẹ mạng.
 *  - Toàn màn hình: bỏ giới hạn, luồng HLS tự nâng lên chất lượng cao nhất.
 */
@Stable
class Playback(context: Context, private val scope: CoroutineScope, previewMuted: Boolean = false) {
    val exo: ExoPlayer = buildPlayer(context)

    /** true = người dùng muốn phát (nút ⏯ hiện "tạm dừng"); false = đã bấm tạm dừng. */
    var playWhenReady by mutableStateOf(true)
        private set
    /** Tắt tiếng ở khung xem trước. Toàn màn hình luôn có tiếng. */
    var previewMuted by mutableStateOf(previewMuted)
        private set
    private var fullscreen = false

    /** Kênh đang phát (hoặc đang chờ tải). null = chưa chọn kênh nào. */
    var channel by mutableStateOf<Channel?>(null)
        private set
    var buffering by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    /** Tỉ lệ khung hình thật của video (dùng cho cửa sổ nổi PiP). */
    var videoAspect by mutableFloatStateOf(16f / 9f)
        private set

    private var loadJob: Job? = null
    private var retryJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            buffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) error = null
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            this@Playback.playWhenReady = playWhenReady
        }

        override fun onPlayerError(e: PlaybackException) {
            error = "Không phát được kênh này (${e.errorCodeName})"
            buffering = false
            // Lỗi mạng / mất luồng: tự thử lại sau 5 giây (lỗi tiếp thì lại thử tiếp)
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(5_000)
                if (error != null && channel != null) exo.prepare()
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }
    }

    init {
        exo.addListener(listener)
        setFullscreen(false)
    }

    /** Vào/ra toàn màn hình: đổi giới hạn chất lượng và tiếng (xem trước có thể đang tắt tiếng). */
    fun setFullscreen(full: Boolean) {
        fullscreen = full
        val b = exo.trackSelectionParameters.buildUpon()
        if (full) b.clearVideoSizeConstraints() else b.setMaxVideoSize(1280, 720)
        exo.trackSelectionParameters = b.build()
        applyVolume()
    }

    fun togglePreviewMute() {
        previewMuted = !previewMuted
        applyVolume()
    }

    private fun applyVolume() {
        exo.volume = if (!fullscreen && previewMuted) 0f else 1f
    }

    /**
     * Phát một kênh. Cùng kênh đang phát thì không tải lại.
     * delayMs: vuốt/bấm đổi kênh liên tục thì chỉ tải kênh cuối cùng (không gọi server dồn dập).
     */
    fun play(ch: Channel, delayMs: Long = 0L) {
        val same = ch.url == channel?.url
        channel = ch
        if (same && error == null && (loadJob?.isActive == true || exo.playbackState != Player.STATE_IDLE)) {
            if (!exo.playWhenReady) resume()
            return
        }
        error = null
        retryJob?.cancel()
        loadJob?.cancel()
        exo.stop()                 // tắt tiếng kênh cũ ngay
        exo.clearMediaItems()
        buffering = true
        loadJob = scope.launch {
            delay(delayMs)
            exo.setMediaItem(MediaItem.fromUri(ch.url))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    fun pause() {
        exo.playWhenReady = false
    }

    fun resume() {
        if (channel == null) return
        if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
        if (exo.isCurrentMediaItemLive) exo.seekToDefaultPosition()   // kênh live: về điểm đang phát
        exo.playWhenReady = true
    }

    fun togglePause() {
        if (exo.playWhenReady) pause() else resume()
    }

    fun stop() {
        loadJob?.cancel()
        retryJob?.cancel()
        exo.stop()
        exo.clearMediaItems()
        channel = null
        error = null
        buffering = false
    }

    fun release() {
        loadJob?.cancel()
        retryJob?.cancel()
        exo.removeListener(listener)
        exo.release()
    }
}
