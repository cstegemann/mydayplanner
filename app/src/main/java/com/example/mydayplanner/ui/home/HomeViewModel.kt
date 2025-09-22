package com.example.mydayplanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mydayplanner.config.Project
import com.example.mydayplanner.data.PlainJsonTodoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.mydayplanner.data.TodoRepository
import com.example.mydayplanner.data.models.Todo

data class HomeUiState(
    val todos: List<Todo> = emptyList(),
    val input: String = "",
    val inputImportant: Boolean = false,
    val inputEstimateMinutes: Int = 15,
    val inputProject: Project = Project.Other
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
        repo.todayTodos
            .map { todos ->
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
                HomeUiState(todos = sorted) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

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
            repo.add(
                text = text,
                important = _inputImportant,
                estimateMinutes = _inputEstimate,
                project = _inputProject
            )
            _input = "" // clear
            //_inputImportant = false
            //_inputEstimate = 15
            //_inputProject = "Other"
        }
    }

    fun toggle(id: String) = viewModelScope.launch { repo.toggle(id) }
    fun remove(id: String) = viewModelScope.launch { repo.remove(id) }
    fun togglePushToTomorrow(id: String) = viewModelScope.launch { repo.togglePushToTomorrow(id) }
}