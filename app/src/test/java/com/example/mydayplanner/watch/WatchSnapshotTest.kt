package com.example.mydayplanner.watch

import com.example.mydayplanner.config.*
import com.example.mydayplanner.data.models.RoutineProgress
import com.example.mydayplanner.data.models.Todo
import com.example.mydayplanner.ui.home.RuleNotice
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchSnapshotTest {
    @Test fun `snapshot contains reduced planner and fake weather data`() {
        val config = TodoConfig(
            areas = listOf(Area("learning"), Area("physical")),
            projects = emptyList(),
            routines = listOf(
                Routine("read", "Read", "learning", RoutineMeasure.COUNT, 4, emptyList()),
                Routine("sport", "Sport", "physical", RoutineMeasure.BOOLEAN, 1, emptyList())
            ),
            rules = emptyList()
        )
        val todos = listOf(
            Todo(text = "done", done = true, estimateMinutes = 30),
            Todo(text = "next", estimateMinutes = 60)
        )
        val notices = List(4) { RuleNotice("warning-$it", RuleEffect.WARNING, "Warning") } +
            RuleNotice("replan", RuleEffect.REPLAN, "Replan")

        val result = buildWatchSnapshot(
            config,
            RoutineProgress(mapOf("read" to 1, "sport" to 1)),
            todos,
            notices,
            freeDay = false,
            timestamp = 123L
        )

        assertEquals(25, result.learningProgress)
        assertEquals(100, result.physicalProgress)
        assertEquals(33, result.taskProgress)
        assertEquals(60, result.remainingMinutes)
        assertEquals(3, result.warningSeverity)
        assertEquals(1, result.replanSeverity)
        assertEquals(20, result.temperatureCelsius)
        assertEquals(3, result.temperatureDelta)
        assertEquals(3, result.rainLevel)
        assertEquals(3, result.windLevel)
        assertEquals(1, result.thunderState)
        assertEquals(123L, result.timestamp)
    }
}
