package com.example.mydayplanner.ui.home

import com.example.mydayplanner.config.*
import com.example.mydayplanner.data.models.RoutineProgress
import com.example.mydayplanner.data.models.Todo
import java.time.LocalTime

internal fun evaluateRules(config: TodoConfig, todos: List<Todo>, progress: RoutineProgress, freeDay: Boolean, now: LocalTime = LocalTime.now()): List<RuleNotice> {
    val daytype = if (freeDay) "free" else "work"
    fun applies(daytypes: List<String>) = daytypes.isEmpty() || daytype in daytypes
    val projectAreas = config.projects.associate { it.id to it.area }
    val routineById = config.routines.associateBy { it.id }
    fun routineApplicable(r: Routine) = applies(r.daytypes)
    fun routineState(r: Routine): String {
        val value = progress.values[r.id] ?: 0
        if (value >= r.target) return "completed"
        return if (r.window?.second?.let { !now.isBefore(it) } == true) "missed" else "pending"
    }
    val truth = linkedMapOf<String, Boolean>()
    config.rules.filterNot { it is CompoundRule }.forEach { rule ->
        truth[rule.id] = applies(rule.daytypes) && when (rule) {
            is RoutineRule -> routineById[rule.routine]?.let { routineApplicable(it) && routineState(it) == rule.state } == true
            is MetricRule -> {
                if (rule.at != null && now.isBefore(rule.at)) false else {
                    val matchingTodos = todos.filter { todo ->
                        val area = todo.liveTrackId?.let(projectAreas::get)
                        (rule.areas.isEmpty() || area in rule.areas) && (rule.loads.isEmpty() || todo.difficulty?.loadName in rule.loads)
                    }
                    val matchingRoutines = config.routines.filter { routineApplicable(it) && (rule.areas.isEmpty() || it.area in rule.areas) }
                    val value = when (rule.metric) {
                        "todos.count" -> matchingTodos.size.toDouble()
                        "todos.planned_minutes" -> matchingTodos.sumOf { it.estimateMinutes }.toDouble()
                        "todos.progress" -> matchingTodos.let { ts -> if (ts.isEmpty()) 0.0 else ts.count { it.done } * 100.0 / ts.size }
                        "routines.completions" -> matchingRoutines.count { (progress.values[it.id] ?: 0) > 0 }.toDouble()
                        else -> matchingRoutines.let { rs -> if (rs.isEmpty()) 0.0 else rs.sumOf { ((progress.values[it.id] ?: 0) * 100.0 / it.target).coerceAtMost(100.0) } / rs.size }
                    }
                    (rule.min != null && value < rule.min) || (rule.max != null && value > rule.max)
                }
            }
            else -> false
        }
    }
    config.rules.filterIsInstance<CompoundRule>().forEach { r ->
        truth[r.id] = applies(r.daytypes) && (r.all?.all { truth[it] == true } ?: r.any.orEmpty().any { truth[it] == true })
    }
    val superseded = config.rules.filterIsInstance<CompoundRule>().filter { truth[it.id] == true }.flatMap { it.supersedes }.toSet()
    return config.rules.filter { truth[it.id] == true && it.id !in superseded }.map { RuleNotice(it.id, it.effect, it.message ?: it.id.replace('_',' ')) }
}

private val com.example.mydayplanner.config.TaskDifficulty.loadName: String
    get() = when (this) {
        com.example.mydayplanner.config.TaskDifficulty.FunNormal -> "recharge"
        com.example.mydayplanner.config.TaskDifficulty.TediousNormal -> "light"
        com.example.mydayplanner.config.TaskDifficulty.FunDraining -> "moderate"
        com.example.mydayplanner.config.TaskDifficulty.TediousDraining -> "high"
    }
