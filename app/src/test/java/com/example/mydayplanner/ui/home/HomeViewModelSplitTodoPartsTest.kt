package com.example.mydayplanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelSplitTodoPartsTest {
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
