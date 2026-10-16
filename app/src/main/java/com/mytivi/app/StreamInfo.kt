@file:OptIn(UnstableApi::class)

package com.mytivi.app

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale
import kotlin.math.roundToInt

/** Thông số kỹ thuật của luồng đang phát (giống TiviMate / UHF). */
data class StreamInfo(
    val width: Int,
    val height: Int,
    val fps: Float,
    val videoCodec: String?,
    val audioCodec: String?,
    val audioChannels: Int,
    val bitrate: Int,
)

/** Đọc thông số từ ExoPlayer; null khi chưa có hình/tiếng. */
fun streamInfoOf(exo: ExoPlayer): StreamInfo? {
    val v: Format? = exo.videoFormat
    val a: Format? = exo.audioFormat
    if (v == null && a == null) return null
    val size = exo.videoSize
    val w = if (size.width > 0) size.width else v?.width ?: Format.NO_VALUE
    val h = if (size.height > 0) size.height else v?.height ?: Format.NO_VALUE
    val br = listOfNotNull(v?.bitrate, v?.averageBitrate, v?.peakBitrate).firstOrNull { it > 0 } ?: 0
    return StreamInfo(
        width = w,
        height = h,
        fps = v?.frameRate ?: Format.NO_VALUE.toFloat(),
        videoCodec = videoCodecName(v),
        audioCodec = audioCodecName(a),
        audioChannels = a?.channelCount ?: Format.NO_VALUE,
        bitrate = br,
    )
}

private fun videoCodecName(f: Format?): String? {
    val mime = f?.sampleMimeType ?: return null
    return when (mime) {
        "video/avc" -> "H.264"
        "video/hevc" -> "H.265"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/mpeg2" -> "MPEG-2"
        "video/mp4v-es" -> "MPEG-4"
        else -> mime.substringAfter('/').uppercase(Locale.US)
    }
}

private fun audioCodecName(f: Format?): String? {
    val mime = f?.sampleMimeType ?: return null
    return when (mime) {
        "audio/mp4a-latm" -> "AAC"
        "audio/ac3" -> "AC3"
        "audio/eac3", "audio/eac3-joc" -> "E-AC3"
        "audio/mpeg" -> "MP3"
        "audio/mpeg-L2" -> "MP2"
        "audio/opus" -> "Opus"
        "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
        else -> mime.substringAfter('/').uppercase(Locale.US)
    }
}

/** Các nhãn hiển thị, ví dụ: FHD · 1920×1080 · 50 fps · H.264 · AAC 2.0 · 4.2 Mbps */
fun StreamInfo.badges(): List<String> {
    val out = ArrayList<String>()
    if (height > 0) {
        out += when {
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            height >= 1080 -> "FHD"
            height >= 720 -> "HD"
            else -> "SD"
        }
        if (width > 0) out += "${width}×$height"
    }
    if (fps > 0f) out += "${fps.roundToInt()} fps"
    videoCodec?.let { out += it }
    audioCodec?.let { codec ->
        val ch = when (audioChannels) {
            1 -> " Mono"
            2 -> " 2.0"
            6 -> " 5.1"
            8 -> " 7.1"
            else -> ""
        }
        out += codec + ch
    }
    if (bitrate > 0) out += String.format(Locale.US, "%.1f Mbps", bitrate / 1_000_000f)
    return out
}

/**
 * Theo dõi thông số luồng của một player. `key` đổi (đổi kênh) thì xóa thông số cũ
 * để không hiện nhầm thông số của kênh trước.
 */
@Composable
fun rememberStreamInfo(exo: ExoPlayer, key: Any?): StreamInfo? {
    var info by remember { mutableStateOf<StreamInfo?>(null) }
    LaunchedEffect(key) { info = null }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                info = streamInfoOf(exo)
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }
    return info
}

/** Hàng nhãn thông số dạng viền mảnh. */
@Composable
fun StreamBadges(info: StreamInfo, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        info.badges().forEachIndexed { i, label ->
            val strong = i == 0 && info.height > 0     // nhãn chất lượng (FHD/HD/4K) nổi bật hơn
            Text(
                label,
                color = if (strong) Accent else Color.White,
                fontSize = 13.sp,
                fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .border(1.dp, if (strong) Accent else Color(0x66FFFFFF), RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
