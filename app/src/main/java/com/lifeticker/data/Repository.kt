package com.lifeticker.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Stores everything in one small JSON file in the app's private storage. */
class Repository(private val file: File) {

    fun load(): AppData {
        if (!file.exists()) return AppData()
        return try {
            fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            // Keep a copy of an unreadable file rather than silently overwriting it.
            file.copyTo(File(file.parentFile, file.name + ".corrupt"), overwrite = true)
            AppData()
        }
    }

    @Synchronized
    fun save(data: AppData) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(toJson(data).toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun toJson(d: AppData): JSONObject = JSONObject().apply {
        put("version", 1)
        put("stocks", JSONArray().apply {
            d.stocks.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id); put("ticker", s.ticker); put("name", s.name); put("createdAt", s.createdAt)
                })
            }
        })
        put("lists", JSONArray().apply {
            d.lists.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id); put("name", l.name)
                    put("stockIds", JSONArray().apply { l.stockIds.forEach { put(it) } })
                })
            }
        })
        put("entries", JSONArray().apply {
            d.entries.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("stockId", e.stockId); put("timestamp", e.timestamp)
                    put("value", e.value.toDouble()); put("note", e.note)
                })
            }
        })
    }

    private fun fromJson(o: JSONObject): AppData {
        val stocks = o.optJSONArray("stocks").objects().map {
            Stock(it.getString("id"), it.getString("ticker"), it.optString("name"), it.optLong("createdAt"))
        }
        val lists = o.optJSONArray("lists").objects().map { l ->
            val ids = l.optJSONArray("stockIds")
            StockList(
                l.getString("id"), l.getString("name"),
                if (ids == null) emptyList() else (0 until ids.length()).map { ids.getString(it) },
            )
        }
        val entries = o.optJSONArray("entries").objects().map {
            Entry(
                it.getString("id"), it.getString("stockId"), it.getLong("timestamp"),
                it.getDouble("value").toFloat(), it.optString("note"),
            )
        }
        return AppData(stocks, lists, entries)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}
