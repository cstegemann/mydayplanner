package com.example.mydayplanner.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

internal data class WatchSyncLogEntry(val timestampMillis: Long, val message: String)

internal data class WatchSyncAttempt(
    val id: Long,
    val startedAtMillis: Long,
    val origin: SnapshotSendOrigin,
    val payload: Map<String, Any>?,
    val entries: List<WatchSyncLogEntry>
)

/** Callback updates belong to their original attempt, even after a newer send starts. */
internal class WatchSyncHistory(
    private val capacity: Int = 100,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val ids = AtomicLong()
    private val _attempts = MutableStateFlow<List<WatchSyncAttempt>>(emptyList())
    val attempts = _attempts.asStateFlow()

    fun start(origin: SnapshotSendOrigin, payload: Map<String, Any>?): WatchSyncAttempt {
        val attempt = WatchSyncAttempt(ids.incrementAndGet(), now(), origin, payload?.toMap(), emptyList())
        _attempts.update { (it + attempt).sortedByDescending { entry -> entry.id }.take(capacity) }
        return attempt
    }

    fun append(id: Long, message: String) {
        val entry = WatchSyncLogEntry(now(), message)
        _attempts.update { attempts ->
            attempts.map { attempt ->
                if (attempt.id == id) attempt.copy(entries = (attempt.entries + entry).takeLast(200)) else attempt
            }
        }
    }
}
