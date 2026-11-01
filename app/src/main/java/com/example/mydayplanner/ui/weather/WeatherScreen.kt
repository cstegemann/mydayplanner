package com.example.mydayplanner.ui.weather

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mydayplanner.di.AppGraph
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(onOpenMenu: () -> Unit) {
    val repo = AppGraph.weatherRepository
    val status by repo.status.collectAsState()
    val scope = rememberCoroutineScope()
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("Weather diagnostics") }, navigationIcon = {
            IconButton(onClick = onOpenMenu) { Icon(Icons.Filled.Menu, contentDescription = "Open menu") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mode: ${if (status.fine) "Fine (requested)" else "Coarse"}", style = MaterialTheme.typography.titleMedium)
            Text("Fine mode currently changes the requested duration only. Radar/5-minute nowcasting is not installed yet.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 2, 4).forEach { hours ->
                    OutlinedButton(onClick = { repo.setFineHours(hours) }) { Text(if (hours == 0) "Off" else "${hours}h") }
                }
            }
            if (status.fine) Text("Expires: ${format(status.fineUntilMillis)}")
            Button(onClick = { scope.launch { repo.refresh(force = true) } }) { Text("Refresh DWD now") }
            HorizontalDivider()
            Text("Provider: DWD MOSMIX_L (forecast)", style = MaterialTheme.typography.titleMedium)
            Text("Station: ${status.station.ifBlank { "Not selected" }}")
            Text("Last successful refresh: ${format(status.lastUpdatedMillis)}")
            Text("Last refresh attempt: ${format(status.lastAttemptMillis)}")
            Text("Background checks: every ~15 min (Android may defer)")
            Text("MOSMIX minimum download interval: ~5 hours, or when station changes")
            Text("Saved location age: ${status.locationAgeMillis?.let { "${it / 60000} min" } ?: "—"}")
            status.lastError?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
            status.values?.let { v ->
                Text("Temperature: ${v.temperatureC ?: "—"} °C")
                Text("3h delta: ${v.delta3h ?: "—"} °C")
                Text("Rain: ${v.rain ?: "—"} (mm/h: ${v.rawRainMmPerHour ?: "—"})")
                Text("Wind: ${v.wind ?: "—"} (gust m/s: ${v.rawGustMetersPerSecond ?: "—"})")
                Text("Thunder: ${v.thunder ?: "—"} (3h probability %: ${v.rawThunderProbabilityPercent ?: "—"})")
            }
            HorizontalDivider()
            Text("Download accounting", style = MaterialTheme.typography.titleMedium)
            Text("Successful downloads: ${status.requestCount}")
            Text("Last payload: ${status.lastDownloadBytes} bytes")
            Text("Total payload: ${status.downloadBytes} bytes (${"%.2f".format(status.downloadBytes / 1_048_576.0)} MiB)")
            Text("Last HTTP status: ${status.lastHttpCode ?: "—"}")
            Text("Accounting measures response-body bytes received by this weather provider, not TLS/HTTP overhead or Garmin traffic.")
            Text("Warnings, radar, KONRAD and international fallback are not yet implemented.")
        }
    }
}

private fun format(epochMillis: Long): String =
    if (epochMillis == 0L) "Never"
    else DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))
