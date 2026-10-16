@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.mytivi.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Bảng lịch hiển thị 12 giờ kể từ mốc nửa giờ gần nhất. */
const val GUIDE_HOURS = 12

/**
 * Kích thước bảng lịch theo bề rộng vùng hiển thị.
 * Hàng cao hơn bản TV (≥ 60dp) để chạm ngón tay không trượt sang hàng bên cạnh.
 */
data class GuideDims(
    val leftW: Dp,          // cột số kênh + logo + tên + ☆
    val rowH: Dp,           // chiều cao mỗi hàng kênh
    val dpPerMin: Float,    // độ rộng theo phút
    val showNumber: Boolean,
    val logo: Dp,
)

fun guideDims(width: Dp): GuideDims = when {
    width >= 840.dp -> GuideDims(leftW = 300.dp, rowH = 66.dp, dpPerMin = 6.4f, showNumber = true, logo = 40.dp)
    width >= 560.dp -> GuideDims(leftW = 250.dp, rowH = 64.dp, dpPerMin = 6.0f, showNumber = true, logo = 38.dp)
    else -> GuideDims(leftW = 172.dp, rowH = 60.dp, dpPerMin = 5.0f, showNumber = false, logo = 32.dp)
}

/** Chương trình ngay sau p trên cùng kênh. */
fun programmeAfter(epg: Map<String, List<Programme>>, ch: Channel, p: Programme): Programme? =
    if (ch.id.isBlank()) null else epg[ch.id.lowercase()]?.firstOrNull { it.start >= p.stop }

/** Thanh thời gian + bảng kênh/chương trình. */
@Composable
fun Guide(
    shown: List<Channel>,
    favorites: Set<String>,
    epg: Map<String, List<Programme>>,
    now: Long,
    windowStart: Long,
    windowEnd: Long,
    dims: GuideDims,
    ui: BrowseUiState,
    selectedUrl: String?,
    picked: Programme?,
    loading: Boolean,
    onRefresh: () -> Unit,
    emptyText: String,
    groupLabel: String,
    onOpenGroups: () -> Unit,
    onTapChannel: (Int, Channel) -> Unit,
    onTapCell: (Int, Channel, Cell) -> Unit,
    onFavorite: (Channel) -> Unit,
    onLongPress: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TimelineHeader(
            now = now,
            windowStart = windowStart,
            scroll = ui.scroll,
            dims = dims,
            groupLabel = groupLabel,
            onOpenGroups = onOpenGroups,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        GuideBody(
            shown = shown,
            favorites = favorites,
            epg = epg,
            now = now,
            windowStart = windowStart,
            windowEnd = windowEnd,
            dims = dims,
            ui = ui,
            selectedUrl = selectedUrl,
            picked = picked,
            loading = loading,
            onRefresh = onRefresh,
            emptyText = emptyText,
            onTapChannel = onTapChannel,
            onTapCell = onTapCell,
            onFavorite = onFavorite,
            onLongPress = onLongPress,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun GuideBody(
    shown: List<Channel>,
    favorites: Set<String>,
    epg: Map<String, List<Programme>>,
    now: Long,
    windowStart: Long,
    windowEnd: Long,
    dims: GuideDims,
    ui: BrowseUiState,
    selectedUrl: String?,
    picked: Programme?,
    loading: Boolean,
    onRefresh: () -> Unit,
    emptyText: String,
    onTapChannel: (Int, Channel) -> Unit,
    onTapCell: (Int, Channel, Cell) -> Unit,
    onFavorite: (Channel) -> Unit,
    onLongPress: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Chỉ hiện vòng xoay "kéo để làm mới" khi chính người dùng kéo (lượt tự cập nhật chạy ngầm thì không)
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (!loading) pulled = false }
    // Kéo bảng lịch sang phải quá giờ hiện tại thì hiện nút "Bây giờ" để quay về
    val nowPx = with(density) { (((now - windowStart) / 60_000f) * dims.dpPerMin).dp.toPx() }
    val away by remember(nowPx) { derivedStateOf { ui.scroll.value > nowPx + 80f } }

    PullToRefreshBox(
        isRefreshing = pulled && loading,
        onRefresh = {
            pulled = true
            onRefresh()
        },
        modifier = modifier,
    ) {
        LazyColumn(state = ui.listState, modifier = Modifier.fillMaxSize()) {
            // Khóa ổn định theo kênh: làm mới danh sách không làm hàng cũ bị vẽ lặp
            itemsIndexed(shown, key = { _, ch -> ch.group + "\u0000" + ch.url }) { i, ch ->
                val isSel = ch.url == selectedUrl
                GuideRow(
                    number = i + 1,
                    ch = ch,
                    favorite = ch.url in favorites,
                    selected = isSel,
                    programmes = if (ch.id.isBlank()) null else epg[ch.id.lowercase()],
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    now = now,
                    scroll = ui.scroll,
                    dims = dims,
                    picked = if (isSel) picked else null,
                    onTapChannel = { onTapChannel(i, ch) },
                    onLongPress = { onLongPress(ch) },
                    onTapCell = { c -> onTapCell(i, ch, c) },
                    onFavorite = { onFavorite(ch) },
                )
            }
        }
        if (shown.isEmpty()) {
            Text(emptyText, color = Dim, modifier = Modifier.padding(24.dp))
        }
        NowChip(
            visible = away,
            onClick = { scope.launch { ui.scroll.animateScrollTo(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun NowChip(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        Text(
            "◀  Bây giờ",
            color = OnAccent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun TimelineHeader(
    now: Long,
    windowStart: Long,
    scroll: ScrollState,
    dims: GuideDims,
    groupLabel: String,
    onOpenGroups: () -> Unit,
) {
    val density = LocalDensity.current
    val dateText = remember(now / 60_000) {
        SimpleDateFormat("EEE dd/MM  HH:mm", Locale.getDefault()).format(Date(now))
    }
    val arrowW = 14.dp
    val nowPx = with(density) { (((now - windowStart) / 60_000f) * dims.dpPerMin).dp.toPx() }
    val halfArrowPx = with(density) { (arrowW / 2).toPx() }

    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        // Nút ◄ + tên nhóm đang xem: chạm để mở ngăn nhóm kênh (ngăn luôn tự ẩn). Kèm ngày giờ.
        Row(
            Modifier.width(dims.leftW).fillMaxHeight().padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(19.dp))
                    .background(SelBg)
                    .clickable(onClick = onOpenGroups)
                    .padding(start = 6.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(R.drawable.ic_arrow_left, "Mở nhóm kênh", Modifier.size(24.dp), tint = Accent)
                Text(
                    groupLabel,
                    color = Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Cột hẹp (điện thoại / chia đôi màn hình): chỉ hiện giờ để chừa chỗ cho tên nhóm
            Text(
                "  " + if (dims.leftW < 200.dp) hhmm(now) else dateText,
                color = Dim,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            // Kéo thanh giờ cũng trượt cả bảng (dùng chung ScrollState)
            Row(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                repeat(GUIDE_HOURS * 2) { n ->
                    Text(
                        hhmm(windowStart + n * HALF_HOUR),
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.width((30 * dims.dpPerMin).dp).padding(start = 6.dp, top = 4.dp),
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
    selected: Boolean,
    programmes: List<Programme>?,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    scroll: ScrollState,
    dims: GuideDims,
    picked: Programme?,
    onTapChannel: () -> Unit,
    onLongPress: () -> Unit,
    onTapCell: (Cell) -> Unit,
    onFavorite: () -> Unit,
) {
    val cells = remember(programmes, windowStart) { buildCells(programmes, windowStart, windowEnd) }

    Row(Modifier.fillMaxWidth().height(dims.rowH), verticalAlignment = Alignment.CenterVertically) {
        // Cột trái: số kênh, logo, tên, ☆ — chạm = xem trước, giữ lâu = thêm/bỏ yêu thích
        Row(
            Modifier
                .width(dims.leftW)
                .fillMaxHeight()
                .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) SelBg else Color.Transparent)
                .combinedClickable(onLongClick = onLongPress, onClick = onTapChannel)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dims.showNumber) {
                Text(
                    "$number",
                    color = if (selected) Accent else Dim,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.width(36.dp),
                )
            }
            AsyncImage(
                model = ch.logo.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(dims.logo),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                ch.name,
                color = if (selected) Accent else Color.White,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onFavorite),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    if (favorite) R.drawable.ic_star else R.drawable.ic_star_border,
                    if (favorite) "Bỏ yêu thích" else "Thêm yêu thích",
                    Modifier.size(22.dp),
                    tint = if (favorite) Gold else Dim,
                )
            }
        }
        // Các ô chương trình (mọi hàng trượt ngang cùng nhau)
        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            cells.forEach { c ->
                GuideCell(
                    cell = c,
                    now = now,
                    dims = dims,
                    isPicked = picked != null && c.programme == picked,
                    onTap = { onTapCell(c) },
                )
            }
        }
    }
}

@Composable
private fun GuideCell(cell: Cell, now: Long, dims: GuideDims, isPicked: Boolean, onTap: () -> Unit) {
    val width = (((cell.end - cell.start) / 60_000f) * dims.dpPerMin).dp
    val live = now >= cell.start && now < cell.end
    val prog = cell.programme
    val base = when {
        prog == null -> Color(0xFF111B2E)
        live -> Color(0xFF24406B)
        else -> Color(0xFF182640)
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .padding(2.dp)
            .clip(shape)
            .background(base)
            .then(if (isPicked) Modifier.border(2.dp, Accent, shape) else Modifier)
            .clickable(onClick = onTap),
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp)) {
            Text(
                prog?.title ?: "Không có thông tin",
                color = if (prog == null) Dim else Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (prog != null && width >= 96.dp) {
                Text(
                    "${hhmm(prog.start)} – ${hhmm(prog.stop)}",
                    color = Dim,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
        if (live) {
            // Vạch tiến độ mảnh dưới ô đang phát
            val f = ((now - cell.start).toFloat() / (cell.end - cell.start).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(f).height(3.dp).background(Accent))
        }
    }
}
