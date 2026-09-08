package com.example.mydayplanner.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTrackParserTest {
    @Test
    fun `parses only active and dormant sections with tags`() {
        val result = LiveTrackParser.parse(
            """
            # Live Tracks
            - ignored #outside
            ## Active
            - jobhunt #delivery #highrisk
            ## Notes
            - ignored2
            ## Dormant
            - blogidea #exploration
            """.trimIndent()
        )

        assertEquals(
            listOf(
                LiveTrack("jobhunt", active = true, modes = listOf("delivery", "highrisk")),
                LiveTrack("blogidea", active = false, modes = listOf("exploration"))
            ),
            result.tracks
        )
    }

    @Test
    fun `rejects duplicate long and malformed ids without crashing`() {
        val result = LiveTrackParser.parse(
            """
            ## Active
            - valid #tag
            - valid #duplicate
            - far-too-long
            - HasCaps #bad
            - #tagonly
            """.trimIndent()
        )

        assertEquals(listOf("valid"), result.tracks.map { it.id })
        assertEquals(4, result.warnings.size)
    }

    @Test
    fun `ignores non-tag trailing tokens but retains entry`() {
        val result = LiveTrackParser.parse("## Active\n- cvproj #learning stray")

        assertEquals(listOf("learning"), result.tracks.single().modes)
        assertTrue(result.warnings.single().contains("invalid tag"))
    }
}
