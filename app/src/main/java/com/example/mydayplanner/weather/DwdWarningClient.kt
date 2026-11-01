package com.example.mydayplanner.weather

import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Fetches Germany-wide DWD full-status district CAP warnings.
 * The HTML index is used only to discover the latest completed German ZIP.
 */
class DwdWarningClient {
    data class Download(val summary: DwdCapWarnings.Summary, val bytes: Long, val filename: String)
    private val root = "https://opendata.dwd.de/weather/alerts/cap/DISTRICT_DWD_STAT/"
    private fun download(url: String, maxBytes: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "mydayplanner-personal/1.0")
        try {
            check(connection.responseCode == 200) { "DWD CAP HTTP ${connection.responseCode}" }
            val data = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(data.size() + count <= maxBytes) { "Oversized DWD warning response" }
                    data.write(buffer, 0, count)
                }
            }
            return data.toByteArray()
        } finally { connection.disconnect() }
    }

    fun fetch(latitude: Double, longitude: Double, now: Instant): Download {
        val listing = download(root, 1_000_000)
        val filename = DwdCapWarnings.latestFilename(listing.toString(Charsets.UTF_8))
        val zip = download(root + filename, 5_000_000)
        val summary = DwdCapWarnings.summarize(zip, latitude, longitude, now)
        return Download(summary, listing.size.toLong() + zip.size, filename)
    }
}
