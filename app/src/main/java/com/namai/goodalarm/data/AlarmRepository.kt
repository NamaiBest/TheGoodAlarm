package com.namai.goodalarm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

object AlarmRepository {
    private const val PREFS = "alarms"
    private const val KEY = "alarms_json"

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()
    private var loaded = false

    @Synchronized
    fun load(context: Context): List<Alarm> {
        if (!loaded) {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
            val arr = JSONArray(raw)
            _alarms.value = (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }.sorted()
            loaded = true
        }
        return _alarms.value
    }

    fun get(context: Context, id: Int): Alarm? = load(context).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(context: Context, alarm: Alarm) {
        val list = load(context).filterNot { it.id == alarm.id } + alarm
        save(context, list)
    }

    @Synchronized
    fun delete(context: Context, id: Int) = save(context, load(context).filterNot { it.id == id })

    fun newId(context: Context): Int = (load(context).maxOfOrNull { it.id } ?: 0) + 1

    private fun save(context: Context, list: List<Alarm>) {
        val sorted = list.sorted()
        _alarms.value = sorted
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONArray(sorted.map { it.toJson() }).toString()).apply()
    }

    private fun List<Alarm>.sorted() = sortedWith(compareBy({ it.hour }, { it.minute }))
}
