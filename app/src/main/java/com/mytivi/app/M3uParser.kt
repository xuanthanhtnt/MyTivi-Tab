package com.mytivi.app

/** Đọc playlist M3U/M3U8 dạng IPTV: #EXTINF:-1 tvg-id="" tvg-logo="" group-title="",Tên kênh */
object M3uParser {
    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(text: String): ParsedPlaylist {
        val channels = ArrayList<Channel>()
        var epgUrl: String? = null
        var attrs: Map<String, String> = emptyMap()
        var name = ""
        var extGroup: String? = null
        var pending = false

        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('\uFEFF')
            when {
                line.startsWith("#EXTM3U") -> {
                    val a = readAttrs(line)
                    epgUrl = (a["url-tvg"] ?: a["x-tvg-url"])?.substringBefore(',')?.trim()
                }
                line.startsWith("#EXTINF") -> {
                    attrs = readAttrs(line)
                    name = nameAfterComma(line)
                    pending = true
                }
                line.startsWith("#EXTGRP:") -> extGroup = line.substringAfter(':').trim()
                line.isEmpty() || line.startsWith("#") -> Unit
                pending -> {
                    val finalName = name.ifBlank { attrs["tvg-name"] ?: "Kênh ${channels.size + 1}" }
                    val group = (attrs["group-title"] ?: extGroup).takeUnless { it.isNullOrBlank() } ?: "Khác"
                    channels += Channel(
                        id = attrs["tvg-id"].orEmpty(),
                        name = finalName,
                        url = line,
                        logo = attrs["tvg-logo"].orEmpty(),
                        group = group,
                    )
                    pending = false
                    extGroup = null
                }
            }
        }
        return ParsedPlaylist(channels, epgUrl)
    }

    private fun readAttrs(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    /** Tên kênh nằm sau dấu phẩy đầu tiên nằm NGOÀI cặp ngoặc kép. */
    private fun nameAfterComma(line: String): String {
        var inQuote = false
        for ((i, c) in line.withIndex()) {
            if (c == '"') inQuote = !inQuote
            else if (c == ',' && !inQuote) return line.substring(i + 1).trim()
        }
        return ""
    }
}
