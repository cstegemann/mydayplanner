package com.example.mydayplanner.data

import kotlinx.coroutines.flow.Flow
import com.example.mydayplanner.data.models.Todo

interface TodoRepository {
    val todayTodos: Flow<List<Todo>>
    suspend fun add(
        text: String,
        important: Boolean = false,
        estimateMinutes: Int = 15,
        project: String = "Other"
    )
    suspend fun toggle(id: String)
    suspend fun remove(id: String)
    suspend fun togglePushToTomorrow(id: String)
}