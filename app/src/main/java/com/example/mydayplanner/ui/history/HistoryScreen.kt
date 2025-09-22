package com.example.mydayplanner.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.mydayplanner.data.TodoRepository
import com.example.mydayplanner.di.AppGraph
import com.example.mydayplanner.ui.formatEstimate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HistoryViewModel = viewModel(factory = historyVmFactory(AppGraph.todoRepo))
) {
    LaunchedEffect(Unit) { vm.load() }
    val days by vm.days.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("History (28 days)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = modifier
                .padding(pad)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(days, key = { it.dayKey }) { day ->
                DayBlock(day)
            }
        }
    }
}

@Composable
private fun DayBlock(day: DayHistory) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Big header
        Text(
            text = "${day.dayKey} • ${day.weekdayShort}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))

        if (day.groups.isEmpty()) {
            Text(
                text = "No completed tasks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Simple bulleted list with estimates
            day.groups.forEachIndexed { idx, group ->
                if (idx > 0) Spacer(Modifier.height(6.dp))
                // Project subheader
                Text(
                    text = group.project.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                // Items
                group.items.forEach { t ->
                    val est = formatEstimate(t.estimateMinutes)
                    Text("• ${t.text}  —  $est", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun historyVmFactory(repo: TodoRepository): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repo) as T
        }
    }