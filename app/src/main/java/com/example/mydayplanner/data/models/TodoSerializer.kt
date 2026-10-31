package com.example.mydayplanner.data.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

/** Legacy estimates are an input alias only. All runtime consumers and writes use estimateMinutes. */
@OptIn(ExperimentalSerializationApi::class)
object TodoSerializer : JsonTransformingSerializer<Todo>(Todo.generatedSerializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        JsonObject(element.jsonObject.toMutableMap().apply {
            if ("estimateMinutes" !in this) {
                this["timePredicted"]?.let { this["estimateMinutes"] = it }
            }
            remove("timePredicted")
        })
}
