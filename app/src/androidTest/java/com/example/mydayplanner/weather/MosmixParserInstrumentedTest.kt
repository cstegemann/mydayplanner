package com.example.mydayplanner.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise Android's actual XML implementation, which differs from desktop Java. */
@RunWith(AndroidJUnit4::class)
class MosmixParserInstrumentedTest {
    private val kml = """
        <kml xmlns:dwd="urn:dwd">
          <dwd:TimeStep>2026-10-31T11:00:00Z</dwd:TimeStep>
          <dwd:Forecast dwd:elementName="TTT"><dwd:value>280.15</dwd:value></dwd:Forecast>
        </kml>
    """.trimIndent()

    @Test fun parsesForecastWithAndroidXmlParser() {
        assertEquals(listOf(280.15), MosmixParser.parseKml(kml.toByteArray()).values["TTT"])
    }

    @Test fun rejectsDoctypeWithAndroidXmlParser() {
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16)) {
            try {
                MosmixParser.parseKml(("<!DOCTYPE kml>" + kml).toByteArray(charset))
                fail("Should reject DOCTYPE")
            } catch (e: IllegalStateException) {
                assertEquals("DOCTYPE is not allowed in MOSMIX KML", e.message)
            }
        }
    }
}
