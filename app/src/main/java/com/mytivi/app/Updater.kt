package com.mytivi.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Thông tin bản phát hành mới nhất trên GitHub Releases. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
)

/**
 * Cập nhật trong app:
 *  - Mỗi lần push mã lên GitHub, Actions build APK và tạo Release có tag "v<số lượt build>".
 *  - App đọc Release mới nhất, so số đó với versionCode đang cài, có bản mới thì tải APK về
 *    và mở trình cài đặt của Android.
 */
object Updater {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Trả về null nếu repo chưa có bản phát hành nào có file .apk. */
    fun fetchLatest(): UpdateInfo? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MyTivi/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw IllegalStateException("GitHub trả về HTTP ${resp.code}")
            val json = JSONObject(resp.body!!.string())
            val tag = json.optString("tag_name")
            val code = tag.removePrefix("v").removePrefix("V").toIntOrNull() ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    return UpdateInfo(
                        versionCode = code,
                        versionName = "1.0.$code",
                        notes = json.optString("body").trim(),
                        apkUrl = asset.getString("browser_download_url"),
                    )
                }
            }
            return null
        }
    }

    /** Thư mục chứa APK tải về. Android 7+ dùng FileProvider; Android 6 cần file đọc được từ ngoài app. */
    private fun updateDir(context: Context): File {
        val base = if (Build.VERSION.SDK_INT >= 24) context.cacheDir
        else context.getExternalFilesDir(null) ?: context.cacheDir
        return File(base, "updates").apply { mkdirs() }
    }

    /** Tải APK, báo tiến độ 0..1 (hoặc -1 nếu không biết dung lượng). Hủy coroutine là dừng tải. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = updateDir(context)
            dir.listFiles()?.forEach { it.delete() }          // dọn bản tải cũ
            val part = File(dir, "download.part")
            val target = File(dir, "MyTivi-${info.versionCode}.apk")

            val request = Request.Builder().url(info.apkUrl)
                .header("User-Agent", "MyTivi/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Tải về lỗi HTTP ${resp.code}")
                val body = resp.body!!
                val total = body.contentLength()
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReport = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - lastReport > 256 * 1024) {
                                lastReport = done
                                onProgress(if (total > 0) done.toFloat() / total else -1f)
                            }
                        }
                    }
                }
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }

            // Kiểm tra file tải về đúng là MyTivi và mới hơn bản đang cài
            val pkg = context.packageManager.getPackageArchiveInfo(target.path, 0)
                ?: throw IllegalStateException("File tải về bị lỗi, hãy thử lại")
            if (pkg.packageName != context.packageName) {
                throw IllegalStateException("File tải về không phải MyTivi")
            }
            if (PackageInfoCompat.getLongVersionCode(pkg) <= BuildConfig.VERSION_CODE.toLong()) {
                throw IllegalStateException("Bản tải về không mới hơn bản đang dùng")
            }
            if (Build.VERSION.SDK_INT < 24) target.setReadable(true, false)
            onProgress(1f)
            target
        }

    /** Mở trình cài đặt hệ thống cho file APK. */
    fun launchInstaller(context: Context, apk: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        } else {
            Uri.fromFile(apk)
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
