@file:OptIn(UnstableApi::class)

package com.mytivi.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Kiểu hiển thị hình (nút ⛶ hoặc chụm 2 ngón để đổi). */
enum class VideoFit(val mode: Int, val label: String) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Vừa khung hình"),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Lấp đầy màn hình (cắt viền)"),
    STRETCH(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Kéo giãn"),
}

/** Thông báo nhỏ giữa màn hình khi chỉnh âm lượng / độ sáng / kiểu hình. */
private class Hud(val icon: Int, val text: String, val level: Float?)

/**
 * Xem kênh toàn màn hình trên tablet.
 *  Chạm: hiện/ẩn điều khiển (⏮ ⏯ ⏭) · Vuốt sang trái: danh sách kênh, vuốt sang phải: đóng
 *  Vuốt dọc nửa trái/phải: độ sáng/âm lượng · Chụm 2 ngón: vừa khung ↔ lấp đầy
 *  Nút ⧉: cửa sổ nổi (PiP) · Nút ☰: danh sách kênh · Nút ⓘ: xem lại mẹo cử chỉ
 *  Bàn phím (bao da bàn phím, DeX): ▲▼ đổi kênh, Enter danh sách, Space tạm dừng, Esc quay lại.
 */
@Composable
fun PlayerScreen(
    playback: Playback,
    list: List<Channel>,
    startIndex: Int,
    favorites: Set<String>,
    epg: Map<String, List<Programme>>,
    initialFit: Int,
    onFitChanged: (Int) -> Unit,
    showTips: Boolean,
    onTipsSeen: () -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val pip = LocalPip.current
    val inPip = pip?.inPip == true
    val now = rememberNow()

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, list.lastIndex)) }
    val current = list[index]
    var controls by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }          // mỗi lần chạm/đổi kênh: giữ điều khiển thêm vài giây
    var showList by remember { mutableStateOf(false) }
    var fit by remember { mutableStateOf(VideoFit.entries.getOrElse(initialFit) { VideoFit.FIT }) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var firstLoad by remember { mutableStateOf(true) }
    var tips by remember { mutableStateOf(showTips) }      // bảng mẹo cử chỉ (tự hiện lần đầu)
    val rootFocus = remember { FocusRequester() }
    val density = LocalDensity.current
    val edgePx = with(density) { 56.dp.toPx() }
    val exclusionPx = with(density) { 200.dp.toPx() }

    fun move(step: Int) {
        if (list.isNotEmpty()) index = (index + step + list.size) % list.size
    }

    fun showHud(h: Hud) {
        hud = h
        hudTick++
    }

    fun setFit(f: VideoFit) {
        if (f == fit) return
        fit = f
        onFitChanged(f.ordinal)
        showHud(Hud(R.drawable.ic_aspect, f.label, null))
    }

    // ---- Ẩn thanh trạng thái/điều hướng; bỏ giới hạn 720p của khung xem trước ----
    ImmersiveSystemBars()
    DisposableEffect(Unit) {
        playback.setFullscreen(true)
        onDispose {
            playback.setFullscreen(false)
            // Trả độ sáng về theo cài đặt của máy
            activity?.window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    // ---- Đổi kênh: lần đầu (cùng kênh đang xem trước) thì không tải lại; vuốt liên tục thì đợi 250ms ----
    LaunchedEffect(current.url) {
        playback.play(current, delayMs = if (firstLoad) 0L else 250L)
        firstLoad = false
        controls = true
        poke++
    }

    // Thông số video/âm thanh của kênh đang xem (xóa khi đổi kênh)
    val streamInfo = rememberStreamInfo(playback.exo, current.url)

    // Tự ẩn điều khiển sau 4,5 giây không chạm
    LaunchedEffect(controls, poke, showList) {
        if (controls && !showList) {
            delay(4_500)
            controls = false
        }
    }
    LaunchedEffect(hudTick) {
        if (hud != null) {
            delay(1_000)
            hud = null
        }
    }

    // ---- Cửa sổ nổi (PiP) ----
    DisposableEffect(pip) {
        pip?.setEnabled(true)
        onDispose { pip?.setEnabled(false) }
    }
    LaunchedEffect(pip, playback.videoAspect, playback.playWhenReady) {
        pip?.update(playback.videoAspect, playback.playWhenReady)
    }
    // Nút kênh trước / phát-dừng / kênh sau trong cửa sổ nổi
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.getIntExtra(EXTRA_PIP_CONTROL, 0)) {
                    PIP_PREV -> move(-1)
                    PIP_NEXT -> move(+1)
                    PIP_TOGGLE -> playback.togglePause()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // ---- Âm lượng / độ sáng bằng vuốt dọc ----
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember(audio) { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val drag = remember { FloatArray(2) }   // [giá trị lúc bắt đầu vuốt, phần đã vuốt]

    fun startVertical(left: Boolean) {
        drag[1] = 0f
        drag[0] = if (left) currentBrightness(activity) else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
    }

    fun dragVertical(left: Boolean, delta: Float) {
        drag[1] += delta * 1.3f
        val v = (drag[0] + drag[1]).coerceIn(0f, 1f)
        if (left) {
            activity?.window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = v.coerceAtLeast(0.02f) }
            }
            showHud(Hud(R.drawable.ic_brightness, "Độ sáng ${(v * 100).roundToInt()}%", v))
        } else {
            val target = (v * maxVol).roundToInt().coerceIn(0, maxVol)
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
            showHud(Hud(R.drawable.ic_volume, "Âm lượng ${target * 100 / maxVol}%", target.toFloat() / maxVol))
        }
    }

    BackHandler {
        when {
            tips -> {
                tips = false
                onTipsSeen()
            }
            showList -> {
                showList = false
            }
            else -> onClose()
        }
    }
    LaunchedEffect(showList) { if (!showList) runCatching { rootFocus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { e ->
                if (showList || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> { move(+1); true }
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> { move(-1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { showList = true; true }
                    Key.Spacebar, Key.MediaPlayPause -> { playback.togglePause(); true }
                    Key.Menu -> { onToggleFavorite(current); true }
                    Key.Info -> { controls = true; poke++; true }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        AndroidView(
            // Video không bao giờ nhận focus (nếu không, phím điều hướng sẽ bị "nuốt")
            modifier = Modifier.fillMaxSize().focusProperties { canFocus = false },
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = playback.exo
                }
            },
            update = { it.resizeMode = fit.mode },
            onRelease = { it.player = null },
        )

        // Lớp nhận cử chỉ, nằm dưới các nút điều khiển
        if (!inPip) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Vuốt sang trái bắt đầu sát mép phải: không để Android hiểu là cử chỉ "Quay lại"
                    // (Android chỉ cho chặn tối đa 200dp chiều cao mỗi mép, đặt ở giữa màn hình)
                    .systemGestureExclusion { c ->
                        val h = c.size.height.toFloat()
                        val top = ((h - exclusionPx) / 2f).coerceAtLeast(0f)
                        Rect(c.size.width - edgePx, top, c.size.width.toFloat(), (top + exclusionPx).coerceAtMost(h))
                    }
                    .videoGestures(
                        onTap = {
                            if (showList) {
                                showList = false
                            } else {
                                controls = !controls
                                poke++
                            }
                        },
                        onSwipeLeft = { showList = true },
                        onSwipeRight = { showList = false },
                        onVerticalStart = { left -> if (!showList) startVertical(left) },
                        onVerticalDrag = { left, d -> if (!showList) dragVertical(left, d) },
                        onPinch = { zoomIn -> setFit(if (zoomIn) VideoFit.ZOOM else VideoFit.FIT) },
                    ),
            )
        }

        val err = playback.error
        if (err != null) {
            Text(
                if (inPip) "Mất tín hiệu" else "$err\nĐang thử lại…",
                color = ErrorRed,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(if (inPip) 8.dp else 24.dp),
            )
        } else if (playback.buffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (!inPip) {
            ControlsOverlay(
                visible = controls && !showList,
                ch = current,
                number = index + 1,
                favorite = current.url in favorites,
                now = now,
                nowProg = nowPlaying(epg, current, now),
                nextProg = nextPlaying(epg, current, now),
                stream = streamInfo,
                playing = playback.playWhenReady,
                busy = playback.buffering || err != null,
                pipSupported = pip?.supported == true,
                fit = fit,
                onBack = onClose,
                onFavorite = {
                    onToggleFavorite(current)
                    poke++
                },
                onPip = { pip?.enter() },
                onFit = { setFit(VideoFit.entries[(fit.ordinal + 1) % VideoFit.entries.size]) },
                onList = { showList = true },
                onHelp = { tips = true },
                onPrev = { move(-1) },
                onNext = { move(+1) },
                onTogglePlay = {
                    playback.togglePause()
                    poke++
                },
            )
            HudView(hud, Modifier.align(Alignment.TopCenter).padding(top = 72.dp))

            if (showList) {
                // Nền mờ (chỉ để nhìn). Chạm ra ngoài hoặc vuốt sang phải do lớp cử chỉ bên dưới xử lý → đóng danh sách
                Box(Modifier.fillMaxSize().background(Color(0x66000000)))
            }
            ChannelPanelHost(
                visible = showList,
                list = list,
                current = index,
                epg = epg,
                now = now,
                favorites = favorites,
                onPick = { i ->
                    index = i
                    showList = false
                },
                onClose = { showList = false },
            )
            if (tips) {
                GestureTips(onDismiss = {
                    tips = false
                    onTipsSeen()
                })
            }
        }
    }
}

/** Ẩn thanh trạng thái + thanh điều hướng; vuốt từ mép màn hình thì hiện tạm. */
@Composable
private fun ImmersiveSystemBars() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val ctrl = if (window != null) WindowCompat.getInsetsController(window, view) else null
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Độ sáng hiện tại 0..1 (lấy độ sáng riêng của app nếu đã chỉnh, không thì theo cài đặt máy). */
private fun currentBrightness(activity: android.app.Activity?): Float {
    val own = activity?.window?.attributes?.screenBrightness ?: -1f
    if (own >= 0f) return own
    val sys = runCatching {
        Settings.System.getInt(activity?.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)
    return (sys / 255f).coerceIn(0f, 1f)
}

// ============================ Điều khiển trên video ============================

@Composable
private fun ControlsOverlay(
    visible: Boolean,
    ch: Channel,
    number: Int,
    favorite: Boolean,
    now: Long,
    nowProg: Programme?,
    nextProg: Programme?,
    stream: StreamInfo?,
    playing: Boolean,
    busy: Boolean,
    pipSupported: Boolean,
    fit: VideoFit,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onPip: () -> Unit,
    onFit: () -> Unit,
    onList: () -> Unit,
    onHelp: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
        ) {
            TopControls(ch, number, favorite, pipSupported, fit, onBack, onFavorite, onPip, onFit, onList, onHelp)
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
        ) {
            CenterControls(playing = playing, busy = busy, onPrev = onPrev, onNext = onNext, onTogglePlay = onTogglePlay)
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 4 },
            exit = fadeOut(tween(400)) + slideOutVertically(tween(400)) { it / 4 },
        ) {
            InfoBar(ch = ch, number = number, favorite = favorite, now = now, nowProg = nowProg, nextProg = nextProg, stream = stream)
        }
    }
}

@Composable
private fun TopControls(
    ch: Channel,
    number: Int,
    favorite: Boolean,
    pipSupported: Boolean,
    fit: VideoFit,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onPip: () -> Unit,
    onFit: () -> Unit,
    onList: () -> Unit,
    onHelp: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xD9070D18), Color.Transparent)))
            .safeDrawingPadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(R.drawable.ic_back, "Quay lại", onBack)
        Spacer(Modifier.width(12.dp))
        Text("$number", style = MaterialTheme.typography.titleLarge, color = Accent)
        Spacer(Modifier.width(12.dp))
        AsyncImage(model = ch.logo.ifBlank { null }, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            ch.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RoundIcon(
            if (favorite) R.drawable.ic_star else R.drawable.ic_star_border,
            if (favorite) "Bỏ yêu thích" else "Thêm yêu thích",
            onFavorite,
            tint = if (favorite) Gold else Color.White,
        )
        Spacer(Modifier.width(6.dp))
        if (pipSupported) {
            RoundIcon(R.drawable.ic_pip, "Cửa sổ nổi", onPip)
            Spacer(Modifier.width(6.dp))
        }
        RoundIcon(R.drawable.ic_aspect, fit.label, onFit)
        Spacer(Modifier.width(6.dp))
        RoundIcon(R.drawable.ic_list, "Danh sách kênh", onList)
        Spacer(Modifier.width(6.dp))
        RoundIcon(R.drawable.ic_info, "Mẹo cử chỉ", onHelp)
    }
}

@Composable
private fun CenterControls(
    playing: Boolean,
    busy: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(R.drawable.ic_skip_prev, "Kênh trước", onPrev, size = 60.dp, iconSize = 32.dp, background = Color(0x66000000))
        if (busy) {
            // Đang tải: chừa chỗ cho vòng xoay ở giữa màn hình
            Spacer(Modifier.size(76.dp))
        } else {
            RoundIcon(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) "Tạm dừng" else "Phát",
                onTogglePlay,
                size = 76.dp,
                iconSize = 40.dp,
                background = Color(0x66000000),
            )
        }
        RoundIcon(R.drawable.ic_skip_next, "Kênh sau", onNext, size = 60.dp, iconSize = 32.dp, background = Color(0x66000000))
    }
}

@Composable
private fun InfoBar(
    ch: Channel,
    number: Int,
    favorite: Boolean,
    now: Long,
    nowProg: Programme?,
    nextProg: Programme?,
    stream: StreamInfo?,
) {
    val dateText = remember(now / 60_000) {
        SimpleDateFormat("EEEE, dd/MM", Locale.getDefault()).format(Date(now))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to Color(0xD9070D18),
                    1f to Color(0xF2070D18),
                ),
            )
            .safeDrawingPadding()
            .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "$number. ${ch.name}" + if (favorite) "  ★" else "",
                color = Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nowProg != null) {
                Text(
                    nowProg.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Text("${hhmm(nowProg.start)} – ${hhmm(nowProg.stop)}", fontSize = 14.sp, maxLines = 1, softWrap = false)
                    LinearProgressIndicator(
                        progress = { progressOf(nowProg, now) },
                        modifier = Modifier.padding(horizontal = 12.dp).weight(1f, fill = false).width(260.dp),
                    )
                    Text(remainText(nowProg, now), color = Dim, fontSize = 14.sp, maxLines = 1, softWrap = false)
                }
            } else {
                Text(
                    "Không có thông tin chương trình",
                    style = MaterialTheme.typography.titleMedium,
                    color = Dim,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (nextProg != null) {
                Text(
                    "Tiếp theo: ${hhmm(nextProg.start)}  ${nextProg.title}",
                    color = Dim,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (stream != null) {
                StreamBadges(stream, Modifier.padding(top = 10.dp))
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(hhmm(now), fontSize = 36.sp, fontWeight = FontWeight.Light)
            Text(dateText, color = Dim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HudView(hud: Hud?, modifier: Modifier = Modifier) {
    if (hud == null) return
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xCC0B1220))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(hud.icon, null, Modifier.size(22.dp), tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(hud.text, maxLines = 1)
        val level = hud.level
        if (level != null) {
            Spacer(Modifier.width(12.dp))
            LinearProgressIndicator(progress = { level }, modifier = Modifier.width(120.dp))
        }
    }
}

// ============================ Danh sách kênh (trượt từ bên phải) ============================

@Composable
private fun ChannelPanelHost(
    visible: Boolean,
    list: List<Channel>,
    current: Int,
    epg: Map<String, List<Programme>>,
    now: Long,
    favorites: Set<String>,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelW = minOf(420.dp, maxWidth * 0.88f)
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            ChannelPanel(list, current, epg, now, favorites, onPick, onClose, Modifier.width(panelW).fillMaxHeight())
        }
    }
}

@Composable
private fun ChannelPanel(
    list: List<Channel>,
    current: Int,
    epg: Map<String, List<Programme>>,
    now: Long,
    favorites: Set<String>,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    Column(modifier.background(Color(0xF20B1220)).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Danh sách kênh (${list.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) { AppIcon(R.drawable.ic_close, "Đóng") }
        }
        LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(list) { i, ch ->
                val isCur = i == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCur) SelBg else Color.Transparent)
                        .clickable { onPick(i) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", color = Dim, fontSize = 14.sp, maxLines = 1, modifier = Modifier.width(40.dp))
                    AsyncImage(model = ch.logo.ifBlank { null }, contentDescription = null, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            ch.name,
                            color = if (isCur) Accent else Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        nowPlaying(epg, ch, now)?.let {
                            Text(it.title, color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (ch.url in favorites) {
                        AppIcon(R.drawable.ic_star, null, Modifier.size(18.dp), tint = Gold)
                    }
                }
            }
        }
    }
}

// ============================ Mẹo cử chỉ (hiện lần đầu) ============================

@Composable
private fun GestureTips(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 540.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Panel)
                .padding(24.dp),
        ) {
            Text("Điều khiển bằng cử chỉ", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            TipRow(R.drawable.ic_list, "Vuốt sang trái", "Mở danh sách kênh · vuốt sang phải để đóng")
            TipRow(R.drawable.ic_volume, "Vuốt lên / xuống ở nửa phải", "Tăng / giảm âm lượng")
            TipRow(R.drawable.ic_brightness, "Vuốt lên / xuống ở nửa trái", "Tăng / giảm độ sáng")
            TipRow(R.drawable.ic_play, "Chạm vào màn hình", "Hiện nút phát / tạm dừng, kênh trước / kênh sau")
            TipRow(R.drawable.ic_aspect, "Mở / chụm 2 ngón", "Lấp đầy màn hình / vừa khung hình")
            TipRow(R.drawable.ic_pip, "Bấm Home khi đang xem", "Thu nhỏ thành cửa sổ nổi")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Đã hiểu") }
        }
    }
}

@Composable
private fun TipRow(icon: Int, title: String, desc: String) {
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(SelBg),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, null, Modifier.size(22.dp), tint = Accent)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, color = Dim, fontSize = 14.sp)
        }
    }
}
