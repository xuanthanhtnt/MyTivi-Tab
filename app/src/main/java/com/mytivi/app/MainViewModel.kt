package com.mytivi.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        // Chỉnh các mốc này nếu muốn cập nhật thưa hoặc dày hơn
        private const val PLAYLIST_TTL_MS = 3 * 3_600_000L   // tải lại playlist sau 3 giờ
        private const val EPG_TTL_MS = 6 * 3_600_000L        // tải lại EPG sau 6 giờ
        private const val CHECK_EVERY_MS = 15 * 60_000L      // cứ 15 phút kiểm tra một lần
    }

    private val prefs = Prefs(app)
    private val cacheFile = File(app.filesDir, "playlist.m3u")
    private val epgFile = File(app.filesDir, "epg.cache")
    private var refreshJob: Job? = null
    private var epgJob: Job? = null
    private var generation = 0

    var channels by mutableStateOf<List<Channel>>(emptyList())
        private set
    var epg by mutableStateOf<Map<String, List<Programme>>>(emptyMap())
        private set
    var favorites by mutableStateOf(prefs.favorites)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastUpdated by mutableLongStateOf(prefs.playlistTime)
        private set

    val playlistUrl: String get() = prefs.playlistUrl
    val epgUrl: String get() = prefs.epgUrl
    var lastChannelUrl: String
        get() = prefs.lastChannelUrl
        set(value) { prefs.lastChannelUrl = value }
    var autoFullscreen: Boolean
        get() = prefs.autoFullscreen
        set(value) { prefs.autoFullscreen = value }
    var videoFit: Int
        get() = prefs.videoFit
        set(value) { prefs.videoFit = value }
    var gestureTipsShown: Boolean
        get() = prefs.gestureTipsShown
        set(value) { prefs.gestureTipsShown = value }
    var previewMuted: Boolean
        get() = prefs.previewMuted
        set(value) { prefs.previewMuted = value }

    init {
        viewModelScope.launch {
            if (prefs.playlistUrl.isNotBlank()) {
                // 1) Hiện ngay dữ liệu đã lưu (kênh + EPG), 2) rồi cập nhật từ mạng
                if (cacheFile.exists()) {
                    runCatching {
                        channels = withContext(Dispatchers.IO) {
                            parseText(cacheFile.readText(), prefs.playlistUrl).channels
                        }
                    }
                    loadEpg(cacheOnly = true)
                }
                startRefresh(forceEpg = false)
            }
            // Vòng lặp tự cập nhật: box để mở nhiều ngày vẫn luôn có dữ liệu mới
            while (true) {
                delay(CHECK_EVERY_MS)
                if (prefs.playlistUrl.isBlank()) continue
                if (System.currentTimeMillis() - prefs.playlistTime > PLAYLIST_TTL_MS) {
                    startRefresh(forceEpg = false)
                } else if (epgStale()) {
                    loadEpg()
                }
            }
        }
    }

    fun savePlaylist(url: String, epgUrl: String) {
        prefs.playlistUrl = url
        prefs.epgUrl = epgUrl
        channels = emptyList()
        epg = emptyMap()
        startRefresh(forceEpg = true)
    }

    /**
     * Nút "Làm mới": tải lại playlist và EPG.
     * Nếu đang tải dở thì bỏ qua (bấm OK 2 lần liên tiếp không tạo 2 lượt tải chạy song song).
     */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        startRefresh(forceEpg = true)
    }

    /** forceEpg = true: tải lại cả EPG; tự động thì chỉ tải EPG khi đã cũ. */
    private fun startRefresh(forceEpg: Boolean) {
        val url = prefs.playlistUrl
        if (url.isBlank()) return
        val gen = ++generation
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading = true
            error = null
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val text = Http.getString(url)
                    ensureActive()                 // lượt này đã bị hủy thì không ghi đè cache
                    writeAtomic(cacheFile, text)
                    parseText(text, url)
                }
                channels = parsed.channels
                prefs.autoEpgUrl = parsed.epgUrl.orEmpty()
                prefs.playlistTime = System.currentTimeMillis()
                lastUpdated = prefs.playlistTime
                if (parsed.channels.isEmpty()) error = "Playlist không có kênh nào."
                if (forceEpg || epgStale()) loadEpg(force = forceEpg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Không tải được playlist: ${e.message}"
            } finally {
                if (gen == generation) loading = false
            }
        }
    }

    private fun currentEpgUrl(): String = prefs.epgUrl.ifBlank { prefs.autoEpgUrl }

    private fun epgStale(): Boolean =
        currentEpgUrl().isNotBlank() && (
            !epgFile.exists() ||
                prefs.epgSource != currentEpgUrl() ||
                System.currentTimeMillis() - prefs.epgTime > EPG_TTL_MS
            )

    /**
     * EPG được lưu thành file cache nên mở app lần sau có lịch ngay, không phải tải lại.
     * cacheOnly = true: chỉ đọc file đã lưu (không dùng mạng).
     */
    private fun loadEpg(force: Boolean = false, cacheOnly: Boolean = false) {
        val url = currentEpgUrl()
        if (url.isBlank()) return
        if (cacheOnly && !epgFile.exists()) return
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            try {
                epg = withContext(Dispatchers.IO) {
                    if (!cacheOnly && (force || epgStale())) {
                        try {
                            // Mỗi lượt tải dùng file tạm riêng, xong mới thay file chính
                            val tmp = File.createTempFile("epg", ".part", epgFile.parentFile)
                            try {
                                Http.download(url, tmp)
                                ensureActive()
                                if (!tmp.renameTo(epgFile)) {
                                    tmp.copyTo(epgFile, overwrite = true)
                                }
                            } finally {
                                tmp.delete()
                            }
                            prefs.epgSource = url
                            prefs.epgTime = System.currentTimeMillis()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Tải lỗi thì dùng tạm file cũ (nếu có), 15 phút sau sẽ thử lại
                            if (!epgFile.exists()) throw e
                        }
                    }
                    Http.openFile(epgFile).use { EpgParser.parse(it, System.currentTimeMillis()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // EPG lỗi thì bỏ qua, vẫn xem kênh bình thường
            }
        }
    }

    fun toggleFavorite(ch: Channel) {
        val updated = if (ch.url in favorites) favorites - ch.url else favorites + ch.url
        favorites = updated
        prefs.favorites = updated
    }

    /** Ghi file qua file tạm rồi đổi tên, để file cache không bao giờ bị ghi dở hoặc ghi chồng. */
    private fun writeAtomic(file: File, text: String) {
        val tmp = File.createTempFile("playlist", ".part", file.parentFile)
        try {
            tmp.writeText(text)
            if (!tmp.renameTo(file)) tmp.copyTo(file, overwrite = true)
        } finally {
            tmp.delete()
        }
    }

    /**
     * Nếu link là một luồng HLS đơn (không phải danh sách kênh) thì coi như 1 kênh.
     * Kênh trùng hoàn toàn (cùng nhóm + cùng link) chỉ giữ 1.
     */
    private fun parseText(text: String, url: String): ParsedPlaylist {
        val parsed = M3uParser.parse(text)
        if (parsed.channels.isEmpty() && text.contains("#EXT-X-")) {
            return ParsedPlaylist(listOf(Channel("", "Luồng trực tiếp", url, "", "Khác")), null)
        }
        return parsed.copy(channels = parsed.channels.distinctBy { it.group to it.url })
    }
}
