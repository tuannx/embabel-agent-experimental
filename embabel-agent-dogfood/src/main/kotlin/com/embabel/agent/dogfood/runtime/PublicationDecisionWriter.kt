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

import com.embabel.agent.dogfood.domain.GitRemoteContext
import com.embabel.agent.dogfood.domain.PublicationAssessment
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.nio.file.Files
import java.nio.file.Path

object PublicationDecisionWriter {

    private val mapper = ObjectMapper().registerModule(kotlinModule())

    fun write(outputDirectory: Path, assessment: PublicationAssessment?): Path? {
        if (assessment == null) {
            return null
        }
        val payload = mapOf(
            "decision" to assessment.decision,
            "content" to assessment.content,
        )
        Files.createDirectories(outputDirectory)
        val path = outputDirectory.resolve("publication-decision.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), payload)
        return path
    }

    fun extractFromReport(markdown: String): String? {
        val section = Regex(
            """##\s+Publication decision\s*\n([\s\S]*?)(?=\n##\s|$)""",
            RegexOption.IGNORE_CASE,
        ).find(markdown)?.groupValues?.get(1).orEmpty()
        return when {
            section.contains("RAISE_PR", ignoreCase = true) -> "RAISE_PR"
            section.contains("STAY_LOCAL", ignoreCase = true) -> "STAY_LOCAL"
            section.contains("NEEDS_WORK", ignoreCase = true) -> "NEEDS_WORK"
            else -> null
        }
    }
}
