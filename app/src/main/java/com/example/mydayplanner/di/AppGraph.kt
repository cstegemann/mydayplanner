package com.example.mydayplanner.di

import android.content.Context
import com.example.mydayplanner.data.PlainJsonTodoRepository
import com.example.mydayplanner.data.TodoRepository
import com.example.mydayplanner.watch.GarminWatchSync
import com.example.mydayplanner.weather.WeatherRepository

object AppGraph {
    lateinit var todoRepo: TodoRepository
        private set
    lateinit var weatherRepository: WeatherRepository
        private set
    lateinit var garminWatchSync: GarminWatchSync
        private set

    fun init(context:Context) {
        todoRepo = PlainJsonTodoRepository(context)
        weatherRepository = WeatherRepository(context)
        garminWatchSync = GarminWatchSync(context, todoRepo, weatherRepository)
    }
}
