package com.mytivi.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Đọc lịch phát sóng XMLTV. Chỉ giữ các chương trình từ -2 giờ đến +24 giờ để tiết kiệm RAM. */
object EpgParser {
    fun parse(input: InputStream, now: Long): Map<String, List<Programme>> {
        val from = now - 2 * 3_600_000L
        val to = now + 24 * 3_600_000L
        val withZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val noZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun time(s: String?): Long {
            if (s == null || s.length < 14) return 0L
            return try {
                (if (s.length > 14) withZone.parse(s.trim()) else noZone.parse(s))?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        val map = HashMap<String, MutableList<Programme>>()
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        var channel = ""
        var start = 0L
        var stop = 0L
        var wanted = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "programme" -> {
                        channel = (parser.getAttributeValue(null, "channel") ?: "").lowercase()
                        start = time(parser.getAttributeValue(null, "start"))
                        stop = time(parser.getAttributeValue(null, "stop"))
                        wanted = channel.isNotEmpty() && stop > from && start < to
                    }
                    "title" -> if (wanted) {
                        val title = parser.nextText().trim()
                        map.getOrPut(channel) { ArrayList() }.add(Programme(start, stop, title))
                        wanted = false
                    }
                }
            }
            event = parser.next()
        }
        // Sắp theo giờ và bỏ bản ghi trùng (một số nguồn EPG lặp lại chương trình)
        return map.mapValues { (_, list) -> list.sortedBy { it.start }.distinctBy { it.start } }
    }
}

fun nowPlaying(epg: Map<String, List<Programme>>, ch: Channel, now: Long): Programme? =
    if (ch.id.isBlank()) null
    else epg[ch.id.lowercase()]?.firstOrNull { now >= it.start && now < it.stop }

fun nextPlaying(epg: Map<String, List<Programme>>, ch: Channel, now: Long): Programme? =
    if (ch.id.isBlank()) null
    else epg[ch.id.lowercase()]?.firstOrNull { it.start > now }
