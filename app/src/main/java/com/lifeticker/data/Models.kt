package com.lifeticker.data

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** A "stock" is anything in your life you want to track: a person, a habit, your job... */
data class Stock(
    val id: String,
    val ticker: String,
    val name: String,
    val createdAt: Long,
)

/** A watch-list such as "Romance", "Career" or "Health". A stock can sit in several lists. */
data class StockList(
    val id: String,
    val name: String,
    val stockIds: List<String>,
)

/** One data point: how things are going, from -10 (terrible) to +10 (amazing). */
data class Entry(
    val id: String,
    val stockId: String,
    val timestamp: Long,
    val value: Float,
    val note: String,
)

data class AppData(
    val stocks: List<Stock> = emptyList(),
    val lists: List<StockList> = emptyList(),
    val entries: List<Entry> = emptyList(),
) {
    fun entriesFor(stockId: String): List<Entry> =
        entries.filter { it.stockId == stockId }.sortedBy { it.timestamp }

    fun listsContaining(stockId: String): List<StockList> =
        lists.filter { stockId in it.stockIds }
}

const val MIN_VALUE = -10f
const val MAX_VALUE = 10f

enum class Range(val label: String, val description: String) {
    D1("1D", "Today"),
    D5("5D", "Past 5 days"),
    M1("1M", "Past month"),
    M6("6M", "Past 6 months"),
    Y1("1Y", "Past year"),
    Y5("5Y", "Past 5 years"),
    YTD("YTD", "Year to date");

    /** Start of the window, in epoch millis, for a window ending at [now]. */
    fun start(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val nowZ = Instant.ofEpochMilli(now).atZone(zone)
        val start = when (this) {
            D1 -> today.atStartOfDay(zone)
            D5 -> today.minusDays(4).atStartOfDay(zone)
            M1 -> nowZ.minusMonths(1)
            M6 -> nowZ.minusMonths(6)
            Y1 -> nowZ.minusYears(1)
            Y5 -> nowZ.minusYears(5)
            YTD -> today.withDayOfYear(1).atStartOfDay(zone)
        }
        return start.toInstant().toEpochMilli()
    }
}

data class ChartPoint(val t: Long, val v: Float, val entry: Entry?)

/**
 * Everything the chart and the header need for one stock in one time range.
 *
 * Like a real ticker, the range opens at the last value recorded *before* the range
 * (the "previous close") when one exists, so "1D" shows how today moved versus yesterday.
 */
data class Series(
    val points: List<ChartPoint>,
    val xStart: Long,
    val xEnd: Long,
    val inRange: List<Entry>,
) {
    val open: Float get() = points.first().v
    val last: Float get() = points.last().v
    val change: Float get() = last - open
    val high: Float get() = points.maxOf { it.v }
    val low: Float get() = points.minOf { it.v }
    val average: Float get() = if (inRange.isEmpty()) last else inRange.map { it.value }.average().toFloat()
}

fun buildSeries(sortedEntries: List<Entry>, range: Range, now: Long, zone: ZoneId = ZoneId.systemDefault()): Series? {
    if (sortedEntries.isEmpty()) return null
    val start = range.start(now, zone)
    val end = maxOf(now, sortedEntries.last().timestamp)
    val prior = sortedEntries.lastOrNull { it.timestamp < start }
    val inRange = sortedEntries.filter { it.timestamp >= start }

    val points = ArrayList<ChartPoint>(inRange.size + 2)
    if (prior != null) points.add(ChartPoint(start, prior.value, null))
    inRange.forEach { points.add(ChartPoint(it.timestamp, it.value, it)) }
    if (points.isEmpty()) return null

    // When the stock is younger than the range, stretch its history across the full width.
    val xStart = if (prior != null) start else points.first().t
    if (points.size == 1) {
        // A single value: draw it as a flat line so there is something to look at.
        points.add(ChartPoint(maxOf(end, points.first().t + 1), points.first().v, null))
    }
    val xEnd = maxOf(end, points.last().t, xStart + 1)
    return Series(points, xStart, xEnd, inRange)
}

/** Latest value and the "day change" versus the previous close, as shown on the home list. */
data class Quote(val value: Float, val change: Float)

fun quoteFor(sortedEntries: List<Entry>, range: Range, now: Long, zone: ZoneId = ZoneId.systemDefault()): Quote? {
    val s = buildSeries(sortedEntries, range, now, zone) ?: return null
    return Quote(sortedEntries.last().value, s.change)
}

fun formatValue(v: Float): String {
    val r = (v * 10).roundToInt() / 10f
    return if (r == r.roundToInt().toFloat()) r.roundToInt().toString() else String.format("%.1f", r)
}

fun formatChange(d: Float): String {
    val r = (d * 10).roundToInt() / 10f
    val mag = formatValue(abs(r))
    return when {
        r > 0f -> "+$mag"
        r < 0f -> "−$mag"
        else -> "0"
    }
}

fun clampValue(v: Float): Float = v.coerceIn(MIN_VALUE, MAX_VALUE)
