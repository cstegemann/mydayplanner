package com.example.mydayplanner.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

internal fun normalizeProviderJsonName(name: String): String? {
    if (!name.endsWith(".json", ignoreCase = true)) return null
    var logical = name
    while (logical.endsWith(".json.json", ignoreCase = true)) logical = logical.dropLast(5)
    logical = logical.replace(Regex(" \\(\\d+\\)(?=\\.json$)", RegexOption.IGNORE_CASE), "")
    return logical.dropLast(5) + ".json"
}

internal class SharedFolderStorage(
    private val context: Context,
    onUnavailable: () -> Unit = {}
) {
    private val prefs = context.getSharedPreferences("shared_storage", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver
    val configuredUri: String? get() = prefs.getString("tree_uri", null)
    private val index = StateFileIndex(MonthlyStateDocuments(object : StateDocumentAccess<Uri> {
        override fun resolveRoot(): Uri {
            val tree = configuredUri?.let(Uri::parse) ?: error("No shared folder configured")
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(root))
            resolver.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE), null, null, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getString(1) == "mydayplanner" && cursor.getString(2) == Document.MIME_TYPE_DIR) {
                            return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                        }
                    }
                } ?: throw IOException("Cannot list shared folder")
            return DocumentsContract.createDocument(resolver, root, Document.MIME_TYPE_DIR, "mydayplanner")
                ?: throw IOException("Cannot create state directory")
        }

        override fun children(directory: Uri): List<StateDocument<Uri>> {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(directory, DocumentsContract.getDocumentId(directory))
            return resolver.query(children, METADATA_COLUMNS, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val actualName = cursor.getString(1) ?: continue
                        add(StateDocument(
                            actualName,
                            DocumentsContract.buildDocumentUriUsingTree(directory, cursor.getString(0)),
                            if (cursor.isNull(2)) null else cursor.getLong(2),
                            cursor.getString(3) == Document.MIME_TYPE_DIR
                        ))
                    }
                }
            } ?: throw IOException("Cannot list state directory")
        }

        override fun createDirectory(parent: Uri, name: String): Uri =
            DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, name)
                ?: throw IOException("Cannot create state directory $name")

        override fun createFile(parent: Uri, name: String): IndexedStateFile<Uri> {
            // Octet-stream avoids a second MIME-derived .json extension on some providers.
            val uri = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
                ?: throw IOException("Cannot create $name")
            // Only query the newly created document, never rescan all state files.
            val metadata = runCatching {
                resolver.query(uri, METADATA_COLUMNS, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) IndexedStateFile(
                        name, cursor.getString(1) ?: name, uri,
                        if (cursor.isNull(2)) null else cursor.getLong(2)
                    ) else null
                }
            }.getOrNull()
            return metadata ?: IndexedStateFile(name, name, uri, null)
        }

        override fun read(uri: Uri): String =
            resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IOException("Cannot open state file")

        override fun write(uri: Uri, contents: String) {
            resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(contents) }
                ?: throw IOException("Cannot write state file")
        }
    }), onUnavailable)

    fun configure(uri: Uri) = index.reset {
        prefs.edit().putString("tree_uri", uri.toString()).apply()
    }

    fun root(): DocumentFile? = configuredUri?.let { uri ->
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uri)) }
            .getOrNull()?.takeIf { it.exists() && it.canRead() && it.canWrite() }
    }

    fun readConfig(): String? = root()?.findFile("todo_config.md")?.let { file ->
        runCatching { resolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
    }

    fun refreshIndex(): Boolean = index.refresh()
    fun names(): List<String> = index.names()
    fun contains(name: String): Boolean = index.contains(name)
    fun read(name: String): String? = index.read(name)
    fun write(name: String, contents: String): Boolean = index.write(name, contents)

    private companion object {
        val METADATA_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_MIME_TYPE
        )
    }
}
