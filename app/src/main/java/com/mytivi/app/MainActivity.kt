package com.mytivi.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val pip by lazy { PipController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Vẽ tràn viền (dưới thanh trạng thái / thanh điều hướng); từng màn hình tự chừa khoảng an toàn
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MyTiviTheme {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    CompositionLocalProvider(LocalPip provides pip) {
                        App()
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pip.onUserLeaveHint()          // Android 8–11: bấm Home khi đang xem = thu nhỏ thành cửa sổ nổi
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip.inPip = isInPictureInPictureMode
    }
}

/** Danh sách đang xem + vị trí kênh, để vuốt / bấm đổi kênh trong đúng nhóm đang chọn. */
class PlaySession(val list: List<Channel>, val index: Int)

@Composable
fun App(vm: MainViewModel = viewModel(), updates: UpdateViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Một trình phát dùng chung cho xem trước và toàn màn hình
    val playback = remember { Playback(context, scope, previewMuted = vm.previewMuted) }
    DisposableEffect(playback) { onDispose { playback.release() } }
    PauseWhenBackground(playback.exo)

    // Nhóm đang chọn, ô tìm kiếm, vị trí cuộn bảng lịch: giữ nguyên khi vào/ra toàn màn hình
    val browse = remember { BrowseUiState() }
    var session by remember { mutableStateOf<PlaySession?>(null) }
    var setup by remember { mutableStateOf(vm.playlistUrl.isBlank()) }
    var about by remember { mutableStateOf(false) }
    var autoStarted by remember { mutableStateOf(false) }

    // Mở app: phát luôn kênh xem gần nhất ở khung xem trước
    // (hoặc vào thẳng toàn màn hình nếu bật trong Cài đặt – giống bản TV)
    LaunchedEffect(vm.channels) {
        if (!autoStarted && vm.channels.isNotEmpty()) {
            autoStarted = true
            val i = vm.channels.indexOfFirst { it.url == vm.lastChannelUrl }
            if (i >= 0 && !setup) {
                playback.play(vm.channels[i])
                if (vm.autoFullscreen) session = PlaySession(vm.channels, i)
            }
        }
    }
    // Nhớ kênh vừa phát để lần sau mở app
    LaunchedEffect(playback.channel?.url) {
        playback.channel?.let { vm.lastChannelUrl = it.url }
    }
    // Vào Cài đặt / Giới thiệu thì tạm dừng tiếng, quay ra thì phát tiếp
    val away = setup || about
    LaunchedEffect(away) {
        if (away) playback.pause() else playback.resume()
    }

    val s = session
    when {
        about -> AboutScreen(
            version = updates.currentVersion,
            updateAvailable = updates.updateAvailable,
            channelCount = vm.channels.size,
            lastUpdated = vm.lastUpdated,
            onCheckUpdate = { updates.onButton() },
            onBack = { about = false },
        )
        setup -> SetupScreen(
            initialUrl = vm.playlistUrl,
            initialEpg = vm.epgUrl,
            initialAutoFullscreen = vm.autoFullscreen,
            canCancel = vm.playlistUrl.isNotBlank(),
            version = updates.currentVersion,
            onSave = { url, epgUrl, autoFull ->
                vm.autoFullscreen = autoFull
                if (url != vm.playlistUrl || epgUrl != vm.epgUrl) {
                    playback.stop()
                    vm.savePlaylist(url, epgUrl)
                }
                setup = false
            },
            onCancel = { setup = false },
            onCheckUpdate = { updates.onButton() },
            onAbout = { about = true },
        )
        s != null -> key(s) {
            PlayerScreen(
                playback = playback,
                list = s.list,
                startIndex = s.index,
                favorites = vm.favorites,
                epg = vm.epg,
                initialFit = vm.videoFit,
                onFitChanged = { vm.videoFit = it },
                showTips = !vm.gestureTipsShown,
                onTipsSeen = { vm.gestureTipsShown = true },
                onToggleFavorite = vm::toggleFavorite,
                onClose = { session = null },
            )
        }
        else -> BrowseScreen(
            vm = vm,
            updates = updates,
            playback = playback,
            ui = browse,
            onFullscreen = { list, i -> session = PlaySession(list, i) },
            onSettings = { setup = true },
            onAbout = { about = true },
        )
    }

    // Hộp thoại cập nhật (mở từ Cài đặt hoặc ngăn nhóm kênh)
    UpdateDialog(updates)
}
