package com.example.mydayplanner.data.models

data class LiveTrack(
    val id: String,
    val active: Boolean,
    val modes: List<String>,
    val name: String = id,
    val area: String = ""
)
data class LiveTrackParseResult(val tracks: List<LiveTrack>, val warnings: List<String>)

object LiveTrackParser {
    private val validId = Regex("[a-z0-9_-]+")

    fun parse(markdown: String): LiveTrackParseResult {
        var activeSection: Boolean? = null
        val tracks = mutableListOf<LiveTrack>()
        val warnings = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        markdown.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.startsWith("## ")) {
                activeSection = when (line.removePrefix("## ").trim().lowercase()) {
                    "active" -> true
                    "dormant" -> false
                    else -> null
                }
                return@forEachIndexed
            }
            if (activeSection == null || !line.startsWith("- ")) return@forEachIndexed
            val tokens = line.removePrefix("- ").trim().split(Regex("\\s+")).filter(String::isNotBlank)
            val id = tokens.firstOrNull()
            if (id == null || id.startsWith("#") || id.length > 10 || !validId.matches(id)) {
                warnings += "Line ${index + 1}: invalid live-track id"
                return@forEachIndexed
            }
            if (!seen.add(id)) {
                warnings += "Line ${index + 1}: duplicate live-track id '$id'"
                return@forEachIndexed
            }
            val tags = tokens.drop(1).filter { it.startsWith("#") && it.length > 1 }.map { it.drop(1) }
            if (tags.size != tokens.size - 1) warnings += "Line ${index + 1}: ignored invalid tag tokens"
            tracks += LiveTrack(id, activeSection == true, tags)
        }
        return LiveTrackParseResult(tracks, warnings)
    }
}
