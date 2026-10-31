package com.example.mydayplanner.watch

import org.junit.Assert.*
import org.junit.Test

class WatchSyncHistoryTest {
    @Test fun `callbacks from overlapping manual and automatic sends retain their own history`() {
        var now = 100L
        val history = WatchSyncHistory(now = { now++ })
        val payload = mutableMapOf<String, Any>("timestamp" to 1)
        val automatic = history.start(SnapshotSendOrigin.PLANNER_UPDATE, payload)
        val manual = history.start(SnapshotSendOrigin.FORCED, payload)
        payload["timestamp"] = 2
        history.append(manual.id, "Device disconnected")
        history.append(automatic.id, "Send result SUCCESS")
        val attempts = history.attempts.value
        assertEquals(listOf(manual.id, automatic.id), attempts.map { it.id })
        assertEquals("Device disconnected", attempts[0].entries.single().message)
        assertEquals("Send result SUCCESS", attempts[1].entries.single().message)
        assertEquals(1, attempts[1].payload!!["timestamp"])
        assertTrue(attempts[1].entries.single().timestampMillis > automatic.startedAtMillis)
    }

    @Test fun `failed and blocked attempts are retained without a successful snapshot`() {
        val history = WatchSyncHistory()
        val attempt = history.start(SnapshotSendOrigin.APP_RESUME, null)
        history.append(attempt.id, "SDK not ready")
        assertNull(history.attempts.value.single().payload)
        assertEquals("SDK not ready", history.attempts.value.single().entries.single().message)
    }

    @Test fun `history is bounded and evicted callbacks do not modify newer attempts`() {
        val history = WatchSyncHistory(capacity = 2)
        val old = history.start(SnapshotSendOrigin.FORCED, null)
        history.start(SnapshotSendOrigin.APP_RESUME, null)
        history.start(SnapshotSendOrigin.PLANNER_UPDATE, null)
        history.append(old.id, "late result")
        assertEquals(2, history.attempts.value.size)
        assertTrue(history.attempts.value.all { it.entries.isEmpty() })
    }
}
