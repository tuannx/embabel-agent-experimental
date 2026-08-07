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

import com.embabel.agent.dogfood.domain.DogfoodImprovementPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ImprovementPlanWriterTest {

    private val samplePlan = """
        ### Improvement iteration
        NEEDS_WORK iteration

        ### Scoped paths
        - embabel-agent-dogfood/
        - scripts/dogfood-improve.sh

        ### Verify commands
        ```bash
        mvn -pl embabel-agent-dogfood test
        ```

        ### Host improvement steps
        1. Commit dogfood module
    """.trimIndent()

    @Test
    fun `extractBashCommands parses verify block`() {
        val commands = ImprovementPlanWriter.extractBashCommands(samplePlan)
        assertEquals(listOf("mvn -pl embabel-agent-dogfood test"), commands)
    }

    @Test
    fun `extractBashCommands parses backtick bullets when no fence`() {
        val plan = """
            ### Verify commands
            - `python3 scripts/dogfood-contract-test.py`
            - `git diff --check`
        """.trimIndent()
        val commands = ImprovementPlanWriter.extractBashCommands(plan)
        assertEquals(
            listOf(
                "python3 scripts/dogfood-contract-test.py",
                "git diff --check",
            ),
            commands,
        )
    }

    @Test
    fun `write persists plan when NEEDS_WORK`(@TempDir dir: Path) {
        val path = ImprovementPlanWriter.write(
            dir,
            "NEEDS_WORK",
            DogfoodImprovementPlan(samplePlan),
        )
        assertNotNull(path)
        val json = Files.readString(path!!)
        assertTrue(json.contains("embabel-agent-dogfood"))
        assertTrue(Files.exists(dir.resolve("improve-prompt.md")))
    }

    @Test
    fun `parseFromReport reads improvement plan section`() {
        val markdown = """
            ## Publication decision
            NEEDS_WORK
            ## Improvement plan
            $samplePlan
            ## OSS interactions (draft)
        """.trimIndent()
        val plan = ImprovementPlanWriter.parseFromReport(markdown)
        assertNotNull(plan)
        assertTrue(plan!!.content.contains("Scoped paths"))
    }
}
