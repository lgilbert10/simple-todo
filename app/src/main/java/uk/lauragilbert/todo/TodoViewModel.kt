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

data class Todo(val id: Long, val text: String)

class TodoViewModel(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "todos.json")
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Todo>> = _items.asStateFlow()

    fun add(text: String) {
        val clean = text.trim().replaceFirstChar { it.uppercase() }
        if (clean.isEmpty()) return
        val id = (_items.value.maxOfOrNull { it.id } ?: 0L) + 1
        mutate { it + Todo(id, clean) }
    }

    fun remove(todo: Todo): Int {
        val index = _items.value.indexOf(todo)
        mutate { it - todo }
        return index
    }

    fun restore(todo: Todo, index: Int) {
        mutate { list ->
            if (list.any { it.id == todo.id }) list
            else list.toMutableList().apply { add(index.coerceIn(0, size), todo) }
        }
    }

    private fun mutate(change: (List<Todo>) -> List<Todo>) {
        _items.update(change)
        val snapshot = _items.value
        viewModelScope.launch(Dispatchers.IO) { save(snapshot) }
    }

    private fun load(): List<Todo> = runCatching {
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Todo(o.getLong("id"), o.getString("text"))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun save(list: List<Todo>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text)) }
        val tmp = File(file.parentFile, "todos.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
