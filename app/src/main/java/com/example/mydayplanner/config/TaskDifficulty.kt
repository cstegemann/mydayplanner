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
    const val funQuestionLabel = "Is it fun?"
    const val drainingQuestionLabel = "Is it draining?"

    const val funLabel = "Fun"
    const val tediousLabel = "Tedious"
    const val normalLabel = "Normal"
    const val drainingLabel = "Draining"

    val doneColor: Color = Color(0xFF7E57C2)

    private val lushGreen = Color(0xFF43A047)
    private val paleGreen = Color(0xFF66BB6A)
    private val paleYellow = Color(0xFFFFE2A8)
    private val orange = Color(0xFFFB8C00)

    val pickerPositiveColor: Color = lushGreen
    val pickerCautionColor: Color = paleYellow

    val tiles: List<DifficultyTileDef> = listOf(
        DifficultyTileDef(TaskDifficulty.FunNormal, "$funLabel + $normalLabel", lushGreen),
        DifficultyTileDef(TaskDifficulty.FunDraining, "$funLabel + $drainingLabel", paleGreen),
        DifficultyTileDef(TaskDifficulty.TediousNormal, "$tediousLabel + $normalLabel", paleYellow),
        DifficultyTileDef(TaskDifficulty.TediousDraining, "$tediousLabel + $drainingLabel", orange)
    )

    val byDifficulty: Map<TaskDifficulty, DifficultyTileDef> = tiles.associateBy { it.difficulty }
}
