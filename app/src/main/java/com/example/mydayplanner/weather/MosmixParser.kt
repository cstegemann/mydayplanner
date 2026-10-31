package com.example.mydayplanner.weather

import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
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
            val thunder = if (currentWeather != null && currentWeather in 95..99) 2
                else if (thunderProbability != null) { if (thunderProbability >= 20.0) 1 else 0 }
                else null
            return WeatherValues(
                temperatureC = (currentK - 273.15).roundToInt(),
                delta3h = futureK?.let { (it - currentK).roundToInt() },
                rain = rain?.let { when { it >= 3.0 -> 3; it >= 1.0 -> 2; it >= 0.1 -> 1; else -> 0 } },
                wind = gust?.let { when { it >= 17 -> 3; it >= 10 -> 2; it >= 5 -> 1; else -> 0 } },
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
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val times = doc.getElementsByTagNameNS("*", "TimeStep")
        val parsedTimes = (0 until times.length).map { Instant.parse(times.item(it).textContent.trim()) }
        check(parsedTimes.isNotEmpty()) { "No MOSMIX forecast timestamps" }
        val issueNodes = doc.getElementsByTagNameNS("*", "IssueTime")
        val issuedAt = if (issueNodes.length > 0) runCatching {
            Instant.parse(issueNodes.item(0).textContent.trim())
        }.getOrNull() else null
        val forecasts = doc.getElementsByTagNameNS("*", "Forecast")
        val values = mutableMapOf<String, List<Double?>>()
        for (i in 0 until forecasts.length) {
            val element = forecasts.item(i) as? Element ?: continue
            val key = (0 until element.attributes.length).firstNotNullOfOrNull { index ->
                element.attributes.item(index).takeIf { it.localName == "elementName" }?.nodeValue
            } ?: continue
            if (key !in setOf("TTT", "RR1c", "RR3c", "FX1", "FX3", "FF", "wwT3", "ww")) continue
            val nodes = element.getElementsByTagNameNS("*", "value")
            if (nodes.length == 0) continue
            val numbers = nodes.item(0).textContent.trim().split(Regex("""\s+"""))
                .map { it.toDoubleOrNull()?.takeUnless { value -> value == -999.0 } }
            values[key] = numbers
        }
        return Forecast(issuedAt, parsedTimes, values)
    }
}
