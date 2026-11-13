package com.mytivi.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mytivi", Context.MODE_PRIVATE)

    // ---- Nhiều nguồn (bản mới) ----

    /** Đã có danh sách nguồn kiểu mới chưa (chưa có = bản cũ, cần chuyển link cũ sang). */
    val hasSourceLists: Boolean get() = sp.contains("playlists")

    /** Các danh sách kênh M3U, theo thứ tự người dùng xếp. */
    var playlists: List<Source>
        get() = decodeSources(sp.getString("playlists", null))
        set(v) { sp.edit().putString("playlists", encodeSources(v)).apply() }

    /** Các link EPG người dùng tự thêm (link ở trên được ưu tiên khi trùng kênh). */
    var epgSources: List<Source>
        get() = decodeSources(sp.getString("epg_sources", null))
        set(v) { sp.edit().putString("epg_sources", encodeSources(v)).apply() }

    /** Tự dùng link EPG ghi sẵn trong playlist (#EXTM3U url-tvg="..."). */
    var useAutoEpg: Boolean
        get() = sp.getBoolean("use_auto_epg", true)
        set(v) { sp.edit().putBoolean("use_auto_epg", v).apply() }

    /** Các link EPG đọc được từ url-tvg của những playlist đang bật. */
    var autoEpgUrls: List<String>
        get() = (sp.getString("epg_auto_list", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) { sp.edit().putString("epg_auto_list", v.joinToString("\n")).apply() }

    // ---- Bản cũ (1 link): chỉ còn dùng để chuyển sang danh sách nguồn ----
    val legacyPlaylistUrl: String get() = sp.getString("playlist_url", "") ?: ""
    val legacyEpgUrl: String get() = sp.getString("epg_url", "") ?: ""
    /** Link EPG của file cache cũ "epg.cache". */
    val legacyEpgCacheUrl: String get() = sp.getString("epg_source", "") ?: ""

    var lastChannelUrl: String
        get() = sp.getString("last_channel", "") ?: ""
        set(v) { sp.edit().putString("last_channel", v).apply() }

    var favorites: Set<String>
        get() = HashSet(sp.getStringSet("favorites", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("favorites", HashSet(v)).apply() }

    /** Bản tablet: mở app là vào thẳng toàn màn hình kênh xem gần nhất (mặc định tắt). */
    var autoFullscreen: Boolean
        get() = sp.getBoolean("auto_fullscreen", false)
        set(v) { sp.edit().putBoolean("auto_fullscreen", v).apply() }

    /** Kiểu hiển thị hình khi xem toàn màn hình: 0 = vừa khung, 1 = lấp đầy, 2 = kéo giãn. */
    var videoFit: Int
        get() = sp.getInt("video_fit", 0)
        set(v) { sp.edit().putInt("video_fit", v).apply() }

    /** Đã xem hướng dẫn cử chỉ ở màn hình toàn màn hình chưa (chỉ hiện lần đầu). */
    var gestureTipsShown: Boolean
        get() = sp.getBoolean("gesture_tips_shown", false)
        set(v) { sp.edit().putBoolean("gesture_tips_shown", v).apply() }

    /** Tắt tiếng ở khung xem trước (toàn màn hình luôn có tiếng). */
    var previewMuted: Boolean
        get() = sp.getBoolean("preview_muted", false)
        set(v) { sp.edit().putBoolean("preview_muted", v).apply() }

    private fun encodeSources(list: List<Source>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.url)
                    .put("enabled", it.enabled),
            )
        }
        return arr.toString()
    }

    private fun decodeSources(json: String?): List<Source> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Source(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    url = o.optString("url"),
                    enabled = o.optBoolean("enabled", true),
                )
            }.filter { it.id.isNotBlank() && it.url.isNotBlank() }
        }.getOrDefault(emptyList())
    }
}
