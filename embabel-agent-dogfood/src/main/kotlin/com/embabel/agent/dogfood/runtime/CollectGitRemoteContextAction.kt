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
import com.embabel.agent.core.JvmType
import com.embabel.agent.core.ProcessContext
import com.embabel.agent.core.support.AbstractAction
import com.embabel.agent.dogfood.domain.ContributorQualityGate
import com.embabel.agent.dogfood.domain.DogfoodAnalysis
import com.embabel.agent.dogfood.domain.DogfoodImprovementPlan
import com.embabel.agent.dogfood.domain.GitRemoteContext
import com.embabel.agent.dogfood.domain.InteractionMemory
import com.embabel.agent.dogfood.domain.OssInteractionDrafts
import com.embabel.agent.dogfood.domain.PublicationAssessment
import com.embabel.agent.dogfood.domain.RepositoryEvidence
import com.embabel.agent.dogfood.domain.TechnicalReview
import com.embabel.agent.dogfood.repo.GitRemoteContextService
import org.springframework.stereotype.Component

@Component
class CollectGitRemoteContextAction(
    agentPlatform: AgentPlatform,
    private val gitRemoteContextService: GitRemoteContextService,
    private val dogfoodRunContext: DogfoodRunContext,
) : AbstractAction(
    name = COLLECT_GIT_REMOTE_CONTEXT,
    description = "Collect six-direction git and GitHub remote context for goal selection",
    inputs = setOf(IoBinding("interactionMemory", InteractionMemory::class.java.name)),
    outputs = setOf(IoBinding("gitRemoteContext", GitRemoteContext::class.java.name)),
    toolGroups = emptySet(),
    canRerun = false,
) {

    override val domainTypes: Collection<DomainType> = agentPlatform.domainTypes + dogfoodDomainTypes()

    override fun referencedInputProperties(variable: String): Set<String> = emptySet()

    override fun execute(processContext: ProcessContext): ActionStatus =
        ActionRunner.execute(processContext) {
            logger.info("[dogfood] action={} phase=start", name)
            val context = gitRemoteContextService.capture()
            dogfoodRunContext.gitRemoteContext = context
            processContext.blackboard["gitRemoteContext"] = context
            logger.info(
                "[dogfood] action={} phase=complete branch={} localOnly={} unpushed={}",
                name,
                context.branch,
                context.dogfoodModuleLocalOnly,
                context.hasUnpushedWork,
            )
        }

    companion object {
        const val COLLECT_GIT_REMOTE_CONTEXT = "collectGitRemoteContext"
    }
}

internal fun dogfoodDomainTypes(): List<JvmType> = listOf(
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
