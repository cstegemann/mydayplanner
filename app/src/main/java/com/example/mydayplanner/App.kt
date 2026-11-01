package com.example.mydayplanner

import android.app.Application
import com.example.mydayplanner.di.AppGraph
import com.example.mydayplanner.weather.WeatherWorker

class App: Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        WeatherWorker.schedule(this)
    }
}