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
import androidx.compose.material.icons.outlined.Star

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = homeVmFactory(AppGraph.todoRepo))
) {
    val ui by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf(viewModel.input) }
    var inputImportant by remember { mutableStateOf(ui.inputImportant) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Today") }) }
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
                        Icon(Icons.Outlined.Star, contentDescription = "Mark important")
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
                            onRemove = { viewModel.remove(todo.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(todo: Todo, onToggle: () -> Unit, onRemove: () -> Unit) {
    val bg = if (todo.important)
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f) // soft yellow-ish (dynamic theme)
    else
        MaterialTheme.colorScheme.surface
    Surface(
        color = bg,
        tonalElevation = if (todo.important) 2.dp else 1.dp,
        shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(checked = todo.done, onCheckedChange = { onToggle() })

            // ⭐️ Important icon, if flagged
            if (todo.important) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Important",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = todo.text,
                    style = if (todo.done) MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else MaterialTheme.typography.bodyLarge
                )
            }
            TextButton(onClick = onRemove) { Text("Delete") }
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