package com.example.mydayplanner.data.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.*
import org.junit.Test

class TodoSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `reads legacy only estimate with default serializer`() {
        val todo = json.decodeFromString<Todo>("""{"text":"Legacy task","timePredicted":45}""")
        assertEquals(45, todo.estimateMinutes)
    }

    @Test fun `current estimate wins over stale original regardless of JSON field order`() {
        for (fields in listOf(
            "\"timePredicted\":15,\"estimateMinutes\":60",
            "\"estimateMinutes\":60,\"timePredicted\":15"
        )) {
            assertEquals(60, json.decodeFromString<Todo>("""{"text":"Edited task",$fields}""").estimateMinutes)
        }
    }

    @Test fun `zero current estimate is not replaced by legacy or default`() {
        assertEquals(0, json.decodeFromString<Todo>("""{"text":"No estimate","timePredicted":30,"estimateMinutes":0}""").estimateMinutes)
        assertEquals(0, json.decodeFromString<Todo>("""{"text":"Legacy zero","timePredicted":0}""").estimateMinutes)
    }

    @Test fun `default estimate remains 15 when neither field exists`() {
        assertEquals(15, json.decodeFromString<Todo>("""{"text":"No fields"}""").estimateMinutes)
    }

    @Test fun `editing old task writes only updated canonical estimate and preserves other fields`() {
        val todo = json.decodeFromString<Todo>("""{"id":"stable-id","text":"Old","timePredicted":45,"createdAt":123,"important":true,"deferredUntil":"2026-11-02"}""")
        val edited = todo.copy(text = "Edited", estimateMinutes = 90)
        val encoded = json.encodeToString(edited)
        val fields = json.parseToJsonElement(encoded).jsonObject
        assertFalse("timePredicted" in fields)
        assertEquals(90, fields.getValue("estimateMinutes").jsonPrimitive.int)
        assertEquals(edited, json.decodeFromString<Todo>(encoded))
        assertEquals("stable-id", edited.id)
        assertEquals(123L, edited.createdAt)
        assertEquals("2026-11-02", edited.deferredUntil)
    }

    @Test fun `always writes canonical estimate including default and with encodeDefaults enabled`() {
        for (codec in listOf(json, Json { encodeDefaults = true })) {
            val todo = Todo(text = "New task")
            val fields = codec.parseToJsonElement(codec.encodeToString(todo)).jsonObject
            assertEquals(15, fields.getValue("estimateMinutes").jsonPrimitive.int)
            assertFalse("timePredicted" in fields)
        }
    }

    @Test fun `day lists mix old and new records and normalize only when saved`() {
        val todos = json.decodeFromString<List<Todo>>("""[{"text":"Old","createdAt":123,"timePredicted":25},{"text":"Both","createdAt":123,"timePredicted":10,"estimateMinutes":40},{"text":"Current","createdAt":123,"estimateMinutes":60}]""")
        assertEquals(listOf(25, 40, 60), todos.map { it.estimateMinutes })
        val encoded = json.encodeToString(todos)
        assertFalse(encoded.contains("timePredicted"))
        assertEquals(todos, json.decodeFromString<List<Todo>>(encoded))
    }
}
