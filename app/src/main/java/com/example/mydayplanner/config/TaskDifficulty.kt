package com.example.mydayplanner.config

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
enum class TaskDifficulty(val key: String) {
    FunNormal("fun_normal"),
    FunDraining("fun_draining"),
    TediousNormal("tedious_normal"),
    TediousDraining("tedious_draining")
}

data class DifficultyTileDef(
    val difficulty: TaskDifficulty,
    val title: String,
    val color: Color
)

object TaskDifficultyDef {
    const val topAxisLabel = "Tedious"
    const val bottomAxisLabel = "Fun"
    const val leftAxisLabel = "Normal"
    const val rightAxisLabel = "Draining"

    val doneColor: Color = Color(0xFF7E57C2)

    private val lushGreen = Color(0xFF43A047)
    private val greenToOrange1 = Color(0xFF66BB6A)
    private val greenToOrange2 = Color(0xFFFFB74D)
    private val orange = Color(0xFFFB8C00)

    val tiles: List<DifficultyTileDef> = listOf(
        DifficultyTileDef(TaskDifficulty.FunNormal, "$bottomAxisLabel + $leftAxisLabel", lushGreen),
        DifficultyTileDef(TaskDifficulty.FunDraining, "$bottomAxisLabel + $rightAxisLabel", greenToOrange1),
        DifficultyTileDef(TaskDifficulty.TediousNormal, "$topAxisLabel + $leftAxisLabel", greenToOrange2),
        DifficultyTileDef(TaskDifficulty.TediousDraining, "$topAxisLabel + $rightAxisLabel", orange)
    )

    val byDifficulty: Map<TaskDifficulty, DifficultyTileDef> = tiles.associateBy { it.difficulty }
}

