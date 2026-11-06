package uk.lauragilbert.todo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class Todo(val id: Long, val text: String, val color: Int)

data class Stats(val total: Int = 0, val day: String = "", val today: Int = 0) {
    fun forToday(date: String) = if (day == date) this else copy(day = date, today = 0)
}

class TodoViewModel(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "todos.json")
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Todo>> = _items.asStateFlow()

    private val statsFile = File(app.filesDir, "stats.json")
    private val _stats = MutableStateFlow(loadStats().forToday(today()))
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    fun refreshDay() = _stats.update { it.forToday(today()) }

    fun add(text: String) {
        val clean = text.trim().replaceFirstChar { it.uppercase() }
        if (clean.isEmpty()) return
        mutate { list ->
            val id = (list.maxOfOrNull { it.id } ?: 0L) + 1
            list + Todo(id, clean, pickColor(list))
        }
    }

    fun edit(todo: Todo, text: String) {
        val clean = text.trim().replaceFirstChar { it.uppercase() }
        if (clean.isEmpty() || clean == todo.text) return
        mutate { list -> list.map { if (it.id == todo.id) it.copy(text = clean) else it } }
    }

    fun remove(todo: Todo): Int {
        val index = _items.value.indexOf(todo)
        mutate { it - todo }
        changeStats(+1)
        return index
    }

    fun restore(todo: Todo, index: Int) {
        if (_items.value.none { it.id == todo.id }) changeStats(-1)
        mutate { list ->
            if (list.any { it.id == todo.id }) list
            else list.toMutableList().apply { add(index.coerceIn(0, size), todo) }
        }
    }

    private fun pickColor(list: List<Todo>): Int {
        val used = list.map { it.color }.toSet()
        val unused = Palette.indices.filter { it !in used }
        val recent = list.takeLast(Palette.size / 2).map { it.color }.toSet()
        val choices = unused.ifEmpty { Palette.indices.filter { it !in recent } }
        return choices.random()
    }

    private fun mutate(change: (List<Todo>) -> List<Todo>) {
        _items.update(change)
        val snapshot = _items.value
        viewModelScope.launch(Dispatchers.IO) { save(snapshot) }
    }

    private fun today() = LocalDate.now().toString()

    private fun changeStats(delta: Int) {
        _stats.update { st ->
            st.forToday(today()).let {
                it.copy(total = maxOf(0, it.total + delta), today = maxOf(0, it.today + delta))
            }
        }
        val snapshot = _stats.value
        viewModelScope.launch(Dispatchers.IO) { saveStats(snapshot) }
    }

    private fun loadStats(): Stats = runCatching {
        val o = JSONObject(statsFile.readText())
        Stats(o.optInt("total"), o.optString("day"), o.optInt("today"))
    }.getOrDefault(Stats())

    @Synchronized
    private fun saveStats(st: Stats) {
        val tmp = File(statsFile.parentFile, "stats.json.tmp")
        tmp.writeText(JSONObject().put("total", st.total).put("day", st.day).put("today", st.today).toString())
        tmp.renameTo(statsFile)
    }

    private fun load(): List<Todo> = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).fold(emptyList<Todo>()) { list, i ->
            val o = arr.getJSONObject(i)
            val color = o.optInt("color", -1).takeIf { it in Palette.indices } ?: pickColor(list)
            list + Todo(o.getLong("id"), o.getString("text"), color)
        }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun save(list: List<Todo>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("color", it.color)) }
        val tmp = File(file.parentFile, "todos.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
