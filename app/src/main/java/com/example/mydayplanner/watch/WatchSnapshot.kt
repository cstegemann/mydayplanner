package com.example.mydayplanner.watch

import com.example.mydayplanner.config.Project
import com.example.mydayplanner.config.RuleEffect
import com.example.mydayplanner.config.TodoConfig
import com.example.mydayplanner.data.models.RoutineProgress
import com.example.mydayplanner.data.models.Todo
import com.example.mydayplanner.ui.home.RuleNotice
import com.example.mydayplanner.ui.home.routineProgressPercent
import java.time.LocalDate

data class ReducedWeather(
    val temperatureCelsius: Int,
    val temperatureDelta: Int,
    val rainLevel: Int,
    val windLevel: Int,
    val thunderState: Int
) {
    companion object {
        /** Placeholder until the DWD weather source is implemented. */
        val Fake = ReducedWeather(20, 2, rainLevel = 1, windLevel = 2, thunderState = 0)
    }
}

/** Produces the complete, compact payload that will eventually be sent via Connect IQ. */
fun buildWatchSnapshot(
    config: TodoConfig?,
    routineProgress: RoutineProgress,
    todos: List<Todo>,
    notices: List<RuleNotice>,
    freeDay: Boolean,
    weather: ReducedWeather = ReducedWeather.Fake,
    timestamp: Long = System.currentTimeMillis() / 1_000
): Map<String, Any> {
    val daytype = if (freeDay) "free" else "work"
    val routines = config?.routines.orEmpty().filter { it.daytypes.isEmpty() || daytype in it.daytypes }
    fun progressFor(area: String): Int {
        val areaIds = config?.areas.orEmpty()
            .filter { it.id.equals(area, ignoreCase = true) || it.name.equals(area, ignoreCase = true) }
            .map { it.id }
            .toSet()
        return routineProgressPercent(
            routines.filter { it.area in areaIds },
            routineProgress
        )
    }

    val today = LocalDate.now()
    val scheduled = todos.filter { it.project != Project.META && !it.isDeferred(today) }
    val totalMinutes = scheduled.sumOf { it.estimateMinutes.coerceAtLeast(0) }
    val doneMinutes = scheduled.filter { it.done }.sumOf { it.estimateMinutes.coerceAtLeast(0) }
    val taskProgress = if (totalMinutes == 0) 0 else (doneMinutes * 100.0 / totalMinutes).toInt().coerceIn(0, 100)

    return mapOf(
        "v" to 1,
        "l" to progressFor("learning"),
        "p" to progressFor("physical"),
        "t" to taskProgress,
        "rm" to scheduled.filterNot { it.done }.sumOf { it.estimateMinutes.coerceAtLeast(0) },
        "w" to notices.count { it.effect == RuleEffect.WARNING }.coerceAtMost(3),
        "rp" to notices.count { it.effect == RuleEffect.REPLAN }.coerceAtMost(3),
        "tc" to weather.temperatureCelsius,
        "td" to weather.temperatureDelta,
        "rn" to weather.rainLevel.coerceIn(0, 3),
        "wi" to weather.windLevel.coerceIn(0, 3),
        "th" to weather.thunderState.coerceIn(0, 2),
        "ts" to timestamp
    )
}
