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

import com.embabel.agent.dogfood.config.DogfoodProperties
import com.embabel.agent.spec.model.StepSpec
import com.embabel.agent.spec.persistence.StepSpecRepository
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.boot.convert.DurationStyle
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SpringClasspathStepSpecRepository(
    properties: DogfoodProperties,
) : StepSpecRepository {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val locationPattern = toPattern(properties.dsl.stepsLocation)

    private val yamlMapper = ObjectMapper(
        YAMLFactory().disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID),
    ).apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        registerModule(
            SimpleModule().addDeserializer(
                Duration::class.java,
                object : JsonDeserializer<Duration>() {
                    override fun deserialize(parser: JsonParser, context: DeserializationContext): Duration =
                        DurationStyle.SIMPLE.parse(parser.text)
                },
            ),
        )
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    override fun save(entity: StepSpec<*>): StepSpec<*> =
        throw UnsupportedOperationException("Classpath step repository is read-only")

    override fun findAll(): Iterable<StepSpec<*>> {
        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources(locationPattern)
        if (resources.isEmpty()) {
            logger.warn("No step specs found for pattern {}", locationPattern)
            return emptyList()
        }

        return resources.mapNotNull { resource ->
            try {
                resource.inputStream.use { input ->
                    yamlMapper.readValue<StepSpec<*>>(input)
                }.also {
                    logger.info("Loaded step spec '{}' from {}", it.name, resource.filename)
                }
            } catch (e: Exception) {
                logger.warn("Failed to read {}: {}", resource.filename, e.message)
                null
            }
        }
    }

    private fun toPattern(location: String): String {
        val normalized = location.removePrefix("classpath:").trim('/')
        return "classpath:/$normalized/*.yml"
    }
}
