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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Duration

@Component
class GitHubInteractionHistorySync(
    private val properties: DogfoodProperties,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val commandRunner by lazy {
        CommandRunner(properties.commandTimeout, minimumTimeout = Duration.ofSeconds(30))
    }

    private val probe by lazy {
        HostGhConfigProbe(Path.of(properties.repository), commandRunner)
    }

    fun hostProbe(): HostGhConfigProbe = probe

    fun resolvedRepoSlug(): String? = probe.resolveRepoSlug(properties.memory.github.repo)

    fun isAvailable(): Boolean =
        properties.memory.github.enabled && probe.isGhAuthenticated()

    fun loadPriorInteractionsMarkdown(): String {
        if (!isAvailable()) {
            return ""
        }
        val repo = resolvedRepoSlug() ?: return ""
        val label = properties.memory.github.label
        val output = runGh(
            "issue",
            "list",
            "--repo",
            repo,
            "--label",
            label,
            "--limit",
            properties.memory.maxPriorRuns.toString(),
            "--json",
            "number,title,url,body,createdAt",
        ) ?: return ""

        if (output.isBlank() || output == "[]") {
            return "(no GitHub issues with label `$label` in $repo)"
        }

        return buildString {
            appendLine("## GitHub dogfood threads ($repo, label `$label`)")
            appendLine()
            appendLine(output.trim())
        }
    }

    fun publishRun(record: InteractionRunRecord): String? {
        if (!isAvailable() || !properties.memory.github.syncOnComplete) {
            return null
        }
        val repo = resolvedRepoSlug() ?: return null
        val title = "dogfood: ${record.commit.take(12)} — ${record.processId}"
        val body = buildString {
            appendLine("<!-- embabel-dogfood-memory -->")
            appendLine()
            appendLine("- Process: `${record.processId}`")
            appendLine("- Commit: `${record.commit}`")
            appendLine("- Model: `${record.model}`")
            appendLine("- Files reviewed: ${record.filesReviewed}")
            appendLine()
            appendLine("## Report excerpt")
            appendLine()
            append(record.reportExcerpt.take(6000))
            if (record.ossInteractionsExcerpt.isNotBlank()) {
                appendLine()
                appendLine("## OSS interactions")
                appendLine()
                append(record.ossInteractionsExcerpt.take(4000))
            }
        }
        val url = runGh(
            "issue",
            "create",
            "--repo",
            repo,
            "--title",
            title,
            "--body",
            body,
            "--label",
            properties.memory.github.label,
        )?.lineSequence()?.lastOrNull { it.startsWith("https://") }

        if (url != null) {
            logger.info("Published dogfood memory to GitHub issue {}", url)
        }
        return url
    }

    private fun runGh(vararg args: String): String? =
        commandRunner.runSuccessOutput(
            listOf("gh", *args),
            Path.of(properties.repository),
        )
}
