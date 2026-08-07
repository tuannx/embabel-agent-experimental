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
package com.embabel.agent.dogfood.repo

import com.embabel.agent.dogfood.config.DogfoodProperties
import com.embabel.agent.dogfood.domain.GitRemoteContext
import com.embabel.agent.dogfood.memory.CommandRunner
import com.embabel.agent.dogfood.memory.hasNonBlankGhToken
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

@Service
class GitRemoteContextService(
    private val properties: DogfoodProperties,
) {

    private val commandRunner = CommandRunner(properties.commandTimeout)
    private val repository: Path get() = Path.of(properties.repository)

    fun capture(): GitRemoteContext {
        require(Files.isDirectory(repository)) {
            "Repository path does not exist: ${properties.repository}"
        }

        val branch = git("branch", "--show-current").ifBlank { "(detached)" }
        val status = git("status", "--short", "--branch")
        val remotes = git("remote", "-v")
        val upstream = runCatching { git("rev-parse", "--abbrev-ref", "@{upstream}") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "HEAD" }
            ?: "(none)"
        val aheadBehind = if (upstream != "(none)") {
            runCatching { git("rev-list", "--left-right", "--count", "$upstream...HEAD") }.getOrNull()
        } else {
            null
        }
        val recentCommits = git("log", "-5", "--oneline")
        val dogfoodTracked = git("ls-files", "embabel-agent-dogfood")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val dogfoodStatusLines = status.lines().filter { "embabel-agent-dogfood" in it }
        val dogfoodModuleLocalOnly = dogfoodTracked.isEmpty() || dogfoodStatusLines.any {
            it.startsWith("??") || it.startsWith("A ") || it.startsWith("AM")
        }
        val hasUnpushedWork = when {
            aheadBehind == null -> status.lines().any { it.isNotBlank() }
            else -> {
                val counts = aheadBehind.split("\t", " ").filter { it.isNotBlank() }
                val ahead = counts.getOrNull(1)?.toIntOrNull() ?: 0
                ahead > 0 || status.lines().any { line ->
                    line.isNotBlank() && !line.startsWith("##")
                }
            }
        }
        val openPullRequestUrl = queryOpenPullRequest(branch)

        val content = buildString {
            appendLine("## Six-direction context (git / GitHub)")
            appendLine()
            appendLine("### Up (upstream / parent)")
            appendLine("- Upstream branch: `$upstream`")
            appendLine("- Ahead/behind vs upstream: ${aheadBehind ?: "(no upstream configured)"}")
            appendLine("- Remotes:")
            appendLine(remotes.ifBlank { "(none)" })
            appendLine()
            appendLine("### Down (fork / consumers)")
            appendLine("- Current branch: `$branch`")
            appendLine("- Open PR for this branch: ${openPullRequestUrl ?: "(none)"}")
            appendLine()
            appendLine("### Before (history)")
            appendLine("```")
            appendLine(recentCommits.ifBlank { "(no commits)" })
            appendLine("```")
            appendLine()
            appendLine("### After (publication intent)")
            appendLine("- Dogfood module tracked files: ${dogfoodTracked.size}")
            appendLine("- Dogfood module appears local-only/unpushed: $dogfoodModuleLocalOnly")
            appendLine("- Unpushed or dirty work detected: $hasUnpushedWork")
            appendLine()
            appendLine("### Inside (working tree)")
            appendLine("```")
            appendLine(status.ifBlank { "(clean)" })
            appendLine("```")
            if (dogfoodStatusLines.isNotEmpty()) {
                appendLine()
                appendLine("Dogfood module status lines:")
                dogfoodStatusLines.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("### Outside (host GitHub auth)")
            appendLine("- GH token present: ${hasNonBlankGhToken(System.getenv("GITHUB_TOKEN"), System.getenv("GH_TOKEN"))}")
            appendLine("- Experimental repo policy: prefer draft PR when quality gate passes and work is local-only")
        }

        return GitRemoteContext(
            content = content.toString(),
            branch = branch,
            hasUnpushedWork = hasUnpushedWork,
            dogfoodModuleLocalOnly = dogfoodModuleLocalOnly,
            openPullRequestUrl = openPullRequestUrl,
        )
    }

    private fun queryOpenPullRequest(branch: String): String? {
        if (!hasNonBlankGhToken(System.getenv("GITHUB_TOKEN"), System.getenv("GH_TOKEN"))) {
            return null
        }
        val json = commandRunner.runSuccessOutput(
            listOf("gh", "pr", "list", "--head", branch, "--json", "url,state", "--limit", "1"),
            repository,
        ) ?: return null
        val urlMatch = Regex("""https://github\.com/[^\s"]+""").find(json)
        return urlMatch?.value
    }

    private fun git(vararg args: String): String =
        commandRunner.runSuccessOutput(listOf("git", *args), repository).orEmpty()
}
