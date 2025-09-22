package com.example.mydayplanner.ui

fun formatMinutesHM(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "$h:${m.toString().padStart(2, '0')}"
}

fun formatEstimate(mins: Int) = when (mins) {
    60 -> "1 h"
    90 -> "1.5 h"
    120 -> "2 h"
    180 -> "3 h"
    else -> "$mins min"
}

