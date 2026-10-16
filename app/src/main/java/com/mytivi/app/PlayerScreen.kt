@file:OptIn(UnstableApi::class)

package com.mytivi.app

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Xem kênh toàn màn hình.
 *  ▲ / CH+  : kênh kế tiếp        ▼ / CH-  : kênh trước đó
 *  OK / ◄ ► : mở danh sách kênh   MENU     : thêm/bỏ yêu thích
 *  INFO     : hiện lại thanh thông tin
 *  BACK     : đóng danh sách, hoặc quay về màn hình chính
 * Thanh thông tin (kênh, chương trình, thông số video) tự hiện vài giây khi vào kênh rồi ẩn.
 */
@Composable
fun PlayerScreen(
    list: List<Channel>,
    startIndex: Int,
    favorites: Set<String>,
    epg: Map<String, List<Programme>>,
    onToggleFavorite: (Channel) -> Unit,
    onPlayed: (Channel) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val now = rememberNow()
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, list.lastIndex)) }
    var showList by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(true) }
    var infoTick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(true) }
    val rootFocus = remember { FocusRequester() }
    val current = list[index]

    val exo = remember { buildPlayer(context) }
    PauseWhenBackground(exo)

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Không phát được kênh này (${e.errorCodeName})"
                retry++
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Đổi kênh: chờ 250ms để bấm liên tục không làm server bị gọi dồn dập
    LaunchedEffect(current.url) {
        error = null
        delay(250)
        exo.setMediaItem(MediaItem.fromUri(current.url))
        exo.prepare()
        onPlayed(current)
    }

    // Lỗi mạng / mất luồng: tự thử lại sau 5 giây
    LaunchedEffect(retry) {
        if (retry > 0) {
            delay(5_000)
            if (error != null) exo.prepare()
        }
    }

    // Thông số video/âm thanh của kênh đang xem (xóa khi đổi kênh)
    val streamInfo = rememberStreamInfo(exo, current.url)

    LaunchedEffect(index, infoTick) {
        showInfo = true
        delay(5_000)
        showInfo = false
    }
    // Khi đọc được thông số luồng (thường sau 1-2 giây) thì hiện lại thanh thông tin cho đủ thời gian xem
    LaunchedEffect(streamInfo != null) {
        if (streamInfo != null) infoTick++
    }

    BackHandler { if (showList) showList = false else onClose() }
    LaunchedEffect(showList) { if (!showList) runCatching { rootFocus.requestFocus() } }

    fun move(step: Int) {
        index = (index + step + list.size) % list.size
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { e ->
                if (showList || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { move(+1); true }
                    Key.DirectionDown, Key.ChannelDown -> { move(-1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                    Key.DirectionLeft, Key.DirectionRight -> { showList = true; true }
                    Key.Menu -> { onToggleFavorite(current); infoTick++; true }
                    Key.Info -> { infoTick++; true }
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
                    this.player = exo
                }
            },
        )

        if (buffering && error == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        error?.let {
            Text(
                it,
                color = ErrorRed,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000))
                    .padding(24.dp),
            )
        }
        AnimatedVisibility(
            visible = showInfo && !showList,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 4 },
            exit = fadeOut(tween(400)) + slideOutVertically(tween(400)) { it / 4 },
        ) {
            InfoBar(
                ch = current,
                number = index + 1,
                favorite = current.url in favorites,
                now = now,
                nowProg = nowPlaying(epg, current, now),
                nextProg = nextPlaying(epg, current, now),
                stream = streamInfo,
            )
        }
        if (showList) {
            ChannelPanel(
                list = list,
                current = index,
                epg = epg,
                now = now,
                onPick = { i -> index = i; showList = false },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
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
    modifier: Modifier = Modifier,
) {
    val dateText = remember(now / 60_000) {
        SimpleDateFormat("EEEE, dd/MM", Locale.getDefault()).format(Date(now))
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to Color(0xD9070D18),
                    1f to Color(0xF2070D18),
                ),
            )
            .padding(start = 48.dp, end = 48.dp, top = 64.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$number", style = MaterialTheme.typography.headlineLarge, color = Accent)
            Spacer(Modifier.width(20.dp))
            AsyncImage(model = ch.logo.ifBlank { null }, contentDescription = null, modifier = Modifier.size(72.dp))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ch.name + if (favorite) "  ★" else "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nowProg != null) {
                    Text(
                        "${hhmm(nowProg.start)} – ${hhmm(nowProg.stop)}   ${nowProg.title}",
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    LinearProgressIndicator(
                        progress = { progressOf(nowProg, now) },
                        modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 8.dp),
                    )
                }
                if (nextProg != null) {
                    Text(
                        "Tiếp theo: ${hhmm(nextProg.start)}  ${nextProg.title}",
                        color = Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (stream != null) {
                    StreamBadges(stream, Modifier.padding(top = 10.dp))
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(hhmm(now), fontSize = 40.sp, fontWeight = FontWeight.Light)
                Text(dateText, color = Dim, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ChannelPanel(
    list: List<Channel>,
    current: Int,
    epg: Map<String, List<Programme>>,
    now: Long,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val jumpFocus = remember { FocusRequester() }
    var target by remember { mutableIntStateOf(current) }     // hàng gắn jumpFocus
    var focused by remember { mutableIntStateOf(current) }    // hàng đang được chọn
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { jumpFocus.requestFocus() }
    }

    /** Cuộn vòng: nhảy tới hàng i rồi đặt focus vào đó. */
    fun jumpTo(i: Int) {
        scope.launch {
            target = i
            state.scrollToItem((i - 2).coerceAtLeast(0))
            withFrameNanos { }
            withFrameNanos { }
            runCatching { jumpFocus.requestFocus() }
        }
    }

    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxHeight()
            .width(440.dp)
            .background(Color(0xE60B1220))
            .padding(12.dp)
            .onPreviewKeyEvent { e ->
                // Ở kênh đầu bấm ▲ -> kênh cuối; ở kênh cuối bấm ▼ -> kênh đầu
                if (e.type != KeyEventType.KeyDown || list.isEmpty()) return@onPreviewKeyEvent false
                when {
                    e.key == Key.DirectionUp && focused <= 0 -> { jumpTo(list.lastIndex); true }
                    e.key == Key.DirectionDown && focused >= list.lastIndex -> { jumpTo(0); true }
                    else -> false
                }
            },
    ) {
        itemsIndexed(list) { i, ch ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (i == target) Modifier.focusRequester(jumpFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused) focused = i }
                    .tvFocusable { onPick(i) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = Dim, modifier = Modifier.width(44.dp))
                Column {
                    Text(ch.name, color = if (i == current) Accent else Color.White, maxLines = 1)
                    nowPlaying(epg, ch, now)?.let { Text(it.title, color = Dim, maxLines = 1) }
                }
            }
        }
    }
}
