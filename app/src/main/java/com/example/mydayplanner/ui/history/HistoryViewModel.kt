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
    val items: List<Todo>
)

data class DayHistory(
    val dayKey: String,               // "2025-09-18"
    val weekdayShort: String,         // "Thu", "Mo", etc.
    val totals: Map<Project, Long>,
    val groups: List<ProjectGroup>
)

private fun projectComparator(): Comparator<Project> = Comparator { a, b ->
    a.sortOrder - b.sortOrder
}

class HistoryViewModel(private val repo: TodoRepository) : ViewModel() {
    private val _days = MutableStateFlow<List<DayHistory>>(emptyList())
    val days: StateFlow<List<DayHistory>> = _days

    fun load(limit: Int = 28) {
        viewModelScope.launch {
            val keys = repo.getRecentDays(limit)
            val out = mutableListOf<DayHistory>()
            val locale = Locale.getDefault()
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            val projCmp = projectComparator()
            for (k in keys) {
                val all = repo.getDay(k)
                if (all.isEmpty()) continue
                val completed = all.filter { it.done }
                val groups = completed
                    .groupBy { it.project }
                    .toSortedMap(projCmp) // order projects
                    .map { (project, items) ->
                        // Sort items inside a project (e.g., by createdAt then text)
                        val sortedItems = items.sortedWith(
                            compareBy<Todo> { it.createdAt }.thenBy { it.text.lowercase() }
                        )
                        ProjectGroup(project = project, items = sortedItems)
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
        }
    }
}
