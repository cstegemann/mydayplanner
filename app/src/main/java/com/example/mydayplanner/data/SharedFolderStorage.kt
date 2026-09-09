package com.example.mydayplanner.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

internal fun normalizeProviderJsonName(name: String): String? {
    if (!name.endsWith(".json", ignoreCase = true)) return null
    var logical = name
    while (logical.endsWith(".json.json", ignoreCase = true)) logical = logical.dropLast(5)
    logical = logical.replace(Regex(" \\(\\d+\\)(?=\\.json$)", RegexOption.IGNORE_CASE), "")
    return logical.dropLast(5) + ".json"
}

internal class SharedFolderStorage(private val context: Context) {
    private val writeLock = Any()
    private val indexLock = Any()
    private val prefs = context.getSharedPreferences("shared_storage", Context.MODE_PRIVATE)
    @Volatile private var stateFiles: List<DocumentFile>? = null
    val configuredUri: String? get() = prefs.getString("tree_uri", null)

    fun configure(uri: Uri) {
        prefs.edit().putString("tree_uri", uri.toString()).apply()
        stateFiles = null
    }

    fun root(): DocumentFile? = configuredUri?.let { uri ->
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uri)) }.getOrNull()
            ?.takeIf { it.exists() && it.canRead() && it.canWrite() }
    }

    fun readConfig(): String? = root()?.findFile("todo_config.md")?.let { file ->
        runCatching { context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
    }

    fun stateDir(): DocumentFile? {
        val root = root() ?: return null
        return root.findFile("mydayplanner") ?: root.createDirectory("mydayplanner")
    }

    /**
     * Refreshes the directory index and returns its logical JSON names. SAF directory listings
     * are comparatively expensive, so subsequent reads reuse this snapshot instead of listing
     * the entire directory once for every history day.
     */
    fun names(): List<String> = refreshIndex()
        .mapNotNull { it.name?.let(::normalizeProviderJsonName) }
        .distinct()

    fun read(name: String): String? = findStateFile(name)?.let { file ->
        runCatching { context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
    }

    fun write(name: String, contents: String): Boolean = synchronized(writeLock) {
        val dir = stateDir() ?: return@synchronized false
        // Some document providers append a MIME-derived extension. Using octet-stream and
        // matching provider-created suffixes prevents repeated " (1).json" files.
        val file = findStateFile(name, dir) ?: dir.createFile("application/octet-stream", name)
            ?: return@synchronized false
        runCatching {
            context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(contents) }
                ?: error("Cannot open $name")
            stateFiles = null
            true
        }.getOrDefault(false)
    }

    /** One-way, non-destructive migration: shared files always win. */
    fun copyMissingFrom(localDir: File) = synchronized(writeLock) {
        val dir = stateDir() ?: return@synchronized
        localDir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.forEach { local ->
            if (findStateFile(local.name, dir) == null) write(local.name, local.readText())
        }
    }

    private fun findStateFile(name: String, dir: DocumentFile? = stateDir()): DocumentFile? {
        val files = indexedFiles(dir)
        return files.firstOrNull { it.name == name }
            ?: files.filter { it.name?.let(::normalizeProviderJsonName) == name }
                .maxByOrNull { it.lastModified() }
    }

    private fun indexedFiles(dir: DocumentFile?): List<DocumentFile> {
        stateFiles?.let { return it }
        if (dir == null) return emptyList()
        return synchronized(indexLock) {
            stateFiles ?: dir.listFiles().toList().also { stateFiles = it }
        }
    }

    private fun refreshIndex(): List<DocumentFile> {
        val files = stateDir()?.listFiles()?.toList().orEmpty()
        synchronized(indexLock) { stateFiles = files }
        return files
    }
}
