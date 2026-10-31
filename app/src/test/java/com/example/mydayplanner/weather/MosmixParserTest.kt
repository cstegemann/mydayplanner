package com.example.mydayplanner.weather

import java.time.Instant
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class MosmixParserTest {
    private val minimalKml = """
        <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:forecast="urn:dwd">
          <forecast:TimeStep>2026-10-31T11:00:00Z</forecast:TimeStep>
          <forecast:Forecast forecast:elementName="TTT">
            <forecast:value>280.15 -999 -</forecast:value>
          </forecast:Forecast>
          <forecast:Forecast forecast:elementName="unused">
            <forecast:value>999</forecast:value>
          </forecast:Forecast>
        </kml>
    """.trimIndent()

    @Test fun parsesKmzAndKeepsMissingValues() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("forecast.kml"))
            zip.write(minimalKml.toByteArray())
            zip.closeEntry()
        }
        val forecast = MosmixParser.parseKmz(bytes.toByteArray())
        assertEquals(listOf(280.15, null, null), forecast.values["TTT"])
        assertEquals(setOf("TTT"), forecast.values.keys)
    }

    @Test fun rejectsDoctypesInUtf8AndUtf16() {
        val declarations = listOf(
            "<!DOCTYPE kml>",
            """<!DOCTYPE kml [<!ENTITY temperature "280.15">]>""",
            """<!DOCTYPE kml SYSTEM "https://example.invalid/weather.dtd">""",
            """<!DOCTYPE kml [<!ENTITY % external SYSTEM "file:///nonexistent-weather.dtd">%external;]>"""
        )
        for (declaration in declarations) {
            for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16)) {
                val xml = (declaration + minimalKml).toByteArray(charset)
                val failure = assertThrows(IllegalStateException::class.java) {
                    MosmixParser.parseKml(xml)
                }
                assertEquals("DOCTYPE is not allowed in MOSMIX KML", failure.message)
            }
        }
    }

    @Test fun parsesUtf16WithoutDoctype() {
        assertEquals(listOf(280.15, null, null),
            MosmixParser.parseKml(minimalKml.toByteArray(Charsets.UTF_16)).values["TTT"])
    }

    @Test fun parsesForecastAndReducesTemperature() {
        val xml = """
          <kml xmlns:dwd="https://opendata.dwd.de/weather/lib/pointforecast_dwd_extension_V1_0.xsd">
            <dwd:IssueTime>2026-10-31T10:00:00.000Z</dwd:IssueTime>
            <dwd:ForecastTimeSteps>
              <dwd:TimeStep>2026-10-31T11:00:00.000Z</dwd:TimeStep>
              <dwd:TimeStep>2026-10-31T12:00:00.000Z</dwd:TimeStep>
              <dwd:TimeStep>2026-10-31T13:00:00.000Z</dwd:TimeStep>
              <dwd:TimeStep>2026-10-31T14:00:00.000Z</dwd:TimeStep>
            </dwd:ForecastTimeSteps>
            <dwd:Forecast dwd:elementName="TTT"><dwd:value>280.15 281.15 282.15 279.15</dwd:value></dwd:Forecast>
            <dwd:Forecast dwd:elementName="RR1c"><dwd:value>0 0.2 1.2 0</dwd:value></dwd:Forecast>
            <dwd:Forecast dwd:elementName="FX3"><dwd:value>4 6 8 12</dwd:value></dwd:Forecast>
            <dwd:Forecast dwd:elementName="wwT3"><dwd:value>0 0 10 30</dwd:value></dwd:Forecast>
          </kml>
        """.trimIndent().toByteArray()
        val result = MosmixParser.parseKml(xml).watchValues(Instant.parse("2026-10-31T11:00:00Z"))
        assertEquals(7, result.temperatureC)
        assertEquals(-1, result.delta3h)
        assertEquals(2, result.rain)
        assertEquals(2, result.wind)
        assertEquals(1, result.thunder)
    }

    @Test fun rejectsMissingTemperature() {
        val forecast = MosmixParser.Forecast(null, listOf(Instant.parse("2026-10-31T11:00:00Z")), emptyMap())
        try {
            forecast.watchValues(Instant.parse("2026-10-31T11:00:00Z"))
            fail("Should reject unavailable current temperature")
        } catch (_: IllegalStateException) { }
    }
}
