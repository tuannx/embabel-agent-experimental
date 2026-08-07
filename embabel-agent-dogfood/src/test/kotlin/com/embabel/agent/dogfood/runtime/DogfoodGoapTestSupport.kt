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

import com.embabel.agent.core.Agent
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentScope
import com.embabel.agent.core.Goal
import com.embabel.agent.core.JvmType
import com.embabel.agent.domain.io.UserInput
import com.embabel.agent.dogfood.config.DogfoodProperties
import com.embabel.agent.dogfood.domain.ContributorQualityGate
import com.embabel.agent.dogfood.domain.DogfoodAnalysis
import com.embabel.agent.dogfood.domain.DogfoodImprovementPlan
import com.embabel.agent.dogfood.domain.GitRemoteContext
import com.embabel.agent.dogfood.domain.InteractionMemory
import com.embabel.agent.dogfood.domain.OssInteractionDrafts
import com.embabel.agent.dogfood.domain.PublicationAssessment
import com.embabel.agent.dogfood.domain.RepositoryEvidence
import com.embabel.agent.dogfood.domain.TechnicalReview
import com.embabel.agent.dogfood.memory.InteractionHistoryService
import com.embabel.agent.dogfood.repo.GitRemoteContextService
import com.embabel.agent.spi.common.Constants
import com.embabel.plan.common.condition.ConditionDetermination
import com.embabel.plan.common.condition.ConditionPlan
import com.embabel.plan.common.condition.ConditionPlanningSystem
import com.embabel.plan.common.condition.WorldStateDeterminer
import com.embabel.plan.goap.OptimizingGoapPlanner
import io.mockk.every
import io.mockk.mockk

internal object DogfoodGoapTestSupport {

    fun properties(): DogfoodProperties = DogfoodProperties()

    fun agentPlatform(): AgentPlatform {
        val platform = mockk<AgentPlatform>(relaxed = true)
        val domainTypes = listOf(
            JvmType(UserInput::class.java),
            JvmType(InteractionMemory::class.java),
            JvmType(GitRemoteContext::class.java),
            JvmType(RepositoryEvidence::class.java),
            JvmType(TechnicalReview::class.java),
            JvmType(ContributorQualityGate::class.java),
            JvmType(PublicationAssessment::class.java),
            JvmType(DogfoodImprovementPlan::class.java),
            JvmType(OssInteractionDrafts::class.java),
            JvmType(DogfoodAnalysis::class.java),
        )
        every { platform.domainTypes } returns domainTypes
        every { platform.toolGroupResolver.availableToolGroups() } returns emptyList()
        return platform
    }

    fun scopeFactory(
        agentPlatform: AgentPlatform = agentPlatform(),
        properties: DogfoodProperties = properties(),
    ): DogfoodAgentScopeFactory {
        val runContext = DogfoodRunContext()
        val historyService = mockk<InteractionHistoryService>(relaxed = true)
        every { historyService.loadMemory(any()) } answers {
            InteractionMemory(
                task = firstArg(),
                priorInteractionsMarkdown = "(test history)",
            )
        }
        val gitRemoteService = mockk<GitRemoteContextService>(relaxed = true)
        every { gitRemoteService.capture() } returns GitRemoteContext(
            content = "(test git context)",
            branch = "test-branch",
            hasUnpushedWork = true,
            dogfoodModuleLocalOnly = true,
        )
        val loadAction = LoadInteractionMemoryAction(
            agentPlatform = agentPlatform,
            interactionHistoryService = historyService,
        )
        val gitRemoteAction = CollectGitRemoteContextAction(
            agentPlatform = agentPlatform,
            gitRemoteContextService = gitRemoteService,
            dogfoodRunContext = runContext,
        )
        val collectAction = CollectRepositoryEvidenceAction(
            agentPlatform = agentPlatform,
            repositorySnapshotService = mockk(relaxed = true),
            dogfoodRunContext = runContext,
        )
        return DogfoodAgentScopeFactory(
            properties = properties,
            agentPlatform = agentPlatform,
            collectRepositoryEvidenceAction = collectAction,
            collectGitRemoteContextAction = gitRemoteAction,
            loadInteractionMemoryAction = loadAction,
            stepSpecRepository = SpringClasspathStepSpecRepository(properties),
        )
    }

    fun goalAgent(
        scope: AgentScope,
        goal: Goal,
        properties: DogfoodProperties = properties(),
    ): Agent =
        scope.createAgent(
            name = properties.dsl.agentName,
            provider = Constants.EMBABEL_PROVIDER,
            description = goal.description,
        ).withSingleGoal(goal)

    fun goapPlannerForTest(worldStateDeterminer: WorldStateDeterminer): OptimizingGoapPlanner =
        goapPlanner(worldStateDeterminer)

    fun planFromUserInput(agent: Agent, goal: Goal): ConditionPlan? =
        goapPlanner(worldStateDeterminer(agent))
            .planToGoal(agent.actions, goal)

    private fun goapPlanner(worldStateDeterminer: WorldStateDeterminer): OptimizingGoapPlanner {
        val clazz = Class.forName("com.embabel.plan.goap.astar.AStarGoapPlanner")
        return clazz.getConstructor(WorldStateDeterminer::class.java)
            .newInstance(worldStateDeterminer) as OptimizingGoapPlanner
    }

    private fun worldStateDeterminer(agent: Agent): WorldStateDeterminer {
        val planningSystem = agent.planningSystem as ConditionPlanningSystem
        val worldState = planningSystem.knownConditions()
            .associateWith { ConditionDetermination.FALSE }
            .toMutableMap()
        val userInputKey = worldState.keys.first { it.contains("UserInput") }
        worldState[userInputKey] = ConditionDetermination.TRUE
        return WorldStateDeterminer.fromMap(worldState)
    }
}
