package com.example.mydayplanner.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoConfigParserTest {
    private val markdown = """
        ## Areas
        ```yaml
        areas:
          - id: work
            name: Work
          - id: physical
        ```
        ## Active Projects
        ```yaml
        projects:
          - id: app
            name: App
            area: work
            tags: [delivery]
        ```
        ## Dormant Projects
        ```yaml
        projects: []
        ```
        ## Routines
        ```yaml
        routines:
          - id: stretch
            name: Stretch
            area: physical
            measure: count
            target: 6
            daytypes: [work, free]
        ```
        ## Rules
        ```yaml
        rules:
          - id: little_activity
            metric: routines.progress
            areas: [physical]
            min: 50
            daytypes: [work]
            effect: warning
          - id: stretch_missed
            routine: stretch
            state: missed
            daytypes: [free]
            effect: replan
          - id: combined
            when:
              any: [little_activity, stretch_missed]
            daytypes: [work, free]
            effect: replan
            supersedes: [little_activity]
        ```
    """.trimIndent()

    @Test fun `parses all configuration sections and preserves routine order`() {
        val config = TodoConfigParser.parse(markdown)
        assertEquals(listOf("work", "physical"), config.areas.map { it.id })
        assertEquals("App", config.projects.single().name)
        assertEquals(listOf("stretch"), config.routines.map { it.id })
        assertEquals(listOf("work", "free"), config.routines.single().daytypes)
        assertEquals(listOf("work"), config.rules.first().daytypes)
        assertEquals(listOf("free"), config.rules[1].daytypes)
        assertEquals(listOf("work", "free"), config.rules.last().daytypes)
        assertTrue(config.rules.last() is CompoundRule)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects references to unknown areas`() {
        TodoConfigParser.parse(markdown.replace("area: physical", "area: nowhere"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unsupported daytypes`() {
        TodoConfigParser.parse(markdown.replace("daytypes: [work]", "daytypes: [off]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects the old profiles field`() {
        TodoConfigParser.parse(markdown.replace("daytypes: [work]", "profiles: [work]"))
    }
}
