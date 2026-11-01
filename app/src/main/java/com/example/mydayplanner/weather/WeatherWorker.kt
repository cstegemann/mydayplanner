package com.example.mydayplanner.weather

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Periodic Android scheduler; does not keep the app permanently running. */
class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = com.example.mydayplanner.di.AppGraph.weatherRepository
        return try {
            // Persisted location is intentionally used instead of background GPS.
            // A failed download leaves the previous data intact.
            val success = repository.refresh()
            if (success) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "periodic_dwd_weather"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
