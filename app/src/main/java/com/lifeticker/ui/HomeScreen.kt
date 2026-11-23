@file:OptIn(ExperimentalMaterial3Api::class)

package com.lifeticker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeticker.data.AppData
import com.lifeticker.data.Range
import com.lifeticker.data.Stock
import com.lifeticker.data.buildSeries
import com.lifeticker.data.formatChange
import com.lifeticker.data.formatValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private sealed interface HomeItem
private data class Header(val key: String, val title: String, val count: Int) : HomeItem
private data class StockItem(val key: String, val stock: Stock) : HomeItem

@Composable
fun HomeScreen(
    data: AppData,
    now: Long,
    onOpenStock: (String) -> Unit,
    onOpenLists: () -> Unit,
    onAddStock: (ticker: String, name: String, listIds: Set<String>) -> Unit,
    onCreateList: (String) -> String,
    onLoadSample: () -> Unit,
) {
    var showNew by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Palette.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Life Tracker", fontWeight = FontWeight.Bold)
                        Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                            style = MaterialTheme.typography.labelMedium, color = Palette.TextDim,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenLists) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Manage lists")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Bg),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New stock") },
                containerColor = Palette.Up,
                contentColor = Color.Black,
            )
        },
    ) { pad ->
        if (data.stocks.isEmpty()) {
            EmptyHome(
                modifier = Modifier.padding(pad),
                onNew = { showNew = true },
                onLoadSample = onLoadSample,
            )
        } else {
            val homeItems = remember(data) { buildHomeItems(data) }
            LazyColumn(
                contentPadding = PaddingValues(
                    top = pad.calculateTopPadding(),
                    bottom = pad.calculateBottomPadding() + 96.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "index") { LifeIndexHeader(data, now) }
                items(homeItems, key = { if (it is Header) it.key else (it as StockItem).key }) { item ->
                    when (item) {
                        is Header -> SectionHeader(item.title, item.count)
                        is StockItem -> StockRow(item.stock, data, now) { onOpenStock(item.stock.id) }
                    }
                }
            }
        }
    }

    if (showNew) {
        StockDialog(
            initial = null,
            lists = data.lists,
            initialListIds = emptySet(),
            existingTickers = data.stocks.map { it.ticker }.toSet(),
            onDismiss = { showNew = false },
            onSave = { t, n, l -> onAddStock(t, n, l); showNew = false },
            onCreateList = onCreateList,
        )
    }
}

private fun buildHomeItems(data: AppData): List<HomeItem> {
    val byId = data.stocks.associateBy { it.id }
    val out = ArrayList<HomeItem>()
    data.lists.forEach { l ->
        val members = l.stockIds.mapNotNull { byId[it] }
        out.add(Header("h_${l.id}", l.name, members.size))
        members.forEach { out.add(StockItem("${l.id}_${it.id}", it)) }
    }
    val listed = data.lists.flatMap { it.stockIds }.toSet()
    val unlisted = data.stocks.filterNot { it.id in listed }
    if (unlisted.isNotEmpty()) {
        out.add(Header("h_unlisted", if (data.lists.isEmpty()) "Stocks" else "Not in a list", unlisted.size))
        unlisted.forEach { out.add(StockItem("u_${it.id}", it)) }
    }
    return out
}

/** Average of every stock's latest value — your overall "life index". */
@Composable
private fun LifeIndexHeader(data: AppData, now: Long) {
    val quotes = data.stocks.mapNotNull { s ->
        val e = data.entriesFor(s.id)
        val series = buildSeries(e, Range.D1, now) ?: return@mapNotNull null
        e.last().value to series.change
    }
    if (quotes.isEmpty()) return
    val avg = quotes.map { it.first }.average().toFloat()
    val avgChange = quotes.map { it.second }.average().toFloat()
    val color = trendColor(avgChange)
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Life Index", color = Palette.TextDim, style = MaterialTheme.typography.labelLarge)
        Text(formatValue(avg), fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text("${formatChange(avgChange)} today", color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Column {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("$count", color = Palette.TextDim)
        }
        HorizontalDivider(color = Palette.Divider)
    }
}

@Composable
private fun StockRow(stock: Stock, data: AppData, now: Long, onClick: () -> Unit) {
    val entries = remember(data.entries, stock.id) { data.entriesFor(stock.id) }
    val spark = remember(entries, now) { buildSeries(entries, Range.M1, now) }
    val day = remember(entries, now) { buildSeries(entries, Range.D1, now) }
    val latest = entries.lastOrNull()?.value
    val dayColor = trendColor(day?.change)

    Column(Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stock.ticker, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (stock.name.isNotBlank()) {
                    Text(
                        stock.name, color = Palette.TextDim, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Sparkline(spark, trendColor(spark?.change), Modifier.width(72.dp).height(32.dp))
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier
                    .width(76.dp)
                    .background(if (latest == null) Palette.SurfaceHigh else dayColor, RoundedCornerShape(6.dp))
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (latest == null) "—" else formatValue(latest),
                    color = if (latest == null) Palette.TextDim else Color.Black,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(start = 20.dp))
    }
}

@Composable
private fun EmptyHome(modifier: Modifier, onNew: () -> Unit, onLoadSample: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Track your life like a stock", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Create a stock for anything you care about — like NINA in your Romance list — " +
                "then log how it's going every day from −10 to +10. Your chart builds itself.",
            color = Palette.TextDim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onNew,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Up, contentColor = Color.Black),
        ) { Text("Create your first stock") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onLoadSample) { Text("Try it with sample data") }
    }
}
