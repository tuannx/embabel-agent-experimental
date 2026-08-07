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
package com.embabel.agent.dogfood.memory

import com.embabel.agent.dogfood.config.DogfoodProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
class LocalInteractionHistoryStore(
    properties: DogfoodProperties,
    private val objectMapper: ObjectMapper = defaultMapper(),
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val historyRoot = Path.of(properties.outputDirectory, "history")
    private val indexPath = historyRoot.resolve("index.json")
    private val runsDir = historyRoot.resolve("runs")
    private val latestMarkdown = historyRoot.resolve("latest.md")
    private val maxPriorRuns = properties.memory.maxPriorRuns

    fun loadPriorInteractionsMarkdown(): String {
        val index = readIndex()
        if (index.runs.isEmpty()) {
            return "(no prior dogfood runs on disk)"
        }
        return index.runs
            .takeLast(maxPriorRuns)
            .joinToString(separator = "\n\n---\n\n") { run ->
                buildString {
                    appendLine("### Run `${run.processId}` @ `${run.commit.take(12)}`")
                    appendLine("- When: ${run.timestamp}")
                    appendLine("- Task: ${run.task}")
                    if (run.githubIssueUrl != null) {
                        appendLine("- GitHub: ${run.githubIssueUrl}")
                    }
                    if (run.claudeSessionId != null) {
                        appendLine("- Claude session: `${run.claudeSessionId}` (turn ${run.claudeTurnCount})")
                    }
                    appendLine()
                    appendLine(run.reportExcerpt.trim())
                    if (run.ossInteractionsExcerpt.isNotBlank()) {
                        appendLine()
                        appendLine("**OSS interactions excerpt:**")
                        appendLine(run.ossInteractionsExcerpt.trim())
                    }
                }
            }
    }

    fun latestClaudeSessionLink(): DogfoodCodingSessionLink? {
        val last = readIndex().runs.lastOrNull() ?: return null
        if (last.claudeSessionId == null) {
            return null
        }
        return DogfoodCodingSessionLink(
            claudeSessionId = last.claudeSessionId,
            turnCount = last.claudeTurnCount,
            totalCostUsd = 0.0,
        )
    }

    fun append(record: InteractionRunRecord) {
        Files.createDirectories(runsDir)
        val index = readIndex()
        val updated = index.copy(runs = (index.runs + record).takeLast(maxPriorRuns * 4))
        writeIndex(updated)
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(runsDir.resolve("${record.processId}.json").toFile(), record)
        Files.writeString(latestMarkdown, renderLatestMarkdown(record))
        logger.info("Appended dogfood interaction history for process {}", record.processId)
    }

    private fun readIndex(): InteractionHistoryIndex {
        if (!Files.isRegularFile(indexPath)) {
            return InteractionHistoryIndex()
        }
        return objectMapper.readValue(indexPath.toFile())
    }

    private fun writeIndex(index: InteractionHistoryIndex) {
        Files.createDirectories(historyRoot)
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexPath.toFile(), index)
    }

    private fun renderLatestMarkdown(record: InteractionRunRecord): String = buildString {
        appendLine("# Latest dogfood interaction")
        appendLine()
        appendLine("- Process: `${record.processId}`")
        appendLine("- Commit: `${record.commit}`")
        appendLine("- Model: `${record.model}`")
        appendLine("- Report: `${record.reportPath}`")
        record.githubIssueUrl?.let { appendLine("- GitHub: $it") }
        appendLine()
        append(record.reportExcerpt.trim())
        appendLine()
    }

    companion object {
        private fun defaultMapper(): ObjectMapper =
            ObjectMapper().registerModule(kotlinModule()).registerModule(JavaTimeModule())
    }
}
