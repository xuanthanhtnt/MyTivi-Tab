package com.mytivi.app

data class Channel(
    val id: String,     // tvg-id, dùng để ghép với EPG (có thể rỗng)
    val name: String,
    val url: String,
    val logo: String,
    val group: String,
)

data class ParsedPlaylist(val channels: List<Channel>, val epgUrl: String?)

data class Programme(val start: Long, val stop: Long, val title: String)
