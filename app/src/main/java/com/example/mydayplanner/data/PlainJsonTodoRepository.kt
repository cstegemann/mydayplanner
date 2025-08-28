package com.example.mydayplanner.data

import android.content.Context
import com.example.mydayplanner.data.models.Todo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class PlainJsonTodoRepository(
    appContext: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : TodoRepository {

    private val context = appContext.applicationContext

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    private val zone = ZoneId.systemDefault()
    private fun todayKey(): String = LocalDate.now(zone).toString() // "2025-08-27"
    private fun yesterdayKey(): String = LocalDate.now(zone).minusDays(1).toString()

    private var loadedDayKey: String? = null

    private val dir: File = File(context.filesDir, "days").apply { mkdirs() }

    private fun fileFor(day: String): File = File(dir, "$day.json")

    private val _today = MutableStateFlow<List<Todo>>(emptyList())
    override val todayTodos = _today.asStateFlow()
    private var initialized = false

    suspend fun initializeIfNeeded() {
        if (!initialized) { loadToday(); initialized = true }
    }

    private suspend fun loadToday() = withContext(io) {
        val f = fileFor(todayKey())
        if (f.exists()) {
            val text = f.readText()
            _today.value = json.decodeFromString<List<Todo>>(text)
        } else {
            _today.value = emptyList()
        }
        val todayFile = fileFor(todayKey())
        val todayList: MutableList<Todo> = if (todayFile.exists()) {
            runCatching { json.decodeFromString<List<Todo>>(todayFile.readText()) }
                .getOrElse { emptyList() }
                .toMutableList()
        } else {
            mutableListOf()
        }

        // Carry over only on first initialization
        if (!initialized) {
            val yFile = fileFor(yesterdayKey())
            if (yFile.exists()) {
                val yTodos = runCatching { json.decodeFromString<List<Todo>>(yFile.readText()) }
                    .getOrElse { emptyList() }

                if (yTodos.isNotEmpty()) {
                    // Build a set of existing IDs to avoid duplicates
                    val existingIds = todayList.asSequence().map { it.id }.toHashSet()

                    val carryOvers = yTodos.asSequence()
                        .filter { !it.done }                         // only incomplete
                        .filter { it.id !in existingIds }            // avoid dupes
                        .map { it.copy(done = false, completedAt = null) } // reset state
                        .toList()

                    if (carryOvers.isNotEmpty()) {
                        todayList += carryOvers
                    }
                }
            }
        }

        _today.value = todayList

        // If we synthesized/modified today's list (new file or carried over), persist it
        // Write when: file didn't exist OR we added carryovers
        val shouldPersist = !todayFile.exists() || (!initialized && todayList.isNotEmpty())
        if (shouldPersist) saveToday()
    }

    private suspend fun saveToday() = withContext(io) {
        val f = fileFor(todayKey())
        // Write atomically: write temp, then replace
        val tmp = File.createTempFile("today", ".tmp", dir)
        tmp.writeText(json.encodeToString(ListSerializer(Todo.serializer()), _today.value))
        f.delete()
        tmp.renameTo(f)
    }

    override suspend fun add(text: String, important: Boolean) {
        if (text.isBlank()) return
        val new = _today.value + Todo(
            id = java.util.UUID.randomUUID().toString(),
            text = text.trim(),
            done = false,
            createdAt = System.currentTimeMillis(),
            important = important
        )
        _today.value = new
        saveToday()
    }

    override suspend fun toggle(id: String) {
        val updated = _today.value.map { t ->
            if (t.id == id) {
                val nowDone = !t.done
                t.copy(done = nowDone, completedAt = if (nowDone) System.currentTimeMillis() else null)
            } else t
        }
        _today.value = updated
        saveToday()
    }

    override suspend fun remove(id: String) {
        _today.value = _today.value.filterNot { it.id == id }
        saveToday()
    }
}