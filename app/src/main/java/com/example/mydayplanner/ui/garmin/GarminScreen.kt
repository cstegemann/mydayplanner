package com.example.mydayplanner.ui.garmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mydayplanner.di.AppGraph
import com.example.mydayplanner.watch.GarminWatchSync
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarminScreen(
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    watchSync: GarminWatchSync = AppGraph.garminWatchSync
) {
    val attempts by watchSync.syncHistory.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Garmin sync history") },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = watchSync::sendSnapshot, modifier = Modifier.fillMaxWidth()) {
                Text("Send snapshot")
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (attempts.isEmpty()) {
                    item {
                        Text(
                            "No sync attempts during this app session.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(attempts, key = { it.id }) { attempt ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${formatSentAt(attempt.startedAtMillis)} · #${attempt.id}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    attempt.origin.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                attempt.payload?.let { payload ->
                                    Text(
                                        "Payload: " + payload.entries.joinToString(", ") { "${it.key}=${it.value}" },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                attempt.entries.forEach { entry ->
                                    Text(
                                        "${formatSentAt(entry.timestampMillis)} · ${entry.message}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SentAtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

private fun formatSentAt(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(SentAtFormatter)
