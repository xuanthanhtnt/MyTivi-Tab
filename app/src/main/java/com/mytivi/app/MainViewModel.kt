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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Dữ liệu của app: NHIỀU danh sách kênh (M3U) và NHIỀU link lịch phát sóng (EPG).
 *  - Kênh của mọi danh sách đang bật được gộp lại (mỗi kênh nhớ nó thuộc danh sách nào).
 *  - Lịch phát sóng của mọi link EPG được ghép theo tvg-id; trùng kênh thì link ở trên được ưu tiên.
 *  - Mỗi nguồn có file lưu tạm riêng: mở app là có kênh + lịch ngay, rồi mới cập nhật từ mạng.
 *    Tải lỗi một nguồn thì vẫn dùng bản đã lưu của nguồn đó, các nguồn khác không bị ảnh hưởng.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        // Chỉnh các mốc này nếu muốn cập nhật thưa hoặc dày hơn
        private const val PLAYLIST_TTL_MS = 3 * 3_600_000L   // tải lại playlist sau 3 giờ
        private const val EPG_TTL_MS = 6 * 3_600_000L        // tải lại EPG sau 6 giờ
        private const val CHECK_EVERY_MS = 15 * 60_000L      // cứ 15 phút kiểm tra một lần

        fun newSourceId(): String = UUID.randomUUID().toString().replace("-", "").take(10)
    }

    private val prefs = Prefs(app)
    private val dir: File = app.filesDir
    private var refreshJob: Job? = null
    private var epgJob: Job? = null
    private var generation = 0

    var playlists by mutableStateOf<List<Source>>(emptyList())
        private set
    var epgSources by mutableStateOf<List<Source>>(emptyList())
        private set
    var useAutoEpg by mutableStateOf(prefs.useAutoEpg)
        private set

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
    var lastUpdated by mutableLongStateOf(0L)
        private set

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

    /** Có ít nhất 1 danh sách kênh (dù đang bật hay tắt). Chưa có thì mở màn hình Cài đặt. */
    val hasPlaylists: Boolean get() = playlists.isNotEmpty()

    init {
        migrateLegacy()
        playlists = prefs.playlists
        epgSources = prefs.epgSources
        viewModelScope.launch {
            if (activePlaylists().isNotEmpty()) {
                // 1) Hiện ngay dữ liệu đã lưu (kênh + EPG), 2) rồi cập nhật từ mạng
                showCached()
                startRefresh(forcePlaylists = true, forceEpg = false)
            }
            // Vòng lặp tự cập nhật: để máy mở nhiều ngày vẫn luôn có dữ liệu mới
            while (true) {
                delay(CHECK_EVERY_MS)
                if (activePlaylists().isEmpty()) continue
                if (activePlaylists().any { playlistStale(it) }) {
                    startRefresh(forcePlaylists = false, forceEpg = false)
                } else if (epgUrls().any { epgStale(it) }) {
                    loadEpg()
                }
            }
        }
    }

    /**
     * Lưu danh sách nguồn từ màn hình Cài đặt. Nguồn mới (chưa có bản lưu) được tải ngay,
     * nguồn cũ dùng bản đã lưu nếu còn mới.
     */
    fun saveSources(newPlaylists: List<Source>, newEpgs: List<Source>, autoEpg: Boolean) {
        prefs.playlists = newPlaylists
        prefs.epgSources = newEpgs
        prefs.useAutoEpg = autoEpg
        playlists = newPlaylists
        epgSources = newEpgs
        useAutoEpg = autoEpg
        cleanupPlaylistCache()
        if (activePlaylists().isEmpty()) {
            refreshJob?.cancel()
            epgJob?.cancel()
            loading = false
            channels = emptyList()
            epg = emptyMap()
            return
        }
        startRefresh(forcePlaylists = false, forceEpg = false, reloadEpg = true)
    }

    /**
     * Nút "Làm mới": tải lại mọi playlist và EPG.
     * Nếu đang tải dở thì bỏ qua (bấm 2 lần liên tiếp không tạo 2 lượt tải chạy song song).
     */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        startRefresh(forcePlaylists = true, forceEpg = true)
    }

    fun toggleFavorite(ch: Channel) {
        val updated = if (ch.url in favorites) favorites - ch.url else favorites + ch.url
        favorites = updated
        prefs.favorites = updated
    }

    // =====================================================================
    // Danh sách kênh
    // =====================================================================

    private fun activePlaylists(): List<Source> = playlists.filter { it.enabled && it.url.isNotBlank() }

    private fun playlistFile(src: Source) = File(dir, "playlist_${src.id}.m3u")

    private fun playlistStale(src: Source): Boolean {
        val f = playlistFile(src)
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > PLAYLIST_TTL_MS
    }

    private class PlaylistResult(val source: Source, val parsed: ParsedPlaylist?, val error: String?)

    /** Đọc kênh từ các file đã lưu (không dùng mạng). */
    private suspend fun showCached() {
        val sources = activePlaylists()
        val results = withContext(Dispatchers.IO) {
            sources.map { src ->
                val f = playlistFile(src)
                val parsed = if (f.exists()) runCatching { parseText(f.readText(), src) }.getOrNull() else null
                PlaylistResult(src, parsed, null)
            }
        }
        applyResults(results, showErrors = false)
        loadEpg(cacheOnly = true)
    }

    /**
     * forcePlaylists = true: tải lại mọi playlist từ mạng; false: chỉ tải playlist mới thêm / đã cũ.
     * forceEpg = true: tải lại mọi EPG; reloadEpg = true: ghép lại EPG (danh sách link có thể đã đổi).
     */
    private fun startRefresh(forcePlaylists: Boolean, forceEpg: Boolean, reloadEpg: Boolean = false) {
        val sources = activePlaylists()
        if (sources.isEmpty()) return
        val gen = ++generation
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading = true
            error = null
            try {
                val results = withContext(Dispatchers.IO) {
                    sources.map { src -> async { loadPlaylist(src, forcePlaylists) } }.awaitAll()
                }
                applyResults(results, showErrors = true)
                if (forceEpg || reloadEpg || epgUrls().any { epgStale(it) }) loadEpg(force = forceEpg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Không tải được danh sách kênh: ${e.message}"
            } finally {
                if (gen == generation) loading = false
            }
        }
    }

    private suspend fun loadPlaylist(src: Source, force: Boolean): PlaylistResult {
        val file = playlistFile(src)
        var text: String? = null
        var err: String? = null
        if (force || playlistStale(src)) {
            try {
                val t = Http.getString(src.url)
                yield()                          // lượt này đã bị hủy thì không ghi đè cache
                writeAtomic(file, t)
                text = t
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
            }
        }
        // Không tải mới (còn mới / lỗi mạng): dùng bản đã lưu
        if (text == null && file.exists()) text = runCatching { file.readText() }.getOrNull()
        if (text == null) return PlaylistResult(src, null, err ?: "chưa tải được")
        return PlaylistResult(src, parseText(text, src), err)
    }

    private fun applyResults(results: List<PlaylistResult>, showErrors: Boolean) {
        channels = results.flatMap { it.parsed?.channels.orEmpty() }
        prefs.autoEpgUrls = results.mapNotNull { it.parsed?.epgUrl?.takeIf { u -> u.isNotBlank() } }.distinct()
        lastUpdated = results.maxOfOrNull { playlistFile(it.source).lastModified() } ?: 0L
        if (!showErrors) return
        val failed = results.filter { it.error != null }
        error = when {
            failed.isNotEmpty() -> "Không tải được: " +
                failed.joinToString("; ") { "${it.source.name} (${it.error})" } +
                if (channels.isNotEmpty()) " – đang dùng bản đã lưu." else ""
            channels.isEmpty() -> "Danh sách kênh không có kênh nào."
            else -> null
        }
    }

    /**
     * Nếu link là một luồng HLS đơn (không phải danh sách kênh) thì coi như 1 kênh.
     * Kênh trùng hoàn toàn (cùng nhóm + cùng link) trong cùng danh sách chỉ giữ 1.
     */
    private fun parseText(text: String, src: Source): ParsedPlaylist {
        val parsed = M3uParser.parse(text)
        if (parsed.channels.isEmpty() && text.contains("#EXT-X-")) {
            val name = src.name.ifBlank { "Luồng trực tiếp" }
            return ParsedPlaylist(listOf(Channel("", name, src.url, "", "Khác", src.id)), null)
        }
        return parsed.copy(
            channels = parsed.channels
                .distinctBy { it.group to it.url }
                .map { it.copy(src = src.id) },
        )
    }

    private fun cleanupPlaylistCache() {
        val keep = playlists.map { playlistFile(it).name }.toSet()
        dir.listFiles { f -> f.name.startsWith("playlist_") && f.name.endsWith(".m3u") && f.name !in keep }
            ?.forEach { it.delete() }
    }

    // =====================================================================
    // Lịch phát sóng (EPG)
    // =====================================================================

    /** Link EPG theo thứ tự ưu tiên: link tự thêm (đang bật), rồi link ghi sẵn trong các playlist. */
    private fun epgUrls(): List<String> {
        val manual = epgSources.filter { it.enabled && it.url.isNotBlank() }.map { it.url }
        val auto = if (useAutoEpg) prefs.autoEpgUrls else emptyList()
        return (manual + auto).distinct()
    }

    private fun epgFile(url: String) = File(dir, "epg_${hash(url)}.xml")

    private fun epgStale(url: String): Boolean {
        val f = epgFile(url)
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > EPG_TTL_MS
    }

    /**
     * Tải (khi cần) rồi ghép lịch của mọi link EPG. Làm lần lượt từng link để TV box / tablet
     * ít RAM không phải giữ nhiều file EPG lớn cùng lúc.
     * cacheOnly = true: chỉ đọc file đã lưu (không dùng mạng).
     */
    private fun loadEpg(force: Boolean = false, cacheOnly: Boolean = false) {
        val urls = epgUrls()
        epgJob?.cancel()
        if (urls.isEmpty()) {
            epg = emptyMap()
            return
        }
        epgJob = viewModelScope.launch {
            try {
                epg = withContext(Dispatchers.IO) {
                    val merged = HashMap<String, List<Programme>>()
                    for (url in urls) {
                        val part = loadOneEpg(url, force, cacheOnly)
                        // Trùng kênh: giữ lịch của link đứng trước (ưu tiên cao hơn)
                        part.forEach { (id, list) -> if (id !in merged) merged[id] = list }
                    }
                    if (!cacheOnly) cleanupEpgCache(urls)
                    merged
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // EPG lỗi thì bỏ qua, vẫn xem kênh bình thường
            }
        }
    }

    private suspend fun loadOneEpg(url: String, force: Boolean, cacheOnly: Boolean): Map<String, List<Programme>> {
        val file = epgFile(url)
        if (!cacheOnly && (force || epgStale(url))) {
            // Mỗi lượt tải dùng file tạm riêng, xong mới thay file chính
            val tmp = File.createTempFile("epg", ".part", dir)
            try {
                Http.download(url, tmp)
                yield()
                if (!tmp.renameTo(file)) tmp.copyTo(file, overwrite = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Tải lỗi thì dùng tạm file cũ (nếu có), 15 phút sau sẽ thử lại
            } finally {
                tmp.delete()
            }
        }
        if (!file.exists()) return emptyMap()
        return runCatching {
            Http.openFile(file).use { EpgParser.parse(it, System.currentTimeMillis()) }
        }.getOrDefault(emptyMap())
    }

    private fun cleanupEpgCache(urls: List<String>) {
        val keep = urls.map { epgFile(it).name }.toSet()
        dir.listFiles { f -> f.name.startsWith("epg_") && f.name.endsWith(".xml") && f.name !in keep }
            ?.forEach { it.delete() }
    }

    // =====================================================================
    // Tiện ích
    // =====================================================================

    /**
     * Bản cũ chỉ có 1 link playlist + 1 link EPG: chuyển sang danh sách nguồn,
     * đổi tên luôn file lưu tạm cũ để lần mở đầu tiên sau khi cập nhật vẫn có kênh ngay.
     */
    private fun migrateLegacy() {
        if (prefs.hasSourceLists) return
        val oldUrl = prefs.legacyPlaylistUrl
        val list = if (oldUrl.isNotBlank()) listOf(Source(newSourceId(), "Danh sách 1", oldUrl)) else emptyList()
        val oldEpg = prefs.legacyEpgUrl
        val epgs = if (oldEpg.isNotBlank()) listOf(Source(newSourceId(), "EPG 1", oldEpg)) else emptyList()
        list.firstOrNull()?.let { src ->
            val old = File(dir, "playlist.m3u")
            if (old.exists()) old.renameTo(playlistFile(src))
        }
        val oldEpgFile = File(dir, "epg.cache")
        val cachedFor = prefs.legacyEpgCacheUrl
        if (oldEpgFile.exists() && cachedFor.isNotBlank()) oldEpgFile.renameTo(epgFile(cachedFor))
        prefs.epgSources = epgs
        prefs.playlists = list          // ghi sau cùng: đánh dấu đã chuyển xong
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

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
}
