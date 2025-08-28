package com.example.mydayplanner.data

import kotlinx.coroutines.flow.Flow
import com.example.mydayplanner.data.models.Todo

interface TodoRepository {
    val todayTodos: Flow<List<Todo>>
    suspend fun add(text: String, important: Boolean = false)
    suspend fun toggle(id: String)
    suspend fun remove(id: String)
}