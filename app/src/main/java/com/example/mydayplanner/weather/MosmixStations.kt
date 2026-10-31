package com.example.mydayplanner.weather

import android.content.Context
import android.location.Location
import java.net.HttpURLConnection
import java.net.URL

/** DWD MOSMIX station catalogue, cached locally. Never sends GPS coordinates to DWD. */
class MosmixStations(private val context: Context) {
    private val file = java.io.File(context.filesDir, "mosmix_stations.cfg")
    private val url = "https://www.dwd.de/DE/leistungen/met_verfahren_mosmix/mosmix_stationskatalog.cfg?__blob=publicationFile&v=3"
    private val fallback = listOf(
        MosmixParser.Station("10338", "Hannover", 52.46, 9.68),
        MosmixParser.Station("10147", "Hamburg", 53.63, 10.00),
        MosmixParser.Station("10382", "Berlin", 52.56, 13.31),
        MosmixParser.Station("10866", "Muenchen", 48.35, 11.79),
        MosmixParser.Station("10637", "Frankfurt", 50.05, 8.60),
        MosmixParser.Station("10400", "Duesseldorf", 51.29, 6.77)
    )
    fun candidates(latitude: Double, longitude: Double): List<MosmixParser.Station> {
        val cached = runCatching { MosmixParser.parseStations(file.readText()) }.getOrDefault(emptyList())
        val stations = if (cached.size >= 100) cached else {
            val downloaded = runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                try {
                    val data = connection.inputStream.bufferedReader().use { it.readText() }
                    val parsed = MosmixParser.parseStations(data)
                    if (parsed.size >= 100) {
                        file.writeText(data)
                        parsed
                    } else cached
                } finally { connection.disconnect() }
            }.getOrDefault(cached)
            if (downloaded.isNotEmpty()) downloaded else fallback
        }
        return stations.sortedBy { station ->
            val result = FloatArray(1)
            Location.distanceBetween(latitude, longitude, station.latitude, station.longitude, result)
            result[0]
        }.take(8)
    }
}
