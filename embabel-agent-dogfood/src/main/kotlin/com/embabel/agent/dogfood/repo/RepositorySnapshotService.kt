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
import com.embabel.agent.dogfood.domain.InteractionMemory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

data class RepositorySnapshot(
    val commit: String,
    val status: String,
    val filesReviewed: Int,
    val body: String,
)

@Service
class RepositorySnapshotService(
    private val properties: DogfoodProperties,
) {

    fun capture(memory: InteractionMemory, gitRemoteContext: GitRemoteContext): RepositorySnapshot {
        val repository = Path.of(properties.repository)
        require(Files.isDirectory(repository)) {
            "Repository path does not exist or is not a directory: ${properties.repository}"
        }

        val commit = git(repository, "rev-parse", "HEAD")
        val status = git(repository, "status", "--short")
        val trackedPaths = git(repository, "ls-files", "--cached", "--others", "--exclude-standard")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .sortedWith(snapshotPathOrder())
            .take(properties.maxFiles)
            .toList()

        val bodyBuilder = StringBuilder()
        bodyBuilder.appendLine("Task:")
        bodyBuilder.appendLine(memory.task)
        bodyBuilder.appendLine()
        if (memory.claudeSessionId != null) {
            bodyBuilder.appendLine("Claude Code session (resume):")
            bodyBuilder.appendLine(memory.claudeSessionId)
            bodyBuilder.appendLine()
        }
        if (memory.githubThreadUrl != null) {
            bodyBuilder.appendLine("GitHub thread:")
            bodyBuilder.appendLine(memory.githubThreadUrl)
            bodyBuilder.appendLine()
        }
        bodyBuilder.appendLine("Prior dogfood interactions:")
        bodyBuilder.appendLine(memory.priorInteractionsMarkdown)
        bodyBuilder.appendLine()
        bodyBuilder.appendLine(gitRemoteContext.content)
        bodyBuilder.appendLine()
        bodyBuilder.appendLine("Commit: $commit")
        bodyBuilder.appendLine()
        bodyBuilder.appendLine("Working tree status:")
        bodyBuilder.appendLine(if (status.isBlank()) "(clean)" else status)
        bodyBuilder.appendLine()
        bodyBuilder.appendLine("Repository files:")
        var remainingBytes = properties.maxSnapshotBytes
        var filesIncluded = 0
        for (relativePath in trackedPaths) {
            val file = repository.resolve(relativePath).normalize()
            require(file.startsWith(repository)) { "Refusing to read path outside repository: $relativePath" }
            if (!Files.isRegularFile(file)) {
                continue
            }
            val content = Files.readAllBytes(file)
                .take(properties.maxFileBytes)
                .toByteArray()
                .toString(StandardCharsets.UTF_8)
            val section = buildString {
                appendLine("--- $relativePath ---")
                appendLine(content)
                appendLine()
            }
            if (section.length > remainingBytes) {
                bodyBuilder.appendLine("--- snapshot truncated at ${properties.maxSnapshotBytes} bytes ---")
                break
            }
            bodyBuilder.append(section)
            remainingBytes -= section.length
            filesIncluded++
        }
        val body = bodyBuilder.toString()

        return RepositorySnapshot(
            commit = commit,
            status = status,
            filesReviewed = filesIncluded,
            body = body,
        )
    }

    private fun snapshotPathOrder(): Comparator<String> =
        compareBy<String> { path ->
            when {
                path.startsWith("dogfood/") -> 0
                path.startsWith("scripts/") -> 1
                path.startsWith("docs/") -> 2
                else -> 3
            }
        }.thenBy { it }

    private fun git(repository: Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(repository.toFile())
            .redirectErrorStream(true)
            .start()
        val completed = process.waitFor(properties.commandTimeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroyForcibly()
            error("git ${args.joinToString(" ")} timed out after ${properties.commandTimeout}")
        }
        if (process.exitValue() != 0) {
            val output = process.inputStream.bufferedReader().readText()
            error("git ${args.joinToString(" ")} failed with exit code ${process.exitValue()}: $output")
        }
        return process.inputStream.bufferedReader().readText().trim()
    }
}
