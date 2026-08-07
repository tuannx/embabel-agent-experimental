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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class HostGhConfigProbeTest {

    private val probe = HostGhConfigProbe(Path.of("."), Duration.ofSeconds(5))

    @Nested
    inner class ParseGithubSlug {

        @Test
        fun `supports ssh and https remotes`() {
            assertEquals(
                "embabel/embabel-agent-experimental",
                probe.parseGithubSlug("git@github.com:embabel/embabel-agent-experimental.git"),
            )
            assertEquals(
                "embabel/embabel-agent-experimental",
                probe.parseGithubSlug("https://github.com/embabel/embabel-agent-experimental.git"),
            )
            assertNull(probe.parseGithubSlug("https://gitlab.com/foo/bar.git"))
        }
    }

    @Nested
    inner class GhTokenDetection {

        @Test
        fun `blank tokens are not treated as authenticated`() {
            assertFalse(hasNonBlankGhToken("", ""))
            assertFalse(hasNonBlankGhToken(null, ""))
            assertFalse(hasNonBlankGhToken("", null))
            assertFalse(hasNonBlankGhToken(null, null))
            assertFalse(hasNonBlankGhToken("   ", "\t"))
        }

        @Test
        fun `non-blank token is present`() {
            assertTrue(hasNonBlankGhToken("ghp_abc", null))
            assertTrue(hasNonBlankGhToken(null, "ghp_abc"))
        }
    }
}

class CommandRunnerTest {

    @TempDir
    lateinit var workingDir: Path

    @Test
    fun `drains large stdout without timing out`() {
        val script = workingDir.resolve("large-output.sh")
        Files.writeString(
            script,
            """
            #!/bin/sh
            python3 -c "print('a' * 100000)"
            """.trimIndent(),
        )
        script.toFile().setExecutable(true)

        val result = CommandRunner(Duration.ofSeconds(15)).run(listOf(script.toString()), workingDir)

        requireNotNull(result)
        assertFalse(result.timedOut)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.length >= 100_000, "expected at least 100k chars, got ${result.stdout.length}")
    }

    @Test
    fun `returns null for missing working directory`() {
        val missing = workingDir.resolve("does-not-exist")
        assertNull(CommandRunner(Duration.ofSeconds(5)).run(listOf("echo", "hi"), missing))
    }
}
