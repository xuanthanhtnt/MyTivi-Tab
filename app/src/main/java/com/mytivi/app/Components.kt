package com.mytivi.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- Bảng màu (giống bản Android TV) ----
val Bg = Color(0xFF0B1220)
val Panel = Color(0xFF0F1A2E)
val CardBg = Color(0xFF16233B)
val Accent = Color(0xFF38BDF8)
val OnAccent = Color(0xFF052538)
val Dim = Color(0xFF94A3B8)
val SelBg = Color(0xFF1E3A5F)
val ErrorRed = Color(0xFFF87171)
val Gold = Color(0xFFFACC15)
val Line = Color(0x33FFFFFF)

@Composable
fun MyTiviTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = OnAccent,
            primaryContainer = SelBg,
            onPrimaryContainer = Color.White,
            secondary = Accent,
            onSecondary = OnAccent,
            secondaryContainer = SelBg,
            onSecondaryContainer = Color.White,
            background = Bg,
            onBackground = Color.White,
            surface = Panel,
            onSurface = Color.White,
            surfaceVariant = CardBg,
            onSurfaceVariant = Dim,
            surfaceContainerLowest = Bg,
            surfaceContainerLow = Panel,
            surfaceContainer = Panel,
            surfaceContainerHigh = CardBg,
            surfaceContainerHighest = CardBg,
            outline = Color(0x66FFFFFF),
            outlineVariant = Line,
            error = ErrorRed,
        ),
        content = content,
    )
}

/** Icon vẽ từ file vector trong res/drawable (bộ icon Material đã chép sẵn). */
@Composable
fun AppIcon(id: Int, desc: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painter = painterResource(id), contentDescription = desc, modifier = modifier, tint = tint)
}

/** Nút tròn cho màn hình cảm ứng (mặc định 48dp – đúng cỡ ngón tay theo khuyến nghị Android). */
@Composable
fun RoundIcon(
    id: Int,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    tint: Color = Color.White,
    background: Color = Color(0x40000000),
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(id, desc, Modifier.size(iconSize), tint)
    }
}

/** Giờ hiện tại, cập nhật mỗi 30 giây (để thanh tiến độ chương trình tự chạy). */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

fun progressOf(p: Programme, now: Long): Float =
    ((now - p.start).toFloat() / (p.stop - p.start).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

/** "còn 25 phút" / "sau 10 phút" / "đã kết thúc" cho một chương trình. */
fun remainText(p: Programme, now: Long): String = when {
    now < p.start -> "bắt đầu sau ${(p.start - now + 59_999) / 60_000} phút"
    now < p.stop -> "còn ${(p.stop - now + 59_999) / 60_000} phút"
    else -> "đã kết thúc"
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
