package com.example.mydayplanner.ui.home

import com.example.mydayplanner.config.Routine
import com.example.mydayplanner.config.RoutineMeasure
import com.example.mydayplanner.data.models.RoutineProgress
import kotlin.math.roundToInt

/** Calculates progress by achievable taps, rather than giving every routine equal weight. */
internal fun routineProgressPercent(
    routines: List<Routine>,
    progress: RoutineProgress
): Int {
    val targetUnits = routines.sumOf { it.targetUnits() }
    if (targetUnits <= 0.0) return 0
    val completedUnits = routines.sumOf { routine ->
        routine.valueUnits(progress.values[routine.id] ?: 0).coerceAtMost(routine.targetUnits())
    }
    return (completedUnits * 100.0 / targetUnits).roundToInt().coerceIn(0, 100)
}

private fun Routine.targetUnits(): Double = when (measure) {
    RoutineMeasure.BOOLEAN -> 1.0
    RoutineMeasure.COUNT -> target.toDouble()
    RoutineMeasure.MINUTES -> target / 30.0
}

private fun Routine.valueUnits(value: Int): Double = when (measure) {
    RoutineMeasure.BOOLEAN -> if (value > 0) 1.0 else 0.0
    RoutineMeasure.COUNT -> value.coerceAtLeast(0).toDouble()
    RoutineMeasure.MINUTES -> value.coerceAtLeast(0) / 30.0
}
