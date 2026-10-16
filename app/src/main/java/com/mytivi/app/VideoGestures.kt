package com.mytivi.app

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

private enum class GestureMode { None, Horizontal, Vertical, Pinch }

/**
 * Cử chỉ trên video toàn màn hình (một bộ xử lý duy nhất để các cử chỉ không giành nhau):
 *  - Chạm: hiện/ẩn bảng điều khiển
 *  - Vuốt sang trái: mở danh sách kênh · vuốt sang phải: đóng danh sách
 *  - Vuốt dọc nửa trái: độ sáng; nửa phải: âm lượng
 *  - Chụm/mở 2 ngón: thu nhỏ (vừa khung) / phóng to (lấp đầy màn hình)
 */
@Composable
fun Modifier.videoGestures(
    onTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onVerticalStart: (left: Boolean) -> Unit,
    onVerticalDrag: (left: Boolean, delta: Float) -> Unit,
    onPinch: (zoomIn: Boolean) -> Unit,
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val swipeLeft by rememberUpdatedState(onSwipeLeft)
    val swipeRight by rememberUpdatedState(onSwipeRight)
    val vStart by rememberUpdatedState(onVerticalStart)
    val vDrag by rememberUpdatedState(onVerticalDrag)
    val pinch by rememberUpdatedState(onPinch)

    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            val left = down.position.x < size.width / 2f
            var mode = GestureMode.None
            var total = Offset.Zero
            var pinchStart = 0f
            var pinchDone = false

            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                // Hai ngón: chụm / mở (chỉ khi chưa bắt đầu vuốt một ngón)
                if (pressed.size >= 2 && (mode == GestureMode.None || mode == GestureMode.Pinch)) {
                    val d = (pressed[0].position - pressed[1].position).getDistance()
                    if (mode == GestureMode.None) {
                        mode = GestureMode.Pinch
                        pinchStart = d
                    } else if (!pinchDone && pinchStart > 0f) {
                        val ratio = d / pinchStart
                        if (ratio > 1.2f) {
                            pinchDone = true
                            pinch(true)
                        } else if (ratio < 0.83f) {
                            pinchDone = true
                            pinch(false)
                        }
                    }
                    event.changes.forEach { it.consume() }
                    continue
                }
                if (mode == GestureMode.Pinch) {
                    event.changes.forEach { it.consume() }
                    continue
                }

                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                val delta = c.position - c.previousPosition
                total += delta
                if (mode == GestureMode.None) {
                    if (abs(total.x) > slop && abs(total.x) > abs(total.y)) {
                        mode = GestureMode.Horizontal
                    } else if (abs(total.y) > slop) {
                        mode = GestureMode.Vertical
                        vStart(left)
                    }
                }
                when (mode) {
                    GestureMode.Vertical -> {
                        // Kéo lên = tăng. delta là phần của chiều cao màn hình (kéo hết màn hình ≈ 0 → 100%)
                        vDrag(left, -delta.y / size.height)
                        c.consume()
                    }
                    GestureMode.Horizontal -> c.consume()
                    else -> Unit
                }
            }

            when (mode) {
                GestureMode.None -> tap()
                GestureMode.Horizontal ->
                    if (abs(total.x) > size.width * 0.12f) {
                        if (total.x < 0f) swipeLeft() else swipeRight()
                    }
                else -> Unit
            }
        }
    }
}
