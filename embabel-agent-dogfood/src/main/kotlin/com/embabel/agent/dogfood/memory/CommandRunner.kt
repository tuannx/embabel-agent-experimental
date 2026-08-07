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
import java.util.concurrent.TimeUnit

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val timedOut: Boolean,
) {
    val succeeded: Boolean get() = !timedOut && exitCode == 0
}

/**
 * Runs subprocesses with stdout drained concurrently to avoid pipe-buffer deadlocks.
 */
class CommandRunner(
    private val commandTimeout: Duration,
    private val minimumTimeout: Duration = Duration.ofSeconds(15),
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun run(command: List<String>, workingDir: Path): CommandResult? {
        if (!Files.isDirectory(workingDir)) {
            return null
        }
        return try {
            val process = ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = Thread {
                process.inputStream.bufferedReader().use { input ->
                    val buffer = CharArray(8_192)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        if (read > 0) {
                            output.append(buffer, 0, read)
                        }
                        read = input.read(buffer)
                    }
                }
            }
            reader.start()
            val timeoutMs = commandTimeout.coerceAtLeast(minimumTimeout).toMillis()
            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            reader.join(timeoutMs.coerceAtLeast(1_000))
            if (!completed) {
                process.destroyForcibly()
                reader.join(1_000)
                logger.warn("Command timed out: {}", command.joinToString(" "))
                return CommandResult(exitCode = -1, stdout = output.toString(), timedOut = true)
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                logger.debug("Command failed ({}): {}", exitCode, command.joinToString(" "))
            }
            CommandResult(exitCode = exitCode, stdout = output.toString(), timedOut = false)
        } catch (e: Exception) {
            logger.debug("Command unavailable {}: {}", command.firstOrNull(), e.message)
            null
        }
    }

    fun runSuccessOutput(command: List<String>, workingDir: Path): String? =
        run(command, workingDir)?.takeIf { it.succeeded }?.stdout?.trim()

    private fun Duration.coerceAtLeast(floor: Duration): Duration =
        if (this < floor) floor else this
}

internal fun hasNonBlankGhToken(
    githubToken: String?,
    ghToken: String?,
): Boolean = !githubToken.isNullOrBlank() || !ghToken.isNullOrBlank()
