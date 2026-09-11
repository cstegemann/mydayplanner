package com.example.mydayplanner.ui.home

import com.example.mydayplanner.config.Routine
import com.example.mydayplanner.config.RoutineMeasure
import com.example.mydayplanner.data.models.RoutineProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressCalculationsTest {
    @Test fun `each count or boolean tap has equal weight`() {
        val routines = listOf(
            routine("sport", RoutineMeasure.BOOLEAN, 1),
            routine("stretch", RoutineMeasure.COUNT, 6),
            routine("healthy", RoutineMeasure.COUNT, 2)
        )

        assertEquals(11, routineProgressPercent(routines, RoutineProgress(mapOf("sport" to 1))))
        assertEquals(11, routineProgressPercent(routines, RoutineProgress(mapOf("stretch" to 1))))
        assertEquals(22, routineProgressPercent(routines, RoutineProgress(mapOf("stretch" to 2))))
    }

    @Test fun `each thirty minute block is one unit`() {
        val routines = listOf(
            routine("study", RoutineMeasure.MINUTES, 60),
            routine("walk", RoutineMeasure.COUNT, 2)
        )

        assertEquals(25, routineProgressPercent(routines, RoutineProgress(mapOf("study" to 30))))
        assertEquals(50, routineProgressPercent(routines, RoutineProgress(mapOf("study" to 60))))
    }

    private fun routine(id: String, measure: RoutineMeasure, target: Int) =
        Routine(id, id, "area", measure, target, emptyList())
}
