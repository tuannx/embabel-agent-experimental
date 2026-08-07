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

import com.embabel.agent.dogfood.domain.ContributorQualityGate
import com.embabel.agent.dogfood.domain.DogfoodAnalysis
import com.embabel.agent.dogfood.domain.DogfoodImprovementPlan
import com.embabel.agent.dogfood.domain.GitRemoteContext
import com.embabel.agent.dogfood.domain.InteractionMemory
import com.embabel.agent.dogfood.domain.OssInteractionDrafts
import com.embabel.agent.dogfood.domain.PublicationAssessment
import com.embabel.agent.dogfood.domain.RepositoryEvidence
import com.embabel.agent.dogfood.domain.TechnicalReview
import com.embabel.plan.common.condition.ConditionDetermination
import com.embabel.plan.common.condition.ConditionPlanningSystem
import com.embabel.plan.common.condition.WorldStateDeterminer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Verifies the OSS-expert declarative dogfood graph is structurally sound and GOAP-plannable.
 */
class DogfoodGoapGraphTest {

    private val factory = DogfoodGoapTestSupport.scopeFactory()
    private val scope = factory.createAgentScope()
    private val goal = factory.goal(scope)
    private val agent = DogfoodGoapTestSupport.goalAgent(scope, goal)

    private val expectedPlan = listOf(
        LoadInteractionMemoryAction.LOAD_INTERACTION_MEMORY,
        CollectGitRemoteContextAction.COLLECT_GIT_REMOTE_CONTEXT,
        CollectRepositoryEvidenceAction.COLLECT_REPOSITORY_EVIDENCE,
        "technicalReview",
        "assessContributorQuality",
        "assessPublicationContext",
        "planDogfoodImprovements",
        "draftOssInteractions",
        "synthesizeDogfoodReport",
    )

    @Nested
    inner class GraphStructure {

        @Test
        fun `scope exposes OSS expert actions and exported goal`() {
            assertEquals(
                expectedPlan.toSet(),
                scope.actions.map { it.name }.toSet(),
            )
            assertEquals(setOf("produceDogfoodReport"), scope.goals.map { it.name }.toSet())
        }

        @Test
        fun `load memory action binds UserInput to InteractionMemory`() {
            val load = scope.actions.first { it.name == LoadInteractionMemoryAction.LOAD_INTERACTION_MEMORY }

            assertEquals(setOf("userInput"), load.inputs.map { it.name }.toSet())
            assertEquals("UserInput", load.inputs.first().type)
            assertEquals("interactionMemory", load.outputs.first().name)
            assertEquals(InteractionMemory::class.java.name, load.outputs.first().type)
        }

        @Test
        fun `git remote action binds InteractionMemory to GitRemoteContext`() {
            val gitRemote = scope.actions.first { it.name == CollectGitRemoteContextAction.COLLECT_GIT_REMOTE_CONTEXT }

            assertEquals(setOf("interactionMemory"), gitRemote.inputs.map { it.name }.toSet())
            assertEquals("gitRemoteContext", gitRemote.outputs.first().name)
            assertEquals(GitRemoteContext::class.java.name, gitRemote.outputs.first().type)
        }

        @Test
        fun `collect action binds memory and git context to RepositoryEvidence`() {
            val collect = scope.actions.first { it.name == CollectRepositoryEvidenceAction.COLLECT_REPOSITORY_EVIDENCE }

            assertEquals(
                setOf("interactionMemory", "gitRemoteContext"),
                collect.inputs.map { it.name }.toSet(),
            )
            assertEquals("repositoryEvidence", collect.outputs.first().name)
            assertEquals(RepositoryEvidence::class.java.name, collect.outputs.first().type)
        }

        @Test
        fun `technical review binds RepositoryEvidence to TechnicalReview`() {
            val review = scope.actions.first { it.name == "technicalReview" }

            assertEquals(RepositoryEvidence::class.java.name, review.inputs.first().type)
            assertEquals(TechnicalReview::class.java.name, review.outputs.first().type)
        }

        @Test
        fun `quality gate binds TechnicalReview to ContributorQualityGate`() {
            val assess = scope.actions.first { it.name == "assessContributorQuality" }

            assertEquals(TechnicalReview::class.java.name, assess.inputs.first().type)
            assertEquals(ContributorQualityGate::class.java.name, assess.outputs.first().type)
        }

        @Test
        fun `publication assessment binds gate and git context to PublicationAssessment`() {
            val publication = scope.actions.first { it.name == "assessPublicationContext" }

            assertEquals(
                setOf(
                    ContributorQualityGate::class.java.name,
                    GitRemoteContext::class.java.name,
                ),
                publication.inputs.map { it.type }.toSet(),
            )
            assertEquals(PublicationAssessment::class.java.name, publication.outputs.first().type)
        }

        @Test
        fun `improvement plan binds publication gate and git context to DogfoodImprovementPlan`() {
            val plan = scope.actions.first { it.name == "planDogfoodImprovements" }

            assertEquals(
                setOf(
                    PublicationAssessment::class.java.name,
                    ContributorQualityGate::class.java.name,
                    GitRemoteContext::class.java.name,
                ),
                plan.inputs.map { it.type }.toSet(),
            )
            assertEquals(DogfoodImprovementPlan::class.java.name, plan.outputs.first().type)
        }

        @Test
        fun `interaction draft binds gate publication and plan to OssInteractionDrafts`() {
            val draft = scope.actions.first { it.name == "draftOssInteractions" }

            assertEquals(
                setOf(
                    ContributorQualityGate::class.java.name,
                    PublicationAssessment::class.java.name,
                    DogfoodImprovementPlan::class.java.name,
                ),
                draft.inputs.map { it.type }.toSet(),
            )
            assertEquals(OssInteractionDrafts::class.java.name, draft.outputs.first().type)
        }

        @Test
        fun `synthesize binds drafts and improvement plan to DogfoodAnalysis`() {
            val synthesize = scope.actions.first { it.name == "synthesizeDogfoodReport" }

            assertEquals(
                setOf(
                    OssInteractionDrafts::class.java.name,
                    DogfoodImprovementPlan::class.java.name,
                ),
                synthesize.inputs.map { it.type }.toSet(),
            )
            assertEquals(DogfoodAnalysis::class.java.name, synthesize.outputs.first().type)
        }

        @Test
        fun `exported goal requires DogfoodAnalysis`() {
            assertEquals("dogfoodAnalysis", goal.inputs.single().name)
            assertEquals(DogfoodAnalysis::class.java.name, goal.inputs.single().type)
        }

        @Test
        fun `agent planning system registers dogfood domain types`() {
            val planningSystem = agent.planningSystem as ConditionPlanningSystem
            val conditions = planningSystem.knownConditions()

            assertTrue(conditions.any { it.contains("InteractionMemory") })
            assertTrue(conditions.any { it.contains("GitRemoteContext") })
            assertTrue(conditions.any { it.contains("RepositoryEvidence") })
            assertTrue(conditions.any { it.contains("TechnicalReview") })
            assertTrue(conditions.any { it.contains("ContributorQualityGate") })
            assertTrue(conditions.any { it.contains("PublicationAssessment") })
            assertTrue(conditions.any { it.contains("DogfoodImprovementPlan") })
            assertTrue(conditions.any { it.contains("OssInteractionDrafts") })
            assertTrue(conditions.any { it.contains("DogfoodAnalysis") })
        }
    }

    @Nested
    inner class GoapPlanning {

        @Test
        fun `planner finds OSS expert plan from UserInput to produceDogfoodReport`() {
            val plan = DogfoodGoapTestSupport.planFromUserInput(agent, goal)

            assertNotNull(plan)
            assertEquals(expectedPlan, plan!!.actions.map { it.name })
            assertEquals("produceDogfoodReport", plan.goal.name)
        }

        @Test
        fun `plan is unreachable without UserInput`() {
            val planningSystem = agent.planningSystem as ConditionPlanningSystem
            val withoutUserInput = planningSystem.knownConditions()
                .associateWith { ConditionDetermination.FALSE }
            val unreachable = DogfoodGoapTestSupport.goapPlannerForTest(
                WorldStateDeterminer.fromMap(withoutUserInput),
            ).planToGoal(agent.actions, goal)

            assertEquals(null, unreachable)
        }
    }
}
