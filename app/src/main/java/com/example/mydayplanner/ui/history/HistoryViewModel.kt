package com.example.mydayplanner.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mydayplanner.config.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.mydayplanner.data.TodoRepository
import com.example.mydayplanner.data.models.DayTracking
import com.example.mydayplanner.data.models.Todo
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale



data class ProjectGroup(
    val project: Project,
    val label: String,
    val items: List<Todo>
)

data class DayHistory(
    val dayKey: String,               // "2025-09-18"
    val weekdayShort: String,         // "Thu", "Mo", etc.
    val totals: Map<Project, Long>,
    val groups: List<ProjectGroup>
)

class HistoryViewModel(private val repo: TodoRepository) : ViewModel() {
    private val _days = MutableStateFlow<List<DayHistory>>(emptyList())
    val days: StateFlow<List<DayHistory>> = _days
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    // Start in loading state so the first frame cannot look like an empty history.
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load(limit: Int = 28) {
        viewModelScope.launch {
            _isLoading.value = true
            _message.value = null
            try {
                val keys = runCatching { repo.getRecentDays(limit) }.getOrElse {
                    _message.value = "Could not read history: ${it.message ?: "unknown storage error"}"
                    return@launch
                }
                val out = mutableListOf<DayHistory>()
                val locale = Locale.getDefault()
                val fmt = DateTimeFormatter.ISO_LOCAL_DATE
                for (k in keys) {
                    val all = runCatching { repo.getDay(k) }.getOrNull()
                    if (all == null) {
                        _message.value = "Some history files could not be read"
                        continue
                    }
                    if (all.isEmpty()) continue
                    val completed = all.filter { it.done }
                    val groups = completed
                        .groupBy { it.trackLabel }
                        .map { (label, items) ->
                            val project = items.first().project
                            // Sort items inside a project (e.g., by createdAt then text)
                            val sortedItems = items.sortedWith(
                                compareBy<Todo> { it.createdAt }.thenBy { it.text.lowercase() }
                            )
                            ProjectGroup(project = project, label = label, items = sortedItems)
                        }
                    val tracking: DayTracking = repo.getDayTracking(k)
                    val totals = tracking.totals // Map<Project, Long>
                    //if (all.isNotEmpty()) {
                    val date = LocalDate.parse(k, fmt)
                    val wd = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) // e.g., "Thu"
                    out += DayHistory(dayKey = k, weekdayShort = wd, groups = groups, totals = totals)
                    //}
                }
                _days.value = out
                if (keys.isEmpty() && _message.value == null) {
                    _message.value = repo.storageMessage.value ?: "No history JSON files were found in the shared folder"
                } else if (keys.isNotEmpty() && out.isEmpty() && _message.value == null) {
                    _message.value = "History files were found, but they contain no readable tasks"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
