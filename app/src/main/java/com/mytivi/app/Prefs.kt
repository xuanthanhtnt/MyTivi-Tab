package com.mytivi.app

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mytivi", Context.MODE_PRIVATE)

    var playlistUrl: String
        get() = sp.getString("playlist_url", "") ?: ""
        set(v) { sp.edit().putString("playlist_url", v).apply() }

    /** Link EPG người dùng tự nhập (ưu tiên hơn link EPG ghi sẵn trong playlist). */
    var epgUrl: String
        get() = sp.getString("epg_url", "") ?: ""
        set(v) { sp.edit().putString("epg_url", v).apply() }

    /** Link EPG đọc từ dòng #EXTM3U url-tvg="..." của playlist. */
    var autoEpgUrl: String
        get() = sp.getString("epg_auto", "") ?: ""
        set(v) { sp.edit().putString("epg_auto", v).apply() }

    /** Link EPG của file cache hiện có (để biết khi nào phải tải lại). */
    var epgSource: String
        get() = sp.getString("epg_source", "") ?: ""
        set(v) { sp.edit().putString("epg_source", v).apply() }

    var playlistTime: Long
        get() = sp.getLong("playlist_time", 0L)
        set(v) { sp.edit().putLong("playlist_time", v).apply() }

    var epgTime: Long
        get() = sp.getLong("epg_time", 0L)
        set(v) { sp.edit().putLong("epg_time", v).apply() }

    var lastChannelUrl: String
        get() = sp.getString("last_channel", "") ?: ""
        set(v) { sp.edit().putString("last_channel", v).apply() }

    var favorites: Set<String>
        get() = HashSet(sp.getStringSet("favorites", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("favorites", HashSet(v)).apply() }
}
