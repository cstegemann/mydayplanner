package com.example.mydayplanner.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mydayplanner.data.TodoRepository
import com.example.mydayplanner.data.models.Todo
import com.example.mydayplanner.di.AppGraph
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color

private val TimeEstimates = listOf(15, 30, 45, 60, 90, 120, 180)
private fun formatEstimate(mins: Int) = when (mins) {
    60 -> "1 h"
    90 -> "1.5 h"
    120 -> "2 h"
    180 -> "3 h"
    else -> "$mins min"
}

private val CurrentProjects = listOf("Other", "TIME", "GW")




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = homeVmFactory(AppGraph.todoRepo))
) {
    val ui by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf(viewModel.input) }
    var inputImportant by remember { mutableStateOf(ui.inputImportant) }
    var estimate by remember { mutableIntStateOf(ui.inputEstimateMinutes) }
    var project by remember { mutableStateOf(ui.inputProject) }

    Scaffold(
        //topBar = { CenterAlignedTopAppBar(title = { Text("Today") }) }
        topBar = { RemainingHoursTopBar(ui.todos) }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // Input row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconToggleButton(
                    checked = inputImportant,
                    onCheckedChange = {
                        inputImportant = it
                        viewModel.onToggleImportantInput()
                    }
                ) {
                    if (inputImportant)
                        Icon(Icons.Filled.Star, contentDescription = "Important")
                    else
                        Icon(
                            Icons.Outlined.Star,
                            contentDescription = "Mark important",
                            tint= Color.LightGray
                        )
                }
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = input,
                    onValueChange = { input = it; viewModel.onInputChange(it) },
                    placeholder = { Text("Add a task…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.add(); input = "" })
                )
                Button(onClick = { viewModel.add(); input = "" }) { Text("Add") }
            }
            Spacer(Modifier.height(8.dp))
            // Row: two exposed dropdowns (Estimate, Project)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // --- Estimate ---
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
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = estExpanded,
                        onDismissRequest = { estExpanded = false }
                    ) {
                        TimeEstimates.forEach { mins ->
                            DropdownMenuItem(
                                text = { Text(formatEstimate(mins)) },
                                onClick = {
                                    estExpanded = false
                                    estimate = mins
                                    viewModel.onSetEstimate(mins)
                                }
                            )
                        }
                    }
                }

                // --- Project ---
                var projExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = projExpanded,
                    onExpandedChange = { projExpanded = !projExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = project,
                        onValueChange = {},
                        label = { Text("Project") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = projExpanded,
                        onDismissRequest = { projExpanded = false }
                    ) {
                        CurrentProjects.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    projExpanded = false
                                    project = p
                                    viewModel.onSetProject(p)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // List
            if (ui.todos.isEmpty()) {
                Text("No tasks yet. Add your first one!", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.todos, key = { it.id }) { todo ->
                        TodoRow(
                            todo = todo,
                            onToggle = { viewModel.toggle(todo.id) },
                            onRemove = { viewModel.remove(todo.id) },
                            onPushToTomorrow = {viewModel.togglePushToTomorrow(todo.id)}
                        )
                    }
                }
            }
        }
    }
}

private fun formatMinutesHM(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "$h:${m.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemainingHoursTopBar(todos: List<Todo>) {
    // Recompute whenever `todos` changes
    val workedMinutes by remember(todos) {
        derivedStateOf {
            todos.asSequence()
                .filter { it.done && it.project != "META" }
                .sumOf { it.estimateMinutes }
        }
    }

    val doneText = if (workedMinutes > 0)
        "${formatMinutesHM(workedMinutes)} h done"
    else
        "Let's start!"

    val remainingMinutes by remember(todos) {
        derivedStateOf {
            todos.asSequence()
                .filter { !it.done && !it.pushedToTomorrow && it.project !in listOf("META", "Other") }
                .sumOf { it.estimateMinutes }
        }
    }

    val remainingText = if (remainingMinutes > 0)
        "${formatMinutesHM(remainingMinutes)} h left"
    else
        "All done 🎉"

    CenterAlignedTopAppBar(
        title = {
            Text("$doneText • $remainingText")
        }
    )
}

@Composable
private fun TodoRow(todo: Todo, onToggle: () -> Unit,
                    onRemove: () -> Unit,
                    onPushToTomorrow: () -> Unit) {
    val bg = if (todo.important)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) // soft yellow-ish (dynamic theme)
    else if (todo.pushedToTomorrow)
        Color.Gray
    else
        MaterialTheme.colorScheme.surface
    Surface(
        color = bg,
        tonalElevation = if (todo.important) 2.dp else 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp) // ← padding applies to both rows now
        ) {
            // ── Row 1: checkbox + star + main text ───────────────────────────────
            Column(
            ) {
                Checkbox(checked = todo.done, onCheckedChange = { onToggle() })
            }
            Column(
                Modifier.fillMaxWidth(),
            ) {
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

                    // Main text expands to take the remaining space
                    Text(
                        text = todo.text,
                        modifier = Modifier.weight(1f),
                        style = if (todo.done)
                            MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else
                            MaterialTheme.typography.bodyLarge
                    )

                    // Optional delete button/icon
                    IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
                }

                // ── Spacer between lines ─────────────────────────────────────────────
                Spacer(Modifier.height(6.dp))

                // ── Row 2: small metadata line (estimate • project) ─────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // keep these compact & subtle
                    if (todo.project != "META") {
                        Text(
                            text = formatEstimate(todo.estimateMinutes), // e.g., "15 min" / "1.5 h"
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = todo.project,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f) // let it ellipsize if long
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onPushToTomorrow) {
                        if (todo.pushedToTomorrow)
                            Text("do today")
                        else
                            Text("not today")
                    }
                }
            }
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
/*
//@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DismissibleTodoRow(
    todo: Todo,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onPushToTomorrow: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState (
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.StartToEnd || it == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true // consume
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove item",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Red)
                            .wrapContentSize(Alignment.CenterEnd)
                            .padding(12.dp),
                        tint = Color.White
                    )
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove item",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Red)
                            .wrapContentSize(Alignment.CenterEnd)
                            .padding(12.dp),
                        tint = Color.White
                    )
                }
                SwipeToDismissBoxValue.Settled -> {}
            }
        }
    ){
        TodoRow(todo = todo, onToggle = onToggle, onRemove = onRemove)
    }
}
*/