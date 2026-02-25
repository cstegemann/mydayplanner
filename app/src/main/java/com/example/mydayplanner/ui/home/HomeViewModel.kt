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

data class HomeUiState(
    val todos: List<Todo> = emptyList(),
    val input: String = "",
    val inputImportant: Boolean = false,
    val inputEstimateMinutes: Int = 15,
    val inputProject: Project = Project.Other,
    val tracking: DayTracking = DayTracking(),
    val totals: Map<Project, Long> = emptyMap()
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
        combine(repo.todayTodos, repo.tracking) { todos, tracking ->
                val sorted = todos.sortedWith(
                    compareBy<Todo> {it.pushedToTomorrow}
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
                    totals = repo.currentTotalsWithLive()
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
            addTodo(text, _inputImportant, _inputEstimate, _inputProject, null)
            _input = ""
        }
    }

    fun addTodo(
        text: String,
        important: Boolean,
        estimateMinutes: Int,
        project: Project,
        difficulty: TaskDifficulty?
    ) = viewModelScope.launch {
        if (text.isNotBlank()) {
            repo.add(
                text = text.trim(),
                important = important,
                estimateMinutes = estimateMinutes,
                project = project,
                difficulty = difficulty
            )
            _input = ""
        }
    }

    fun updateTodo(todo: Todo) = viewModelScope.launch { repo.update(todo) }

    fun toggle(id: String) = viewModelScope.launch { repo.toggle(id) }
    fun remove(id: String) = viewModelScope.launch { repo.remove(id) }
    fun togglePushToTomorrow(id: String) = viewModelScope.launch { repo.togglePushToTomorrow(id) }
    fun onSelectCurrentProject(p: Project?) = viewModelScope.launch {
        repo.setCurrentProject(p)
    }
}
