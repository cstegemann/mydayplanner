package com.example.mydayplanner.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class MosmixStationCatalogTest {
    @Test fun convertsDwdDegreeMinutesCoordinates() {
        val rows = """
ID    ICAO NAME                 LAT    LON     ELEV
----- ---- -------------------- -----  ------- -----
01001 ENJA JAN MAYEN             70.56   -8.40    10
01008 ENSB SVALBARD              78.15   15.28    29
01025 ---- TROMSOE               69.41   18.55    10
        """.trimIndent()
        val stations = MosmixParser.parseStations(rows)
        assertEquals(3, stations.size)
        assertEquals(70 + 56 / 60.0, stations[0].latitude, 0.00001)
        assertEquals(-(8 + 40 / 60.0), stations[0].longitude, 0.00001)
        assertEquals("01025", stations[2].id)
    }
}
