package com.mytivi.app

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class NeedPermission(val info: UpdateInfo, val apk: File) : UpdateState
    data class Installing(val info: UpdateInfo, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var showDialog by mutableStateOf(false)
        private set

    val currentVersion: String = BuildConfig.VERSION_NAME
    val updateAvailable: Boolean get() = state is UpdateState.Available

    private var job: Job? = null

    init {
        check(silent = true)          // mở app là tự kiểm tra ngầm, có bản mới thì nút đổi màu
    }

    /** Nút "Kiểm tra cập nhật" trong ngăn nhóm kênh. */
    fun onButton() {
        showDialog = true
        when (state) {
            is UpdateState.Idle, is UpdateState.UpToDate, is UpdateState.Failed -> check(silent = false)
            else -> Unit
        }
    }

    fun check(silent: Boolean) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            state = UpdateState.Checking
            state = try {
                val info = withContext(Dispatchers.IO) { Updater.fetchLatest() }
                if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Failed("Không kiểm tra được cập nhật: ${e.message}")
            }
        }
    }

    fun download(info: UpdateInfo) {
        job?.cancel()
        job = viewModelScope.launch {
            state = UpdateState.Downloading(info, 0f)
            try {
                val apk = Updater.download(getApplication(), info) { p ->
                    viewModelScope.launch { if (state is UpdateState.Downloading) state = UpdateState.Downloading(info, p) }
                }
                install(info, apk)
            } catch (e: CancellationException) {
                state = UpdateState.Available(info)
                throw e
            } catch (e: Exception) {
                state = UpdateState.Failed("Tải bản cập nhật lỗi: ${e.message}")
            }
        }
    }

    fun install(info: UpdateInfo, apk: File) {
        val ctx = getApplication<Application>()
        // Android 8+: phải cho phép MyTivi "cài ứng dụng không rõ nguồn gốc" một lần
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            state = UpdateState.NeedPermission(info, apk)
            return
        }
        state = UpdateState.Installing(info, apk)
        try {
            Updater.launchInstaller(ctx, apk)
        } catch (e: Exception) {
            state = UpdateState.Failed("Không mở được trình cài đặt: ${e.message}")
        }
    }

    fun openPermissionSettings() {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT < 26) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            state = UpdateState.Failed(
                "Box không mở được màn hình cấp quyền. Vào Cài đặt > Bảo mật (hoặc Ứng dụng) > " +
                    "Cài ứng dụng không rõ nguồn gốc, bật cho MyTivi rồi thử lại.",
            )
        }
    }

    fun dismiss() {
        showDialog = false
        val s = state
        if (s is UpdateState.Downloading) {
            job?.cancel()
        }
        if (s is UpdateState.Failed) state = UpdateState.Idle
    }
}
