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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private enum class SourceKind { Playlist, Epg }

/** Hộp thoại đang mở: thêm (source = null) hoặc sửa một nguồn. */
private data class SourceEdit(val kind: SourceKind, val source: Source?)

/**
 * Cài đặt:
 *  - NHIỀU danh sách kênh (M3U / luồng .m3u8): thêm, sửa, xóa, bật/tắt, đưa lên trên.
 *  - NHIỀU link lịch phát sóng (EPG, XMLTV): như trên; link ở trên được ưu tiên khi trùng kênh.
 *  - Tự dùng EPG ghi sẵn trong playlist, mở app là xem toàn màn hình, kiểm tra cập nhật, giới thiệu.
 * Mọi thay đổi chỉ áp dụng khi bấm "Lưu" (bấm "Hủy" / Quay lại là bỏ).
 */
@Composable
fun SetupScreen(
    initialPlaylists: List<Source>,
    initialEpgs: List<Source>,
    initialUseAutoEpg: Boolean,
    initialAutoFullscreen: Boolean,
    canCancel: Boolean,
    version: String,
    onSave: (playlists: List<Source>, epgs: List<Source>, useAutoEpg: Boolean, autoFullscreen: Boolean) -> Unit,
    onCancel: () -> Unit,
    onCheckUpdate: () -> Unit,
    onAbout: () -> Unit,
) {
    val playlists = remember { mutableStateListOf<Source>().apply { addAll(initialPlaylists) } }
    val epgs = remember { mutableStateListOf<Source>().apply { addAll(initialEpgs) } }
    var useAutoEpg by remember { mutableStateOf(initialUseAutoEpg) }
    var autoFull by remember { mutableStateOf(initialAutoFullscreen) }
    var err by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<SourceEdit?>(null) }
    var deleting by remember { mutableStateOf<SourceEdit?>(null) }

    BackHandler(enabled = canCancel && editor == null && deleting == null) { onCancel() }
    // Lần đầu dùng (chưa có danh sách nào): mở luôn hộp thoại thêm danh sách kênh
    LaunchedEffect(Unit) {
        if (initialPlaylists.isEmpty()) editor = SourceEdit(SourceKind.Playlist, null)
    }

    fun listFor(kind: SourceKind) = if (kind == SourceKind.Playlist) playlists else epgs

    fun save() {
        err = when {
            playlists.isEmpty() -> "Thêm ít nhất 1 danh sách kênh (.m3u)."
            playlists.none { it.enabled } -> "Bật ít nhất 1 danh sách kênh."
            else -> null
        }
        if (err == null) onSave(playlists.toList(), epgs.toList(), useAutoEpg, autoFull)
    }

    Box(
        Modifier.fillMaxSize().background(Bg).safeDrawingPadding(),   // safeDrawing gồm cả bàn phím ảo
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

            SourceSection(
                title = "Danh sách kênh (M3U)",
                hint = "Thêm được nhiều danh sách. Kênh của các danh sách đang bật được gộp lại; " +
                    "ngăn nhóm kênh chia mục theo từng danh sách.",
                items = playlists,
                emptyText = "Chưa có danh sách kênh nào.",
                addLabel = "Thêm danh sách kênh",
                onAdd = { editor = SourceEdit(SourceKind.Playlist, null) },
                onEdit = { editor = SourceEdit(SourceKind.Playlist, it) },
                onToggle = { s -> playlists.replaceById(s.copy(enabled = !s.enabled)) },
                onMoveUp = { s -> playlists.moveUp(s) },
                onDelete = { deleting = SourceEdit(SourceKind.Playlist, it) },
            )

            SourceSection(
                title = "Lịch phát sóng (EPG)",
                hint = "Không bắt buộc. Thêm được nhiều link XMLTV (.xml, .xml.gz); lịch được ghép theo tvg-id, " +
                    "link ở trên được ưu tiên khi nhiều EPG có cùng kênh.",
                items = epgs,
                emptyText = "Chưa thêm link EPG nào.",
                addLabel = "Thêm link EPG",
                onAdd = { editor = SourceEdit(SourceKind.Epg, null) },
                onEdit = { editor = SourceEdit(SourceKind.Epg, it) },
                onToggle = { s -> epgs.replaceById(s.copy(enabled = !s.enabled)) },
                onMoveUp = { s -> epgs.moveUp(s) },
                onDelete = { deleting = SourceEdit(SourceKind.Epg, it) },
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Panel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                SwitchRow(
                    title = "Tự dùng EPG ghi sẵn trong playlist",
                    desc = "Đọc link url-tvg ở đầu file .m3u (nếu có), dùng sau các link EPG ở trên.",
                    checked = useAutoEpg,
                    onChange = { useAutoEpg = it },
                )
                SwitchRow(
                    title = "Mở app là xem toàn màn hình",
                    desc = "Vào thẳng kênh xem gần nhất (giống bản Android TV). Tắt thì mở ra màn hình lịch kênh.",
                    checked = autoFull,
                    onChange = { autoFull = it },
                )
            }

            err?.let { Text(it, color = ErrorRed) }
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

    editor?.let { e ->
        SourceDialog(
            edit = e,
            onDismiss = { editor = null },
            onSave = { name, url ->
                val list = listFor(e.kind)
                val src = e.source
                if (src == null) {
                    val fallback = if (e.kind == SourceKind.Playlist) "Danh sách ${list.size + 1}" else "EPG ${list.size + 1}"
                    list.add(Source(MainViewModel.newSourceId(), name.ifBlank { fallback }, url))
                } else {
                    list.replaceById(src.copy(name = name.ifBlank { src.name }, url = url))
                }
                err = null
                editor = null
            },
        )
    }
    deleting?.let { d ->
        val src = d.source
        if (src != null) {
            ConfirmDeleteDialog(
                name = src.name,
                isPlaylist = d.kind == SourceKind.Playlist,
                onConfirm = {
                    listFor(d.kind).removeAll { it.id == src.id }
                    deleting = null
                },
                onDismiss = { deleting = null },
            )
        }
    }
}

private fun MutableList<Source>.replaceById(s: Source) {
    val i = indexOfFirst { it.id == s.id }
    if (i >= 0) this[i] = s
}

private fun MutableList<Source>.moveUp(s: Source) {
    val i = indexOfFirst { it.id == s.id }
    if (i > 0) {
        val above = this[i - 1]
        this[i - 1] = this[i]
        this[i] = above
    }
}

@Composable
private fun SourceSection(
    title: String,
    hint: String,
    items: List<Source>,
    emptyText: String,
    addLabel: String,
    onAdd: () -> Unit,
    onEdit: (Source) -> Unit,
    onToggle: (Source) -> Unit,
    onMoveUp: (Source) -> Unit,
    onDelete: (Source) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(hint, color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        if (items.isEmpty()) {
            Text(emptyText, color = Dim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        items.forEachIndexed { i, s ->
            SourceRow(
                source = s,
                canMoveUp = i > 0,
                onEdit = { onEdit(s) },
                onToggle = { onToggle(s) },
                onMoveUp = { onMoveUp(s) },
                onDelete = { onDelete(s) },
            )
        }
        TextButton(onClick = onAdd, modifier = Modifier.padding(top = 4.dp)) {
            AppIcon(R.drawable.ic_add, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(addLabel)
        }
    }
}

@Composable
private fun SourceRow(
    source: Source,
    canMoveUp: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onEdit)
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                source.name,
                color = if (source.enabled) Color.White else Dim,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                source.url,
                color = Dim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canMoveUp) {
            IconButton(onClick = onMoveUp) { AppIcon(R.drawable.ic_arrow_up, "Đưa lên trên", tint = Dim) }
        } else {
            Spacer(Modifier.width(48.dp))
        }
        IconButton(onClick = onEdit) { AppIcon(R.drawable.ic_edit, "Sửa", tint = Dim) }
        IconButton(onClick = onDelete) { AppIcon(R.drawable.ic_delete, "Xóa", tint = Dim) }
        Switch(checked = source.enabled, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(desc, color = Dim, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Thêm / sửa một nguồn: link (có nút Dán) + tên hiển thị. */
@Composable
private fun SourceDialog(edit: SourceEdit, onDismiss: () -> Unit, onSave: (name: String, url: String) -> Unit) {
    val isPlaylist = edit.kind == SourceKind.Playlist
    var url by remember { mutableStateOf(edit.source?.url ?: "") }
    var name by remember { mutableStateOf(edit.source?.name ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val urlFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { urlFocus.requestFocus() } }

    fun submit() {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            err = "Link phải bắt đầu bằng http:// hoặc https://"
            return
        }
        onSave(name.trim(), u)
    }

    val title = when {
        edit.source != null && isPlaylist -> "Sửa danh sách kênh"
        edit.source != null -> "Sửa link EPG"
        isPlaylist -> "Thêm danh sách kênh"
        else -> "Thêm link EPG"
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                if (isPlaylist) "Link file .m3u (danh sách kênh) hoặc một luồng .m3u8."
                else "Link lịch phát sóng XMLTV (.xml hoặc .xml.gz).",
                color = Dim,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    err = null
                },
                label = { Text(if (isPlaylist) "Link danh sách kênh" else "Link EPG") },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = {
                        clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { url = it }
                    }) { Text("Dán") }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(urlFocus),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tên hiển thị (không bắt buộc)") },
                placeholder = { Text(if (isPlaylist) "Ví dụ: Truyền hình VN" else "Ví dụ: EPG VN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            err?.let { Text(it, color = ErrorRed, modifier = Modifier.padding(top = 8.dp)) }
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Hủy") }
                Button(onClick = { submit() }) { Text(if (edit.source == null) "Thêm" else "Lưu") }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(name: String, isPlaylist: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text("Xóa “$name”?", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (isPlaylist) "Kênh của danh sách này sẽ không còn trong app sau khi bấm Lưu."
                else "Lịch phát sóng từ link này sẽ không còn dùng sau khi bấm Lưu.",
                color = Dim,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Hủy") }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                ) { Text("Xóa") }
            }
        }
    }
}
