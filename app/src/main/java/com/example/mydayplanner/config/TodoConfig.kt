package com.example.mydayplanner.config

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.time.LocalTime

data class Area(val id: String, val name: String = id)
data class ConfigProject(val id: String, val name: String, val area: String, val tags: List<String>, val active: Boolean)
enum class RoutineMeasure { BOOLEAN, COUNT, MINUTES }
data class Routine(val id: String, val name: String, val area: String, val measure: RoutineMeasure, val target: Int,
    val daytypes: List<String>, val window: Pair<LocalTime, LocalTime>? = null)
enum class RuleEffect { INFO, WARNING, REPLAN }
sealed interface Rule { val id: String; val daytypes: List<String>; val effect: RuleEffect; val message: String? }
data class MetricRule(override val id: String, val metric: String, val areas: List<String>, val loads: List<String>,
    val min: Double?, val max: Double?, val at: LocalTime?, override val daytypes: List<String>,
    override val effect: RuleEffect, override val message: String?) : Rule
data class RoutineRule(override val id: String, val routine: String, val state: String,
    override val daytypes: List<String>, override val effect: RuleEffect, override val message: String?) : Rule
data class CompoundRule(override val id: String, val all: List<String>?, val any: List<String>?,
    override val daytypes: List<String>, override val effect: RuleEffect, override val message: String?,
    val supersedes: List<String>) : Rule
data class TodoConfig(val areas: List<Area>, val projects: List<ConfigProject>, val routines: List<Routine>, val rules: List<Rule>)

object TodoConfigParser {
    private val headings = listOf("Areas", "Active Projects", "Dormant Projects", "Routines", "Rules")
    private val ids = Regex("[a-z0-9_-]+")
    fun parse(markdown: String): TodoConfig {
        val blocks = headings.associateWith { heading -> extract(markdown, heading) }
        val areasValue = blocks.getValue("Areas")["areas"]
        val areas = (areasValue as? List<*>)?.map { value ->
            if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST") val item = value as Map<String, Any?>
                Area(reqString(item, "id"), item["name"]?.toString() ?: reqString(item, "id"))
            } else Area(value.toString())
        } ?: emptyList()
        require(areas.isNotEmpty()) { "Areas must contain at least one area" }
        val areaIds = areas.map { it.id }.toSet()
        val projects = projectList(blocks.getValue("Active Projects"), true) + projectList(blocks.getValue("Dormant Projects"), false)
        require(projects.map { it.id }.distinct().size == projects.size) { "Project IDs must be unique" }
        projects.forEach { require(it.area in areaIds) { "Project '${it.id}' references unknown area '${it.area}'" } }
        val routines = list(blocks.getValue("Routines"), "routines").map { m ->
            val measure = runCatching { RoutineMeasure.valueOf(reqString(m, "measure").uppercase()) }.getOrElse { error("Routine '${m["id"]}' has invalid measure") }
            val window = strings(m["window"]).takeIf { it.isNotEmpty() }?.let { require(it.size == 2) { "Routine window needs two times" }; LocalTime.parse(it[0]) to LocalTime.parse(it[1]) }
            Routine(reqString(m,"id"), reqString(m,"name"), reqString(m,"area"), measure, number(m,"target").toInt(), daytypes(m), window)
        }
        require(routines.map { it.id }.distinct().size == routines.size) { "Routine IDs must be unique" }
        routines.forEach { require(it.area in areaIds) { "Routine '${it.id}' references unknown area '${it.area}'" }; require(it.target > 0) { "Routine '${it.id}' target must be positive" } }
        val rules = parseRules(blocks.getValue("Rules"))
        val allIds = rules.map { it.id }.toSet(); require(allIds.size == rules.size) { "Rule IDs must be unique" }
        rules.filterIsInstance<RoutineRule>().forEach { require(routines.any { r -> r.id == it.routine }) { "Rule '${it.id}' references unknown routine" } }
        rules.filterIsInstance<CompoundRule>().forEach { r -> (r.all.orEmpty()+r.any.orEmpty()+r.supersedes).forEach { require(it in allIds) { "Rule '${r.id}' references unknown rule '$it'" } } }
        return TodoConfig(areas, projects, routines, rules)
    }
    private fun extract(md: String, heading: String): Map<String, Any?> {
        val section = Regex("(?ms)^##\\s+${Regex.escape(heading)}\\s*$([\\s\\S]*?)(?=^##\\s|\\z)").find(md)?.groupValues?.get(1) ?: error("Missing heading: ## $heading")
        val yaml = Regex("(?ms)```(?:yaml|YAML)\\s*\\n(.*?)```").find(section)?.groupValues?.get(1) ?: error("Missing YAML block below ## $heading")
        val loaded = Yaml(SafeConstructor(LoaderOptions())).load<Any?>(yaml) ?: emptyMap<String, Any?>()
        return loaded as? Map<String, Any?> ?: error("## $heading YAML must be a mapping")
    }
    private fun projectList(m: Map<String,Any?>, active: Boolean) = list(m, if(active) "projects" else "projects").map { ConfigProject(reqString(it,"id"),reqString(it,"name"),reqString(it,"area"),strings(it["tags"]),active) }
    private fun parseRules(m: Map<String,Any?>): List<Rule> = list(m,"rules").map { x ->
        val id=reqString(x,"id"); val daytypes=daytypes(x); val effect=runCatching { RuleEffect.valueOf(reqString(x,"effect").uppercase()) }.getOrElse { error("Rule '$id' has invalid effect") }; val msg=x["message"]?.toString()
        when { x.containsKey("metric") -> { val metric=reqString(x,"metric"); require(metric in setOf("todos.count","todos.planned_minutes","todos.progress","routines.completions","routines.progress")){"Rule '$id' has invalid metric"}; val min=(x["min"] as? Number)?.toDouble(); val max=(x["max"] as? Number)?.toDouble(); require(min!=null||max!=null){"Rule '$id' needs min or max"}; MetricRule(id,metric,strings(x["areas"]),strings(x["loads"]),min,max,x["at"]?.toString()?.let(LocalTime::parse),daytypes,effect,msg) }
            x.containsKey("routine") -> { val state=reqString(x,"state"); require(state in setOf("pending","completed","missed")){"Rule '$id' has invalid state"}; RoutineRule(id,reqString(x,"routine"),state,daytypes,effect,msg) }
            x.containsKey("when") -> { val w=x["when"] as? Map<*,*> ?: error("Rule '$id' when must be a mapping"); val all=strings(w["all"]); val any=strings(w["any"]); require((all.isNotEmpty()) xor (any.isNotEmpty())){"Rule '$id' must have exactly one of all/any"}; CompoundRule(id,all.takeIf{it.isNotEmpty()},any.takeIf{it.isNotEmpty()},daytypes,effect,msg,strings(x["supersedes"])) }
            else -> error("Rule '$id' has no recognized rule type") }
    }
    @Suppress("UNCHECKED_CAST") private fun list(m:Map<String,Any?>, key:String)=(m[key] as? List<*>)?.map { it as? Map<String,Any?> ?: error("$key entries must be mappings") } ?: emptyList()
    private fun strings(v:Any?):List<String> = (v as? List<*>)?.map { it.toString() } ?: emptyList()
    private fun daytypes(m: Map<String, Any?>): List<String> {
        require(!m.containsKey("profiles")) { "'profiles' has been renamed to 'daytypes'" }
        return strings(m["daytypes"]).also { values ->
            require(values.all { it in setOf("work", "free") }) { "daytypes may only contain 'work' or 'free'" }
        }
    }
    private fun reqString(m:Map<String,Any?>,k:String)=m[k]?.toString()?.takeIf{it.isNotBlank()}?.also { if(k=="id") require(ids.matches(it)){"Invalid id '$it'"} } ?: error("Missing $k")
    private fun number(m:Map<String,Any?>,k:String)=(m[k] as? Number) ?: error("Missing numeric $k")
}
