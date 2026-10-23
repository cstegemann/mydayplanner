package com.example.mydayplanner.data

import org.junit.Assert.*
import org.junit.Test
import java.io.FileNotFoundException
import java.time.LocalDate

class MonthlyStateDocumentsTest {
    private class Access : StateDocumentAccess<String> {
        var root = "root"
        var resolves = 0
        var fileCreates = 0
        val directories = mutableMapOf<String, MutableList<StateDocument<String>>>()
        val contents = mutableMapOf<String, String>()
        val scans = mutableMapOf<String, Int>()
        val createdDirectories = mutableListOf<String>()

        fun seedDirectory(path: String) {
            if (path in directories) return
            directories[path] = mutableListOf()
            if ('/' in path) {
                val parent = path.substringBeforeLast('/')
                seedDirectory(parent)
                directories.getValue(parent).add(StateDocument(path.substringAfterLast('/'), path, null, true))
            }
        }

        fun add(path: String, text: String = path, modified: Long? = 1) {
            val parent = path.substringBeforeLast('/')
            seedDirectory(parent)
            directories.getValue(parent).add(StateDocument(path.substringAfterLast('/'), path, modified, false))
            contents[path] = text
        }

        override fun resolveRoot(): String { resolves++; return root }
        override fun children(directory: String): List<StateDocument<String>> {
            scans[directory] = (scans[directory] ?: 0) + 1
            return directories[directory]?.toList() ?: throw FileNotFoundException(directory)
        }
        override fun createDirectory(parent: String, name: String): String {
            check(directories.getValue(parent).none { it.actualName == name })
            val path = "$parent/$name"
            seedDirectory(path)
            createdDirectories.add(path)
            return path
        }
        override fun createFile(parent: String, name: String): IndexedStateFile<String> {
            check(parent in directories)
            fileCreates++
            // Model a provider which appends an extra MIME extension.
            val path = "$parent/$name.json"
            add(path, text = "")
            return IndexedStateFile(name, "$name.json", path, 1)
        }
        override fun read(uri: String): String = contents[uri] ?: throw FileNotFoundException(uri)
        override fun write(uri: String, contents: String) {
            if (uri !in this.contents) throw FileNotFoundException(uri)
            this.contents[uri] = contents
        }
    }

    @Test fun `350 files across years and months need one cold query per directory only`() {
        val access = Access()
        repeat(350) {
            val date = LocalDate.of(2025, 11, 1).plusDays(it.toLong()).toString()
            access.add("root/${date.take(7).replace('-', '/')}/$date.json", text = date)
        }
        val index = StateFileIndex(MonthlyStateDocuments(access))
        val names = index.names()
        assertEquals(350, names.size)
        assertEquals(access.directories.keys, access.scans.keys)
        assertTrue(access.scans.values.all { it == 1 })
        val coldScans = access.scans.toMap()
        repeat(28) { assertEquals(names[it].removeSuffix(".json"), index.read(names[it])) }
        index.contains(names.last())
        index.names()
        assertEquals(coldScans, access.scans)
        assertEquals(1, access.resolves)
    }

    @Test fun `new files in cached month keep full names without directory scans`() {
        val access = Access().apply { add("root/2026/10/2026-10-23.json") }
        val index = StateFileIndex(MonthlyStateDocuments(access))
        index.names()
        val scans = access.scans.toMap()
        for (name in listOf("2026-10-24.json", "2026-10-24.track.json", "2026-10-24.routines.json")) {
            assertTrue(index.write(name, name))
            assertEquals(name, index.read(name))
            assertEquals(name, access.contents["root/2026/10/$name.json"])
        }
        assertEquals(scans, access.scans)
        assertTrue(access.createdDirectories.isEmpty())
    }

    @Test fun `month and year rollover create and cache only missing folders`() {
        val access = Access().apply { add("root/2026/10/2026-10-23.json") }
        val index = StateFileIndex(MonthlyStateDocuments(access))
        index.names()
        assertTrue(index.write("2026-11-01.json", "November"))
        assertTrue(index.write("2027-01-01.json", "January"))
        val scans = access.scans.toMap()
        assertTrue(index.write("2027-01-02.track.json", "Tracking"))
        assertEquals(scans, access.scans)
        assertEquals(listOf("root/2026/11", "root/2027", "root/2027/01"), access.createdDirectories)
        assertEquals("November", index.read("2026-11-01.json"))
        assertEquals("January", index.read("2027-01-01.json"))
        assertEquals("Tracking", access.contents["root/2027/01/2027-01-02.track.json.json"])
    }

    @Test fun `explicit refresh discovers external month and newest duplicate`() {
        val access = Access().apply { add("root/2026/10/2026-10-23.json", "old", 1) }
        val index = StateFileIndex(MonthlyStateDocuments(access))
        assertEquals("old", index.read("2026-10-23.json"))
        access.add("root/2026/10/2026-10-23 (2).json.json", "new", 9)
        access.add("root/2026/11/2026-11-01.track.json", "external")
        assertFalse(index.contains("2026-11-01.track.json"))
        assertTrue(index.refresh())
        assertEquals("new", index.read("2026-10-23.json"))
        assertEquals("external", index.read("2026-11-01.track.json"))
        assertTrue(index.write("2026-10-23.json", "updated"))
        assertEquals("updated", access.contents["root/2026/10/2026-10-23 (2).json.json"])
        assertEquals("old", access.contents["root/2026/10/2026-10-23.json"])
    }

    @Test fun `sync created month is reused instead of creating a duplicate folder`() {
        val access = Access().apply { add("root/2026/10/2026-10-23.json") }
        val index = StateFileIndex(MonthlyStateDocuments(access))
        index.names()
        access.add("root/2026/11/2026-11-01.json")
        assertTrue(index.write("2026-11-02.json", "new day"))
        assertTrue(access.createdDirectories.isEmpty())
        assertEquals("new day", index.read("2026-11-02.json"))
    }

    @Test fun `changing folder clears all month and year caches`() {
        val access = Access().apply { add("root/2026/10/2026-10-23.json", "old") }
        val index = StateFileIndex(MonthlyStateDocuments(access))
        assertEquals("old", index.read("2026-10-23.json"))
        index.reset {
            access.root = "other"
            access.add("other/2026/10/2026-10-23.json", "new")
        }
        assertEquals("new", index.read("2026-10-23.json"))
        assertTrue(index.write("2026-10-24.json", "new folder"))
        assertEquals("new folder", access.contents["other/2026/10/2026-10-24.json.json"])
        assertFalse("root/2026/10/2026-10-24.json.json" in access.contents)
    }

    @Test fun `stale URI recovery also clears month caches before next write`() {
        val access = Access().apply { add("root/2026/10/2026-10-23.json", "old") }
        val index = StateFileIndex(MonthlyStateDocuments(access))
        index.read("2026-10-23.json")
        access.contents.clear()
        access.root = "replacement"
        access.add("replacement/2026/10/2026-10-23.json", "replaced")
        assertEquals("replaced", index.read("2026-10-23.json"))
        assertTrue(index.write("2026-10-24.json", "new"))
        assertEquals("new", access.contents["replacement/2026/10/2026-10-24.json.json"])
    }

    @Test fun `old flat files backups and files in wrong month are not migrated by app`() {
        val access = Access().apply {
            add("root/2026-10-01.json")
            add("root/_migration_duplicates/2026-10-02.json")
            add("root/2026/10/2026-11-01.json")
            add("root/2026/13/2026-12-01.json")
            add("root/2026/10/2026-10-23.routines.json")
        }
        val names = StateFileIndex(MonthlyStateDocuments(access)).names()
        assertEquals(listOf("2026-10-23.routines.json"), names)
        assertTrue(access.createdDirectories.isEmpty())
    }

    @Test fun `month path validates dates and preserves supported filename suffixes`() {
        assertEquals("2024/02", stateFileMonth("2024-02-29.json"))
        assertEquals("2026/10", stateFileMonth("2026-10-23.track.json"))
        assertEquals("2026/10", stateFileMonth("2026-10-23.routines.json"))
        assertNull(stateFileMonth("2025-02-29.json"))
        assertNull(stateFileMonth("2026-13-01.json"))
        assertNull(stateFileMonth("../2026-10-23.json"))
        assertNull(stateFileMonth("todo_config.md"))
    }
}
