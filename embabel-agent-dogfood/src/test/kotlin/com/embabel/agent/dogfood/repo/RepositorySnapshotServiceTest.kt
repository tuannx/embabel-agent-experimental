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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RepositorySnapshotServiceTest {

    @Test
    fun `marks files cut at maxFileBytes so the model does not report them as truncated`(@TempDir repo: Path) {
        Files.writeString(repo.resolve("big.txt"), "a".repeat(50))
        Files.writeString(repo.resolve("small.txt"), "tiny")
        git(repo, "init", "-q")
        git(repo, "add", ".")
        git(repo, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "init")

        val snapshot = RepositorySnapshotService(
            DogfoodProperties(repository = repo.toString(), maxFileBytes = 10, maxSnapshotBytes = 100_000),
        ).capture(
            InteractionMemory(task = "t", priorInteractionsMarkdown = ""),
            GitRemoteContext(content = "", branch = "main", hasUnpushedWork = false, dogfoodModuleLocalOnly = false),
        )

        assertTrue(snapshot.body.contains("first 10 of 50 bytes; file continues on disk"), snapshot.body)
        assertFalse(snapshot.body.contains("of 4 bytes"))
    }

    private fun git(repo: Path, vararg args: String) {
        val exit = ProcessBuilder("git", *args).directory(repo.toFile()).inheritIO().start().waitFor()
        check(exit == 0) { "git ${args.joinToString(" ")} failed: $exit" }
    }
}
