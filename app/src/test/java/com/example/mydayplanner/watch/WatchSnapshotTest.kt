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

        assertEquals(1, result["v"])
        assertEquals(25, result["l"])
        assertEquals(100, result["p"])
        assertEquals(33, result["t"])
        assertEquals(60, result["rm"])
        assertEquals(3, result["w"])
        assertEquals(1, result["rp"])
        assertEquals(20, result["tc"])
        assertEquals(2, result["td"])
        assertEquals(1, result["rn"])
        assertEquals(2, result["wi"])
        assertEquals(0, result["th"])
        assertEquals(123L, result["ts"])
    }
}
