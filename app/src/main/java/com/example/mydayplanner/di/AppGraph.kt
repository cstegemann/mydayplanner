package com.example.mydayplanner.di

import android.content.Context
import com.example.mydayplanner.data.PlainJsonTodoRepository
import com.example.mydayplanner.data.TodoRepository

object AppGraph {
    lateinit var todoRepo: TodoRepository
        private set

    fun init(context:Context) {
        todoRepo = PlainJsonTodoRepository(context)
    }
}
