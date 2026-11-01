package com.example.mydayplanner.weather

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream

/**
 * DWD CAP 1.2 full-status ZIP, DISTRICT_DWD_STAT, German language.
 * Uses the geographic CAP polygons, not the MOSMIX station location.
 * A complete empty ZIP means zero warnings; an invalid ZIP must throw.
 */
object DwdCapWarnings {
    data class Area(
        val polygons: List<String>,
        val exclusions: List<String>
    ) {
        fun contains(latitude: Double, longitude: Double): Boolean =
            polygons.any { containsPolygon(it, latitude, longitude) } &&
                exclusions.none { containsPolygon(it, latitude, longitude) }
    }

    data class Warning(
        val headline: String,
        val event: String,
        val severity: Int,
        val thunderstorm: Boolean,
        val onset: Instant?,
        val expires: Instant,
        val areas: List<Area>
    ) {
        fun relevant(latitude: Double, longitude: Double, now: Instant): Boolean =
            expires.isAfter(now) &&
                (onset == null || onset.isBefore(now.plusSeconds(3 * 3600))) &&
                areas.any { it.contains(latitude, longitude) }

        fun active(now: Instant): Boolean = onset == null || !onset.isAfter(now)
    }

    data class Summary(
        val warnings: List<Warning>,
        val checkedAt: Instant
    ) {
        val highestSeverity: Int get() = warnings.maxOfOrNull { it.severity } ?: 0
        fun thunderLevel(now: Instant): Int =
            when {
                warnings.any { it.thunderstorm && it.active(now) } -> 2
                warnings.any { it.thunderstorm } -> 1
                else -> 0
            }
    }

    /** Latest *complete status* DE ZIP, not a difference or MUL/FR version. */
    fun latestFilename(directoryListing: String): String {
        val filenames = Regex("""Z_CAP_C_EDZW_\d{14}_PVW_STATUS_PREMIUMDWD_DISTRICT_DE\.zip""")
            .findAll(directoryListing).map { it.value }.toList()
        return filenames.maxOrNull() ?: error("No DWD CAP district DE status ZIP in listing")
    }

    fun summarize(
        zipBytes: ByteArray,
        latitude: Double,
        longitude: Double,
        now: Instant
    ): Summary = Summary(parseZip(zipBytes).filter { it.relevant(latitude, longitude, now) }, now)

    fun parseZip(zipBytes: ByteArray): List<Warning> {
        val parsed = mutableListOf<Warning>()
        var entries = 0
        var totalUncompressed = 0
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                check(entries <= 10000) { "Too many CAP entries" }
                if (!entry.isDirectory && entry.name.endsWith(".xml", ignoreCase = true)) {
                    val data = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        check(data.size() + n <= 1_500_000) { "Single CAP XML too large" }
                        totalUncompressed += n
                        check(totalUncompressed <= 30_000_000) { "CAP ZIP inflated beyond limit" }
                        data.write(buffer, 0, n)
                    }
                    parsed.addAll(parseXml(data.toByteArray()))
                }
                zip.closeEntry()
            }
        }
        check(entries > 0) { "Empty/corrupted CAP ZIP" }
        return parsed
    }

    fun parseXml(xml: ByteArray): List<Warning> {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            .newPullParser().apply {
                check(!getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) { "Unsafe CAP XML parser" }
                setInput(ByteArrayInputStream(xml), null)
            }
        var status = ""
        var msgType = ""
        var language = ""
        var headline = ""
        var event = ""
        var severity = 0
        var onset: Instant? = null
        var expires: Instant? = null
        var groupThunder = false
        var codeName = ""
        var codeValue = ""
        var geoName = ""
        var geoValue = ""
        var insideEventCode = false
        var insideGeocode = false
        val areas = mutableListOf<Area>()
        val polygons = mutableListOf<String>()
        val exclusions = mutableListOf<String>()
        val warnings = mutableListOf<Warning>()
        while (true) {
            when (parser.nextToken()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.DOCDECL -> error("CAP XML with DOCTYPE is not allowed")
                XmlPullParser.START_TAG -> when (parser.name) {
                    "info" -> {
                        language = ""; headline = ""; event = ""; severity = 0
                        onset = null; expires = null; groupThunder = false; areas.clear()
                    }
                    "area" -> { polygons.clear(); exclusions.clear() }
                    "eventCode" -> { insideEventCode = true; codeName = ""; codeValue = "" }
                    "geocode" -> { insideGeocode = true; geoName = ""; geoValue = "" }
                    "status" -> status = parser.nextText().trim()
                    "msgType" -> msgType = parser.nextText().trim()
                    "language" -> language = parser.nextText().trim()
                    "headline" -> headline = parser.nextText().trim()
                    "event" -> event = parser.nextText().trim()
                    "severity" -> severity = when (parser.nextText().trim()) {
                        "Minor" -> 1
                        "Moderate" -> 2
                        "Severe", "Extreme" -> 3
                        else -> 0
                    }
                    "onset" -> onset = runCatching { Instant.parse(parser.nextText().trim()) }.getOrNull()
                    "expires" -> expires = runCatching { Instant.parse(parser.nextText().trim()) }.getOrNull()
                    "polygon" -> polygons.add(parser.nextText().trim())
                    "valueName" -> {
                        val value = parser.nextText().trim()
                        if (insideEventCode) codeName = value
                        if (insideGeocode) geoName = value
                    }
                    "value" -> {
                        val value = parser.nextText().trim()
                        if (insideEventCode) codeValue = value
                        if (insideGeocode) geoValue = value
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "eventCode" -> {
                        if (codeName.equals("GROUP", ignoreCase = true) &&
                            codeValue.contains("THUNDERSTORM", ignoreCase = true)) groupThunder = true
                        insideEventCode = false
                    }
                    "geocode" -> {
                        if (geoName.equals("EXCLUDE_POLYGON", ignoreCase = true)) exclusions.add(geoValue)
                        insideGeocode = false
                    }
                    "area" -> areas.add(Area(polygons.toList(), exclusions.toList()))
                    "info" -> {
                        val expiry = expires
                        if ((language.isBlank() || language.startsWith("de", ignoreCase = true)) &&
                            expiry != null && areas.isNotEmpty() &&
                            status.equals("Actual", ignoreCase = true) &&
                            !msgType.equals("Cancel", ignoreCase = true)) {
                            warnings.add(Warning(
                                headline.ifBlank { event }, event, severity,
                                groupThunder || event.contains("Gewitter", ignoreCase = true) ||
                                    event.contains("Thunderstorm", ignoreCase = true),
                                onset, expiry, areas.toList()
                            ))
                        }
                    }
                }
            }
        }
        return warnings
    }

    /** CAP coordinates are latitude,longitude pairs separated by whitespace. */
    fun containsPolygon(text: String, latitude: Double, longitude: Double): Boolean {
        val points = text.trim().split(Regex("""\s+""")).mapNotNull { point ->
            val parts = point.split(',')
            if (parts.size != 2) null else {
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                if (lat == null || lon == null) null else Pair(lat, lon)
            }
        }
        if (points.size < 3) return false
        var inside = false
        var previous = points.last()
        for (point in points) {
            val crosses = (point.first > latitude) != (previous.first > latitude)
            if (crosses) {
                val intersection = (previous.second - point.second) *
                    (latitude - point.first) / (previous.first - point.first) + point.second
                if (longitude < intersection) inside = !inside
            }
            previous = point
        }
        return inside
    }
}
