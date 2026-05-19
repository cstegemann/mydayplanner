package com.example.mydayplanner.data

import android.content.Context
import com.example.mydayplanner.config.Project
import com.example.mydayplanner.config.TaskDifficulty
import com.example.mydayplanner.data.models.DayTracking
import com.example.mydayplanner.data.models.Todo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.DayOfWeek
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
    private fun lastActiveDayKey(d: Long): String = LocalDate.now(zone).minusDays(d).toString()

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
            var i: Long = 1
            var yFile = fileFor(lastActiveDayKey(i))
            val maxBack = 30
            while (!yFile.exists() && i <= maxBack){
                i++
                yFile = fileFor(lastActiveDayKey(i))
            }
            val carry = if (yFile.exists()) {
                runCatching { json.decodeFromString<List<Todo>>(yFile.readText()) }
                    .getOrElse { emptyList() }
                    .asSequence()
                    .filter { !it.done }
                    .map { it.copy(done = false, completedAt = null, pushedToTomorrow = false) }
                    .toList()
            } else emptyList()

            carry.toMutableList().also {
                it.add(Todo(text="Tagesplan", important = true, timePredicted = 15, project= Project.META))
                it.add(Todo(text="Vormittags keine visuelle Unterhaltung", important = true, timePredicted = 0, project=Project.META))
                it.add(Todo(text="Vor dem Mittagessen 3h", important = true, timePredicted = 0, project=Project.META))
                it.add(Todo(text="Nach dem Mittagessen 2h", important = true, timePredicted = 0, project=Project.META))
                it.add(Todo(text="Insgesamt 6h", important = true, timePredicted = 0, project=Project.META))
                it.add(Todo(text="Eine Einheit Sport", important = true, timePredicted = 0, project=Project.META))
                // Persist a new (possibly empty) today file so we don't re-import later
                val f = fileFor(today)
                val tmp = File.createTempFile("today", ".tmp", dir)
                tmp.writeText(json.encodeToString(ListSerializer(Todo.serializer()), it))
                f.delete()
                tmp.renameTo(f)
            }
        }
        // load or create today's tracking
        loadTracking(today)

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

    override suspend fun add(
        text: String,
        important: Boolean,
        estimateMinutes: Int,
        project: Project,
        difficulty: TaskDifficulty?
    ) = withContext(io) {
        if (text.isBlank()) return@withContext
        withTodayLoaded {
            val newList = _today.value + Todo(
                id = java.util.UUID.randomUUID().toString(),
                text = text.trim(),
                done = false,
                createdAt = System.currentTimeMillis(),
                important = important,
                timePredicted = estimateMinutes,
                estimateMinutes = estimateMinutes,
                project = project,
                difficulty = difficulty
            )
            _today.value = newList
            saveToday()
        }
    }

    override suspend fun update(todo: Todo) = withContext(io) {
        withTodayLoaded {
            _today.value = _today.value.map { existing ->
                if (existing.id == todo.id) todo else existing
            }
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

    override suspend fun togglePushToTomorrow(id: String) = withContext(io) {
        withTodayLoaded {
            val updated = _today.value.map { t ->
                if (t.id == id) {
                    val pushed = !t.pushedToTomorrow
                    t.copy(pushedToTomorrow = pushed)
                } else t
            }
            _today.value = updated
            saveToday()
        }
    }

    // data/PlainJsonTodoRepository.kt  (add these impls)
    override suspend fun getRecentDays(limit: Int): List<String> = withContext(io) {
        // list files like 2025-09-18.json → sort desc → take up to limit
        dir.listFiles()
            ?.asSequence()
            ?.mapNotNull { f ->
                val name = f.name
                if (name.endsWith(".json")) name.removeSuffix(".json") else null
            }
            ?.filter { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
            ?.sortedDescending()
            ?.take(limit)
            ?.toList()
            ?: emptyList()
    }

    override suspend fun getDay(dayKey: String): List<Todo> = withContext(io) {
        val f = fileFor(dayKey)
        if (!f.exists()) return@withContext emptyList()
        runCatching { json.decodeFromString<List<Todo>>(f.readText()) }
            .getOrElse { emptyList() }
    }

    /*
    * TRACKING STUFF
    * */

    private fun trackingFileFor(day: String): File = File(dir, "$day.track.json")

    private val _tracking = MutableStateFlow(DayTracking())
    override val tracking = _tracking.asStateFlow()

    private suspend fun loadTracking(day: String = todayKey()) = withContext(io) {
        val tf = trackingFileFor(day)
        val state = if (tf.exists()) {
            runCatching { json.decodeFromString<DayTracking>(tf.readText()) }
                .getOrElse { DayTracking() }
        } else {
            val dayDate = runCatching { LocalDate.parse(day) }.getOrNull()
            if (dayDate != null && dayDate.dayOfWeek != DayOfWeek.MONDAY) {
                val prev = trackingFileFor(dayDate.minusDays(1).toString())
                val previousState = if (prev.exists()) {
                    runCatching { json.decodeFromString<DayTracking>(prev.readText()) }
                        .getOrElse { DayTracking() }
                } else DayTracking()

                if (previousState.current == Project.FREE_DAY) {
                    DayTracking(current = Project.FREE_DAY, startedAt = null, totals = emptyMap())
                } else {
                    DayTracking()
                }
            } else {
                DayTracking()
            }
        }
        _tracking.value = state
    }

    private suspend fun saveTracking(day: String = todayKey()) = withContext(io) {
        val tf = trackingFileFor(day)
        val tmp = File.createTempFile("track", ".tmp", dir)
        tmp.writeText(json.encodeToString(DayTracking.serializer(), _tracking.value))
        tf.delete()
        tmp.renameTo(tf)
    }

    override suspend fun setCurrentProject(project: Project?){
        withContext(io) {
            ensureLoadedForToday()
            val now = System.currentTimeMillis()
            val cur = _tracking.value

            val totals = cur.totals.toMutableMap()

            // Close previous segment if any
            if (cur.current != null && cur.startedAt != null) {
                val elapsed = (now - cur.startedAt).coerceAtLeast(0L)
                totals[cur.current] = (totals[cur.current] ?: 0L) + elapsed
            }

            // Start new or stop
            _tracking.value = if (project == null) {
                cur.copy(current = null, startedAt = null, totals = totals)
            } else {
                val startsTimer = project.countInTimer
                DayTracking(current = project, startedAt = if (startsTimer) now else null, totals = totals)
            }

            saveTracking()
        }
    }

    /** Read totals including the live-running segment for display */
    override fun currentTotalsWithLive(nowMillis: Long): Map<Project, Long> {
        val t = _tracking.value
        val base = t.totals.toMutableMap()
        if (t.current != null && t.startedAt != null) {
            val live = (nowMillis - t.startedAt).coerceAtLeast(0L)
            base[t.current] = (base[t.current] ?: 0L) + live
        }
        return base
    }

    // Implement the interface method
    override suspend fun getDayTracking(dayKey: String): DayTracking = withContext(io) {
        val tf = trackingFileFor(dayKey)
        if (!tf.exists()) return@withContext DayTracking()
        runCatching { json.decodeFromString<DayTracking>(tf.readText()) }
            .getOrElse { DayTracking() }
    }

}
