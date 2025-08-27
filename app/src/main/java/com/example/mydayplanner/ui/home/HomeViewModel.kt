package com.example.mydayplanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mydayplanner.data.FileBackedTodoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.mydayplanner.data.TodoRepository
import com.example.mydayplanner.data.models.Todo

data class HomeUiState(
    val todos: List<Todo> = emptyList(),
    val input: String = ""
)

class HomeViewModel(
    private val repo: TodoRepository
) : ViewModel() {
    init {
        viewModelScope.launch {
            if (repo is FileBackedTodoRepository) repo.initializeIfNeeded()
        }
    }
    val uiState: StateFlow<HomeUiState> =
        repo.todayTodos
            .map { HomeUiState(todos = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onInputChange(value: String) {
        // keep input in a separate StateFlow if you prefer; for brevity we store it ad hoc:
        // we’ll expose a setter via a backing StateFlow, but here’s a simple pattern:
        _input = value
    }
    private var _input: String = ""
    val input get() = _input

    fun add() = viewModelScope.launch {
        val text = _input.trim()
        if (text.isNotEmpty()) {
            repo.add(text)
            _input = "" // clear
        }
    }

    fun toggle(id: String) = viewModelScope.launch { repo.toggle(id) }
    fun remove(id: String) = viewModelScope.launch { repo.remove(id) }
}