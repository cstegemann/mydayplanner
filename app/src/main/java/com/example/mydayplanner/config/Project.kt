package com.example.mydayplanner.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ProjectAsStringSerializer::class)
enum class Project(
    val displayName: String,
    val sortOrder: Int,
    val selectableInPicker: Boolean = true,  // ← hide from “new task” dropdown when false
    val countInTimer: Boolean = true,
    val aliases: Set<String> = emptySet()    // ← decode legacy labels/typos
) {

    TIME("TIME", sortOrder = 1),
    GW("GW", sortOrder = 2),
    Other("Other", countInTimer = false, sortOrder = 98),
    META("META", selectableInPicker = false, countInTimer = false, sortOrder = 99);

    companion object {
        val all = entries
        val timedList: List<Project> = all.filter { it.countInTimer }

        /** What the add-task dropdown should show */
        val pickerList: List<Project> = all.filter { it.selectableInPicker }

        /** Map a string (case-insensitive) to an enum; unknown → Other */
        fun fromLabel(label: String?): Project {
            if (label.isNullOrBlank()) return Other
            val norm = label.trim().lowercase()
            // match displayName
            all.firstOrNull { it.displayName.lowercase() == norm }?.let { return it }
            // match aliases
            all.firstOrNull { norm in it.aliases.map(String::lowercase) }?.let { return it }
            return Other
        }
    }
}

/** Serialize as a plain string in JSON; unknown values decode to Other. */
object ProjectAsStringSerializer : KSerializer<Project> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Project", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Project =
        Project.fromLabel(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Project) =
        encoder.encodeString(value.displayName)
}