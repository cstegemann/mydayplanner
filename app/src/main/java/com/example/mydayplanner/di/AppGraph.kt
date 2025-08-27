package com.example.mydayplanner.di

import com.example.mydayplanner.data.InMemoryTodoRepository
import com.example.mydayplanner.data.TodoRepository

object AppGraph {
    val todoRepo: TodoRepository by lazy { InMemoryTodoRepository() }
}
