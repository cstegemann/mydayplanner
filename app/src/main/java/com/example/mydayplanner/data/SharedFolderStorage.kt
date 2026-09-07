package com.example.mydayplanner.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

internal class SharedFolderStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("shared_storage", Context.MODE_PRIVATE)
    val configuredUri: String? get() = prefs.getString("tree_uri", null)

    fun configure(uri: Uri) {
        prefs.edit().putString("tree_uri", uri.toString()).apply()
    }

    fun root(): DocumentFile? = configuredUri?.let { uri ->
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uri)) }.getOrNull()
            ?.takeIf { it.exists() && it.canRead() && it.canWrite() }
    }

    fun readConfig(): String? = root()?.findFile("_live-tracks.md")?.let { file ->
        runCatching { context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
    }

    fun stateDir(): DocumentFile? {
        val root = root() ?: return null
        return root.findFile("mydayplanner") ?: root.createDirectory("mydayplanner")
    }

    fun names(): List<String> = stateDir()?.listFiles()?.mapNotNull { it.name } ?: emptyList()

    fun read(name: String): String? = stateDir()?.findFile(name)?.let { file ->
        runCatching { context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
    }

    fun write(name: String, contents: String): Boolean {
        val dir = stateDir() ?: return false
        val file = dir.findFile(name) ?: dir.createFile("application/json", name) ?: return false
        return runCatching {
            context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(contents) }
                ?: error("Cannot open $name")
            true
        }.getOrDefault(false)
    }

    /** One-way, non-destructive migration: shared files always win. */
    fun copyMissingFrom(localDir: File) {
        if (stateDir() == null) return
        localDir.listFiles()?.filter { it.isFile && (it.name.endsWith(".json")) }?.forEach { local ->
            if (read(local.name) == null) write(local.name, local.readText())
        }
    }
}
