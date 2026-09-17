package com.example.mydayplanner.ui.garmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
    val snapshots by watchSync.sentSnapshots.collectAsState()
    val forcedSend by watchSync.lastForcedSend.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Garmin snapshots") },
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
                if (snapshots.isEmpty()) {
                    item {
                        Text(
                            "No snapshots have been sent during this app session.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(snapshots) { snapshot ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${formatSentAt(snapshot.sentAtMillis)} · ${snapshot.deviceName}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    snapshot.payload.entries.joinToString(", ") { "${it.key}=${it.value}" },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Last forced send", style = MaterialTheme.typography.titleSmall)
                    if (forcedSend == null) {
                        Text("Not requested yet", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(formatSentAt(forcedSend!!.startedAtMillis), style = MaterialTheme.typography.labelMedium)
                        forcedSend!!.entries.forEach { entry ->
                            Text("• $entry", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private val SentAtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun formatSentAt(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(SentAtFormatter)
