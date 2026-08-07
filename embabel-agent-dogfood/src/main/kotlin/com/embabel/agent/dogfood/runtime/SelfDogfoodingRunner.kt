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

import com.embabel.agent.api.common.autonomy.Autonomy
import com.embabel.agent.core.ProcessOptions
import com.embabel.agent.core.Verbosity
import com.embabel.agent.domain.io.UserInput
import com.embabel.agent.dogfood.config.DogfoodProperties
import com.embabel.agent.dogfood.domain.DogfoodAnalysis
import com.embabel.agent.dogfood.domain.PublicationAssessment
import com.embabel.agent.dogfood.memory.InteractionHistoryService
import com.embabel.agent.spi.common.Constants
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
@ConditionalOnProperty(prefix = "dogfood", name = ["run-on-startup"], havingValue = "true")
class SelfDogfoodingRunner(
    private val properties: DogfoodProperties,
    private val autonomy: Autonomy,
    private val dogfoodAgentScopeFactory: DogfoodAgentScopeFactory,
    private val dogfoodRunContext: DogfoodRunContext,
    private val interactionHistoryService: InteractionHistoryService,
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        val scope = dogfoodAgentScopeFactory.deploy()
        val goal = dogfoodAgentScopeFactory.goal(scope)
        val agent = scope.createAgent(
            name = properties.dsl.agentName,
            provider = Constants.EMBABEL_PROVIDER,
            description = goal.description,
        ).withSingleGoal(goal)

        logger.info(
            "[dogfood] starting OSS JVM expert run goal={} actions={}",
            goal.name,
            scope.actions.map { it.name },
        )

        val execution = autonomy.runAgent(
            UserInput(properties.task),
            buildProcessOptions(),
            agent,
        )

        val analysis = when (val output = execution.output) {
            is DogfoodAnalysis -> output.content
            is String -> output
            else -> error("Unexpected dogfood output type: ${output::class.qualifiedName}")
        }
        val reportPath = DogfoodReportWriter.write(
            outputDirectory = Path.of(properties.outputDirectory),
            model = properties.model,
            commit = dogfoodRunContext.commit,
            filesReviewed = dogfoodRunContext.filesReviewed,
            processId = execution.agentProcess.id,
            analysis = analysis,
        )
        val publicationSection = extractSection(analysis, "Publication decision")
        val decision = PublicationDecisionWriter.extractFromReport(analysis)
        decision?.let { publicationDecision ->
            val assessment = PublicationAssessment(
                content = publicationSection.ifBlank { publicationDecision },
                decision = publicationDecision,
            )
            dogfoodRunContext.publicationAssessment = assessment
            val decisionPath = PublicationDecisionWriter.write(
                Path.of(properties.outputDirectory),
                assessment,
            )
            logger.info(
                "[dogfood] publication decision={} file={}",
                publicationDecision,
                decisionPath,
            )
            val improvementPlan = ImprovementPlanWriter.parseFromReport(analysis)
                ?: dogfoodRunContext.improvementPlan
            dogfoodRunContext.improvementPlan = improvementPlan
            val planPath = ImprovementPlanWriter.write(
                Path.of(properties.outputDirectory),
                publicationDecision,
                improvementPlan,
            )
            if (planPath != null) {
                logger.info("[dogfood] improvement plan file={}", planPath)
            }
        }
        val historyRecord = interactionHistoryService.recordRun(
            processId = execution.agentProcess.id,
            commit = dogfoodRunContext.commit,
            model = properties.model,
            filesReviewed = dogfoodRunContext.filesReviewed,
            task = properties.task,
            reportPath = reportPath.toString(),
            reportContent = analysis,
            ossInteractionsExcerpt = extractSection(analysis, "OSS interactions"),
        )
        logger.info(
            "[dogfood] completed processId={} model={} report={}{}",
            execution.agentProcess.id,
            properties.model,
            reportPath,
            historyRecord.githubIssueUrl?.let { " github=$it" } ?: "",
        )
    }

    private fun buildProcessOptions(): ProcessOptions {
        if (!properties.debug) {
            return ProcessOptions.DEFAULT
        }
        return ProcessOptions.DEFAULT.withVerbosity(Verbosity.DEFAULT.showPlanning())
    }

    private fun extractSection(markdown: String, heading: String): String {
        val pattern = Regex("##\\s+${Regex.escape(heading)}\\s*\\n([\\s\\S]*?)(?=\\n##\\s|$)")
        return pattern.find(markdown)?.groupValues?.get(1)?.trim().orEmpty()
    }
}
