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

class FileBackedTodoRepository(
    appContext: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : TodoRepository {

    private val context = appContext.applicationContext

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    private val zone = ZoneId.systemDefault()
    private fun todayKey(): String = LocalDate.now(zone).toString() // "2025-08-27"

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
    }

    private suspend fun saveToday() = withContext(io) {
        val f = fileFor(todayKey())
        // Write atomically: write temp, then replace
        val tmp = File.createTempFile("today", ".tmp", dir)
        tmp.writeText(json.encodeToString(ListSerializer(Todo.serializer()), _today.value))
        f.delete()
        tmp.renameTo(f)
    }

    override suspend fun add(text: String) {
        if (text.isBlank()) return
        val new = _today.value + Todo(
            id = java.util.UUID.randomUUID().toString(),
            text = text.trim(),
            done = false,
            createdAt = System.currentTimeMillis()
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