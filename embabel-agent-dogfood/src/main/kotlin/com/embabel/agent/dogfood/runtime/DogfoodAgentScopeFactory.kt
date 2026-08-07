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

import com.embabel.agent.core.Action
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentScope
import com.embabel.agent.core.Goal
import com.embabel.agent.dogfood.config.DogfoodProperties
import com.embabel.agent.spec.model.GoalSpec
import com.embabel.agent.spec.model.StepSpecContext
import org.springframework.stereotype.Component

@Component
class DogfoodAgentScopeFactory(
    private val properties: DogfoodProperties,
    private val agentPlatform: AgentPlatform,
    private val collectRepositoryEvidenceAction: CollectRepositoryEvidenceAction,
    private val collectGitRemoteContextAction: CollectGitRemoteContextAction,
    private val loadInteractionMemoryAction: LoadInteractionMemoryAction,
    private val stepSpecRepository: SpringClasspathStepSpecRepository,
) {

    fun deploy(): AgentScope = createAgentScope().also { agentPlatform.deploy(it) }

    fun createAgentScope(): AgentScope {
        require(properties.dsl.enabled) { "Dogfood DSL is disabled" }

        val stepContext = StepSpecContext(
            name = properties.dsl.agentName,
            dataDictionary = agentPlatform,
            toolGroups = agentPlatform.toolGroupResolver.availableToolGroups(),
        )

        val nativeActions = setOf(
            LoadInteractionMemoryAction.LOAD_INTERACTION_MEMORY,
            CollectGitRemoteContextAction.COLLECT_GIT_REMOTE_CONTEXT,
            CollectRepositoryEvidenceAction.COLLECT_REPOSITORY_EVIDENCE,
        )

        val actions = mutableListOf<Action>()
        val goals = mutableSetOf<Goal>()
        for (step in stepSpecRepository.findAll()) {
            if (step.name in nativeActions) {
                continue
            }
            if (step is GoalSpec && !step.export) {
                continue
            }
            when (val emitted = step.emit(stepContext)) {
                is Action -> actions += emitted
                is Goal -> goals += emitted
            }
        }
        actions += listOf(
            loadInteractionMemoryAction,
            collectGitRemoteContextAction,
            collectRepositoryEvidenceAction,
        )

        return AgentScope(
            name = properties.dsl.agentName,
            description = "Declarative self-dogfood reviewer loaded from ${properties.dsl.stepsLocation}",
            actions = actions,
            goals = goals,
            conditions = emptySet(),
        )
    }

    fun goal(scope: AgentScope): Goal =
        scope.goals.find { it.name == properties.dsl.goal }
            ?: error("Goal '${properties.dsl.goal}' was not found in ${properties.dsl.stepsLocation}")
}
