package com.example.mydayplanner.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class WeatherStatus(
    val fineUntilMillis: Long = 0L,
    val lastUpdatedMillis: Long = 0L,
    val station: String = "",
    val values: MosmixParser.WeatherValues? = null,
    val lastError: String? = null,
    val downloadBytes: Long = 0,
    val requestCount: Long = 0,
    val lastDownloadBytes: Long = 0,
    val lastHttpCode: Int? = null,
    val locationAgeMillis: Long? = null,
    val lastAttemptMillis: Long = 0L
) {
    val fine: Boolean get() = fineUntilMillis > System.currentTimeMillis()
}

/**
 * MOSMIX_L data is refreshed on a slow cadence; the 15-min worker is for checking
 * whether a new forecast or changed station actually warrants a download.
 * No background GPS permission is required: workers reuse the last saved location.
 */
class WeatherRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val mutex = Mutex()
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

    suspend fun refresh(force: Boolean = false, background: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val previous = mutable.value
                mutable.value = previous.copy(lastAttemptMillis = System.currentTimeMillis())
                try {
                    val location = (if (background) null else currentLocation()) ?: savedLocation()
                        ?: error("Open the app once with location enabled to choose a weather station")
                    val age = System.currentTimeMillis() - location.time
                    if (!background && location.time > prefs.getLong("locationTime", 0)) {
                        prefs.edit().putString("lat", location.latitude.toString())
                            .putString("lon", location.longitude.toString())
                            .putLong("locationTime", location.time).apply()
                    }
                    val stations = MosmixStations(context).candidates(location.latitude, location.longitude)
                    val closest = stations.firstOrNull() ?: error("No MOSMIX stations available")
                    val movedStation = previous.station.isNotBlank() &&
                        !previous.station.endsWith("(${closest.id})")
                    if (!force && !movedStation && previous.values != null &&
                        System.currentTimeMillis() - previous.lastUpdatedMillis < WeatherLevels.forecastMinRefreshMillis) {
                        mutable.value = mutable.value.copy(locationAgeMillis = age)
                        return@withLock true
                    }
                    var lastFailure: Exception? = null
                    for (station in stations.take(3)) {
                        try {
                            val url = "https://opendata.dwd.de/weather/local_forecasts/mos/MOSMIX_L/single_stations/${station.id}/kml/MOSMIX_L_LATEST_${station.id}.kmz"
                            val conn = URL(url).openConnection() as HttpURLConnection
                            conn.connectTimeout = 12000
                            conn.readTimeout = 20000
                            conn.setRequestProperty("User-Agent", "mydayplanner-personal/1.0")
                            try {
                                val code = conn.responseCode
                                check(code == 200) { "DWD HTTP $code at ${station.id}" }
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
                                val values = MosmixParser.parseKmz(bytes).watchValues(Instant.now())
                                mutable.value = mutable.value.copy(
                                    station = "${station.name} (${station.id})",
                                    values = values,
                                    locationAgeMillis = age,
                                    lastUpdatedMillis = System.currentTimeMillis(),
                                    lastError = null
                                )
                                return@withLock true
                            } finally { conn.disconnect() }
                        } catch (e: Exception) {
                            lastFailure = e
                        }
                    }
                    throw lastFailure ?: IllegalStateException("All nearby stations failed")
                } catch (e: Exception) {
                    mutable.value = mutable.value.copy(lastError = e.message ?: e.javaClass.simpleName)
                    false
                }
            }
        }

    private fun recordUsage(bytes: Long, code: Int) {
        val previous = mutable.value
        val total = previous.downloadBytes + bytes
        val requests = previous.requestCount + 1
        prefs.edit().putLong("bytes", total).putLong("requests", requests).apply()
        mutable.value = previous.copy(downloadBytes = total, requestCount = requests,
            lastDownloadBytes = bytes, lastHttpCode = code)
    }

    private fun savedLocation(): Location? {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("lon", null)?.toDoubleOrNull() ?: return null
        return Location("last_saved").apply {
            latitude = lat
            longitude = lon
            time = prefs.getLong("locationTime", 0)
        }
    }

    @Suppress("MissingPermission")
    private fun currentLocation(): Location? {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.getProviders(true).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }
}
