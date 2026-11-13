package com.mytivi.app

data class Channel(
    val id: String,     // tvg-id, dùng để ghép với EPG (có thể rỗng)
    val name: String,
    val url: String,
    val logo: String,
    val group: String,
    val src: String = "",   // mã danh sách kênh (Source.id) chứa kênh này
)

data class ParsedPlaylist(val channels: List<Channel>, val epgUrl: String?)

data class Programme(val start: Long, val stop: Long, val title: String)

/** Một nguồn người dùng thêm: danh sách kênh (M3U) hoặc lịch phát sóng (EPG). */
data class Source(
    val id: String,             // mã ngẫu nhiên, dùng đặt tên file lưu tạm và phân biệt kênh
    val name: String,
    val url: String,
    val enabled: Boolean = true,
)

/**
 * Một dòng trong ngăn nhóm kênh.
 * key: "fav" | "all" | "src:<id>" (mọi kênh của 1 danh sách) | "grp:<id>\u0000<nhóm>" | "h:<id>" (tiêu đề mục).
 */
data class GroupEntry(
    val key: String,
    val label: String,          // chữ hiện trong danh sách
    val title: String,          // chữ hiện ở đầu bảng lịch / thanh trên cùng
    val count: Int,
    val header: Boolean = false,
    val src: String? = null,
    val group: String? = null,
)

/**
 * Dựng danh sách nhóm: Yêu thích, Tất cả, rồi từng danh sách kênh đang bật.
 * Chỉ khi có từ 2 danh sách trở lên mới chia mục (tiêu đề = tên danh sách + dòng "Tất cả kênh" của danh sách đó).
 */
fun buildGroupEntries(channels: List<Channel>, favorites: Set<String>, sources: List<Source>): List<GroupEntry> {
    val out = mutableListOf(
        GroupEntry("fav", "★ Yêu thích", "★ Yêu thích", channels.count { it.url in favorites }),
        GroupEntry("all", "Tất cả", "Tất cả", channels.size),
    )
    val bySrc = channels.groupBy { it.src }
    val known = sources.filter { bySrc.containsKey(it.id) }
    // Kênh không thuộc danh sách nào đã biết (hiếm) vẫn có trong "Tất cả"
    val multi = known.size > 1
    for (s in known) {
        val list = bySrc[s.id].orEmpty()
        if (multi) {
            out += GroupEntry("h:${s.id}", s.name, s.name, list.size, header = true, src = s.id)
            out += GroupEntry("src:${s.id}", "Tất cả kênh", s.name, list.size, src = s.id)
        }
        val counts = list.groupingBy { it.group }.eachCount()
        for (g in list.map { it.group }.distinct()) {
            out += GroupEntry("grp:${s.id}\u0000$g", g, g, counts[g] ?: 0, src = s.id, group = g)
        }
    }
    return out
}

/** Các kênh thuộc một dòng nhóm. */
fun channelsFor(entry: GroupEntry?, channels: List<Channel>, favorites: Set<String>): List<Channel> = when {
    entry == null || entry.key == "all" -> channels
    entry.key == "fav" -> channels.filter { it.url in favorites }
    entry.group == null -> channels.filter { it.src == entry.src }
    else -> channels.filter { it.src == entry.src && it.group == entry.group }
}
