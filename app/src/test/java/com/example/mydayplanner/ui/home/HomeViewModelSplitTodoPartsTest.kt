package com.example.mydayplanner.ui.home

import com.example.mydayplanner.config.Area
import com.example.mydayplanner.config.ConfigProject
import com.example.mydayplanner.config.TodoConfig
import com.example.mydayplanner.data.models.Todo
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelSplitTodoPartsTest {
    @Test
    fun sortsTodosByConfiguredProjectBeforeImportanceAndCreation() {
        val config = TodoConfig(
            areas = listOf(Area("work")),
            projects = listOf(
                ConfigProject("first", "First", "work", emptyList(), true),
                ConfigProject("second", "Second", "work", emptyList(), true)
            ),
            routines = emptyList(),
            rules = emptyList()
        )
        val todos = listOf(
            Todo(id = "second-old", text = "Second old", liveTrackId = "second", createdAt = 1),
            Todo(id = "first-new", text = "First new", liveTrackId = "first", createdAt = 3),
            Todo(id = "second-important", text = "Second important", liveTrackId = "second", important = true, createdAt = 2),
            Todo(id = "first-old", text = "First old", liveTrackId = "first", createdAt = 0)
        )

        assertEquals(
            listOf("first-old", "first-new", "second-important", "second-old"),
            sortTodosByProject(todos, config).map { it.id }
        )
    }

    @Test
    fun keepsSingleTodoWhenEstimateIsAtMostOneHour() {
        val parts = splitTodoParts(text = "Read docs", estimateMinutes = 60)

        assertEquals(listOf(TodoPart(text = "Read docs", estimateMinutes = 60)), parts)
    }

    @Test
    fun splitsIntoHourChunksAndRemainderWithRomanNumerals() {
        val parts = splitTodoParts(text = "Write report", estimateMinutes = 130)

        assertEquals(
            listOf(
                TodoPart(text = "Write report I", estimateMinutes = 60),
                TodoPart(text = "Write report II", estimateMinutes = 60),
                TodoPart(text = "Write report III", estimateMinutes = 10)
            ),
            parts
        )
    }
}
