package com.example.mydayplanner.watch

import com.example.mydayplanner.config.Project
import com.example.mydayplanner.config.RuleEffect
import com.example.mydayplanner.config.TodoConfig
import com.example.mydayplanner.data.models.RoutineProgress
import com.example.mydayplanner.data.models.Todo
import com.example.mydayplanner.ui.home.RuleNotice
import com.example.mydayplanner.ui.home.routineProgressPercent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class WatchSnapshot(
    @SerialName("v") val version: Int = 1,
    @SerialName("l") val learningProgress: Int,
    @SerialName("p") val physicalProgress: Int,
    @SerialName("t") val taskProgress: Int,
    @SerialName("rm") val remainingMinutes: Int,
    @SerialName("w") val warningSeverity: Int,
    @SerialName("rp") val replanSeverity: Int,
    @SerialName("tc") val temperatureCelsius: Int,
    @SerialName("td") val temperatureDelta: Int,
    @SerialName("rn") val rainLevel: Int,
    @SerialName("wi") val windLevel: Int,
    @SerialName("th") val thunderState: Int,
    @SerialName("ts") val timestamp: Long
)

data class ReducedWeather(
    val temperatureCelsius: Int,
    val temperatureDelta: Int,
    val rainLevel: Int,
    val windLevel: Int,
    val thunderState: Int
) {
    companion object {
        /** Placeholder until the DWD weather source is implemented. */
        val Fake = ReducedWeather(20, 3, rainLevel = 3, windLevel = 3, thunderState = 1)
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
): WatchSnapshot {
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

    return WatchSnapshot(
        learningProgress = progressFor("learning"),
        physicalProgress = progressFor("physical"),
        taskProgress = taskProgress,
        remainingMinutes = scheduled.filterNot { it.done }.sumOf { it.estimateMinutes.coerceAtLeast(0) },
        warningSeverity = notices.count { it.effect == RuleEffect.WARNING }.coerceAtMost(3),
        replanSeverity = notices.count { it.effect == RuleEffect.REPLAN }.coerceAtMost(3),
        temperatureCelsius = weather.temperatureCelsius,
        temperatureDelta = weather.temperatureDelta,
        rainLevel = weather.rainLevel.coerceIn(0, 3),
        windLevel = weather.windLevel.coerceIn(0, 3),
        thunderState = weather.thunderState.coerceIn(0, 2),
        timestamp = timestamp
    )
}
