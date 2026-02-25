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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
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

    Scaffold(topBar = { MultiUseTopBar(ui.todos, onOpenHistory, ui.tracking, viewModel) }) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            DifficultyMixBar(ui.todos)
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (input.trim().isNotEmpty()) {
                            editorDraft = TodoEditorDraft(
                                id = null,
                                text = input,
                                important = false,
                                estimate = 15,
                                project = Project.Other,
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
                                    difficulty = TaskDifficulty.TediousNormal
                                )
                            }
                        })
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (ui.todos.isEmpty()) {
                    Text("No tasks yet. Add your first one!", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ui.todos, key = { it.id }) { todo ->
                            TodoRow(
                                todo = todo,
                                onToggle = { viewModel.toggle(todo.id) },
                                onPushToTomorrow = { viewModel.togglePushToTomorrow(todo.id) },
                                onOpenEditor = {
                                    editorDraft = TodoEditorDraft(
                                        id = todo.id,
                                        text = todo.text,
                                        important = todo.important,
                                        estimate = todo.estimateMinutes,
                                        project = todo.project,
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

    editorDraft?.let { draft ->
        TodoEditorDialog(
            draft = draft,
            isNew = draft.id == null,
            onDismiss = { editorDraft = null },
            onDelete = {
                draft.id?.let { viewModel.remove(it) }
                editorDraft = null
            },
            onSave = { edited ->
                if (edited.id == null) {
                    viewModel.addTodo(
                        text = edited.text,
                        important = edited.important,
                        estimateMinutes = edited.estimate,
                        project = edited.project,
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
    val visibleTodos = todos.filter { it.project != Project.META }
    val total = visibleTodos.size
    if (total == 0) return

    val segments = buildList {
        TaskDifficulty.entries.forEach { diff ->
            val count = visibleTodos.count { !it.done && it.difficulty == diff }
            if (count > 0) add(TaskDifficultyDef.byDifficulty.getValue(diff).color to count)
        }
        val doneCount = visibleTodos.count { it.done }
        if (doneCount > 0) add(TaskDifficultyDef.doneColor to doneCount)
    }

    if (segments.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(MaterialTheme.shapes.small)
    ) {
        segments.forEach { (color, count) ->
            Box(
                modifier = Modifier
                    .weight(count.toFloat())
                    .fillMaxSize()
                    .background(color)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiUseTopBar(todos: List<Todo>, onOpenHistory: () -> Unit, tracking: DayTracking, viewModel: HomeViewModel) {
    val workedMinutes by remember(todos) {
        derivedStateOf {
            todos.asSequence().filter { it.done && it.project != Project.META }.sumOf { it.estimateMinutes }
        }
    }

    val doneText = if (workedMinutes > 0) formatMinutesHM(workedMinutes) else "Let's go!"

    val remainingMinutes by remember(todos) {
        derivedStateOf {
            todos.asSequence()
                .filter { !it.done && !it.pushedToTomorrow && it.project in Project.timedList }
                .sumOf { it.estimateMinutes }
        }
    }

    val remainingText = if (remainingMinutes > 0) formatMinutesHM(remainingMinutes) else "0:00 🎉"

    CenterAlignedTopAppBar(
        title = { Text("$doneText / $remainingText") },
        navigationIcon = {
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Filled.Menu, contentDescription = "History")
            }
        },
        actions = {
            var expanded by remember { mutableStateOf(false) }
            val current = tracking.current
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                val label = current?.displayName ?: "----"
                OutlinedTextField(
                    readOnly = true,
                    value = label,
                    onValueChange = {},
                    label = { Text("Current") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .widthIn(min = 60.dp, max = 120.dp)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("----") }, onClick = { expanded = false; viewModel.onSelectCurrentProject(null) })
                    HorizontalDivider()
                    Project.pickerList.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            onClick = { expanded = false; viewModel.onSelectCurrentProject(p) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun TodoRow(
    todo: Todo,
    onToggle: () -> Unit,
    onPushToTomorrow: () -> Unit,
    onOpenEditor: () -> Unit
) {
    val bg = if (todo.important) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    else if (todo.pushedToTomorrow) Color.Gray
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
                            text = todo.project.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onPushToTomorrow) {
                            Text(if (todo.pushedToTomorrow) "do today" else "not today")
                        }
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
    onSave: (TodoEditorDraft) -> Unit
) {
    var text by remember(draft) { mutableStateOf(draft.text) }
    var important by remember(draft) { mutableStateOf(draft.important) }
    var estimate by remember(draft) { mutableIntStateOf(draft.estimate) }
    var project by remember(draft) { mutableStateOf(draft.project) }
    var selectedDifficulty by remember(draft) { mutableStateOf(draft.difficulty ?: TaskDifficulty.TediousNormal) }

    fun save() = onSave(
        draft.copy(
            text = text.trim(),
            important = important,
            estimate = estimate,
            project = project,
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
                            value = project.displayName,
                            onValueChange = {},
                            label = { Text("Project") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = projExpanded, onDismissRequest = { projExpanded = false }) {
                            Project.pickerList.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.displayName) },
                                    onClick = { projExpanded = false; project = p }
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
                title = TaskDifficultyDef.tediousLabel,
                color = TaskDifficultyDef.byDifficulty.getValue(TaskDifficulty.TediousNormal).color,
                selected = !isFun,
                onSelected = {
                    val next = if (isDraining) TaskDifficulty.TediousDraining else TaskDifficulty.TediousNormal
                    onSelected(next)
                }
            )
            DifficultyTile(
                title = TaskDifficultyDef.funLabel,
                color = TaskDifficultyDef.byDifficulty.getValue(TaskDifficulty.FunNormal).color,
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
                title = TaskDifficultyDef.normalLabel,
                color = TaskDifficultyDef.byDifficulty.getValue(TaskDifficulty.TediousNormal).color,
                selected = !isDraining,
                onSelected = {
                    val next = if (isFun) TaskDifficulty.FunNormal else TaskDifficulty.TediousNormal
                    onSelected(next)
                }
            )
            DifficultyTile(
                title = TaskDifficultyDef.drainingLabel,
                color = TaskDifficultyDef.byDifficulty.getValue(TaskDifficulty.TediousDraining).color,
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
    title: String,
    color: Color,
    selected: Boolean,
    onSelected: () -> Unit
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .clickable { onSelected() },
        color = color.copy(alpha = if (selected) 1f else 0.7f),
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
