package com.example.mydayplanner

import android.app.Application
import com.example.mydayplanner.di.AppGraph

class App: Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}