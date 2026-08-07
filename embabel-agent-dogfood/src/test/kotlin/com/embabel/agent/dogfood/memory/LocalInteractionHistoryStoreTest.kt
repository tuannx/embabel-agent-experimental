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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class LocalInteractionHistoryStoreTest {

    @TempDir
    lateinit var outputDir: Path

    @Test
    fun `append and reload prior interactions markdown`() {
        val properties = DogfoodProperties(outputDirectory = outputDir.toString())
        val store = LocalInteractionHistoryStore(properties)
        store.append(
            InteractionRunRecord(
                processId = "test_run",
                commit = "abc123",
                timestamp = Instant.parse("2026-08-06T12:00:00Z"),
                task = "review",
                model = "deepseek-v4-flash",
                filesReviewed = 3,
                reportPath = outputDir.resolve("report.md").toString(),
                reportExcerpt = "## Summary\nPrior finding about GOAP.",
            ),
        )

        val markdown = store.loadPriorInteractionsMarkdown()
        assertTrue(markdown.contains("test_run"))
        assertTrue(markdown.contains("GOAP"))
        assertTrue(Files.isRegularFile(outputDir.resolve("history/index.json")))
        assertTrue(Files.isRegularFile(outputDir.resolve("history/latest.md")))
    }
}
