package com.example.mydayplanner.data

import java.time.LocalDate

/** Keep the full logical filename; folders are only a physical organization detail. */
internal fun stateFileMonth(name: String): String? {
    if (!name.matches(Regex("""\d{4}-\d{2}-\d{2}(?:\.[A-Za-z0-9_-]+)*\.json"""))) return null
    return runCatching { LocalDate.parse(name.take(10)).toString().take(7).replace('-', '/') }.getOrNull()
}

internal data class StateDocument<U>(
    val actualName: String,
    val uri: U,
    val lastModified: Long?,
    val isDirectory: Boolean
)

internal interface StateDocumentAccess<U> {
    fun resolveRoot(): U
    fun children(directory: U): List<StateDocument<U>>
    fun createDirectory(parent: U, name: String): U
    fun createFile(parent: U, name: String): IndexedStateFile<U>
    fun read(uri: U): String
    fun write(uri: U, contents: String)
}

/** One metadata query per directory on cold scans; warm reads never traverse this tree. */
internal class MonthlyStateDocuments<U>(private val access: StateDocumentAccess<U>) : StateDocuments<U> {
    private val years = mutableMapOf<String, U>()
    private val months = mutableMapOf<String, U>()

    override fun resolveDirectory(): U {
        years.clear()
        months.clear()
        return access.resolveRoot()
    }

    override fun listFiles(directory: U): List<IndexedStateFile<U>> = buildList {
        for (year in access.children(directory)) {
            if (!year.isDirectory || !year.actualName.matches(Regex("""\d{4}"""))) continue
            years[year.actualName] = year.uri
            for (month in access.children(year.uri)) {
                if (!month.isDirectory || !month.actualName.matches(Regex("0[1-9]|1[0-2]"))) continue
                val path = "${year.actualName}/${month.actualName}"
                months[path] = month.uri
                for (file in access.children(month.uri)) {
                    if (file.isDirectory) continue
                    val logical = normalizeProviderJsonName(file.actualName) ?: continue
                    if (stateFileMonth(logical) != path) continue
                    add(IndexedStateFile(logical, file.actualName, file.uri, file.lastModified))
                }
            }
        }
    }

    override fun createFile(directory: U, name: String): IndexedStateFile<U> {
        val path = stateFileMonth(name) ?: error("Invalid daily state filename: $name")
        val year = path.take(4)
        val yearUri = years.getOrPut(year) { findOrCreateDirectory(directory, year) }
        val monthUri = months.getOrPut(path) { findOrCreateDirectory(yearUri, path.takeLast(2)) }
        return access.createFile(monthUri, name)
    }

    private fun findOrCreateDirectory(parent: U, name: String): U =
        // A previously absent month/year may have arrived through sync since the last refresh.
        access.children(parent).firstOrNull { it.isDirectory && it.actualName == name }?.uri
            ?: access.createDirectory(parent, name)

    override fun read(uri: U): String = access.read(uri)
    override fun write(uri: U, contents: String) = access.write(uri, contents)
}
