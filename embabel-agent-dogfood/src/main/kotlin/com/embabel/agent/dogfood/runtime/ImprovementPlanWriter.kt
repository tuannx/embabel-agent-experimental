/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.agent.dogfood.runtime

import com.embabel.agent.dogfood.domain.DogfoodImprovementPlan
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.nio.file.Files
import java.nio.file.Path

object ImprovementPlanWriter {

    private val mapper = ObjectMapper().registerModule(kotlinModule())

    fun write(
        outputDirectory: Path,
        publicationDecision: String?,
        plan: DogfoodImprovementPlan?,
    ): Path? {
        if (plan == null || publicationDecision != "NEEDS_WORK") {
            return null
        }
        val scopedPaths = extractBulletPaths(plan.content, "Scoped paths")
        val verifyCommands = extractBashCommands(plan.content)
        val payload = mapOf(
            "publicationDecision" to publicationDecision,
            "content" to plan.content,
            "scopedPaths" to scopedPaths,
            "verifyCommands" to verifyCommands,
        )
        Files.createDirectories(outputDirectory)
        val jsonPath = outputDirectory.resolve("improvement-plan.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), payload)
        val promptPath = outputDirectory.resolve("improve-prompt.md")
        Files.writeString(promptPath, plan.content)
        return jsonPath
    }

    fun parseFromReport(markdown: String): DogfoodImprovementPlan? {
        val section = Regex(
            """##\s+Improvement plan\s*\n([\s\S]*?)(?=\n## (?![#])|$)""",
            RegexOption.IGNORE_CASE,
        ).find(markdown)?.groupValues?.get(1)?.trim().orEmpty()
        if (section.isBlank() || section.contains("no host improvement", ignoreCase = true)) {
            return null
        }
        return DogfoodImprovementPlan(content = section)
    }

    fun extractBulletPaths(markdown: String, heading: String): List<String> {
        val section = sectionAfterHeading(markdown, heading, level = 3)
            .ifBlank { sectionAfterHeading(markdown, heading, level = 2) }
        return section.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("-") }
            .map { it.removePrefix("-").trim().removePrefix("`").removeSuffix("`") }
            .filter { it.isNotEmpty() }
            .toList()
    }

    fun extractBashCommands(markdown: String): List<String> {
        val verifySection = sectionAfterHeading(markdown, "Verify commands", level = 3)
            .ifBlank { sectionAfterHeading(markdown, "Verify commands", level = 2) }
        val fenced = Regex("""```(?:bash|sh)?\s*\n([\s\S]*?)```""").findAll(verifySection)
        val fromFences = fenced.flatMap { match ->
            match.groupValues[1].lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        }.toList()
        if (fromFences.isNotEmpty()) {
            return fromFences
        }
        return verifySection.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("-") }
            .map { line ->
                line.removePrefix("-").trim()
                    .removePrefix("`").removeSuffix("`")
            }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
    }

    private fun sectionAfterHeading(markdown: String, heading: String, level: Int = 2): String {
        val prefix = "#".repeat(level)
        val stopAt = when (level) {
            3 -> """(?=\n###\s|\n## (?![#])|\n# (?![#])|$)"""
            else -> """(?=\n## (?![#])|\n# (?![#])|$)"""
        }
        return Regex(
            """${Regex.escape(prefix)}\s+${Regex.escape(heading)}\s*\n([\s\S]*?)$stopAt""",
            RegexOption.IGNORE_CASE,
        ).find(markdown)?.groupValues?.get(1).orEmpty()
    }
}
