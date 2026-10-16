package com.mytivi.app

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Thông tin nhà phát triển hiện ở màn hình Giới thiệu.
 * ĐỔI Ở ĐÂY là đổi trong app (tên, email liên hệ, năm bản quyền...).
 */
object AppInfo {
    const val APP_NAME = "MyTivi Tab"
    const val TAGLINE = "Trình phát IPTV cho máy tính bảng Android"
    const val DEVELOPER = "xuanthanhtnt"
    const val EMAIL = "xuanthanhtnt@gmail.com"
    const val COPYRIGHT_FROM = 2026
    val repoUrl: String get() = "https://github.com/${BuildConfig.UPDATE_REPO}"
    val issuesUrl: String get() = "$repoUrl/issues"
    const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
}

/** Thư viện mã nguồn mở dùng trong app (tên – giấy phép). */
private val LIBRARIES = listOf(
    "Jetpack Compose · Material 3" to "Apache License 2.0",
    "AndroidX Media3 (ExoPlayer)" to "Apache License 2.0",
    "OkHttp" to "Apache License 2.0",
    "Coil" to "Apache License 2.0",
    "Kotlin · Kotlin Coroutines" to "Apache License 2.0",
    "Material Design Icons" to "Apache License 2.0",
)

/**
 * Màn hình Giới thiệu: phiên bản & bản dựng, nhà phát triển, liên hệ, thông tin thiết bị
 * (sao chép được để gửi kèm khi báo lỗi), thư viện mã nguồn mở và điều khoản sử dụng.
 * Màn hình rộng (tablet nằm ngang) chia 2 cột.
 */
@Composable
fun AboutScreen(
    version: String,
    updateAvailable: Boolean,
    channelCount: Int,
    lastUpdated: Long,
    onCheckUpdate: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val config = LocalConfiguration.current
    var copied by remember { mutableStateOf(false) }
    val device = remember(config) { deviceLines(context, config) }

    BackHandler { onBack() }

    fun openLink(url: String) {
        runCatching { uri.openUri(url) }
    }

    val mailSubject = "[${AppInfo.APP_NAME} $version] Góp ý / báo lỗi"
    val mailUrl = "mailto:${AppInfo.EMAIL}?subject=${Uri.encode(mailSubject)}"
    val playlistLine = if (lastUpdated > 0) {
        "$channelCount kênh · cập nhật " +
            SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(lastUpdated))
    } else {
        "$channelCount kênh"
    }
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val years = if (year > AppInfo.COPYRIGHT_FROM) "${AppInfo.COPYRIGHT_FROM}–$year" else "${AppInfo.COPYRIGHT_FROM}"

    // ---- Các khối nội dung (xếp 1 hoặc 2 cột tùy bề rộng) ----
    val developer: @Composable () -> Unit = {
        Section(R.drawable.ic_person, "Nhà phát triển") {
            InfoRow("Phát triển bởi", AppInfo.DEVELOPER)
            InfoRow("Email liên hệ", AppInfo.EMAIL, R.drawable.ic_mail) { openLink(mailUrl) }
            InfoRow("Mã nguồn", AppInfo.repoUrl.removePrefix("https://"), R.drawable.ic_open) { openLink(AppInfo.repoUrl) }
            InfoRow("Báo lỗi / góp ý", "Mở trang Issues trên GitHub", R.drawable.ic_open) { openLink(AppInfo.issuesUrl) }
        }
    }
    val app: @Composable () -> Unit = {
        Section(R.drawable.ic_info, "Ứng dụng") {
            InfoRow("Phiên bản", "$version (bản dựng ${BuildConfig.VERSION_CODE})")
            InfoRow("Ngày dựng", "${BuildConfig.BUILD_TIME} · ${BuildConfig.GIT_SHA}")
            InfoRow("Mã ứng dụng", context.packageName)
            InfoRow("Kênh cập nhật", BuildConfig.UPDATE_REPO)
            InfoRow("Danh sách kênh", playlistLine)
        }
    }
    val deviceCard: @Composable () -> Unit = {
        Section(R.drawable.ic_tablet, "Thiết bị") {
            device.forEach { (label, value) -> InfoRow(label, value) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val text = buildString {
                    appendLine("${AppInfo.APP_NAME} $version (${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_SHA}")
                    device.forEach { (label, value) -> appendLine("$label: $value") }
                }
                clipboard.setText(AnnotatedString(text))
                copied = true
            }) {
                AppIcon(R.drawable.ic_copy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Đã sao chép" else "Sao chép thông tin để báo lỗi")
            }
        }
    }
    val libraries: @Composable () -> Unit = {
        Section(R.drawable.ic_code, "Thư viện mã nguồn mở") {
            LIBRARIES.forEach { (name, license) -> InfoRow(name, license) }
            InfoRow("Toàn văn giấy phép", "apache.org/licenses/LICENSE-2.0", R.drawable.ic_open) { openLink(AppInfo.LICENSE_URL) }
        }
    }
    val terms: @Composable () -> Unit = {
        Section(R.drawable.ic_info, "Điều khoản & quyền riêng tư") {
            Text(
                "${AppInfo.APP_NAME} chỉ là trình phát: ứng dụng không cung cấp, lưu trữ hay phân phối kênh truyền hình nào. " +
                    "Bạn tự nhập danh sách kênh (M3U) và lịch phát sóng (XMLTV), và tự chịu trách nhiệm về quyền xem các nội dung đó.",
                color = Dim,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ứng dụng không thu thập dữ liệu cá nhân, không quảng cáo. Link playlist, kênh yêu thích và lịch sử xem chỉ lưu trên máy này. " +
                    "App chỉ kết nối tới máy chủ playlist/EPG do bạn nhập và tới GitHub để kiểm tra bản cập nhật.",
                color = Dim,
                fontSize = 14.sp,
            )
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { AppIcon(R.drawable.ic_back, "Quay lại") }
            Text("Giới thiệu", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val twoColumns = maxWidth >= 840.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 1040.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Header(version, updateAvailable, onCheckUpdate, onOpenRepo = { openLink(AppInfo.repoUrl) })
                    if (twoColumns) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                developer()
                                app()
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                deviceCard()
                                libraries()
                            }
                        }
                    } else {
                        developer()
                        app()
                        deviceCard()
                        libraries()
                    }
                    terms()
                    Text(
                        "© $years ${AppInfo.DEVELOPER} · Phát triển tại Việt Nam",
                        color = Dim,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(version: String, updateAvailable: Boolean, onCheckUpdate: () -> Unit, onOpenRepo: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(AppInfo.APP_NAME, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(AppInfo.TAGLINE, color = Dim)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Phiên bản $version", Color.White)
                    if (updateAvailable) Pill("Có bản mới", Accent)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCheckUpdate) {
                AppIcon(R.drawable.ic_update, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (updateAvailable) "Cập nhật ngay" else "Kiểm tra cập nhật")
            }
            OutlinedButton(onClick = onOpenRepo) {
                AppIcon(R.drawable.ic_open, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Trang dự án")
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .border(1.dp, if (color == Color.White) Color(0x66FFFFFF) else color, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Section(icon: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            AppIcon(icon, null, Modifier.size(20.dp), tint = Accent)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

/** Một dòng "nhãn — giá trị"; có onClick thì chạm được (mở link, gửi email...). */
@Composable
private fun InfoRow(label: String, value: String, icon: Int? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Dim, fontSize = 14.sp, modifier = Modifier.width(150.dp))
        Text(
            value,
            color = if (onClick != null) Accent else Color.White,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            AppIcon(icon, null, Modifier.size(18.dp), tint = Accent)
        }
    }
}

/** Thông tin máy để gửi kèm khi báo lỗi. */
private fun deviceLines(context: Context, config: Configuration): List<Pair<String, String>> {
    val maker = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val pip = Build.VERSION.SDK_INT >= 26 &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    return listOf(
        "Thiết bị" to "$maker ${Build.MODEL}",
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Màn hình" to "${config.screenWidthDp} × ${config.screenHeightDp} dp · ${config.densityDpi} dpi",
        "Cửa sổ nổi" to if (pip) "Có hỗ trợ" else "Không hỗ trợ",
    )
}
