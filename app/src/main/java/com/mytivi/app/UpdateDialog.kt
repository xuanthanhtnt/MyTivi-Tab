package com.mytivi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Hộp thoại cập nhật (chạm, hoặc bàn phím). Luồng giống hệt bản TV: kiểm tra → tải → mở trình cài đặt. */
@Composable
fun UpdateDialog(vm: UpdateViewModel) {
    if (!vm.showDialog) return
    val state = vm.state

    Dialog(onDismissRequest = { vm.dismiss() }) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(20.dp))
                .padding(24.dp),
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
                    Buttons { TextButton(onClick = { vm.dismiss() }) { Text("Đóng") } }
                }
                is UpdateState.UpToDate -> {
                    Text("Bạn đang dùng bản mới nhất.")
                    Buttons { Button(onClick = { vm.dismiss() }) { Text("Đóng") } }
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
                        TextButton(onClick = { vm.dismiss() }) { Text("Để sau") }
                        Button(onClick = { vm.download(state.info) }) { Text("Cập nhật ngay") }
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
                    Buttons { TextButton(onClick = { vm.dismiss() }) { Text("Hủy") } }
                }
                is UpdateState.NeedPermission -> {
                    Text(
                        "Lần đầu cập nhật, cần cho phép MyTivi cài ứng dụng.\n" +
                            "1. Chạm \"Mở cài đặt\" và bật \"Cho phép từ nguồn này\".\n" +
                            "2. Quay lại MyTivi, rồi chạm \"Cài đặt\".",
                    )
                    Buttons {
                        TextButton(onClick = { vm.openPermissionSettings() }) { Text("Mở cài đặt") }
                        Button(onClick = { vm.install(state.info, state.apk) }) { Text("Cài đặt") }
                    }
                }
                is UpdateState.Installing -> {
                    Text("Đã mở trình cài đặt. Chọn \"Cài đặt\" hoặc \"Cập nhật\" trên màn hình của Android.")
                    Text(
                        "Nếu không thấy trình cài đặt hiện ra, chạm \"Cài lại\".",
                        color = Dim,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Buttons {
                        TextButton(onClick = { vm.dismiss() }) { Text("Đóng") }
                        Button(onClick = { vm.install(state.info, state.apk) }) { Text("Cài lại") }
                    }
                }
                is UpdateState.Failed -> {
                    Text(state.message, color = ErrorRed)
                    Buttons {
                        TextButton(onClick = { vm.dismiss() }) { Text("Đóng") }
                        Button(onClick = { vm.check(silent = false) }) { Text("Thử lại") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Buttons(content: @Composable RowScope.() -> Unit) {
    Spacer(Modifier.height(22.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        content = content,
    )
}
