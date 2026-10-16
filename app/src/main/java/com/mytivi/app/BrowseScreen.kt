@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package com.mytivi.app

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bề rộng ngăn nhóm kênh cố định (tablet nằm ngang, màn hình rộng). */
private val PANE_W = 280.dp

/**
 * Trạng thái màn hình chính, giữ ở cấp App để vào/ra toàn màn hình (hoặc xoay máy)
 * không bị mất nhóm đang chọn, chữ đang tìm và vị trí cuộn bảng lịch.
 */
@Stable
class BrowseUiState {
    var sel by mutableIntStateOf(1)            // 0 = Yêu thích, 1 = Tất cả, 2.. = nhóm
    var query by mutableStateOf("")
    var searchOpen by mutableStateOf(false)
    /** Ô chương trình vừa chạm: (link kênh, chương trình) – để xem thông tin chương trình sắp chiếu. */
    var picked by mutableStateOf<Pair<String, Programme>?>(null)
    val listState = LazyListState()
    val scroll = ScrollState(0)               // dùng chung: kéo ngang hàng nào thì mọi hàng cùng trượt
}

/**
 * Màn hình chính kiểu TiviMate, tự đổi bố cục theo kích thước màn hình:
 *  - Tablet nằm ngang (≥ 840dp): ngăn nhóm kênh cố định bên trái | xem trước + thông tin | bảng lịch.
 *  - Tablet dựng dọc, máy nhỏ, chia đôi màn hình: thanh trên cùng + ngăn nhóm kéo ra (☰),
 *    xem trước rộng toàn chiều ngang, hàng chip nhóm kênh, bảng lịch bên dưới.
 * Chạm 1 lần vào kênh = xem trước (có tiếng); chạm lần nữa / chạm khung hình = toàn màn hình.
 */
@Composable
fun BrowseScreen(
    vm: MainViewModel,
    updates: UpdateViewModel,
    playback: Playback,
    ui: BrowseUiState,
    onFullscreen: (List<Channel>, Int) -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val channels = vm.channels
    val favorites = vm.favorites
    val epg = vm.epg
    val now = rememberNow()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val sel = ui.sel
    val query = ui.query
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
    val groupLabel = labels.getOrElse(sel) { "Tất cả" }

    // ---- Cửa sổ thời gian của bảng lịch: mốc đầu tự dời theo mỗi nửa giờ ----
    val windowStart = remember(now / HALF_HOUR) { floorTo(now, HALF_HOUR) }
    val windowEnd = windowStart + GUIDE_HOURS * HOUR

    // ---- Kênh đang xem trước + chương trình hiển thị ở phần thông tin ----
    val selected = playback.channel
    val selectedIndex = if (selected == null) -1 else shown.indexOfFirst { it.url == selected.url }
    val picked = ui.picked?.takeIf { it.first == selected?.url }?.second
    val infoProg = picked ?: selected?.let { nowPlaying(epg, it, now) }
    val nextProg = when {
        selected == null -> null
        infoProg != null -> programmeAfter(epg, selected, infoProg)
        else -> nextPlaying(epg, selected, now)
    }

    /** Vào toàn màn hình; vuốt đổi kênh sẽ chạy trong nhóm đang xem (không có thì trong tất cả kênh). */
    fun openFullscreen() {
        val ch = playback.channel ?: return
        val i = shown.indexOfFirst { it.url == ch.url }
        if (i >= 0) {
            onFullscreen(shown, i)
            return
        }
        val j = channels.indexOfFirst { it.url == ch.url }
        if (j >= 0) onFullscreen(channels, j) else onFullscreen(listOf(ch), 0)
    }

    fun tapChannel(i: Int, ch: Channel) {
        if (ch.url == playback.channel?.url) {
            onFullscreen(shown, i)
        } else {
            ui.picked = null
            playback.play(ch)
        }
    }

    fun tapCell(i: Int, ch: Channel, cell: Cell) {
        val live = now >= cell.start && now < cell.end
        val prog = cell.programme
        if (ch.url != playback.channel?.url) {
            // Kênh khác: xem trước kênh đó; nếu chạm ô chương trình sắp chiếu thì hiện thông tin ô đó
            playback.play(ch)
            ui.picked = if (!live && prog != null) ch.url to prog else null
        } else if (live) {
            onFullscreen(shown, i)
        } else {
            ui.picked = if (prog != null) ch.url to prog else null
        }
    }

    fun selectGroup(i: Int) {
        ui.sel = i
        scope.launch { ui.listState.scrollToItem(0) }
    }

    fun setQuery(q: String) {
        ui.query = q
        scope.launch { ui.listState.scrollToItem(0) }
    }

    // Mở app, hoặc thoát toàn màn hình sau khi đã vuốt sang kênh khác:
    // cuộn bảng kênh tới kênh đang phát nếu nó đang bị khuất.
    LaunchedEffect(shown.isNotEmpty()) {
        if (shown.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        val url = playback.channel?.url ?: return@LaunchedEffect
        val idx = shown.indexOfFirst { it.url == url }
        if (idx >= 0 && ui.listState.layoutInfo.visibleItemsInfo.none { it.index == idx }) {
            ui.listState.scrollToItem((idx - 2).coerceAtLeast(0))
        }
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = ui.searchOpen && !drawer.isOpen) {
        ui.searchOpen = false
        setQuery("")
    }

    val updated = if (vm.lastUpdated > 0) " · cập nhật ${hhmm(vm.lastUpdated)}" else ""
    val status = "${channels.size} kênh$updated"
    val updateLabel = when (val u = updates.state) {
        is UpdateState.Available -> "Cập nhật lên bản ${u.info.versionName}"
        is UpdateState.Checking -> "Đang kiểm tra cập nhật…"
        is UpdateState.Downloading -> "Đang tải bản cập nhật…"
        else -> "Kiểm tra cập nhật"
    }
    val searchHint = if (sel == 1) "Tìm kênh…" else "Tìm trong “$groupLabel”…"
    val emptyText = when {
        query.isNotBlank() -> "Không tìm thấy kênh nào khớp “$query” trong nhóm này."
        sel == 0 -> "Chưa có kênh yêu thích. Chạm ☆ cạnh tên kênh (hoặc giữ lâu tên kênh) để thêm."
        vm.loading -> "Đang tải danh sách kênh…"
        else -> "Không có kênh nào."
    }

    // ---- Các khối giao diện, được xếp chỗ khác nhau tùy bố cục ----
    val preview: @Composable (Modifier) -> Unit = { m ->
        PreviewBox(
            playback = playback,
            onOpen = { openFullscreen() },
            onToggleMute = {
                playback.togglePreviewMute()
                vm.previewMuted = playback.previewMuted
            },
            modifier = m,
        )
    }
    val info: @Composable (Modifier, Boolean) -> Unit = { m, compact ->
        InfoPanel(
            channel = selected,
            number = selectedIndex + 1,
            prog = infoProg,
            next = nextProg,
            now = now,
            favorite = selected != null && selected.url in favorites,
            compact = compact,
            updateAvailable = updates.updateAvailable,
            onFullscreen = { openFullscreen() },
            onFavorite = { selected?.let { vm.toggleFavorite(it) } },
            onUpdate = { updates.onButton() },
            modifier = m,
        )
    }
    val chips: @Composable () -> Unit = {
        GroupChips(labels = labels, counts = counts, selected = sel, onSelect = { selectGroup(it) })
    }
    val guide: @Composable (Modifier, GuideDims) -> Unit = { m, dims ->
        Guide(
            shown = shown,
            favorites = favorites,
            epg = epg,
            now = now,
            windowStart = windowStart,
            windowEnd = windowEnd,
            dims = dims,
            ui = ui,
            selectedUrl = selected?.url,
            picked = picked,
            loading = vm.loading,
            onRefresh = { vm.refresh() },
            emptyText = emptyText,
            onTapChannel = { i, ch -> tapChannel(i, ch) },
            onTapCell = { i, ch, cell -> tapCell(i, ch, cell) },
            onFavorite = { vm.toggleFavorite(it) },
            onLongPress = { ch ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.toggleFavorite(ch)
            },
            modifier = m,
        )
    }
    val pane: @Composable (Boolean, Modifier) -> Unit = { inDrawer, m ->
        GroupPane(
            labels = labels,
            counts = counts,
            selected = sel,
            onSelect = {
                selectGroup(it)
                if (inDrawer) scope.launch { drawer.close() }
            },
            showSearch = !inDrawer,
            query = query,
            onQuery = { setQuery(it) },
            searchHint = searchHint,
            status = status,
            loading = vm.loading,
            error = vm.error,
            version = updates.currentVersion,
            updateLabel = updateLabel,
            updateHighlight = updates.updateAvailable,
            onRefresh = { vm.refresh() },
            onSettings = onSettings,
            onUpdate = { updates.onButton() },
            onAbout = onAbout,
            modifier = m,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val wide = maxWidth >= 840.dp && maxHeight >= 480.dp
        val landscape = maxWidth > maxHeight
        if (wide) {
            Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                pane(false, Modifier.width(PANE_W).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
                MainArea(
                    landscape = true,
                    showChips = false,
                    preview = preview,
                    info = info,
                    chips = chips,
                    guide = guide,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = drawer.isOpen,      // chỉ vuốt để đóng; mở bằng nút ☰ (tránh đụng thao tác kéo bảng lịch)
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = Panel, modifier = Modifier.width(300.dp)) {
                        pane(true, Modifier.fillMaxSize())
                    }
                },
            ) {
                Column(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
                    TopBar(
                        title = groupLabel,
                        query = query,
                        onQuery = { setQuery(it) },
                        searchHint = searchHint,
                        searchOpen = ui.searchOpen,
                        onSearchOpen = { open ->
                            ui.searchOpen = open
                            if (!open) setQuery("")
                        },
                        loading = vm.loading,
                        updateAvailable = updates.updateAvailable,
                        onMenu = { scope.launch { drawer.open() } },
                        onRefresh = { vm.refresh() },
                        onSettings = onSettings,
                        onUpdate = { updates.onButton() },
                    )
                    MainArea(
                        landscape = landscape,
                        showChips = !landscape,
                        preview = preview,
                        info = info,
                        chips = chips,
                        guide = guide,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ============================ Xếp bố cục ============================

@Composable
private fun MainArea(
    landscape: Boolean,
    showChips: Boolean,
    preview: @Composable (Modifier) -> Unit,
    info: @Composable (Modifier, Boolean) -> Unit,
    chips: @Composable () -> Unit,
    guide: @Composable (Modifier, GuideDims) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val dims = guideDims(maxWidth)
        val topH = (maxHeight * 0.36f).coerceIn(150.dp, 340.dp)
        val previewH = minOf((maxWidth - 32.dp) * 9f / 16f, maxHeight * 0.38f)
        if (landscape) {
            // Ngang: [xem trước | thông tin] ở trên, bảng lịch ở dưới (giống bản TV)
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(topH)
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
                ) {
                    preview(Modifier.fillMaxHeight().aspectRatio(16f / 9f, matchHeightConstraintsFirst = true))
                    Spacer(Modifier.width(20.dp))
                    info(Modifier.weight(1f).fillMaxHeight(), false)
                }
                if (showChips) chips()
                guide(Modifier.weight(1f).fillMaxWidth(), dims)
            }
        } else {
            // Dọc: xem trước rộng hết chiều ngang, thông tin gọn 1 dải, chip nhóm, bảng lịch
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    preview(Modifier.height(previewH).aspectRatio(16f / 9f, matchHeightConstraintsFirst = true))
                }
                info(Modifier.fillMaxWidth().padding(horizontal = 16.dp), true)
                if (showChips) chips()
                guide(Modifier.weight(1f).fillMaxWidth(), dims)
            }
        }
    }
}

// ============================ Khung xem trước & thông tin ============================

/**
 * Khung xem trước (có tiếng):
 *  - Chạm 1 lần: hiện/ẩn nút điều khiển (⏯ phát/tạm dừng, tắt/bật tiếng), tự ẩn sau 3,5 giây.
 *  - Chạm đúp, hoặc nút ⛶ ở góc phải: xem toàn màn hình (không tải lại luồng).
 */
@Composable
private fun PreviewBox(
    playback: Playback,
    onOpen: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val ch = playback.channel
    var controls by remember { mutableStateOf(false) }
    var poke by remember { mutableIntStateOf(0) }
    LaunchedEffect(controls, poke) {
        if (controls) {
            delay(3_500)
            controls = false
        }
    }
    // Vừa chọn kênh: hiện nút một lúc để người dùng biết khung này bấm được
    LaunchedEffect(ch?.url) {
        if (ch != null) {
            controls = true
            poke++
        }
    }
    Box(modifier.clip(shape).background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // TextureView (khai báo trong layout) để video bị cắt theo góc bo
                (LayoutInflater.from(ctx).inflate(R.layout.preview_player_view, null) as PlayerView).apply {
                    player = playback.exo
                }
            },
            update = { it.keepScreenOn = playback.isPlaying },   // đang xem trước thì không tắt màn hình
            onRelease = { it.player = null },
        )
        when {
            ch == null -> Text(
                "Chạm vào một kênh để xem trước",
                color = Dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
            playback.error != null -> Text(
                "Không xem trước được kênh này\nĐang thử lại…",
                color = ErrorRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
            playback.buffering -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                strokeWidth = 3.dp,
            )
            else -> Unit
        }
        // Lớp chạm phủ trên video: chạm = hiện/ẩn nút, chạm đúp = toàn màn hình
        Box(
            Modifier
                .fillMaxSize()
                .combinedClickable(
                    enabled = ch != null,
                    onDoubleClick = onOpen,
                    onClick = {
                        controls = !controls
                        poke++
                    },
                ),
        )
        if (ch != null) {
            PreviewControls(
                visible = controls,
                playing = playback.playWhenReady,
                busy = playback.buffering || playback.error != null,
                muted = playback.previewMuted,
                onTogglePlay = {
                    playback.togglePause()
                    poke++
                },
                onToggleMute = {
                    onToggleMute()
                    poke++
                },
                onOpen = onOpen,
            )
        }
        // Viền mảnh phủ lên trên video cho gọn mắt
        Box(Modifier.fillMaxSize().border(1.dp, Color(0x40FFFFFF), shape))
    }
}

@Composable
private fun PreviewControls(
    visible: Boolean,
    playing: Boolean,
    busy: Boolean,
    muted: Boolean,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onOpen: () -> Unit,
) {
    val overlay = Color(0x80000000)
    Box(Modifier.fillMaxSize()) {
        // Luôn hiện: nút toàn màn hình
        RoundIcon(
            R.drawable.ic_fullscreen,
            "Xem toàn màn hình",
            onOpen,
            Modifier.align(Alignment.BottomEnd).padding(8.dp),
            size = 44.dp,
            background = overlay,
        )
        // Tắt/bật tiếng: hiện khi đang mở nút, hoặc luôn hiện khi đang tắt tiếng
        AnimatedVisibility(
            visible = visible || muted,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            RoundIcon(
                if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume,
                if (muted) "Bật tiếng" else "Tắt tiếng",
                onToggleMute,
                size = 44.dp,
                background = overlay,
            )
        }
        // Phát / tạm dừng ở giữa (đang tạm dừng thì luôn hiện nút ▶)
        AnimatedVisibility(
            visible = (visible || !playing) && !busy,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            RoundIcon(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) "Tạm dừng" else "Phát",
                onTogglePlay,
                size = 60.dp,
                iconSize = 32.dp,
                background = overlay,
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                "Chạm đúp để xem toàn màn hình",
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(overlay)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun InfoPanel(
    channel: Channel?,
    number: Int,
    prog: Programme?,
    next: Programme?,
    now: Long,
    favorite: Boolean,
    compact: Boolean,
    updateAvailable: Boolean,
    onFullscreen: () -> Unit,
    onFavorite: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val strip = compact || maxHeight < 170.dp
        val tall = maxHeight >= 230.dp
        val narrow = maxWidth < 360.dp
        if (strip) {
            InfoStrip(channel, number, prog, now, favorite, onFullscreen, onFavorite)
        } else {
            InfoFull(channel, number, prog, next, now, favorite, updateAvailable, tall, narrow, onFullscreen, onFavorite, onUpdate)
        }
    }
}

/** Dải thông tin gọn (tablet dựng dọc): chương trình, giờ, tiến độ + nút ★ và toàn màn hình. */
@Composable
private fun InfoStrip(
    channel: Channel?,
    number: Int,
    prog: Programme?,
    now: Long,
    favorite: Boolean,
    onFullscreen: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (channel == null) {
                Text(
                    "Chạm vào một kênh để xem trước, chạm lần nữa để xem toàn màn hình.",
                    color = Dim,
                    fontSize = 14.sp,
                )
            } else {
                Text(
                    prog?.title ?: "Không có thông tin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (prog != null) ProgTime(prog, now, Modifier.padding(top = 2.dp))
                Text(
                    channelLine(channel, number),
                    color = Dim,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (channel != null) {
            RoundIcon(
                if (favorite) R.drawable.ic_star else R.drawable.ic_star_border,
                if (favorite) "Bỏ yêu thích" else "Thêm yêu thích",
                onFavorite,
                tint = if (favorite) Gold else Color.White,
                background = Color.Transparent,
            )
            Spacer(Modifier.width(6.dp))
            RoundIcon(R.drawable.ic_fullscreen, "Xem toàn màn hình", onFullscreen, tint = OnAccent, background = Accent)
        }
    }
}

/** Phần thông tin đầy đủ bên phải khung xem trước (tablet nằm ngang). */
@Composable
private fun InfoFull(
    channel: Channel?,
    number: Int,
    prog: Programme?,
    next: Programme?,
    now: Long,
    favorite: Boolean,
    updateAvailable: Boolean,
    tall: Boolean,
    narrow: Boolean,
    onFullscreen: () -> Unit,
    onFavorite: () -> Unit,
    onUpdate: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (channel == null) {
            Text("Chạm vào một kênh để xem trước", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Chạm lần nữa vào kênh đang xem (hoặc vào khung hình) để xem toàn màn hình. " +
                    "Kéo ngang bảng lịch để xem chương trình sắp tới; giữ lâu tên kênh để thêm vào Yêu thích.",
                color = Dim,
                fontSize = 14.sp,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = channel.logo.ifBlank { null },
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(channelLine(channel, number), color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                prog?.title ?: "Không có thông tin",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = if (tall) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (prog != null) ProgTime(prog, now, Modifier.padding(top = 8.dp))
            if (next != null && tall) {
                Text(
                    "Tiếp theo: ${hhmm(next.start)}  ${next.title}",
                    color = Dim,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (updateAvailable) {
                Text(
                    "Có bản cập nhật mới – chạm để cập nhật",
                    color = Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onUpdate).padding(vertical = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onFullscreen) {
                    AppIcon(R.drawable.ic_fullscreen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Toàn màn hình", maxLines = 1)
                }
                if (narrow) {
                    RoundIcon(
                        if (favorite) R.drawable.ic_star else R.drawable.ic_star_border,
                        if (favorite) "Bỏ yêu thích" else "Thêm yêu thích",
                        onFavorite,
                        tint = Gold,
                        background = CardBg,
                    )
                } else {
                    OutlinedButton(onClick = onFavorite) {
                        AppIcon(
                            if (favorite) R.drawable.ic_star else R.drawable.ic_star_border,
                            null,
                            Modifier.size(18.dp),
                            tint = Gold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (favorite) "Đã yêu thích" else "Yêu thích", maxLines = 1)
                    }
                }
            }
        }
    }
}

/** "20:00 – 21:00  ▬▬▬▭▭  còn 25 phút" */
@Composable
private fun ProgTime(p: Programme, now: Long, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("${hhmm(p.start)} – ${hhmm(p.stop)}", fontSize = 14.sp, maxLines = 1, softWrap = false)
        if (now >= p.start && now < p.stop) {
            LinearProgressIndicator(
                progress = { progressOf(p, now) },
                modifier = Modifier.padding(horizontal = 12.dp).width(110.dp),
            )
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Text(remainText(p, now), color = Dim, fontSize = 14.sp, maxLines = 1, softWrap = false)
    }
}

private fun channelLine(ch: Channel, number: Int) = if (number > 0) "$number. ${ch.name}" else ch.name

// ============================ Thanh trên cùng / chip nhóm / ngăn nhóm ============================

@Composable
private fun TopBar(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    searchHint: String,
    searchOpen: Boolean,
    onSearchOpen: (Boolean) -> Unit,
    loading: Boolean,
    updateAvailable: Boolean,
    onMenu: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onUpdate: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) { AppIcon(R.drawable.ic_menu, "Nhóm kênh") }
        if (searchOpen) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            SearchField(query, onQuery, searchHint, Modifier.weight(1f), focus)
            IconButton(onClick = { onSearchOpen(false) }) { AppIcon(R.drawable.ic_close, "Đóng tìm kiếm") }
        } else {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("MyTivi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(title, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onSearchOpen(true) }) { AppIcon(R.drawable.ic_search, "Tìm kênh") }
            if (updateAvailable) {
                IconButton(onClick = onUpdate) { AppIcon(R.drawable.ic_update, "Có bản cập nhật", tint = Accent) }
            }
            if (loading) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = onRefresh) { AppIcon(R.drawable.ic_refresh, "Làm mới") }
            }
            IconButton(onClick = onSettings) { AppIcon(R.drawable.ic_settings, "Cài đặt") }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { AppIcon(R.drawable.ic_search, null, Modifier.size(20.dp)) },
        trailingIcon = if (query.isEmpty()) null else {
            { IconButton(onClick = { onQuery("") }) { AppIcon(R.drawable.ic_close, "Xóa", Modifier.size(20.dp)) } }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = modifier.then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
    )
}

/** Hàng chip nhóm kênh, cuộn ngang (tablet dựng dọc). */
@Composable
private fun GroupChips(labels: List<String>, counts: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0))
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        itemsIndexed(labels) { i, label ->
            FilterChip(
                selected = i == selected,
                onClick = { onSelect(i) },
                label = { Text("$label · ${counts.getOrElse(i) { 0 }}", maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SelBg,
                    selectedLabelColor = Accent,
                ),
            )
        }
    }
}

/** Ngăn nhóm kênh: cố định bên trái khi nằm ngang, hoặc kéo ra từ nút ☰ khi dựng dọc. */
@Composable
private fun GroupPane(
    labels: List<String>,
    counts: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    showSearch: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    searchHint: String,
    status: String,
    loading: Boolean,
    error: String?,
    version: String,
    updateLabel: String,
    updateHighlight: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onUpdate: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 3).coerceAtLeast(0))
    Column(modifier.background(Panel).padding(horizontal = 12.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("MyTivi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (showSearch) {
            SearchField(query, onQuery, searchHint, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "NHÓM KÊNH",
            color = Dim,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 14.dp, bottom = 4.dp),
        )
        LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(labels) { i, label ->
                val isSel = i == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isSel) SelBg else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        color = if (isSel) Accent else Color.White,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${counts.getOrElse(i) { 0 }}", color = Dim, fontSize = 13.sp)
                }
            }
        }

        // ===== Dưới cùng: Làm mới, Cài đặt, Cập nhật, trạng thái =====
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Line))
        PaneAction(R.drawable.ic_refresh, if (loading) "Đang làm mới…" else "Làm mới", onRefresh, busy = loading)
        PaneAction(R.drawable.ic_settings, "Cài đặt", onSettings)
        PaneAction(R.drawable.ic_update, updateLabel, onUpdate, highlight = updateHighlight)
        PaneAction(R.drawable.ic_info, "Giới thiệu", onAbout)
        Text(status, color = Dim, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 14.dp, top = 6.dp))
        Text("Phiên bản $version", color = Dim, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 14.dp))
        if (error != null) {
            Text(
                error,
                color = ErrorRed,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun PaneAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
    busy: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            AppIcon(icon, null, Modifier.size(20.dp), tint = if (highlight) Accent else Dim)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (highlight) Accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
