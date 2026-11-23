@file:OptIn(ExperimentalMaterial3Api::class)

package com.lifeticker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeticker.data.AppData
import com.lifeticker.data.Entry
import com.lifeticker.data.Range
import com.lifeticker.data.Stock
import com.lifeticker.data.buildSeries
import com.lifeticker.data.formatChange
import com.lifeticker.data.formatValue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyFmt = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
private val scrubFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · h:mm a")

private fun fmt(ts: Long, f: DateTimeFormatter): String =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(f)

@Composable
fun StockDetailScreen(
    stock: Stock,
    data: AppData,
    now: Long,
    onBack: () -> Unit,
    onEditStock: (ticker: String, name: String, listIds: Set<String>) -> Unit,
    onDeleteStock: () -> Unit,
    onCreateList: (String) -> String,
    onAddEntry: (timestamp: Long, value: Float, note: String) -> Unit,
    onUpdateEntry: (Entry) -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    var range by rememberSaveable { mutableStateOf(Range.M1) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }

    val entries = remember(data.entries, stock.id) { data.entriesFor(stock.id) }
    val series = remember(entries, range, now) { buildSeries(entries, range, now) }
    val lists = data.listsContaining(stock.id)
    val color = trendColor(series?.change)

    val scrubPoint = scrub?.let { series?.points?.getOrNull(it) }
    val shownValue = scrubPoint?.v ?: entries.lastOrNull()?.value
    val shownChange = when {
        series == null -> null
        scrubPoint != null -> scrubPoint.v - series.open
        else -> series.change
    }
    val shownColor = if (scrubPoint != null) trendColor(shownChange) else color

    Scaffold(
        containerColor = Palette.Bg,
        topBar = {
            TopAppBar(
                title = { Text(stock.ticker, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showEdit = true }) { Icon(Icons.Filled.Edit, "Edit stock") }
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, "Delete stock") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Bg),
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp),
        ) {
            // ---- Price header -------------------------------------------------------------
            item(key = "header") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (stock.name.isNotBlank()) {
                        Text(stock.name, color = Palette.TextDim, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        shownValue?.let { (if (it > 0) "+" else "") + formatValue(it) } ?: "No data yet",
                        fontSize = if (shownValue == null) 28.sp else 44.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (shownChange != null) {
                        Text(
                            "${formatChange(shownChange)} pts  " +
                                (if (scrubPoint != null) fmt(scrubPoint.t, scrubFmt) else range.description),
                            color = shownColor, fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text("Log your first entry to start the chart.", color = Palette.TextDim)
                    }
                    scrubPoint?.entry?.note?.takeIf { it.isNotBlank() }?.let {
                        Text("“$it”", color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // ---- Chart ----------------------------------------------------------------------
            item(key = "chart") {
                TickerChart(
                    series = series,
                    color = color,
                    scrubIndex = scrub,
                    onScrub = { scrub = it },
                    modifier = Modifier.fillMaxWidth().height(250.dp).padding(vertical = 12.dp),
                )
            }

            // ---- Range selector ------------------------------------------------------------
            item(key = "ranges") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Range.entries.forEach { r ->
                        val sel = r == range
                        Box(
                            Modifier
                                .background(if (sel) color else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable { range = r; scrub = null }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(
                                r.label,
                                color = if (sel) Color.Black else color,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            )
                        }
                    }
                }
                HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(top = 12.dp))
            }

            // ---- Log button ----------------------------------------------------------------
            item(key = "log") {
                Button(
                    onClick = { showLog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Up, contentColor = Color.Black),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(48.dp),
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Log today", fontWeight = FontWeight.Bold)
                }
            }

            // ---- Stats ---------------------------------------------------------------------
            if (series != null) {
                item(key = "stats") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text("Stats · ${range.label}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Stat("Open", formatValue(series.open), Modifier.weight(1f))
                            Stat("High", formatValue(series.high), Modifier.weight(1f))
                            Stat("Low", formatValue(series.low), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Stat("Average", formatValue(series.average), Modifier.weight(1f))
                            Stat("Entries", series.inRange.size.toString(), Modifier.weight(1f))
                            Stat("All-time", entries.size.toString(), Modifier.weight(1f))
                        }
                    }
                }
            }

            // ---- Lists ---------------------------------------------------------------------
            item(key = "lists") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Lists", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    if (lists.isEmpty()) {
                        Text(
                            "Not in any list. Tap edit to add it to one.",
                            color = Palette.TextDim,
                            modifier = Modifier.clickable { showEdit = true },
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            lists.forEach {
                                Text(
                                    it.name,
                                    modifier = Modifier
                                        .background(Palette.SurfaceHigh, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ---- History -------------------------------------------------------------------
            if (entries.isNotEmpty()) {
                item(key = "history_title") {
                    Text(
                        "History", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                val reversed = entries.asReversed()
                items(reversed, key = { it.id }) { e ->
                    val idx = entries.indexOf(e)
                    val prev = if (idx > 0) entries[idx - 1].value else null
                    HistoryRow(e, prev) { editing = e }
                }
            }
        }
    }

    if (showEdit) {
        StockDialog(
            initial = stock,
            lists = data.lists,
            initialListIds = lists.map { it.id }.toSet(),
            existingTickers = data.stocks.map { it.ticker }.toSet(),
            onDismiss = { showEdit = false },
            onSave = { t, n, l -> onEditStock(t, n, l); showEdit = false },
            onCreateList = onCreateList,
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "Delete ${stock.ticker}?",
            message = "This removes the stock and all ${entries.size} entries. It can't be undone.",
            confirmText = "Delete",
            onDismiss = { showDelete = false },
            onConfirm = { showDelete = false; onDeleteStock() },
        )
    }
    if (showLog) {
        EntryDialog(
            ticker = stock.ticker,
            initial = null,
            defaultValue = entries.lastOrNull()?.value ?: 0f,
            onDismiss = { showLog = false },
            onSave = { ts, v, n -> onAddEntry(ts, v, n); showLog = false },
            onDelete = null,
        )
    }
    editing?.let { e ->
        EntryDialog(
            ticker = stock.ticker,
            initial = e,
            defaultValue = e.value,
            onDismiss = { editing = null },
            onSave = { ts, v, n -> onUpdateEntry(e.copy(timestamp = ts, value = v, note = n.trim())); editing = null },
            onDelete = { onDeleteEntry(e.id); editing = null },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 6.dp)) {
        Text(label, color = Palette.TextDim, style = MaterialTheme.typography.labelMedium)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun HistoryRow(e: Entry, previous: Float?, onClick: () -> Unit) {
    val delta = previous?.let { e.value - it }
    val c = trendColor(delta ?: e.value)
    Column(Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(fmt(e.timestamp, historyFmt), style = MaterialTheme.typography.bodyMedium)
                if (e.note.isNotBlank()) {
                    Text(e.note, color = Palette.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((if (e.value > 0) "+" else "") + formatValue(e.value), fontWeight = FontWeight.Bold, color = c)
                if (delta != null) {
                    Text(formatChange(delta), color = c, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(start = 20.dp))
    }
}
