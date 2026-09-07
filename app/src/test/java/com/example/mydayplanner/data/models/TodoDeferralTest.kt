package com.example.mydayplanner.data.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodoDeferralTest {
    private val today = LocalDate.of(2026, 9, 7)

    @Test fun `deferred task returns on target date`() {
        val todo = Todo(text = "Later", deferredUntil = "2026-09-10")
        assertTrue(todo.isDeferred(today))
        assertFalse(todo.isDeferred(LocalDate.of(2026, 9, 10)))
    }

    @Test fun `legacy pushed flag remains compatible`() {
        assertTrue(Todo(text = "Legacy", pushedToTomorrow = true).isDeferred(today))
    }

    @Test fun `malformed deferred date fails soft`() {
        assertFalse(Todo(text = "Bad", deferredUntil = "not-a-date").isDeferred(today))
    }
}
