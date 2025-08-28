package com.example.mydayplanner.data.models


import java.util.UUID

@kotlinx.serialization.Serializable
data class Todo(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val done: Boolean = false,
    val timePredicted: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val important: Boolean = false
)