package com.mytivi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Hộp thoại cập nhật, điều khiển hoàn toàn bằng remote. */
@Composable
fun UpdateDialog(vm: UpdateViewModel) {
    if (!vm.showDialog) return
    val state = vm.state
    val firstButton = remember { FocusRequester() }
    // Mỗi lần đổi bước (đang kiểm tra -> có bản mới -> đang tải...) đưa focus về nút đầu tiên
    LaunchedEffect(state::class) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstButton.requestFocus() }
    }

    Dialog(onDismissRequest = { vm.dismiss() }) {
        Column(
            Modifier
                .width(560.dp)
                .background(Color(0xFF0F1A2E), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            Text("Cập nhật MyTivi", style = MaterialTheme.typography.headlineSmall)
            Text("Bản đang dùng: ${vm.currentVersion}", color = Dim, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))

            when (state) {
                is UpdateState.Idle, is UpdateState.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                        Text("   Đang kiểm tra bản mới…")
                    }
                    Buttons { DialogButton("Đóng", firstButton) { vm.dismiss() } }
                }
                is UpdateState.UpToDate -> {
                    Text("Bạn đang dùng bản mới nhất.")
                    Buttons { DialogButton("Đóng", firstButton) { vm.dismiss() } }
                }
                is UpdateState.Available -> {
                    Text("Có bản mới: ${state.info.versionName}", color = Accent, fontSize = 18.sp)
                    if (state.info.notes.isNotBlank()) {
                        Text(
                            state.info.notes,
                            color = Dim,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Buttons {
                        DialogButton("Cập nhật ngay", firstButton) { vm.download(state.info) }
                        DialogButton("Để sau") { vm.dismiss() }
                    }
                }
                is UpdateState.Downloading -> {
                    val p = state.progress
                    Text(if (p >= 0f) "Đang tải… ${(p * 100).toInt()}%" else "Đang tải…")
                    Spacer(Modifier.height(10.dp))
                    if (p >= 0f) {
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Buttons { DialogButton("Hủy", firstButton) { vm.dismiss() } }
                }
                is UpdateState.NeedPermission -> {
                    Text(
                        "Lần đầu cập nhật, cần cho phép MyTivi cài ứng dụng.\n" +
                            "1. Bấm \"Mở cài đặt\" và bật cho phép.\n" +
                            "2. Bấm Back để quay lại, rồi bấm \"Cài đặt\".",
                    )
                    Buttons {
                        DialogButton("Mở cài đặt", firstButton) { vm.openPermissionSettings() }
                        DialogButton("Cài đặt") { vm.install(state.info, state.apk) }
                    }
                }
                is UpdateState.Installing -> {
                    Text("Đã mở trình cài đặt. Chọn \"Cài đặt\" hoặc \"Cập nhật\" trên màn hình của Android.")
                    Text(
                        "Nếu không thấy trình cài đặt hiện ra, bấm \"Cài lại\".",
                        color = Dim,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Buttons {
                        DialogButton("Cài lại", firstButton) { vm.install(state.info, state.apk) }
                        DialogButton("Đóng") { vm.dismiss() }
                    }
                }
                is UpdateState.Failed -> {
                    Text(state.message, color = ErrorRed)
                    Buttons {
                        DialogButton("Thử lại", firstButton) { vm.check(silent = false) }
                        DialogButton("Đóng") { vm.dismiss() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Buttons(content: @Composable () -> Unit) {
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun DialogButton(label: String, requester: FocusRequester? = null, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        modifier = Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .tvFocusable(onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
