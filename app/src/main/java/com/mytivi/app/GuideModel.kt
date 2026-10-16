package com.mytivi.app

const val MINUTE = 60_000L
const val HALF_HOUR = 30 * MINUTE
const val HOUR = 60 * MINUTE

/** Một ô trên bảng lịch. programme = null nghĩa là "Không có thông tin". */
data class Cell(val start: Long, val end: Long, val programme: Programme?)

fun floorTo(ms: Long, step: Long): Long = ms - ms % step

/**
 * Chia khoảng [from, to) của một kênh thành các ô liên tiếp:
 * chương trình có trong EPG thì thành một ô, khoảng trống thì chia thành các khối 1 giờ.
 */
fun buildCells(list: List<Programme>?, from: Long, to: Long): List<Cell> {
    val result = ArrayList<Cell>()
    var cursor = from

    fun fillGap(until: Long) {
        while (cursor < until) {
            val end = minOf((cursor / HOUR + 1) * HOUR, until)
            result += Cell(cursor, end, null)
            cursor = end
        }
    }

    list?.forEach { p ->
        if (p.stop <= cursor || p.start >= to) return@forEach
        if (p.start > cursor) fillGap(p.start)
        val end = minOf(p.stop, to)
        result += Cell(cursor, end, p)
        cursor = end
    }
    if (cursor < to) fillGap(to)
    return result
}
