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
package com.embabel.agent.dogfood.host.improve

import com.embabel.agent.api.common.Actor
import com.embabel.agent.api.common.AiBuilder
import com.embabel.agent.claudecode.ClaudeCodeAgentExecutor
import com.embabel.agent.claudecode.ClaudeCodeAllowedTool
import com.embabel.agent.claudecode.ClaudeCodePermissionMode
import com.embabel.agent.claudecode.ClaudeCodeResult
import com.embabel.agent.domain.library.code.SoftwareProject
import com.embabel.agent.dogfood.config.DogfoodProperties
import com.embabel.agent.prompt.persona.Instruction
import com.embabel.common.ai.model.LlmOptions
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

data class ImproveCoderRequest(
    val repo: Path,
    val prompt: String,
    val systemPrompt: String,
    val scopedPaths: List<String> = emptyList(),
    val sessionId: String? = null,
    val sessionOut: Path? = null,
    val maxTurns: Int = 40,
)

interface ImproveCoderBackend {
    val id: String
    fun apply(request: ImproveCoderRequest): Int
}

class AgentCodeImproveBackend(
    private val aiBuilder: AiBuilder,
    private val properties: DogfoodProperties,
) : ImproveCoderBackend {
    override val id: String = "code"

    override fun apply(request: ImproveCoderRequest): Int {
        val project = SoftwareProject(request.repo.toString())
        val llm = LlmOptions(
            model = properties.model,
            maxTokens = properties.maxOutputTokens,
        )
        val persona = Instruction(
            buildString {
                appendLine(request.systemPrompt)
                appendLine()
                append(project.notes())
            },
        )
        val actor = Actor(persona, llm)
        val runner = actor.promptRunner(aiBuilder.ai()).withReference(project)
        val text = runner generateText request.prompt
        println("[dogfood-improve-host:code] completed (${text.length} chars)")
        return 0
    }
}

class ClaudeCodeImproveBackend : ImproveCoderBackend {
    override val id: String = "claude-code"

    override fun apply(request: ImproveCoderRequest): Int {
        val executor = ClaudeCodeAgentExecutor(
            name = "dogfood-improve",
            description = "Apply dogfood improvement plan on host",
            defaultPermissionMode = ClaudeCodePermissionMode.ACCEPT_EDITS,
            defaultTimeout = 30.minutes,
        )
        executor.checkAvailability()?.let { denied ->
            System.err.println("[dogfood-improve-host:claude-code] ${denied.reason}")
            return 1
        }
        val tools = ClaudeCodeAllowedTool.entries.toList()
        val result = executor.execute(
            prompt = request.prompt,
            workingDirectory = request.repo,
            allowedTools = tools,
            maxTurns = request.maxTurns,
            permissionMode = ClaudeCodePermissionMode.ACCEPT_EDITS,
            sessionId = request.sessionId,
            systemPrompt = request.systemPrompt,
            streamOutput = true,
        )
        return when (result) {
            is ClaudeCodeResult.Success -> {
                request.sessionOut?.let { out ->
                    Files.createDirectories(out.parent)
                    val payload = mapOf(
                        "sessionId" to result.sessionId,
                        "backend" to id,
                        "numTurns" to result.numTurns,
                        "costUsd" to result.costUsd,
                        "summary" to result.result.take(2000),
                    )
                    ObjectMapper().registerModule(kotlinModule())
                        .writerWithDefaultPrettyPrinter()
                        .writeValue(out.toFile(), payload)
                    println("[dogfood-improve-host:claude-code] session file=$out")
                }
                println("[dogfood-improve-host:claude-code] success turns=${result.numTurns} cost=\$${result.costUsd}")
                0
            }
            is ClaudeCodeResult.Failure -> {
                System.err.println("[dogfood-improve-host:claude-code] failed: ${result.error}")
                1
            }
            is ClaudeCodeResult.Denied -> {
                System.err.println("[dogfood-improve-host:claude-code] denied: ${result.reason}")
                1
            }
        }
    }
}
