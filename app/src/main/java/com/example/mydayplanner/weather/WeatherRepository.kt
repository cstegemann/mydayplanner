package com.example.mydayplanner.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.math.roundToInt

data class WeatherStatus(
    val fineUntilMillis: Long = 0L,
    val lastUpdatedMillis: Long = 0L,
    val station: String = "",
    val values: MosmixParser.WeatherValues? = null,
    val lastError: String? = null,
    val downloadBytes: Long = 0,
    val requestCount: Long = 0,
    val lastDownloadBytes: Long = 0,
    val lastHttpCode: Int? = null
) {
    val fine: Boolean get() = fineUntilMillis > System.currentTimeMillis()
}

/** Private-app implementation: MOSMIX_L now; radar is deliberately NOT activated by fine mode yet. */
class WeatherRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(WeatherStatus(
        fineUntilMillis = prefs.getLong("fineUntil", 0),
        downloadBytes = prefs.getLong("bytes", 0),
        requestCount = prefs.getLong("requests", 0)
    ))
    val status: StateFlow<WeatherStatus> = mutable

    fun setFineHours(hours: Int) {
        val deadline = if (hours <= 0) 0L else System.currentTimeMillis() + hours * 3_600_000L
        prefs.edit().putLong("fineUntil", deadline).apply()
        mutable.value = mutable.value.copy(fineUntilMillis = deadline)
    }

    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        val before = mutable.value
        if (!force && System.currentTimeMillis() - before.lastUpdatedMillis < 3_600_000L) return@withContext
        try {
            val location = currentLocation() ?: error("Location permission or last known location unavailable")
            // Initial catalogue: expand with the official DWD station catalogue in a subsequent change.
            val stations = listOf(
                MosmixParser.Station("10338", "Hannover", 52.46, 9.68),
                MosmixParser.Station("10147", "Hamburg", 53.63, 10.00),
                MosmixParser.Station("10382", "Berlin", 52.56, 13.31),
                MosmixParser.Station("10866", "Muenchen", 48.35, 11.79),
                MosmixParser.Station("10637", "Frankfurt", 50.05, 8.60),
                MosmixParser.Station("10400", "Duesseldorf", 51.29, 6.77)
            )
            val closest = stations.minBy { station ->
                val dLat = location.latitude - station.latitude
                val dLon = (location.longitude - station.longitude) * 0.65
                dLat * dLat + dLon * dLon
            }
            val url = "https://opendata.dwd.de/weather/local_forecasts/mos/MOSMIX_L/single_stations/${closest.id}/kml/MOSMIX_L_LATEST_${closest.id}.kmz"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "mydayplanner-personal/1.0")
            try {
                val code = conn.responseCode
                check(code == 200) { "DWD HTTP $code" }
                val bytes = conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(out.size() + count <= 2_000_000) { "Unexpectedly large MOSMIX response" }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
                recordUsage(bytes.size.toLong(), code)
                val forecast = MosmixParser.parseKmz(bytes)
                val values = forecast.watchValues(Instant.now())
                mutable.value = mutable.value.copy(
                    station = "${closest.name} (${closest.id})",
                    values = values,
                    lastUpdatedMillis = System.currentTimeMillis(),
                    lastError = null
                )
            } finally { conn.disconnect() }
        } catch (e: Exception) {
            mutable.value = mutable.value.copy(lastError = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun recordUsage(bytes: Long, code: Int) {
        val previous = mutable.value
        val total = previous.downloadBytes + bytes
        val requests = previous.requestCount + 1
        prefs.edit().putLong("bytes", total).putLong("requests", requests).apply()
        mutable.value = previous.copy(downloadBytes = total, requestCount = requests, lastDownloadBytes = bytes, lastHttpCode = code)
    }

    @Suppress("MissingPermission")
    private fun currentLocation(): android.location.Location? {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.getProviders(true).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }
}
