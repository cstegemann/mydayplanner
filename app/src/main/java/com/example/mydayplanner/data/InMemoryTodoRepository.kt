package com.example.mydayplanner.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.mydayplanner.data.models.Todo

class InMemoryTodoRepository : TodoRepository {
    private val _today = MutableStateFlow<List<Todo>>(emptyList())
    override val todayTodos = _today.asStateFlow()

    override suspend fun add(text: String) {
        if (text.isBlank()) return
        _today.value += Todo(text = text.trim())
    }

    override suspend fun toggle(id: String) {
        _today.value = _today.value.map { t ->
            if (t.id == id) t.copy(done = !t.done, completedAt = if (!t.done) System.currentTimeMillis() else null)
            else t
        }
    }

    override suspend fun remove(id: String) {
        _today.value = _today.value.filterNot { it.id == id }
    }
}