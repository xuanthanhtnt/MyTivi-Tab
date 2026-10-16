package com.mytivi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Bg = Color(0xFF0B1220)
val Accent = Color(0xFF38BDF8)
val Dim = Color(0xFF94A3B8)
val FocusBg = Color(0xFF1E3A5F)
val ErrorRed = Color(0xFFF87171)
val Gold = Color(0xFFFACC15)

/**
 * Phần tử điều hướng được bằng remote: tô nền + viền khi đang được focus.
 * Lưu ý: onFocusChanged phải đứng TRƯỚC clickable (clickable là nơi tạo focus).
 */
@Composable
fun Modifier.tvFocusable(onClick: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    return this
        .onFocusChanged { focused = it.isFocused }
        .background(if (focused) FocusBg else Color.Transparent, shape)
        .then(if (focused) Modifier.border(2.dp, Accent, shape) else Modifier)
        .clickable(onClick = onClick)
}

@Composable
fun TvButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        modifier = Modifier
            .tvFocusable(onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
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
