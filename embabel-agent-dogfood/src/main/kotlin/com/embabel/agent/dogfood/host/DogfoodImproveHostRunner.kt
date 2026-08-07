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
package com.embabel.agent.dogfood.host

import com.embabel.agent.dogfood.host.improve.ClaudeCodeImproveBackend
import com.embabel.agent.dogfood.host.improve.CodeImproveSpringHost
import com.embabel.agent.dogfood.host.improve.ImproveCoderRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Host-side entry point: apply improve-prompt.md using a switchable improve coder backend.
 *
 * Backends are selected with --backend (code or claude-code), matching NomiNomi
 * dogfood/config/improve-coders profiles.
 */
fun main(args: Array<String>) {
    val options = parseArgs(args)
    val repo = Path.of(options.repo).toAbsolutePath().normalize()
    val promptPath = repo.resolve(options.prompt).normalize()
    require(repo.isRegularFile().not() && Files.isDirectory(repo)) { "repo not found: $repo" }
    require(promptPath.isRegularFile()) { "prompt not found: $promptPath" }

    val prompt = Files.readString(promptPath)
    val scopedPaths = options.scopedPathsFile?.let { path ->
        val file = repo.resolve(path)
        if (file.isRegularFile()) {
            ObjectMapper().registerModule(kotlinModule()).readTree(file.toFile())
                .path("scopedPaths")
                .map { it.asText() }
        } else {
            emptyList()
        }
    }.orEmpty()

    val systemPrompt = buildString {
        appendLine("You are a host-side coding agent applying a dogfood improvement plan.")
        appendLine("Working directory: $repo")
        appendLine("Modify files only under these scoped paths (if any):")
        if (scopedPaths.isEmpty()) {
            appendLine("- (no explicit allowlist — stay minimal)")
        } else {
            scopedPaths.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("You may read, edit, write files and run terminal commands.")
        appendLine("Do not push to git remotes unless explicitly asked.")
        appendLine("Prefer DSL changes in dogfood/steps and dogfood/config over patching Embabel upstream.")
    }

    val request = ImproveCoderRequest(
        repo = repo,
        prompt = prompt,
        systemPrompt = systemPrompt,
        scopedPaths = scopedPaths,
        sessionId = options.sessionId,
        sessionOut = options.sessionOut?.let { repo.resolve(it).normalize() },
        maxTurns = options.maxTurns,
    )

    val status = when (options.backend) {
        "code" -> CodeImproveSpringHost.withContext { ctx ->
            CodeImproveSpringHost.agentCodeBackend(ctx).apply(request)
        }
        "claude-code" -> ClaudeCodeImproveBackend().apply(request)
        else -> error("unknown backend: ${options.backend}")
    }
    kotlin.system.exitProcess(status)
}

private data class RunnerOptions(
    val repo: String,
    val prompt: String,
    val backend: String = "code",
    val scopedPathsFile: String? = null,
    val sessionId: String? = null,
    val sessionOut: String? = "build/dogfood/improve-session.json",
    val maxTurns: Int = 40,
)

private fun parseArgs(args: Array<String>): RunnerOptions {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        when (val key = args[i]) {
            "--repo", "--prompt", "--plan", "--session", "--session-out", "--max-turns", "--backend" -> {
                map[key] = args.getOrElse(i + 1) { error("missing value for $key") }
                i += 2
            }
            else -> error("unknown arg: $key")
        }
    }
    return RunnerOptions(
        repo = map["--repo"] ?: error("--repo required"),
        prompt = map["--prompt"] ?: "build/dogfood/improve-prompt.md",
        backend = map["--backend"] ?: "code",
        scopedPathsFile = map["--plan"],
        sessionId = map["--session"],
        sessionOut = map["--session-out"],
        maxTurns = map["--max-turns"]?.toIntOrNull() ?: 40,
    )
}
