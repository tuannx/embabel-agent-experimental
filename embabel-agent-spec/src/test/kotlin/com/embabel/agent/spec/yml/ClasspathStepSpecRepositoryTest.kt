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

import com.embabel.agent.spec.model.PromptedActionSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClasspathStepSpecRepositoryTest {

    @Test
    fun `findAll loads yaml specs from classpath`() {
        val repository = ClasspathStepSpecRepository(
            location = "classpath:/yml",
            classLoader = javaClass.classLoader,
        )

        val steps = repository.findAll().toList()

        assertTrue(steps.isNotEmpty())
        val summarize = steps.filterIsInstance<PromptedActionSpec>().first { it.name == "summarize" }
        assertEquals(setOf("UserInput"), summarize.inputTypeNames)
        assertEquals("Summary", summarize.outputTypeName)
    }
}
