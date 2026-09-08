package com.example.mydayplanner.data

import com.example.mydayplanner.config.Project
import com.example.mydayplanner.config.TaskDifficulty
import com.example.mydayplanner.data.models.DayTracking
import kotlinx.coroutines.flow.Flow
import com.example.mydayplanner.data.models.Todo
import com.example.mydayplanner.data.models.LiveTrack
import kotlinx.coroutines.flow.StateFlow

interface TodoRepository {
    val todayTodos: Flow<List<Todo>>
    suspend fun add(
        text: String,
        important: Boolean = false,
        estimateMinutes: Int = 15,
        project: Project = Project.Other,
        liveTrackId: String? = null,
        difficulty: TaskDifficulty? = null
    )
    suspend fun update(todo: Todo)
    suspend fun toggle(id: String)
    suspend fun remove(id: String)
    suspend fun pushBack(id: String, days: Int)
    suspend fun getRecentDays(limit: Int = 28): List<String> // returns ["2025-09-18", "2025-09-17", ...]
    suspend fun getDay(dayKey: String): List<Todo>

    val tracking: Flow<DayTracking>
    suspend fun getDayTracking(dayKey: String): DayTracking
    suspend fun setCurrentProject(project: Project?)
    fun currentTotalsWithLive(nowMillis: Long = System.currentTimeMillis()): Map<Project, Long>
    val liveTracks: StateFlow<List<LiveTrack>>
    val storageMessage: StateFlow<String?>
    val sharedFolderUri: String?
    suspend fun configureSharedFolder(uri: android.net.Uri)
    suspend fun refreshSharedData()
}
