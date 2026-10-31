package com.example.mydayplanner.weather

import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure parser/reducer for DWD's station-specific MOSMIX_L KMZ, not a radar nowcast. */
object MosmixParser {
    data class Station(val id: String, val name: String, val latitude: Double, val longitude: Double)

    data class Forecast(
        val issuedAt: Instant?,
        val times: List<Instant>,
        val values: Map<String, List<Double?>>
    ) {
        fun at(parameter: String, whenUtc: Instant): Double? {
            val index = times.indices.minByOrNull { abs(times[it].epochSecond - whenUtc.epochSecond) } ?: return null
            // Never use an unrelated forecast hour for missing data.
            if (abs(times[index].epochSecond - whenUtc.epochSecond) > 5400) return null
            return values[parameter]?.getOrNull(index)
        }

        fun watchValues(now: Instant): WeatherValues {
            val threeHours = now.plusSeconds(3 * 3600)
            val oneHour = now.plusSeconds(3600)
            val currentK = at("TTT", now) ?: error("MOSMIX has no current temperature")
            val futureK = at("TTT", threeHours)
            val gust = at("FX3", threeHours) ?: at("FX1", oneHour) ?: at("FF", now)
            // RR1c is precipitation over the preceding hour; prefer its 3 upcoming buckets.
            val hourlyRain = (1..3).mapNotNull { at("RR1c", now.plusSeconds(it * 3600L)) }
            val rain = hourlyRain.maxOrNull() ?: (at("RR3c", threeHours)?.div(3.0))
            val thunderProbability = at("wwT3", threeHours)
            val currentWeather = at("ww", oneHour)?.toInt()
            val thunder = WeatherLevels.thunder(currentWeather, thunderProbability)
            return WeatherValues(
                temperatureC = (currentK - 273.15).roundToInt(),
                delta3h = futureK?.let { (it - currentK).roundToInt() },
                rain = WeatherLevels.rain(rain),
                wind = WeatherLevels.wind(gust),
                thunder = thunder,
                rawRainMmPerHour = rain,
                rawGustMetersPerSecond = gust,
                rawThunderProbabilityPercent = thunderProbability
            )
        }
    }

    data class WeatherValues(
        val temperatureC: Int?,
        val delta3h: Int?,
        val rain: Int?,
        val wind: Int?,
        val thunder: Int?,
        val rawRainMmPerHour: Double?,
        val rawGustMetersPerSecond: Double?,
        val rawThunderProbabilityPercent: Double?
    )

    /** DWD catalogue is fixed-column-ish text; names may contain spaces. */
    fun parseStations(text: String): List<Station> {
        val pattern = Regex("""^\s*([A-Za-z0-9]{4,6})\s+\S{4}\s+(.+?)\s+(-?\d{1,2}\.\d+)\s+(-?\d{1,3}\.\d+)\s+-?\d+\s*$""")
        return text.lineSequence().mapNotNull { line ->
            val match = pattern.matchEntire(line) ?: return@mapNotNull null
            val lat = match.groupValues[3].toDoubleOrNull() ?: return@mapNotNull null
            val lon = match.groupValues[4].toDoubleOrNull() ?: return@mapNotNull null
            Station(match.groupValues[1], match.groupValues[2].trim(), lat, lon)
        }.toList()
    }

    fun parseKmz(compressed: ByteArray): Forecast {
        ZipInputStream(ByteArrayInputStream(compressed)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.endsWith(".kml", ignoreCase = true)) {
                    // Defend against malformed or unexpectedly huge ZIP entries.
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        check(output.size() + n < 4_000_000) { "MOSMIX KML too large" }
                        output.write(buffer, 0, n)
                    }
                    return parseKml(output.toByteArray())
                }
            }
        }
        error("KMZ did not contain a KML file")
    }

    fun parseKml(xml: ByteArray): Forecast {
        // Android does not support Xerces' disallow-doctype-decl feature.
        // Use its native pull parser and reject DTDs explicitly before reading values.
        val parser = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }.newPullParser().apply {
            // DTD processing defaults to false; some implementations reject even
            // setting this optional feature to false, so verify it without setting it.
            check(!getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) {
                "MOSMIX XML parser must not process DTDs"
            }
            setInput(ByteArrayInputStream(xml), null)
        }
        val parsedTimes = mutableListOf<Instant>()
        var issuedAt: Instant? = null
        var parameter: String? = null
        val values = mutableMapOf<String, List<Double?>>()
        val parameters = setOf("TTT", "RR1c", "RR3c", "FX1", "FX3", "FF", "wwT3", "ww")
        while (true) {
            // nextToken exposes DOCDECL; next would silently skip it.
            when (parser.nextToken()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.DOCDECL -> error("DOCTYPE is not allowed in MOSMIX KML")
                XmlPullParser.START_TAG -> when (parser.name) {
                    "TimeStep" -> parsedTimes.add(Instant.parse(parser.nextText().trim()))
                    "IssueTime" -> {
                        val text = parser.nextText().trim()
                        issuedAt = runCatching { Instant.parse(text) }.getOrNull()
                    }
                    "Forecast" -> parameter = (0 until parser.attributeCount)
                        .firstNotNullOfOrNull { index ->
                            parser.getAttributeValue(index).takeIf {
                                parser.getAttributeName(index) == "elementName"
                            }
                        }?.takeIf { it in parameters }
                    "value" -> parameter?.let { key ->
                        values[key] = parser.nextText().trim().split(Regex("""\s+"""))
                            .map { it.toDoubleOrNull()?.takeUnless { value -> value == -999.0 } }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "Forecast") parameter = null
            }
        }
        check(parsedTimes.isNotEmpty()) { "No MOSMIX forecast timestamps" }
        return Forecast(issuedAt, parsedTimes, values)
    }
}
