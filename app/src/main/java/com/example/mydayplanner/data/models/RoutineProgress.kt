package com.example.mydayplanner.data.models

import kotlinx.serialization.Serializable

@Serializable
data class RoutineProgress(val values: Map<String, Int> = emptyMap(), val collapsed: Boolean = false)
