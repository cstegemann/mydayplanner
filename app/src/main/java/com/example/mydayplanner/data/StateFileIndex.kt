package com.example.mydayplanner.data

internal data class IndexedStateFile<U>(
    val logicalName: String,
    val actualName: String,
    val uri: U,
    val lastModified: Long?
)

/** The provider boundary: listings return metadata, never lazily queried document objects. */
internal interface StateDocuments<U> {
    fun resolveDirectory(): U
    fun listFiles(directory: U): List<IndexedStateFile<U>>
    fun createFile(directory: U, name: String): IndexedStateFile<U>
    fun read(uri: U): String
    fun write(uri: U, contents: String)
}

/** Serializes configuration, refreshes and I/O so an old scan cannot replace a newer index. */
internal class StateFileIndex<U>(
    private val documents: StateDocuments<U>,
    private val onUnavailable: () -> Unit = {}
) {
    private val lock = Any()
    private var directory: U? = null
    private var files: MutableMap<String, IndexedStateFile<U>>? = null

    fun reset(updateConfiguration: () -> Unit = {}) = synchronized(lock) {
        updateConfiguration()
        invalidate()
    }

    fun refresh(): Boolean = synchronized(lock) {
        invalidate()
        retry { index(); true } ?: false
    }

    fun names(): List<String> = synchronized(lock) {
        retry { index().keys.toList() }.orEmpty()
    }

    fun contains(name: String): Boolean = synchronized(lock) {
        retry { name in index() } ?: false
    }

    fun read(name: String): String? = synchronized(lock) {
        retry { index()[name]?.let { documents.read(it.uri) } }
    }

    fun write(name: String, contents: String): Boolean = synchronized(lock) {
        retry {
            val current = index()
            val file = current[name] ?: documents.createFile(directory!!, name)
            documents.write(file.uri, contents)
            // The provider owns the timestamp; do not issue another metadata query or invent one.
            current[name] = file.copy(lastModified = null)
            true
        } ?: false
    }

    private fun index(): MutableMap<String, IndexedStateFile<U>> {
        files?.let { return it }
        val dir = directory ?: documents.resolveDirectory().also { directory = it }
        val result = linkedMapOf<String, IndexedStateFile<U>>()
        for (file in documents.listFiles(dir)) {
            val previous = result[file.logicalName]
            val modified = file.lastModified ?: 0L
            val previousModified = previous?.lastModified ?: 0L
            if (previous == null || modified > previousModified ||
                (modified == previousModified && file.actualName == file.logicalName)) {
                result[file.logicalName] = file
            }
        }
        return result.also { files = it }
    }

    private fun invalidate() {
        directory = null
        files = null
    }

    private fun <T> retry(block: () -> T): T? {
        repeat(2) {
            try {
                return block()
            } catch (_: Exception) {
                // A sync provider can replace a directory or document without changing its name.
                invalidate()
            }
        }
        onUnavailable()
        return null
    }
}
