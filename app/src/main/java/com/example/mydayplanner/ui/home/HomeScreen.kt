package com.example.mydayplanner.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mydayplanner.config.Routine
import com.example.mydayplanner.config.RoutineMeasure
import com.example.mydayplanner.config.RuleEffect
import com.example.mydayplanner.config.Project
import com.example.mydayplanner.config.TaskDifficulty
import com.example.mydayplanner.config.TaskDifficultyDef
import com.example.mydayplanner.data.TodoRepository
import com.example.mydayplanner.data.models.DayTracking
import com.example.mydayplanner.data.models.Todo
import com.example.mydayplanner.di.AppGraph
import com.example.mydayplanner.ui.formatEstimate
import com.example.mydayplanner.ui.formatMinutesHM

private val TimeEstimates = listOf(15, 30, 45, 60, 90, 120, 180)

private data class TodoEditorDraft(
    val id: String?,
    val text: String,
    val important: Boolean,
    val estimate: Int,
    val project: Project,
    val liveTrackId: String?,
    val difficulty: TaskDifficulty?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = homeVmFactory(AppGraph.todoRepo))
) {
    val ui by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf(viewModel.input) }
    var editorDraft by remember { mutableStateOf<TodoEditorDraft?>(null) }
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            viewModel.configureSharedFolder(uri)
        }
    }

    val freeDayMode = ui.tracking.isFreeDayMode()
    val freeDayBackground = Color(0xFF6FAF46)

    Scaffold(
        containerColor = if (freeDayMode) freeDayBackground else MaterialTheme.colorScheme.background,
        topBar = {
            if (!ui.isLoading) {
                MultiUseTopBar(
                    todos = ui.todos,
                    onOpenHistory = onOpenHistory,
                    tracking = ui.tracking,
                    viewModel = viewModel,
                    freeDayMode = freeDayMode,
                    freeDayBackground = freeDayBackground
                )
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (ui.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Loading planner…", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                if (!freeDayMode) {
                    DifficultyMixBar(ui.todos)
                }
                RuleNotices(ui.ruleNotices)
                if (ui.storageMessage != null || ui.sharedFolderUri == null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ui.storageMessage ?: "Shared folder not configured", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { folderPicker.launch(null) }) { Text(if (ui.sharedFolderUri == null) "Choose folder" else "Reconnect") }
                        if (ui.sharedFolderUri != null) TextButton(onClick = viewModel::refreshSharedData) { Text("Refresh") }
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                ) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().clickable { viewModel.setRoutinesCollapsed(!ui.routineProgress.collapsed) }.padding(vertical = 6.dp)) {
                            Text(if (ui.routineProgress.collapsed) "Routines ▸" else "Routines ▾", style = MaterialTheme.typography.titleLarge)
                            if (ui.routineProgress.collapsed) {
                                val daytype = if (freeDayMode) "free" else "work"
                                val summary = ui.config?.routines
                                    ?.filter { it.daytypes.isEmpty() || daytype in it.daytypes }
                                    ?.groupBy { it.area }?.map { (area, routines) ->
                                    val percent = routineProgressPercent(routines, ui.routineProgress)
                                    "$area $percent%"
                                }.orEmpty()
                                if (summary.isNotEmpty()) Text(summary.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (!ui.routineProgress.collapsed) {
                        val daytype = if (freeDayMode) "free" else "work"
                        ui.config?.routines?.filter { it.daytypes.isEmpty() || daytype in it.daytypes }?.forEach { routine ->
                            item(key = "routine-${routine.id}") {
                                RoutineRow(routine, ui.routineProgress.values[routine.id] ?: 0,
                                    areaIcon = ui.config?.areas?.firstOrNull { it.id == routine.area }?.icon,
                                    onIncrement = { viewModel.changeRoutine(routine.id, when (routine.measure) { RoutineMeasure.MINUTES -> 30; RoutineMeasure.COUNT -> 1; RoutineMeasure.BOOLEAN -> if ((ui.routineProgress.values[routine.id] ?: 0) > 0) -1 else 1 }) },
                                    onDecrement = { viewModel.changeRoutine(routine.id, if (routine.measure == RoutineMeasure.MINUTES) -30 else -1) })
                            }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); Text("Tasks", style = MaterialTheme.typography.titleLarge) }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (input.trim().isNotEmpty()) {
                            editorDraft = TodoEditorDraft(
                                id = null,
                                text = input,
                                important = false,
                                estimate = 15,
                                project = Project.Other,
                                liveTrackId = null,
                                difficulty = TaskDifficulty.TediousNormal
                            )
                        }
                    }) { Text("Add") }
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = input,
                        onValueChange = { input = it; viewModel.onInputChange(it) },
                        placeholder = { Text("Add a task…") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                            if (input.trim().isNotEmpty()) {
                                editorDraft = TodoEditorDraft(
                                    id = null,
                                    text = input,
                                    important = false,
                                    estimate = 15,
                                    project = Project.Other,
                                    liveTrackId = null,
                                    difficulty = TaskDifficulty.TediousNormal
                                )
                            }
                        })
                    )
                } }
                item { Spacer(Modifier.height(8.dp)) }
                if (ui.configError != null) {
                    item { Text(ui.configError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
                } else if (ui.todos.isEmpty()) {
                    item { Text("No tasks yet. Add your first one!", style = MaterialTheme.typography.bodyMedium) }
                } else {
                    val today = java.time.LocalDate.now()
                    val groups = listOf("Active" to ui.todos.filter { !it.done && !it.isDeferred(today) }, "Done" to ui.todos.filter { it.done }, "Pushed" to ui.todos.filter { !it.done && it.isDeferred(today) })
                    groups.forEach { (title, entries) ->
                        if (entries.isNotEmpty()) item { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                        items(entries, key = { it.id }) { todo ->
                            TodoRow(
                                todo = todo,
                                projectIcon = ui.config?.projects?.firstOrNull { it.id == todo.liveTrackId }?.icon,
                                onToggle = { viewModel.toggle(todo.id) },
                                onOpenEditor = {
                                    editorDraft = TodoEditorDraft(
                                        id = todo.id,
                                        text = todo.text,
                                        important = todo.important,
                                        estimate = todo.estimateMinutes,
                                        project = todo.project,
                                        liveTrackId = todo.liveTrackId,
                                        difficulty = todo.difficulty
                                    )
                                }
                            )
                        }
                    }
                }
                }
            }
        }
    }

    editorDraft?.let { draft ->
        TodoEditorDialog(
            draft = draft,
            isNew = draft.id == null,
            onDismiss = { editorDraft = null },
            onDelete = {
                draft.id?.let { viewModel.remove(it) }
                editorDraft = null
            },
            activeTracks = ui.liveTracks.filter { it.active },
            onPushBack = { days ->
                draft.id?.let { viewModel.pushBack(it, days) }
                editorDraft = null
            },
            onSave = { edited ->
                if (edited.id == null) {
                    viewModel.addTodo(
                        text = edited.text,
                        important = edited.important,
                        estimateMinutes = edited.estimate,
                        project = edited.project,
                        liveTrackId = edited.liveTrackId,
                        difficulty = edited.difficulty
                    )
                    input = ""
                } else {
                    val original = ui.todos.firstOrNull { it.id == edited.id } ?: return@TodoEditorDialog
                    viewModel.updateTodo(
                        original.copy(
                            text = edited.text,
                            important = edited.important,
                            estimateMinutes = edited.estimate,
                            project = edited.project,
                            liveTrackId = edited.liveTrackId,
                            difficulty = edited.difficulty
                        )
                    )
                }
                editorDraft = null
            }
        )
    }
}

@Composable
private fun DifficultyMixBar(todos: List<Todo>) {
    val today = java.time.LocalDate.now()
    val visibleTodos = todos.filter { it.project != Project.META }
    val totalPlannedMinutes = visibleTodos.sumOf { it.estimateMinutes }
    if (totalPlannedMinutes <= 0) return

    val segments = buildList {
        TaskDifficulty.entries.forEach { diff ->
            val minutes = visibleTodos
                .asSequence()
                .filter { !it.done && !it.isDeferred(today) && it.difficulty == diff }
                .sumOf { it.estimateMinutes }
            if (minutes > 0) add(TaskDifficultyDef.byDifficulty.getValue(diff).color to minutes)
        }
        val doneMinutes = visibleTodos.asSequence().filter { it.done }.sumOf { it.estimateMinutes }
        if (doneMinutes > 0) add(TaskDifficultyDef.doneColor to doneMinutes)
    }

    if (segments.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(12.dp)
            .clip(MaterialTheme.shapes.small)
    ) {
        segments.forEach { (color, minutes) ->
            Box(
                modifier = Modifier
                    .weight(minutes.toFloat())
                    .fillMaxSize()
                    .background(color)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun RuleNotices(notices: List<RuleNotice>) {
    if (notices.isEmpty()) return
    Text(
        text = notices.joinToString(" | ") { n -> (if (n.effect == RuleEffect.REPLAN) "↻ " else if (n.effect == RuleEffect.WARNING) "⚠ " else "ℹ ") + n.message },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
    )
    Spacer(Modifier.height(8.dp))
}

@Composable private fun RoutineRow(routine: Routine, value: Int, areaIcon: String?, onIncrement: () -> Unit, onDecrement: () -> Unit) {
    val complete = value >= routine.target
    Surface(color = if (complete) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onIncrement)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (areaIcon != null) {
                Text(areaIcon)
                Spacer(Modifier.width(8.dp))
            }
            if (routine.measure == RoutineMeasure.BOOLEAN) Checkbox(complete, onCheckedChange = { onIncrement() })
            Text(routine.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (routine.measure != RoutineMeasure.BOOLEAN) {
                Text(when(routine.measure) { RoutineMeasure.COUNT -> "$value/${routine.target}"; RoutineMeasure.MINUTES -> "$value/${routine.target} min"; RoutineMeasure.BOOLEAN -> "" })
            }
            if (routine.measure != RoutineMeasure.BOOLEAN) TextButton(onClick = onDecrement, enabled = value > 0) { Text("−") }
        }
    }
}

private fun DayTracking.isFreeDayMode(): Boolean = current == Project.FREE_DAY

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiUseTopBar(
    todos: List<Todo>,
    onOpenHistory: () -> Unit,
    tracking: DayTracking,
    viewModel: HomeViewModel,
    freeDayMode: Boolean,
    freeDayBackground: Color
) {
    val workedMinutes by remember(todos) {
        derivedStateOf {
            todos.asSequence().filter { it.done && it.project != Project.META }.sumOf { it.estimateMinutes }
        }
    }

    val doneText = if (workedMinutes > 0) formatMinutesHM(workedMinutes) else "Let's go!"

    val remainingMinutes by remember(todos) {
        derivedStateOf {
            val today = java.time.LocalDate.now()
            todos.asSequence()
                .filter { !it.done && !it.isDeferred(today) && it.project != Project.META }
                .sumOf { it.estimateMinutes }
        }
    }

    val remainingText = if (remainingMinutes > 0) formatMinutesHM(remainingMinutes) else "0:00 🎉"

    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = if (freeDayMode) freeDayBackground else MaterialTheme.colorScheme.surface
        ),
        title = {
            if (freeDayMode) {
                Text("Free day 🌿")
            } else {
                Text("$doneText / $remainingText")
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Filled.Menu, contentDescription = "History")
            }
        },
        actions = {
            Text("Free day", style = MaterialTheme.typography.labelMedium)
            Switch(checked = freeDayMode, onCheckedChange = {
                viewModel.onSelectCurrentProject(if (it) Project.FREE_DAY else null)
            })
        }
    )
}

@Composable
private fun TodoRow(
    todo: Todo,
    projectIcon: String?,
    onToggle: () -> Unit,
    onOpenEditor: () -> Unit
) {
    val deferred = todo.isDeferred(java.time.LocalDate.now())
    val bg = if (todo.important) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    else if (deferred) Color.Gray
    else MaterialTheme.colorScheme.surface

    val difficultyColor = todo.difficulty?.let { TaskDifficultyDef.byDifficulty[it]?.color }

    Surface(
        modifier = Modifier.clickable(onClick = onOpenEditor),
        color = bg,
        tonalElevation = if (todo.important) 2.dp else 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(difficultyColor ?: Color.Transparent)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Checkbox(checked = todo.done, onCheckedChange = { onToggle() })
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (todo.important) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "Important",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        if (projectIcon != null) Text(projectIcon)

                        Text(
                            text = todo.text,
                            modifier = Modifier.weight(1f),
                            style = if (todo.done)
                                MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else MaterialTheme.typography.bodyLarge
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (todo.project != Project.META) {
                            Text(
                                text = formatEstimate(todo.estimateMinutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = todo.trackLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (deferred) Text("Back ${todo.deferredUntil ?: "tomorrow"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoEditorDialog(
    draft: TodoEditorDraft,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    activeTracks: List<com.example.mydayplanner.data.models.LiveTrack>,
    onPushBack: (Int) -> Unit,
    onSave: (TodoEditorDraft) -> Unit
) {
    var text by remember(draft) { mutableStateOf(draft.text) }
    var important by remember(draft) { mutableStateOf(draft.important) }
    var estimate by remember(draft) { mutableIntStateOf(draft.estimate) }
    var project by remember(draft) { mutableStateOf(draft.project) }
    var liveTrackId by remember(draft) { mutableStateOf(draft.liveTrackId) }
    var pushDays by remember(draft) { mutableIntStateOf(1) }
    var selectedDifficulty by remember(draft) { mutableStateOf(draft.difficulty ?: TaskDifficulty.TediousNormal) }

    fun save() = onSave(
        draft.copy(
            text = text.trim(),
            important = important,
            estimate = estimate,
            project = project,
            liveTrackId = liveTrackId,
            difficulty = selectedDifficulty
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = { if (text.isNotBlank()) save() }) { Text("Save") } },
        dismissButton = {
            if (!isNew) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                }
            }
        },
        title = { Text(if (isNew) "New task" else "Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Task") },
                    singleLine = true
                )
                DifficultyQuestions(selectedDifficulty) { tapped ->
                    if (selectedDifficulty == tapped) {
                        if (text.isNotBlank()) save()
                    } else {
                        selectedDifficulty = tapped
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var estExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = estExpanded,
                        onExpandedChange = { estExpanded = !estExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = formatEstimate(estimate),
                            onValueChange = {},
                            label = { Text("Estimate") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = estExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = estExpanded, onDismissRequest = { estExpanded = false }) {
                            TimeEstimates.forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text(formatEstimate(mins)) },
                                    onClick = { estExpanded = false; estimate = mins }
                                )
                            }
                        }
                    }

                    var projExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = projExpanded,
                        onExpandedChange = { projExpanded = !projExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = liveTrackId ?: project.displayName,
                            onValueChange = {},
                            label = { Text("Project") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = projExpanded, onDismissRequest = { projExpanded = false }) {
                            activeTracks.forEach { track ->
                                DropdownMenuItem(
                                    text = { Text(track.name) },
                                    onClick = { projExpanded = false; project = Project.Other; liveTrackId = track.id }
                                )
                            }
                            if (activeTracks.none { it.id == "other" }) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Other") },
                                    onClick = { projExpanded = false; project = Project.Other; liveTrackId = null }
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconToggleButton(checked = important, onCheckedChange = { important = it }) {
                        if (important) {
                            Icon(Icons.Filled.Star, contentDescription = "Important")
                        } else {
                            Icon(Icons.Outlined.Star, contentDescription = "Mark important", tint = Color.LightGray)
                        }
                    }
                    Text("Important")
                }
                if (!isNew) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var pushExpanded by remember { mutableStateOf(false) }
                        Button(onClick = { onPushBack(pushDays) }) { Text("Push back") }
                        ExposedDropdownMenuBox(expanded = pushExpanded, onExpandedChange = { pushExpanded = !pushExpanded }) {
                            OutlinedTextField(
                                readOnly = true,
                                value = "$pushDays d",
                                onValueChange = {},
                                label = { Text("For") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pushExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).width(110.dp)
                            )
                            ExposedDropdownMenu(expanded = pushExpanded, onDismissRequest = { pushExpanded = false }) {
                                listOf(1, 2, 3, 5, 7).forEach { days ->
                                    DropdownMenuItem(text = { Text("$days days") }, onClick = { pushDays = days; pushExpanded = false })
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun DifficultyQuestions(
    selected: TaskDifficulty?,
    onSelected: (TaskDifficulty) -> Unit
) {
    Text("Difficulty")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val effective = selected ?: TaskDifficulty.TediousNormal
        val isFun = effective == TaskDifficulty.FunNormal || effective == TaskDifficulty.FunDraining
        val isDraining = effective == TaskDifficulty.FunDraining || effective == TaskDifficulty.TediousDraining

        Text(TaskDifficultyDef.funQuestionLabel, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DifficultyTile(
                modifier = Modifier.weight(1f),
                title = TaskDifficultyDef.tediousLabel,
                color = TaskDifficultyDef.pickerCautionColor,
                selected = !isFun,
                onSelected = {
                    val next = if (isDraining) TaskDifficulty.TediousDraining else TaskDifficulty.TediousNormal
                    onSelected(next)
                }
            )
            DifficultyTile(
                modifier = Modifier.weight(1f),
                title = TaskDifficultyDef.funLabel,
                color = TaskDifficultyDef.pickerPositiveColor,
                selected = isFun,
                onSelected = {
                    val next = if (isDraining) TaskDifficulty.FunDraining else TaskDifficulty.FunNormal
                    onSelected(next)
                }
            )
        }

        Text(TaskDifficultyDef.drainingQuestionLabel, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DifficultyTile(
                modifier = Modifier.weight(1f),
                title = TaskDifficultyDef.normalLabel,
                color = TaskDifficultyDef.pickerPositiveColor,
                selected = !isDraining,
                onSelected = {
                    val next = if (isFun) TaskDifficulty.FunNormal else TaskDifficulty.TediousNormal
                    onSelected(next)
                }
            )
            DifficultyTile(
                modifier = Modifier.weight(1f),
                title = TaskDifficultyDef.drainingLabel,
                color = TaskDifficultyDef.pickerCautionColor,
                selected = isDraining,
                onSelected = {
                    val next = if (isFun) TaskDifficulty.FunDraining else TaskDifficulty.TediousDraining
                    onSelected(next)
                }
            )
        }
    }
}

@Composable
private fun DifficultyTile(
    modifier: Modifier = Modifier,
    title: String,
    color: Color,
    selected: Boolean,
    onSelected: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(58.dp)
            .clickable { onSelected() },
        color = color.copy(alpha = if (selected) 1f else 0.7f),
        contentColor = if (color.luminance() > 0.45f) Color(0xFF151515) else Color.White,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
        shape = MaterialTheme.shapes.small
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun homeVmFactory(repo: TodoRepository): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repo) as T
        }
    }
