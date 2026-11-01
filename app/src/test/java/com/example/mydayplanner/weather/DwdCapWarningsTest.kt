package com.example.mydayplanner.weather

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DwdCapWarningsTest {
    private val now = Instant.parse("2026-11-01T12:00:00Z")
    private fun xml(event: String = "STARKES GEWITTER", expires: String = "2026-11-01T15:00:00Z") =
        """
        <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
          <identifier>test</identifier><sender>dwd@example.de</sender>
          <sent>2026-11-01T11:00:00Z</sent><status>Actual</status><msgType>Alert</msgType>
          <info>
            <language>de-DE</language><event>$event</event><severity>Severe</severity>
            <headline>Thunderstorm test</headline><onset>2026-11-01T11:30:00Z</onset>
            <expires>$expires</expires>
            <area><areaDesc>Test district</areaDesc>
              <polygon>52.0,9.0 53.0,9.0 53.0,10.0 52.0,10.0 52.0,9.0</polygon>
            </area>
          </info>
        </alert>
        """.trimIndent()

    @Test fun geographicAndTimeFiltering() {
        val parsed = DwdCapWarnings.parseXml(xml().toByteArray())
        assertEquals(1, parsed.size)
        assertTrue(parsed[0].relevant(52.5, 9.5, now))
        assertFalse(parsed[0].relevant(48.0, 9.5, now))
        assertEquals(2, DwdCapWarnings.Summary(parsed, now).thunderLevel(now))
        assertFalse(parsed[0].relevant(52.5, 9.5, Instant.parse("2026-11-01T16:00:00Z")))
    }

    @Test fun nonThunderstormAndFutureWarning() {
        val parsed = DwdCapWarnings.parseXml(xml(event = "STARKREGEN").toByteArray())
        assertFalse(parsed.single().thunderstorm)
        val tomorrow = Instant.parse("2026-11-02T12:00:00Z")
        assertFalse(parsed.single().relevant(52.5, 9.5, tomorrow))
    }

    @Test fun readsLatestGermanFullStatusOnly() {
        val html = """
        Z_CAP_C_EDZW_20261101120000_PVW_STATUS_PREMIUMDWD_DISTRICT_DE.zip
        Z_CAP_C_EDZW_20261101123000_PVW_STATUS_PREMIUMDWD_DISTRICT_EN.zip
        Z_CAP_C_EDZW_20261101122000_PVW_STATUS_PREMIUMDWD_DISTRICT_DE.zip
        """.trimIndent()
        assertEquals("Z_CAP_C_EDZW_20261101122000_PVW_STATUS_PREMIUMDWD_DISTRICT_DE.zip",
            DwdCapWarnings.latestFilename(html))
    }

    @Test fun zippedCapAndMissingData() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("warning.xml"))
            zip.write(xml().toByteArray())
            zip.closeEntry()
        }
        assertEquals(1, DwdCapWarnings.summarize(out.toByteArray(), 52.5, 9.5, now).warnings.size)
        assertEquals(0, DwdCapWarnings.summarize(out.toByteArray(), 48.0, 9.5, now).warnings.size)
    }

    @Test fun rejectDoctypes() {
        val unsafe = "<!DOCTYPE alert>\n" + xml()
        assertThrows(IllegalStateException::class.java) { DwdCapWarnings.parseXml(unsafe.toByteArray()) }
    }
}
