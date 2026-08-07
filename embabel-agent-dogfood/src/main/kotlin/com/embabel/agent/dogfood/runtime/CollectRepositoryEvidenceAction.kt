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
import com.embabel.agent.dogfood.domain.GitRemoteContext
import com.embabel.agent.dogfood.domain.InteractionMemory
import com.embabel.agent.dogfood.domain.RepositoryEvidence
import com.embabel.agent.dogfood.repo.RepositorySnapshotService
import org.springframework.stereotype.Component

@Component
class CollectRepositoryEvidenceAction(
    agentPlatform: AgentPlatform,
    private val repositorySnapshotService: RepositorySnapshotService,
    private val dogfoodRunContext: DogfoodRunContext,
) : AbstractAction(
    name = COLLECT_REPOSITORY_EVIDENCE,
    description = "Collect the bounded, read-only repository evidence used by the review",
    inputs = setOf(
        IoBinding("interactionMemory", InteractionMemory::class.java.name),
        IoBinding("gitRemoteContext", GitRemoteContext::class.java.name),
    ),
    outputs = setOf(IoBinding("repositoryEvidence", RepositoryEvidence::class.java.name)),
    toolGroups = emptySet(),
    canRerun = false,
) {

    override val domainTypes: Collection<DomainType> = agentPlatform.domainTypes + dogfoodDomainTypes()

    override fun referencedInputProperties(variable: String): Set<String> = emptySet()

    override fun execute(processContext: ProcessContext): ActionStatus =
        ActionRunner.execute(processContext) {
            logger.info("[dogfood] action={} phase=start", name)
            val memory = processContext.blackboard["interactionMemory"] as? InteractionMemory
                ?: error("Expected InteractionMemory binding for $name")
            val gitRemoteContext = processContext.blackboard["gitRemoteContext"] as? GitRemoteContext
                ?: error("Expected GitRemoteContext binding for $name")
            val snapshot = repositorySnapshotService.capture(memory, gitRemoteContext)
            dogfoodRunContext.commit = snapshot.commit
            dogfoodRunContext.filesReviewed = snapshot.filesReviewed
            processContext.blackboard["repositoryEvidence"] = RepositoryEvidence(snapshot.body)
            logger.info(
                "[dogfood] action={} phase=complete commit={} filesReviewed={}",
                name,
                snapshot.commit.take(12),
                snapshot.filesReviewed,
            )
        }

    companion object {
        const val COLLECT_REPOSITORY_EVIDENCE = "collectRepositoryEvidence"
    }
}
