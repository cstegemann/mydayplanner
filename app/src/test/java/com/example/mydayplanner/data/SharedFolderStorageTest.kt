package com.example.mydayplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedFolderStorageTest {
    @Test fun `normalizes collision suffix added by document provider`() {
        assertEquals("2026-09-08.json", normalizeProviderJsonName("2026-09-08 (1).json"))
        assertEquals("2026-09-08.track.json", normalizeProviderJsonName("2026-09-08.track (2).json"))
    }

    @Test fun `normalizes duplicate mime extension`() {
        assertEquals("2026-09-08.json", normalizeProviderJsonName("2026-09-08.json.json"))
        assertEquals("2026-09-08.json", normalizeProviderJsonName("2026-09-08 (3).json.json"))
    }

    @Test fun `normalizes uppercase extension`() {
        assertEquals("2026-09-08.json", normalizeProviderJsonName("2026-09-08.JSON"))
    }

    @Test fun `ignores non-json documents`() {
        assertNull(normalizeProviderJsonName("_live-tracks.md"))
    }
}
