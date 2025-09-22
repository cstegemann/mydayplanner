package com.example.mydayplanner.data.models

import com.example.mydayplanner.config.Project
import kotlinx.serialization.Serializable

@Serializable
data class DayTracking(
    val current: Project? = null,   // null means “None”
    val startedAt: Long? = null,    // epoch millis when current started
    val totals: Map<Project, Long> = emptyMap() // per-project millis accrued
)