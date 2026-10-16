@file:OptIn(ExperimentalLayoutApi::class)

package com.mytivi.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cài đặt: link playlist (.m3u / .m3u8), link EPG, tùy chọn mở app, phiên bản và kiểm tra cập nhật.
 * Có nút "Dán" cạnh mỗi ô vì gõ link dài trên bàn phím ảo tablet rất mất công.
 */
@Composable
fun SetupScreen(
    initialUrl: String,
    initialEpg: String,
    initialAutoFullscreen: Boolean,
    canCancel: Boolean,
    version: String,
    onSave: (url: String, epg: String, autoFullscreen: Boolean) -> Unit,
    onCancel: () -> Unit,
    onCheckUpdate: () -> Unit,
    onAbout: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var epg by remember { mutableStateOf(initialEpg) }
    var autoFull by remember { mutableStateOf(initialAutoFullscreen) }
    var err by remember { mutableStateOf<String?>(null) }
    val firstField = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current

    BackHandler(enabled = canCancel) { onCancel() }
    // Lần đầu dùng (chưa có link) thì mở sẵn bàn phím ở ô playlist
    LaunchedEffect(Unit) { if (initialUrl.isBlank()) runCatching { firstField.requestFocus() } }

    fun save() {
        val u = url.trim()
        val e = epg.trim()
        err = when {
            !u.startsWith("http://") && !u.startsWith("https://") ->
                "Link playlist phải bắt đầu bằng http:// hoặc https://"
            e.isNotEmpty() && !e.startsWith("http://") && !e.startsWith("https://") ->
                "Link EPG phải bắt đầu bằng http:// hoặc https://"
            else -> null
        }
        if (err == null) onSave(u, e, autoFull)
    }

    fun paste(): String? = clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }

    Box(
        Modifier.fillMaxSize().background(Bg).safeDrawingPadding(),   // safeDrawing gồm cả bàn phím ảo
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Cài đặt MyTivi", style = MaterialTheme.typography.headlineSmall)
                    Text("Phiên bản $version · bản máy tính bảng", color = Dim, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Dán link danh sách kênh (.m3u) hoặc một luồng .m3u8. Link EPG không bắt buộc – " +
                    "để trống thì app dùng link EPG ghi sẵn trong playlist (nếu có).",
                color = Dim,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link playlist (.m3u) hoặc luồng .m3u8") },
                singleLine = true,
                trailingIcon = { TextButton(onClick = { paste()?.let { url = it } }) { Text("Dán") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(firstField),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = epg,
                onValueChange = { epg = it },
                label = { Text("Link EPG – lịch phát sóng XMLTV (không bắt buộc)") },
                singleLine = true,
                trailingIcon = { TextButton(onClick = { paste()?.let { epg = it } }) { Text("Dán") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { autoFull = !autoFull }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Mở app là xem toàn màn hình")
                    Text(
                        "Vào thẳng kênh xem gần nhất (giống bản Android TV). Tắt thì mở ra màn hình lịch kênh.",
                        color = Dim,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = autoFull, onCheckedChange = { autoFull = it })
            }

            err?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = ErrorRed)
            }
            Spacer(Modifier.height(20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { save() }) { Text("Lưu") }
                OutlinedButton(onClick = onCheckUpdate) { Text("Kiểm tra cập nhật") }
                OutlinedButton(onClick = onAbout) { Text("Giới thiệu") }
                if (canCancel) TextButton(onClick = onCancel) { Text("Hủy") }
            }
        }
    }
}
