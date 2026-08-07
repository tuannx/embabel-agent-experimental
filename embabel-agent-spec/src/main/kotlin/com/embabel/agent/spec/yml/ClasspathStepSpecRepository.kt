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
package com.embabel.agent.spec.yml

import com.embabel.agent.spec.model.StepSpec
import com.embabel.agent.spec.persistence.StepSpecRepository
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.jar.JarFile

/**
 * Read-only [StepSpecRepository] that loads YAML step definitions from the classpath.
 *
 * Locations use the `classpath:` prefix, for example `classpath:/steps`.
 */
class ClasspathStepSpecRepository(
    private val location: String,
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
    additionalSubtypes: List<Class<out StepSpec<*>>> = emptyList(),
) : StepSpecRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val yamlMapper = ObjectMapper(
        YAMLFactory().disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID),
    ).apply {
        registerKotlinModule()
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        if (additionalSubtypes.isNotEmpty()) {
            registerSubtypes(*additionalSubtypes.toTypedArray())
        }
    }

    override fun save(entity: StepSpec<*>): StepSpec<*> =
        throw UnsupportedOperationException("Classpath step repository is read-only")

    override fun findAll(): Iterable<StepSpec<*>> {
        val resourceDirectory = location.removePrefix("classpath:").trim('/')
        val resources = listClasspathResources(resourceDirectory)
        if (resources.isEmpty()) {
            logger.warn("No step specs found under classpath:{}", resourceDirectory)
            return emptyList()
        }

        return resources.mapNotNull { (name, bytes) ->
            try {
                yamlMapper.readValue<StepSpec<*>>(bytes).also {
                    logger.info("Loaded step spec '{}' from {}", it.name, name)
                }
            } catch (e: Exception) {
                logger.warn("Failed to read {}: {}", name, e.message)
                null
            }
        }
    }

    private fun listClasspathResources(resourceDirectory: String): List<Pair<String, ByteArray>> {
        val directoryPrefix = "$resourceDirectory/"
        val directoryUrl = classLoader.getResource(directoryPrefix)
            ?: classLoader.getResource(resourceDirectory)
            ?: return emptyList()

        return when (directoryUrl.protocol) {
            "file" -> listFileResources(directoryUrl.toURI())
            "jar" -> listJarResources(directoryUrl.toString(), directoryPrefix)
            else -> emptyList()
        }
    }

    private fun listFileResources(directoryUri: java.net.URI): List<Pair<String, ByteArray>> {
        val directory = java.io.File(directoryUri)
        if (!directory.isDirectory) {
            return emptyList()
        }
        return directory.listFiles { file -> file.extension == "yml" }
            ?.map { file -> file.name to file.readBytes() }
            ?: emptyList()
    }

    private fun listJarResources(jarUrl: String, directoryPrefix: String): List<Pair<String, ByteArray>> {
        val jarPath = jarUrl.substringAfter("file:").substringBefore("!")
        val decodedJarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8)
        return JarFile(decodedJarPath).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith(directoryPrefix) && it.name.endsWith(".yml") }
                .map { entry ->
                    val name = entry.name.substringAfterLast('/')
                    name to jar.getInputStream(entry).use { it.readBytes() }
                }
                .toList()
        }
    }
}
