package com.example.mydayplanner.data

import org.junit.Assert.*
import org.junit.Test
import java.io.FileNotFoundException
import java.time.LocalDate

class StateFileIndexTest {
    private class Documents : StateDocuments<String> {
        var resolves = 0
        var scans = 0
        var reads = 0
        var writes = 0
        var creates = 0
        var directory = "folder-a"
        val entries = linkedMapOf<String, MutableList<IndexedStateFile<String>>>()
        val contents = mutableMapOf<String, String>()
        var failWrites = false

        fun add(name: String, modified: Long? = 1, uri: String = "$directory/$name", text: String = name) {
            entries.getOrPut(directory) { mutableListOf() }.add(
                IndexedStateFile(normalizeProviderJsonName(name)!!, name, uri, modified)
            )
            contents[uri] = text
        }

        override fun resolveDirectory(): String { resolves++; return directory }
        override fun listFiles(directory: String): List<IndexedStateFile<String>> {
            scans++
            return entries[directory]?.toList() ?: throw FileNotFoundException(directory)
        }
        override fun createFile(directory: String, name: String): IndexedStateFile<String> {
            creates++
            add("$name.json", uri = "$directory/new-$creates", text = "")
            return entries.getValue(directory).last()
        }
        override fun read(uri: String): String {
            reads++
            return contents[uri] ?: throw FileNotFoundException(uri)
        }
        override fun write(uri: String, contents: String) {
            writes++
            if (failWrites || uri !in this.contents) throw FileNotFoundException(uri)
            this.contents[uri] = contents
        }
    }

    @Test fun `350 files are scanned once and 28 warm reads only open their streams`() {
        val documents = Documents()
        repeat(350) { documents.add("${LocalDate.of(2026, 1, 1).plusDays(it.toLong())}.json") }
        val index = StateFileIndex(documents)
        val names = index.names()
        repeat(28) { assertEquals(names[it], index.read(names[it])) }
        assertTrue(index.contains(names[0]))
        assertEquals(names, index.names())
        assertEquals(1, documents.resolves)
        assertEquals(1, documents.scans)
        assertEquals(28, documents.reads)
    }

    @Test fun `cold read chooses newest normalized duplicate even over exact canonical name`() {
        val documents = Documents().apply {
            add("2026-10-23.json", 1)
            add("2026-10-23 (1).json.json", 9, text = "newest")
            add("2026-10-23 (2).json", 3)
        }
        val index = StateFileIndex(documents)
        assertEquals("newest", index.read("2026-10-23.json"))
        assertEquals(listOf("2026-10-23.json"), index.names())
        assertEquals(1, documents.scans)
    }

    @Test fun `new and overwritten files are readable without rescanning`() {
        val documents = Documents().apply { add("2026-10-23 (1).json", 7) }
        val index = StateFileIndex(documents)
        assertTrue(index.write("2026-10-23.json", "updated"))
        assertEquals("updated", index.read("2026-10-23.json"))
        assertTrue(index.write("2026-10-24.json", "new day"))
        assertEquals("new day", index.read("2026-10-24.json"))
        assertTrue(index.contains("2026-10-24.json"))
        assertEquals(1, documents.scans)
        assertEquals(1, documents.resolves)
        assertEquals(1, documents.creates)
        assertEquals("updated", documents.contents["folder-a/2026-10-23 (1).json"])
    }

    @Test fun `missing names do not rescan until explicit refresh`() {
        val documents = Documents().apply { add("old.json") }
        val index = StateFileIndex(documents)
        assertNull(index.read("external.json"))
        documents.add("external.json")
        assertNull(index.read("external.json"))
        assertEquals(listOf("old.json"), index.names())
        assertTrue(index.refresh())
        assertEquals("external.json", index.read("external.json"))
        assertEquals(2, documents.scans)
    }

    @Test fun `configure invalidates cached directory and index`() {
        val documents = Documents().apply { add("day.json", text = "old") }
        val index = StateFileIndex(documents)
        assertEquals("old", index.read("day.json"))
        index.reset {
            documents.directory = "folder-b"
            documents.add("day.json", text = "new")
        }
        assertEquals("new", index.read("day.json"))
        assertEquals(2, documents.resolves)
        assertEquals(2, documents.scans)
    }

    @Test fun `stale file or directory is resolved again on stream failure`() {
        val documents = Documents().apply { add("day.json", text = "old") }
        val index = StateFileIndex(documents)
        assertEquals("old", index.read("day.json"))
        documents.contents.clear()
        documents.directory = "folder-b"
        documents.add("day.json", text = "replaced")
        assertEquals("replaced", index.read("day.json"))
        assertEquals(2, documents.resolves)
        assertEquals(2, documents.scans)
    }

    @Test fun `failed write retries a newly created file without creating duplicates`() {
        val documents = Documents().apply { add("old.json"); failWrites = true }
        val index = StateFileIndex(documents)
        assertFalse(index.write("new.json", "contents"))
        assertEquals(1, documents.creates)
        documents.failWrites = false
        assertTrue(index.write("new.json", "contents"))
        assertEquals("contents", index.read("new.json"))
        assertEquals(1, documents.creates)
    }

    @Test fun `unknown timestamps do not beat newer dated duplicate`() {
        val documents = Documents().apply { add("day.json", null); add("day (1).json", 5) }
        assertEquals("day (1).json", StateFileIndex(documents).read("day.json"))
    }
}
