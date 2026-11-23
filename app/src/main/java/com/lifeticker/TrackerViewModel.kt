package com.lifeticker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifeticker.data.AppData
import com.lifeticker.data.Entry
import com.lifeticker.data.Repository
import com.lifeticker.data.Stock
import com.lifeticker.data.StockList
import com.lifeticker.data.clampValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

class TrackerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(File(app.filesDir, "lifeticker.json"))
    private val _data = MutableStateFlow(repo.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    init {
        // Persist every change, in order, off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            _data.drop(1).collect { repo.save(it) }
        }
    }

    private fun newId() = UUID.randomUUID().toString()

    // ---- Stocks -------------------------------------------------------------------------

    fun addStock(ticker: String, name: String, listIds: Set<String>): String {
        val id = newId()
        _data.update { d ->
            d.copy(
                stocks = d.stocks + Stock(id, ticker, name, System.currentTimeMillis()),
                lists = d.lists.map { l -> if (l.id in listIds) l.copy(stockIds = l.stockIds + id) else l },
            )
        }
        return id
    }

    fun updateStock(id: String, ticker: String, name: String, listIds: Set<String>) {
        _data.update { d ->
            d.copy(
                stocks = d.stocks.map { if (it.id == id) it.copy(ticker = ticker, name = name) else it },
                lists = d.lists.map { l ->
                    val has = id in l.stockIds
                    val want = l.id in listIds
                    when {
                        want && !has -> l.copy(stockIds = l.stockIds + id)
                        !want && has -> l.copy(stockIds = l.stockIds - id)
                        else -> l
                    }
                },
            )
        }
    }

    fun deleteStock(id: String) {
        _data.update { d ->
            d.copy(
                stocks = d.stocks.filterNot { it.id == id },
                lists = d.lists.map { it.copy(stockIds = it.stockIds - id) },
                entries = d.entries.filterNot { it.stockId == id },
            )
        }
    }

    // ---- Lists --------------------------------------------------------------------------

    fun addList(name: String): String {
        val id = newId()
        _data.update { it.copy(lists = it.lists + StockList(id, name, emptyList())) }
        return id
    }

    fun renameList(id: String, name: String) {
        _data.update { d -> d.copy(lists = d.lists.map { if (it.id == id) it.copy(name = name) else it }) }
    }

    fun deleteList(id: String) {
        _data.update { d -> d.copy(lists = d.lists.filterNot { it.id == id }) }
    }

    fun setListMembers(listId: String, stockIds: Set<String>) {
        _data.update { d ->
            d.copy(lists = d.lists.map { l ->
                if (l.id != listId) l
                else {
                    // Keep the existing order, then append newly added stocks.
                    val kept = l.stockIds.filter { it in stockIds }
                    l.copy(stockIds = kept + stockIds.filterNot { it in kept })
                }
            })
        }
    }

    // ---- Entries ------------------------------------------------------------------------

    fun addEntry(stockId: String, timestamp: Long, value: Float, note: String) {
        val e = Entry(newId(), stockId, timestamp, clampValue(value), note.trim())
        _data.update { it.copy(entries = it.entries + e) }
    }

    fun updateEntry(entry: Entry) {
        _data.update { d -> d.copy(entries = d.entries.map { if (it.id == entry.id) entry else it }) }
    }

    fun deleteEntry(id: String) {
        _data.update { d -> d.copy(entries = d.entries.filterNot { it.id == id }) }
    }

    // ---- Sample data --------------------------------------------------------------------

    /** Creates a few example stocks with ~200 days of made-up history so the charts have something to show. */
    fun loadSampleData() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val rnd = Random(7)
        fun walk(stockId: String, startValue: Float, drift: Float): List<Entry> {
            var v = startValue
            return (200 downTo 0).map { daysAgo ->
                v = clampValue(v + drift + (rnd.nextFloat() - 0.5f) * 3f)
                val ts = today.minusDays(daysAgo.toLong()).atTime(20, 30).atZone(zone).toInstant().toEpochMilli()
                Entry(newId(), stockId, ts, Math.round(v).toFloat(), "")
            }
        }
        val nina = Stock(newId(), "NINA", "Nina", System.currentTimeMillis())
        val work = Stock(newId(), "WORK", "My job", System.currentTimeMillis())
        val gym = Stock(newId(), "GYM", "Fitness", System.currentTimeMillis())
        val romance = StockList(newId(), "Romance", listOf(nina.id))
        val career = StockList(newId(), "Career", listOf(work.id))
        val health = StockList(newId(), "Health", listOf(gym.id))
        val entries = walk(nina.id, 3f, 0.03f) + walk(work.id, 0f, -0.01f) + walk(gym.id, -4f, 0.05f)
        _data.update { d ->
            d.copy(
                stocks = d.stocks + listOf(nina, work, gym),
                lists = d.lists + listOf(romance, career, health),
                entries = d.entries + entries,
            )
        }
    }
}
