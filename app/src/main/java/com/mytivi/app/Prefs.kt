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
}
