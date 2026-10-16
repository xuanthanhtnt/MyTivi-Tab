package com.mytivi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Bg,
                    surface = Bg,
                    onBackground = Color.White,
                    onSurface = Color.White,
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

/** Danh sách đang xem + vị trí kênh, để bấm ▲▼ đổi kênh trong đúng nhóm đang chọn. */
class PlaySession(val list: List<Channel>, val index: Int)

@Composable
fun App(vm: MainViewModel = viewModel(), updates: UpdateViewModel = viewModel()) {
    var session by remember { mutableStateOf<PlaySession?>(null) }
    var setup by remember { mutableStateOf(vm.playlistUrl.isBlank()) }
    var autoStarted by remember { mutableStateOf(false) }

    // Mở app là vào thẳng kênh xem gần nhất (như TiviMate)
    LaunchedEffect(vm.channels) {
        if (!autoStarted && vm.channels.isNotEmpty()) {
            autoStarted = true
            val i = vm.channels.indexOfFirst { it.url == vm.lastChannelUrl }
            if (i >= 0 && !setup) session = PlaySession(vm.channels, i)
        }
    }

    val s = session
    when {
        setup -> SetupScreen(
            initialUrl = vm.playlistUrl,
            initialEpg = vm.epgUrl,
            canCancel = vm.playlistUrl.isNotBlank(),
            version = updates.currentVersion,
            onSave = { url, epgUrl ->
                if (url != vm.playlistUrl || epgUrl != vm.epgUrl) vm.savePlaylist(url, epgUrl)
                setup = false
            },
            onCancel = { setup = false },
            onCheckUpdate = { updates.onButton() },
        )
        s != null -> PlayerScreen(
            list = s.list,
            startIndex = s.index,
            favorites = vm.favorites,
            epg = vm.epg,
            onToggleFavorite = vm::toggleFavorite,
            onPlayed = { vm.lastChannelUrl = it.url },
            onClose = { session = null },
        )
        else -> BrowseScreen(
            vm = vm,
            updates = updates,
            onPlay = { list, i -> session = PlaySession(list, i) },
            onSettings = { setup = true },
        )
    }

    // Hộp thoại cập nhật (mở từ Cài đặt hoặc ngăn nhóm kênh)
    UpdateDialog(updates)
}
