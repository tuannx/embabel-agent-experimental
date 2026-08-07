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

import com.embabel.agent.core.ActionRunner
import com.embabel.agent.core.ActionStatus
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.DomainType
import com.embabel.agent.core.IoBinding
import com.embabel.agent.core.ProcessContext
import com.embabel.agent.core.support.AbstractAction
import com.embabel.agent.dogfood.domain.InteractionMemory
import com.embabel.agent.dogfood.memory.InteractionHistoryService
import org.springframework.stereotype.Component

@Component
class LoadInteractionMemoryAction(
    agentPlatform: AgentPlatform,
    private val interactionHistoryService: InteractionHistoryService,
) : AbstractAction(
    name = LOAD_INTERACTION_MEMORY,
    description = "Load prior dogfood interaction history from local disk and GitHub",
    inputs = setOf(IoBinding("userInput", "UserInput")),
    outputs = setOf(IoBinding("interactionMemory", InteractionMemory::class.java.name)),
    toolGroups = emptySet(),
    canRerun = false,
) {

    override val domainTypes: Collection<DomainType> = agentPlatform.domainTypes + dogfoodDomainTypes()

    override fun referencedInputProperties(variable: String): Set<String> = emptySet()

    override fun execute(processContext: ProcessContext): ActionStatus =
        ActionRunner.execute(processContext) {
            logger.info("[dogfood] action={} phase=start", name)
            val userInput = processContext.blackboard["userInput"] as? com.embabel.agent.domain.io.UserInput
                ?: error("Expected UserInput binding for $name")
            val memory = interactionHistoryService.loadMemory(userInput.content)
            processContext.blackboard["interactionMemory"] = memory
            logger.info("[dogfood] action={} phase=complete", name)
        }

    companion object {
        const val LOAD_INTERACTION_MEMORY = "loadInteractionMemory"
    }
}
