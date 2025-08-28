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
        if (!initialized) { ensureLoadedForToday(); initialized = true }
    }

    private suspend fun ensureLoadedForToday() = withContext(io) {
        val today = todayKey()
        if (loadedDayKey == today) return@withContext

        val todayFile = fileFor(today)

        val todayList: MutableList<Todo> = if (todayFile.exists()) {
            runCatching { json.decodeFromString<List<Todo>>(todayFile.readText()) }
                .getOrElse { emptyList() }
                .toMutableList()
        } else {
            // First touch of the day: build from yesterday's unfinished
            val yFile = fileFor(yesterdayKey())
            val carry = if (yFile.exists()) {
                runCatching { json.decodeFromString<List<Todo>>(yFile.readText()) }
                    .getOrElse { emptyList() }
                    .asSequence()
                    .filter { !it.done }
                    .map { it.copy(done = false, completedAt = null) }
                    .toList()
            } else emptyList()

            carry.toMutableList().also {
                // Persist a new (possibly empty) today file so we don't re-import later
                val f = fileFor(today)
                val tmp = File.createTempFile("today", ".tmp", dir)
                tmp.writeText(json.encodeToString(ListSerializer(Todo.serializer()), it))
                f.delete()
                tmp.renameTo(f)
            }
        }

        _today.value = todayList
        loadedDayKey = today
    }

    private suspend fun withTodayLoaded(block: suspend () -> Unit) {
        ensureLoadedForToday()
        return block()
    }

    private suspend fun saveToday() = withContext(io) {
        val f = fileFor(todayKey())
        // Write atomically: write temp, then replace
        val tmp = File.createTempFile("today", ".tmp", dir)
        tmp.writeText(json.encodeToString(ListSerializer(Todo.serializer()), _today.value))
        f.delete()
        tmp.renameTo(f)
    }

    override suspend fun add(text: String, important: Boolean) = withContext(io) {
        if (text.isBlank()) return@withContext
        withTodayLoaded {
            val newList = _today.value + Todo(
                id = java.util.UUID.randomUUID().toString(),
                text = text.trim(),
                done = false,
                createdAt = System.currentTimeMillis(),
                important = important
            )
            _today.value = newList
            saveToday()
        }
    }

    override suspend fun toggle(id: String) = withContext(io) {
        withTodayLoaded {
            val updated = _today.value.map { t ->
                if (t.id == id) {
                    val nowDone = !t.done
                    t.copy(done = nowDone, completedAt = if (nowDone) System.currentTimeMillis() else null)
                } else t
            }
            _today.value = updated
            saveToday()
        }
    }

    override suspend fun remove(id: String) = withContext(io) {
        withTodayLoaded {
            _today.value = _today.value.filterNot { it.id == id }
            saveToday()
        }
    }
}