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
import com.embabel.agent.dogfood.domain.InteractionMemory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class InteractionHistoryService(
    private val properties: DogfoodProperties,
    private val localStore: LocalInteractionHistoryStore,
    private val githubSync: GitHubInteractionHistorySync,
) {

    fun loadMemory(task: String): InteractionMemory {
        if (!properties.memory.enabled) {
            return InteractionMemory(task = task, priorInteractionsMarkdown = "(memory disabled)")
        }
        val local = localStore.loadPriorInteractionsMarkdown()
        val github = githubSync.loadPriorInteractionsMarkdown()
        val claudeLink = localStore.latestClaudeSessionLink()
        val markdown = buildString {
            appendLine("### Local dogfood history")
            appendLine(local)
            if (github.isNotBlank()) {
                appendLine()
                appendLine(github)
            }
        }
        return InteractionMemory(
            task = task,
            priorInteractionsMarkdown = markdown,
            claudeSessionId = claudeLink?.claudeSessionId,
            githubThreadUrl = extractLatestGithubUrl(github),
        )
    }

    fun recordRun(
        processId: String,
        commit: String,
        model: String,
        filesReviewed: Int,
        task: String,
        reportPath: String,
        reportContent: String,
        ossInteractionsExcerpt: String = "",
        claudeSessionLink: DogfoodCodingSessionLink? = null,
    ): InteractionRunRecord {
        val record = InteractionRunRecord(
            processId = processId,
            commit = commit,
            timestamp = Instant.now(),
            task = task,
            model = model,
            filesReviewed = filesReviewed,
            reportPath = reportPath,
            reportExcerpt = excerpt(reportContent, 4000),
            ossInteractionsExcerpt = excerpt(ossInteractionsExcerpt, 2000),
            claudeSessionId = claudeSessionLink?.claudeSessionId,
            claudeTurnCount = claudeSessionLink?.turnCount ?: 0,
        )
        if (!properties.memory.enabled) {
            return record
        }
        val withGithub = if (properties.memory.github.syncOnComplete) {
            val issueUrl = githubSync.publishRun(record)
            if (issueUrl != null) record.copy(githubIssueUrl = issueUrl) else record
        } else {
            record
        }
        localStore.append(withGithub)
        return withGithub
    }

    private fun excerpt(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars) + "\n\n…(truncated)"

    private fun extractLatestGithubUrl(githubMarkdown: String): String? =
        Regex("https://github\\.com/[^\\s)\"]+").find(githubMarkdown)?.value
}
