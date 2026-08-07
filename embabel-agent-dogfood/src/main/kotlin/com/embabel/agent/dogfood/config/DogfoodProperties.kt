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
package com.embabel.agent.dogfood.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("dogfood")
data class DogfoodProperties(
    val dsl: Dsl = Dsl(),
    val repository: String = "/workspace/repo",
    val outputDirectory: String = "/output",
    val task: String = "As a top 1% OSS Java/Kotlin engineer, dogfood this Embabel experimental repo: review code, gate against contributor principles, and draft concise GitHub interactions.",
    val model: String = "deepseek-v4-flash",
    val maxFiles: Int = 18,
    val maxFileBytes: Int = 10_000,
    val maxSnapshotBytes: Int = 36_000,
    val maxOutputTokens: Int = 384_000,
    val commandTimeout: Duration = Duration.ofSeconds(20),
    val runOnStartup: Boolean = true,
    val debug: Boolean = true,
    val memory: Memory = Memory(),
    val publication: Publication = Publication(),
    val improve: Improve = Improve(),
) {
    data class Improve(
        /** When true, host script may run improve loop after NEEDS_WORK. */
        val autoImprove: Boolean = false,
        val maxIterations: Int = 2,
        /** When true, dogfood-improve.sh may invoke `cursor agent` if available. */
        val invokeCursor: Boolean = false,
    )

    data class Publication(
        /** When true, host script may open a draft PR after a RAISE_PR decision. */
        val autoRaisePr: Boolean = false,
        val baseBranch: String = "main",
        val draftOnly: Boolean = true,
    )

    data class Memory(
        val enabled: Boolean = true,
        val maxPriorRuns: Int = 5,
        val github: Github = Github(),
    ) {
        data class Github(
            val enabled: Boolean = true,
            val repo: String = "",
            val label: String = "dogfood",
            val syncOnComplete: Boolean = true,
        )
    }

    data class Dsl(
        val enabled: Boolean = true,
        val stepsLocation: String = "classpath:/steps",
        val agentName: String = "oss-java-kotlin-expert",
        val goal: String = "produceDogfoodReport",
    )
}
