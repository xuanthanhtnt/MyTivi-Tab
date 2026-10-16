package com.mytivi.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cài đặt: link playlist (.m3u / .m3u8) và link EPG, kèm phiên bản app và nút kiểm tra cập nhật. */
@Composable
fun SetupScreen(
    initialUrl: String,
    initialEpg: String,
    canCancel: Boolean,
    version: String,
    onSave: (url: String, epg: String) -> Unit,
    onCancel: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var epg by remember { mutableStateOf(initialEpg) }
    var err by remember { mutableStateOf<String?>(null) }
    val firstField = remember { FocusRequester() }

    BackHandler(enabled = canCancel) { onCancel() }
    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Cài đặt MyTivi", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text("Phiên bản $version", color = Dim, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text("Dán link danh sách kênh (.m3u) hoặc một luồng .m3u8", color = Dim)
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Link playlist (.m3u) hoặc luồng .m3u8") },
            singleLine = true,
            modifier = Modifier.width(680.dp).focusRequester(firstField),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = epg,
            onValueChange = { epg = it },
            label = { Text("Link EPG – lịch phát sóng XMLTV (không bắt buộc)") },
            singleLine = true,
            modifier = Modifier.width(680.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        err?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = ErrorRed)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton("Lưu") {
                val u = url.trim()
                val e = epg.trim()
                err = when {
                    !u.startsWith("http://") && !u.startsWith("https://") ->
                        "Link playlist phải bắt đầu bằng http:// hoặc https://"
                    e.isNotEmpty() && !e.startsWith("http://") && !e.startsWith("https://") ->
                        "Link EPG phải bắt đầu bằng http:// hoặc https://"
                    else -> null
                }
                if (err == null) onSave(u, e)
            }
            TvButton("Kiểm tra cập nhật", onCheckUpdate)
            if (canCancel) TvButton("Hủy", onCancel)
        }
    }
}
