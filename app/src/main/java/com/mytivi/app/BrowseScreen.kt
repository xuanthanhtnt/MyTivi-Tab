@file:OptIn(UnstableApi::class)

package com.mytivi.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---- Kích thước bảng lịch (chỉnh ở đây nếu muốn) ----
private val LEFT_W = 330.dp          // cột số kênh + logo + tên
private val ROW_H = 58.dp            // chiều cao mỗi hàng kênh
private const val DP_PER_MIN = 6.4f  // 30 phút ≈ 192dp
private const val GUIDE_HOURS = 12   // bảng lịch hiển thị 12 giờ kể từ bây giờ

/**
 * Màn hình chính kiểu TiviMate: khung xem trước + thông tin chương trình ở trên,
 * thanh thời gian và bảng kênh/chương trình ở dưới.
 */
@Composable
fun BrowseScreen(
    vm: MainViewModel,
    updates: UpdateViewModel,
    onPlay: (List<Channel>, Int) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val channels = vm.channels
    val favorites = vm.favorites
    val epg = vm.epg
    val now = rememberNow()

    var sel by remember { mutableIntStateOf(1) }          // 0 = Yêu thích, 1 = Tất cả, 2.. = nhóm
    var query by remember { mutableStateOf("") }
    var showDrawer by remember { mutableStateOf(false) }
    val groups = remember(channels) { channels.map { it.group }.distinct() }
    val labels = remember(groups) { listOf("★ Yêu thích", "Tất cả") + groups }
    val counts = remember(channels, favorites, groups) {
        val byGroup = channels.groupingBy { it.group }.eachCount()
        listOf(channels.count { it.url in favorites }, channels.size) + groups.map { byGroup[it] ?: 0 }
    }
    val shown = remember(channels, favorites, groups, sel, query) {
        val base = when (sel) {
            0 -> channels.filter { it.url in favorites }
            1 -> channels
            else -> groups.getOrNull(sel - 2)?.let { g -> channels.filter { it.group == g } } ?: channels
        }
        if (query.isBlank()) base else base.filter { it.name.contains(query, ignoreCase = true) }
    }

    // ---- Cửa sổ thời gian của bảng lịch ----
    // Mốc đầu bảng lịch tự dời theo mỗi nửa giờ (box mở cả ngày vẫn đúng giờ)
    val windowStart = remember(now / HALF_HOUR) { floorTo(now, HALF_HOUR) }
    val windowEnd = windowStart + GUIDE_HOURS * HOUR
    val scroll = rememberScrollState()          // dùng chung: kéo ngang là mọi hàng cùng trượt
    val listState = rememberLazyListState()

    // ---- Kênh / chương trình đang được chọn ----
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    var focusedNumber by remember { mutableIntStateOf(0) }
    var focusedProg by remember { mutableStateOf<Programme?>(null) }

    // ---- Xem trước: có tiếng, độ phân giải thấp cho nhẹ ----
    var previewLoading by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf(false) }
    val previewPlayer = remember {
        buildPlayer(context).apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(854, 480)
                .build()
        }
    }
    PauseWhenBackground(previewPlayer)
    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                previewLoading = state == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                previewError = true
            }
        }
        previewPlayer.addListener(listener)
        onDispose {
            previewPlayer.removeListener(listener)
            previewPlayer.release()
        }
    }
    LaunchedEffect(focusedChannel?.url) {
        val ch = focusedChannel ?: return@LaunchedEffect
        previewError = false
        previewPlayer.stop()
        previewPlayer.clearMediaItems()
        delay(700)                       // lướt nhanh thì không tải từng kênh
        previewPlayer.setMediaItem(MediaItem.fromUri(ch.url))
        previewPlayer.prepare()
        previewPlayer.play()
    }

    // ---- Đặt focus vào kênh xem gần nhất (hoặc kênh đầu tiên) ----
    val rowFocus = remember { FocusRequester() }
    var focusIndex by remember { mutableIntStateOf(-1) }
    var focusTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    /** Đưa focus tới hàng kênh thứ idx (dùng khi cuộn vòng: cuối -> đầu, đầu -> cuối). */
    fun focusRowAt(idx: Int) {
        scope.launch {
            focusIndex = idx
            listState.scrollToItem(idx)
            withFrameNanos { }
            withFrameNanos { }
            runCatching { rowFocus.requestFocus() }
        }
    }
    LaunchedEffect(focusTick, channels.isNotEmpty()) {
        if (shown.isEmpty() || showDrawer) return@LaunchedEffect
        val current = focusedChannel?.url
        var idx = shown.indexOfFirst { it.url == current }
        if (idx < 0) idx = shown.indexOfFirst { it.url == vm.lastChannelUrl }
        if (idx < 0) idx = 0
        focusIndex = idx
        listState.scrollToItem(idx)
        withFrameNanos { }
        withFrameNanos { }
        runCatching { rowFocus.requestFocus() }
    }

    // Bấm Back khi đang mở ngăn nhóm kênh thì đóng ngăn (không thoát app)
    BackHandler(enabled = showDrawer) {
        showDrawer = false
        focusTick++
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp)
            .focusProperties { canFocus = !showDrawer },   // mở ngăn nhóm thì phần chính không nhận focus
    ) {
        // ===== Phần trên: xem trước | thông tin | nút chức năng =====
        Row(Modifier.fillMaxWidth().height(205.dp)) {
            PreviewBox(
                exo = previewPlayer,
                hasChannel = focusedChannel != null,
                loading = previewLoading,
                error = previewError,
                modifier = Modifier.width(364.dp),
            )
            Spacer(Modifier.width(24.dp))
            InfoPanel(
                channel = focusedChannel,
                number = focusedNumber,
                prog = focusedProg,
                now = now,
                updateAvailable = updates.updateAvailable,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(10.dp))

        // ===== Thanh thời gian =====
        TimelineHeader(now = now, windowStart = windowStart, scroll = scroll)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))

        // ===== Bảng kênh + chương trình =====
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onPreviewKeyEvent { e ->
                    // Cuộn vòng: ở kênh đầu bấm ▲ -> kênh cuối; ở kênh cuối bấm ▼ -> kênh đầu.
                    // (Đồng thời không cho focus chạy lên khung xem trước.)
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val n = shown.size
                    when {
                        n == 0 -> e.key == Key.DirectionUp
                        e.key == Key.DirectionUp && focusedNumber <= 1 -> {
                            focusRowAt(n - 1)
                            true
                        }
                        e.key == Key.DirectionDown && focusedNumber >= n -> {
                            focusRowAt(0)
                            true
                        }
                        else -> false
                    }
                },
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // Khóa ổn định theo kênh: làm mới danh sách không làm hàng cũ bị vẽ lặp
                itemsIndexed(shown, key = { _, ch -> ch.group + "\u0000" + ch.url }) { i, ch ->
                    GuideRow(
                        number = i + 1,
                        ch = ch,
                        favorite = ch.url in favorites,
                        programmes = if (ch.id.isBlank()) null else epg[ch.id.lowercase()],
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        now = now,
                        scroll = scroll,
                        headerRequester = if (i == focusIndex) rowFocus else null,
                        onFocusRow = { prog ->
                            focusedChannel = ch
                            focusedNumber = i + 1
                            focusedProg = prog
                        },
                        onPlay = { onPlay(shown, i) },
                        onFavorite = { vm.toggleFavorite(ch) },
                        onOpenGroups = { showDrawer = true },
                    )
                }
            }
            if (shown.isEmpty()) {
                Text(
                    when {
                        sel == 0 -> "Chưa có kênh yêu thích. Bấm ☆ cạnh tên kênh để thêm."
                        vm.loading -> "Đang tải danh sách kênh…"
                        else -> "Không có kênh nào."
                    },
                    color = Dim,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showDrawer,
        modifier = Modifier.align(Alignment.CenterStart),
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
    ) {
        val updated = if (vm.lastUpdated > 0) " · cập nhật ${hhmm(vm.lastUpdated)}" else ""
        GroupDrawer(
            labels = labels,
            counts = counts,
            selected = sel,
            query = query,
            status = "${channels.size} kênh$updated",
            loading = vm.loading,
            error = vm.error,
            onQuery = { query = it },
            onHover = { sel = it },
            onRefresh = { vm.refresh() },
            onSettings = onSettings,
            updateLabel = when (val u = updates.state) {
                is UpdateState.Available -> "Cập nhật lên bản ${u.info.versionName}"
                is UpdateState.Checking -> "Đang kiểm tra cập nhật…"
                is UpdateState.Downloading -> "Đang tải bản cập nhật…"
                else -> "Kiểm tra cập nhật"
            },
            updateHighlight = updates.updateAvailable,
            version = updates.currentVersion,
            onUpdate = { updates.onButton() },
            onClose = {
                showDrawer = false
                focusTick++
            },
        )
    }
    }   // hết Box
}

// ============================ Khung xem trước & thông tin ============================

@Composable
private fun PreviewBox(
    exo: androidx.media3.exoplayer.ExoPlayer,
    hasChannel: Boolean,
    loading: Boolean,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.aspectRatio(16f / 9f).clip(shape).background(Color.Black)) {
        AndroidView(
            // Khung xem trước KHÔNG được nhận focus: nếu focus lọt vào video thì
            // phím điều hướng và OK bị "nuốt", chỉ còn Back hoạt động
            modifier = Modifier.fillMaxSize().focusProperties { canFocus = false },
            factory = { ctx ->
                // Dùng TextureView (khai báo trong layout) để video bị cắt theo góc bo
                (LayoutInflater.from(ctx).inflate(R.layout.preview_player_view, null) as PlayerView).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.player = exo
                }
            },
        )
        when {
            !hasChannel -> Text("Chọn một kênh để xem trước", color = Dim, modifier = Modifier.align(Alignment.Center))
            error -> Text("Không xem trước được kênh này", color = ErrorRed, modifier = Modifier.align(Alignment.Center))
            loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(28.dp),
                strokeWidth = 3.dp,
            )
            else -> Unit
        }
        // Viền mảnh phủ lên trên video cho gọn mắt
        Box(Modifier.fillMaxSize().border(1.dp, Color(0x40FFFFFF), shape))
    }
}

@Composable
private fun InfoPanel(
    channel: Channel?,
    number: Int,
    prog: Programme?,
    now: Long,
    updateAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (channel == null) {
            Text("Lướt qua danh sách kênh để xem trước.", color = Dim)
        } else {
            Text(
                prog?.title ?: "Không có thông tin",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            if (prog != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${hhmm(prog.start)} – ${hhmm(prog.stop)}", maxLines = 1, softWrap = false)
                    if (now >= prog.start && now < prog.stop) {
                        LinearProgressIndicator(
                            progress = { progressOf(prog, now) },
                            modifier = Modifier.padding(horizontal = 14.dp).width(140.dp),
                        )
                        Text(
                            "còn ${(prog.stop - now + 59_999) / 60_000} phút",
                            color = Dim,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "$number. ${channel.name}",
                color = Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (updateAvailable) {
                Text(
                    "Có bản cập nhật mới. Bấm ◄ mở ngăn bên trái, chọn \"Cập nhật\".",
                    color = Accent,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "OK: Toàn Màn Hình     ◄: Nhóm Kênh",
                color = Dim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit, color: Color = Color.White) {
    Text(
        label,
        color = color,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .tvFocusable(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

// ============================ Bảng lịch ============================

@Composable
private fun TimelineHeader(now: Long, windowStart: Long, scroll: ScrollState) {
    val density = LocalDensity.current
    val dateText = remember(now / 60_000) {
        SimpleDateFormat("EEE, dd/MM  HH:mm", Locale.getDefault()).format(Date(now))
    }
    val arrowW = 14.dp
    val nowPx = with(density) { (((now - windowStart) / 60_000f) * DP_PER_MIN).dp.toPx() }
    val halfArrowPx = with(density) { (arrowW / 2).toPx() }

    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            dateText,
            color = Dim,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.width(LEFT_W).padding(start = 8.dp),
        )
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            Row(Modifier.fillMaxSize().horizontalScroll(scroll, enabled = false)) {
                repeat(GUIDE_HOURS * 2) { n ->
                    Text(
                        hhmm(windowStart + n * HALF_HOUR),
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier.width((30 * DP_PER_MIN).dp).padding(start = 6.dp, top = 3.dp),
                    )
                }
            }
            // Mũi tên tam giác chỉ xuống vị trí "bây giờ", trượt cùng thanh thời gian
            Canvas(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset((nowPx - scroll.value - halfArrowPx).roundToInt(), 0) }
                    .size(arrowW, 8.dp),
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, Accent)
            }
        }
    }
}

@Composable
private fun GuideRow(
    number: Int,
    ch: Channel,
    favorite: Boolean,
    programmes: List<Programme>?,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    scroll: ScrollState,
    headerRequester: FocusRequester?,
    onFocusRow: (Programme?) -> Unit,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onOpenGroups: () -> Unit,
) {
    val cells = remember(programmes, windowStart) { buildCells(programmes, windowStart, windowEnd) }

    Row(Modifier.fillMaxWidth().height(ROW_H), verticalAlignment = Alignment.CenterVertically) {
        // Cột trái: số kênh, logo, tên, ★
        Row(
            Modifier.width(LEFT_W).fillMaxHeight().padding(vertical = 1.5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (headerRequester != null) Modifier.focusRequester(headerRequester) else Modifier)
                    .onFocusChanged {
                        if (it.isFocused) onFocusRow(programmes?.firstOrNull { p -> now >= p.start && now < p.stop })
                    }
                    .onKeyEvent { e ->
                        // Bấm Trái khi đã ở cột tên kênh (ngoài cùng bên trái) -> mở ngăn nhóm kênh
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) {
                            onOpenGroups()
                            true
                        } else false
                    }
                    .tvFocusable(onPlay)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$number", color = Dim, fontSize = 16.sp, modifier = Modifier.width(36.dp))
                AsyncImage(
                    model = ch.logo.ifBlank { null },
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(38.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    ch.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (favorite) "★" else "☆",
                color = Gold,
                fontSize = 22.sp,
                modifier = Modifier.tvFocusable(onFavorite).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        // Các ô chương trình (trượt ngang cùng nhau)
        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            cells.forEach { c ->
                GuideCell(c, now, onFocus = { onFocusRow(c.programme) }, onPlay = onPlay)
            }
        }
    }
}

@Composable
private fun GuideCell(cell: Cell, now: Long, onFocus: () -> Unit, onPlay: () -> Unit) {
    val width = (((cell.end - cell.start) / 60_000f) * DP_PER_MIN).dp
    val live = now >= cell.start && now < cell.end
    val base = when {
        cell.programme == null -> Color(0xFF111B2E)
        live -> Color(0xFF24406B)
        else -> Color(0xFF182640)
    }
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .padding(1.5.dp)
            .background(base, RoundedCornerShape(6.dp))
            .onFocusChanged { if (it.isFocused) onFocus() }
            .tvFocusable(onPlay),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            cell.programme?.title ?: "Không có thông tin",
            color = if (cell.programme == null) Dim else Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

// ============================ Ngăn nhóm kênh (trượt từ bên trái) ============================

@Composable
private fun GroupDrawer(
    labels: List<String>,
    counts: List<Int>,
    selected: Int,
    query: String,
    status: String,
    loading: Boolean,
    error: String?,
    onQuery: (String) -> Unit,
    onHover: (Int) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    updateLabel: String,
    updateHighlight: Boolean,
    version: String,
    onUpdate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = remember { selected }
    val focusInitial = remember { FocusRequester() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 3).coerceAtLeast(0))
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { focusInitial.requestFocus() }
    }
    Column(
        modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(Color(0xF2091220))
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .onKeyEvent { e ->
                // Bấm Phải ở bất kỳ đâu trong ngăn = quay lại danh sách kênh
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
                    onClose()
                    true
                } else false
            },
    ) {
        Text("Nhóm kênh", style = MaterialTheme.typography.titleLarge)
        Text(
            "Bấm ► để quay lại danh sách kênh",
            color = Dim,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Tìm kênh…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(labels) { i, label ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (i == initial) Modifier.focusRequester(focusInitial) else Modifier)
                        .onFocusChanged { if (it.isFocused) onHover(i) }   // di chuyển tới nhóm nào, danh sách kênh đổi theo
                        .tvFocusable(onClose)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        color = if (i == selected) Accent else Color.White,
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${counts.getOrElse(i) { 0 }}", color = Dim, fontSize = 13.sp)
                }
            }
        }

        // ===== Khu vực dưới cùng: Làm mới, Cài đặt, trạng thái =====
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
        Spacer(Modifier.height(8.dp))
        MenuButton(if (loading) "Đang làm mới…" else "Làm mới", onRefresh)
        MenuButton("Cài đặt", onSettings)
        MenuButton(updateLabel, onUpdate, color = if (updateHighlight) Accent else Color.White)
        Spacer(Modifier.height(4.dp))
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("  Đang tải…", color = Dim, fontSize = 12.sp)
            }
        }
        Text(status, color = Dim, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 14.dp, top = 2.dp))
        Text("Phiên bản $version", color = Dim, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 14.dp))
        error?.let {
            Text(
                it,
                color = ErrorRed,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp),
            )
        }
    }
}
