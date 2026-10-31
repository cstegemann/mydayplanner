package com.example.mydayplanner.data.models


import com.example.mydayplanner.config.Project
import com.example.mydayplanner.config.TaskDifficulty
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = TodoSerializer::class)
data class Todo(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val important: Boolean = false,
    @EncodeDefault val estimateMinutes: Int = 15,
    val project: Project = Project.Other,
    val pushedToTomorrow: Boolean = false,
    val liveTrackId: String? = null,
    val deferredUntil: String? = null,
    val difficulty: TaskDifficulty? = null
) {
    fun isDeferred(onDate: java.time.LocalDate): Boolean =
        deferredUntil?.let { runCatching { java.time.LocalDate.parse(it).isAfter(onDate) }.getOrDefault(false) }
            ?: pushedToTomorrow

    val trackLabel: String get() = liveTrackId ?: project.displayName
}
