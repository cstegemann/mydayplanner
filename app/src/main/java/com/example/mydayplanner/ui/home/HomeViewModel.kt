package com.example.mydayplanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mydayplanner.config.Project
import com.example.mydayplanner.config.TaskDifficulty
import com.example.mydayplanner.data.PlainJsonTodoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.mydayplanner.data.TodoRepository
import com.example.mydayplanner.data.models.DayTracking
import com.example.mydayplanner.data.models.Todo
import kotlinx.coroutines.flow.combine
import com.example.mydayplanner.data.models.LiveTrack

data class HomeUiState(
    val todos: List<Todo> = emptyList(),
    val input: String = "",
    val inputImportant: Boolean = false,
    val inputEstimateMinutes: Int = 15,
    val inputProject: Project = Project.Other,
    val tracking: DayTracking = DayTracking(),
    val totals: Map<Project, Long> = emptyMap(),
    val liveTracks: List<LiveTrack> = emptyList(),
    val storageMessage: String? = null,
    val sharedFolderUri: String? = null
)

class HomeViewModel(
    private val repo: TodoRepository
) : ViewModel() {
    init {
        viewModelScope.launch {
            if (repo is PlainJsonTodoRepository) repo.initializeIfNeeded()
        }
    }
    val uiState: StateFlow<HomeUiState> =
        combine(repo.todayTodos, repo.tracking, repo.liveTracks, repo.storageMessage) { todos, tracking, tracks, message ->
                val today = java.time.LocalDate.now()
                val sorted = todos.sortedWith(
                    compareBy<Todo> { it.isDeferred(today) }
                        .thenBy { it.done }
                        .thenByDescending {
                            if (it.project == Project.META) 0 else 1
                        }
                        .thenByDescending {
                            if (it.project == Project.Other) 0 else 1
                        }
                        .thenByDescending { it.important }
                        .thenBy { it.createdAt }
                )
                HomeUiState(todos = sorted,
                    tracking = tracking,
                    totals = repo.currentTotalsWithLive(),
                    liveTracks = tracks,
                    storageMessage = message,
                    sharedFolderUri = repo.sharedFolderUri
                )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onInputChange(value: String) {
        _input = value
    }
    fun onToggleImportantInput() { _inputImportant = !_inputImportant }
    private var _input: String = ""
    private var _inputImportant: Boolean = false
    private var _inputEstimate = 15
    private var _inputProject = Project.Other
    val input get() = _input

    fun onSetEstimate(minutes: Int) { _inputEstimate = minutes }
    fun onSetProject(project: Project) { _inputProject = project }

    fun add() = viewModelScope.launch {
        val text = _input.trim()
        if (text.isNotEmpty()) {
            addTodo(text, _inputImportant, _inputEstimate, _inputProject, difficulty = null)
            _input = ""
        }
    }

    fun addTodo(
        text: String,
        important: Boolean,
        estimateMinutes: Int,
        project: Project,
        liveTrackId: String? = null,
        difficulty: TaskDifficulty?
    ) = viewModelScope.launch {
        if (text.isNotBlank()) {
            splitTodoParts(text.trim(), estimateMinutes).forEach { part ->
                repo.add(
                    text = part.text,
                    important = important,
                    estimateMinutes = part.estimateMinutes,
                    project = project,
                    liveTrackId = liveTrackId,
                    difficulty = difficulty
                )
            }
            _input = ""
        }
    }

    fun updateTodo(todo: Todo) = viewModelScope.launch { repo.update(todo) }

    fun toggle(id: String) = viewModelScope.launch { repo.toggle(id) }
    fun remove(id: String) = viewModelScope.launch { repo.remove(id) }
    fun pushBack(id: String, days: Int) = viewModelScope.launch { repo.pushBack(id, days) }
    fun onSelectCurrentProject(p: Project?) = viewModelScope.launch {
        repo.setCurrentProject(p)
    }
    fun configureSharedFolder(uri: android.net.Uri) = viewModelScope.launch { repo.configureSharedFolder(uri) }
    fun refreshSharedData() = viewModelScope.launch { repo.refreshSharedData() }
}

internal data class TodoPart(
    val text: String,
    val estimateMinutes: Int
)

internal fun splitTodoParts(text: String, estimateMinutes: Int): List<TodoPart> {
    if (estimateMinutes <= 60) {
        return listOf(TodoPart(text = text, estimateMinutes = estimateMinutes))
    }

    val partCount = (estimateMinutes + 59) / 60
    val romanNumerals = listOf("I", "II", "III", "IV", "V")
    var remaining = estimateMinutes

    return List(partCount) { index ->
        val minutes = minOf(60, remaining)
        remaining -= minutes
        val suffix = romanNumerals.getOrElse(index) { "${index + 1}" }
        TodoPart(text = "$text $suffix", estimateMinutes = minutes)
    }
}
