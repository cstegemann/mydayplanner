package com.example.mydayplanner.data

import android.content.Context
import android.net.Uri
import com.example.mydayplanner.config.Project
import com.example.mydayplanner.config.TaskDifficulty
import com.example.mydayplanner.data.models.DayTracking
import com.example.mydayplanner.data.models.Todo
import com.example.mydayplanner.data.models.LiveTrack
import com.example.mydayplanner.data.models.LiveTrackParser
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
    private val shared = SharedFolderStorage(context)

    private val _liveTracks = MutableStateFlow<List<LiveTrack>>(emptyList())
    override val liveTracks = _liveTracks.asStateFlow()
    private val _storageMessage = MutableStateFlow<String?>(null)
    override val storageMessage = _storageMessage.asStateFlow()
    override val sharedFolderUri: String? get() = shared.configuredUri

    private fun stateExists(name: String): Boolean =
        when {
            shared.root() != null -> name in shared.names()
            shared.configuredUri == null -> File(dir, name).exists()
            else -> false
        }

    private fun readState(name: String): String? =
        when {
            shared.root() != null -> shared.read(name)
            shared.configuredUri == null -> File(dir, name).takeIf(File::exists)?.readText()
            else -> null
        }

    private fun writeState(name: String, contents: String) {
        if (shared.root() != null) {
            if (!shared.write(name, contents)) _storageMessage.value = "Could not write shared folder"
        } else if (shared.configuredUri == null) {
            File(dir, name).writeText(contents)
        } else {
            _storageMessage.value = "Shared folder unavailable; changes cannot be saved"
        }
    }

    override suspend fun configureSharedFolder(uri: Uri) = withContext(io) {
        shared.configure(uri)
        shared.copyMissingFrom(dir)
        loadedDayKey = null
        refreshSharedData()
        ensureLoadedForToday()
    }

    override suspend fun refreshSharedData() = withContext(io) {
        val root = shared.root()
        if (root == null) {
            _storageMessage.value = if (shared.configuredUri == null) "Choose your Obsidian folder to enable sync" else "Shared folder unavailable"
            return@withContext
        }
        val markdown = shared.readConfig()
        if (markdown == null) {
            _storageMessage.value = "_live-tracks.md not found; keeping previous tracks"
            return@withContext
        }
        val parsed = LiveTrackParser.parse(markdown)
        _liveTracks.value = parsed.tracks
        _storageMessage.value = parsed.warnings.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private val _today = MutableStateFlow<List<Todo>>(emptyList())
    override val todayTodos = _today.asStateFlow()
    private var initialized = false

    suspend fun initializeIfNeeded() {
        if (!initialized) { refreshSharedData(); ensureLoadedForToday(); initialized = true }
    }

    private suspend fun ensureLoadedForToday() = withContext(io) {
        val today = todayKey()
        if (loadedDayKey == today) return@withContext

        val hadTodayFile = stateExists("$today.json")

        val todayList: MutableList<Todo> = if (hadTodayFile) {
            runCatching { json.decodeFromString<List<Todo>>(readState("$today.json") ?: "[]") }
                .getOrElse { emptyList() }
                .toMutableList()
        } else {
            // First touch of the day: build from yesterday's unfinished
            var i: Long = 1
            var previousDay = lastActiveDayKey(i)
            val maxBack = 30
            while (!stateExists("$previousDay.json") && i <= maxBack){
                i++
                previousDay = lastActiveDayKey(i)
            }
            val carry = if (stateExists("$previousDay.json")) {
                runCatching { json.decodeFromString<List<Todo>>(readState("$previousDay.json") ?: "[]") }
                    .getOrElse { emptyList() }
                    .asSequence()
                    .filter { !it.done }
                    .map { it.copy(done = false, completedAt = null, pushedToTomorrow = false) }
                    .toList()
            } else emptyList()

            val initialTodos = carry.toMutableList()

            // load or create today's tracking before deciding whether to inject META tasks
            loadTracking(today)

            if (_tracking.value.current != Project.FREE_DAY) {
                initialTodos.add(Todo(text="Tagesplan", important = true, timePredicted = 15, project= Project.META))
                initialTodos.add(Todo(text="Vormittags keine visuelle Unterhaltung", important = true, timePredicted = 0, project=Project.META))
                initialTodos.add(Todo(text="Vor dem Mittagessen 3h", important = true, timePredicted = 0, project=Project.META))
                initialTodos.add(Todo(text="Nach dem Mittagessen 2h", important = true, timePredicted = 0, project=Project.META))
                initialTodos.add(Todo(text="Insgesamt 6h", important = true, timePredicted = 0, project=Project.META))
                initialTodos.add(Todo(text="Eine Einheit Sport", important = true, timePredicted = 0, project=Project.META))
            }

            // Persist a new (possibly empty) today file so we don't re-import later
            writeState("$today.json", json.encodeToString(ListSerializer(Todo.serializer()), initialTodos))

            initialTodos
        }

        if (hadTodayFile) {
            // load or create today's tracking
            loadTracking(today)
        }

        _today.value = todayList
        loadedDayKey = today
    }

    private suspend fun withTodayLoaded(block: suspend () -> Unit) {
        ensureLoadedForToday()
        return block()
    }

    private suspend fun saveToday() = withContext(io) {
        writeState("${todayKey()}.json", json.encodeToString(ListSerializer(Todo.serializer()), _today.value))
    }

    override suspend fun add(
        text: String,
        important: Boolean,
        estimateMinutes: Int,
        project: Project,
        liveTrackId: String?,
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
                liveTrackId = liveTrackId,
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

    override suspend fun pushBack(id: String, days: Int) = withContext(io) {
        withTodayLoaded {
            val updated = _today.value.map { t ->
                if (t.id == id) {
                    t.copy(pushedToTomorrow = false, deferredUntil = LocalDate.now(zone).plusDays(days.coerceAtLeast(1).toLong()).toString())
                } else t
            }
            _today.value = updated
            saveToday()
        }
    }

    // data/PlainJsonTodoRepository.kt  (add these impls)
    override suspend fun getRecentDays(limit: Int): List<String> = withContext(io) {
        // list files like 2025-09-18.json → sort desc → take up to limit
        val names = when {
            shared.root() != null -> shared.names().asSequence()
            shared.configuredUri == null -> dir.listFiles()?.asSequence()?.map { it.name } ?: emptySequence()
            else -> {
                _storageMessage.value = "Shared folder unavailable; history cannot be loaded"
                emptySequence()
            }
        }
        names
            .mapNotNull { name ->
                if (name.endsWith(".json")) name.removeSuffix(".json") else null
            }
            .filter { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
            .sortedDescending().take(limit).toList()
    }

    override suspend fun getDay(dayKey: String): List<Todo> = withContext(io) {
        val contents = readState("$dayKey.json") ?: return@withContext emptyList()
        runCatching { json.decodeFromString<List<Todo>>(contents) }
            .getOrElse {
                _storageMessage.value = "Could not parse $dayKey.json"
                emptyList()
            }
    }

    /*
    * TRACKING STUFF
    * */

    private fun trackingName(day: String): String = "$day.track.json"

    private val _tracking = MutableStateFlow(DayTracking())
    override val tracking = _tracking.asStateFlow()

    private suspend fun loadTracking(day: String = todayKey()) = withContext(io) {
        val name = trackingName(day)
        val state = if (stateExists(name)) {
            runCatching { json.decodeFromString<DayTracking>(readState(name) ?: "{}") }
                .getOrElse { DayTracking() }
        } else {
            val dayDate = runCatching { LocalDate.parse(day) }.getOrNull()
            if (dayDate != null && dayDate.dayOfWeek != DayOfWeek.MONDAY) {
                var daysToSubtract = 1L
                var prevName = trackingName(dayDate.minusDays(daysToSubtract).toString())
                val maxLookBack = 3L
                while (!stateExists(prevName) && daysToSubtract < maxLookBack){
                    daysToSubtract++
                    prevName = trackingName(dayDate.minusDays(daysToSubtract).toString())
                }
                val previousState = if (stateExists(prevName)) {
                    runCatching { json.decodeFromString<DayTracking>(readState(prevName) ?: "{}") }
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
        writeState(trackingName(day), json.encodeToString(DayTracking.serializer(), _tracking.value))
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
        val contents = readState(trackingName(dayKey)) ?: return@withContext DayTracking()
        runCatching { json.decodeFromString<DayTracking>(contents) }
            .getOrElse { DayTracking() }
    }

}
