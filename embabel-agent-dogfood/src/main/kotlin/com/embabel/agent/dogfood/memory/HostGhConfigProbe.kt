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

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Reads GitHub settings from the host machine: `gh` CLI login state, default repo,
 * and `git remote origin` — no manual `DOGFOOD_MEMORY_GITHUB_REPO` required when
 * run from a checkout with `gh auth login`.
 */
class HostGhConfigProbe(
    private val repository: Path,
    private val commandRunner: CommandRunner,
) {

    constructor(repository: Path, commandTimeout: Duration) : this(
        repository,
        CommandRunner(commandTimeout),
    )

    private val logger = LoggerFactory.getLogger(javaClass)

    fun isGhInstalled(): Boolean = command(listOf("gh", "--version")) != null

    fun isGhAuthenticated(): Boolean {
        if (!isGhInstalled()) {
            return false
        }
        if (hasNonBlankGhToken(System.getenv("GITHUB_TOKEN"), System.getenv("GH_TOKEN"))) {
            return true
        }
        val status = command(listOf("gh", "auth", "status")) ?: return false
        return status.contains("Logged in", ignoreCase = true) ||
            status.contains("logged in to", ignoreCase = true)
    }

    fun resolveRepoSlug(configuredRepo: String): String? {
        if (configuredRepo.isNotBlank()) {
            return configuredRepo
        }
        if (isGhAuthenticated()) {
            val fromGh = command(
                listOf(
                    "gh",
                    "repo",
                    "view",
                    "--json",
                    "nameWithOwner",
                    "-q",
                    ".nameWithOwner",
                ),
            )?.trim()?.takeIf { it.isNotEmpty() && it.contains("/") }
            if (fromGh != null) {
                logger.debug("Resolved GitHub repo from gh: {}", fromGh)
                return fromGh
            }
        }
        val remote = command(listOf("git", "remote", "get-url", "origin")) ?: return null
        return parseGithubSlug(remote).also {
            if (it != null) {
                logger.debug("Resolved GitHub repo from git remote: {}", it)
            }
        }
    }

    fun ghConfigDir(): String? {
        System.getenv("GH_CONFIG_DIR")?.takeIf { it.isNotBlank() }?.let { return it }
        val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getenv("HOME")?.let { "$it/.config" })
        val ghDir = xdg?.let { Path.of(it, "gh") }
        return ghDir?.takeIf { Files.isDirectory(it) }?.toString()
    }

    internal fun parseGithubSlug(remote: String): String? {
        val ssh = Regex("git@github\\.com:([^/]+/[^.\\s]+)(?:\\.git)?")
        val https = Regex("https://github\\.com/([^/]+/[^.\\s]+)(?:\\.git)?")
        return ssh.find(remote)?.groupValues?.get(1) ?: https.find(remote)?.groupValues?.get(1)
    }

    private fun command(args: List<String>): String? =
        commandRunner.runSuccessOutput(args, repository)
}
