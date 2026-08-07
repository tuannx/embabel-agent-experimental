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
package com.embabel.agent.dogfood.domain

/** Prior dogfood runs and task context fed into the GOAP graph. */
data class InteractionMemory(
    val task: String,
    val priorInteractionsMarkdown: String,
    val claudeSessionId: String? = null,
    val githubThreadUrl: String? = null,
)

data class RepositoryEvidence(
    val body: String,
)

/** Six-direction git/GitHub situational awareness (up/down, before/after, inside/outside). */
data class GitRemoteContext(
    val content: String,
    val branch: String,
    val hasUnpushedWork: Boolean,
    val dogfoodModuleLocalOnly: Boolean,
    val openPullRequestUrl: String? = null,
)

/** Evidence-based technical review of the repository snapshot. */
data class TechnicalReview(
    val content: String,
)

/** Rubric pass/fail against top-contributor OSS engineering principles. */
data class ContributorQualityGate(
    val content: String,
)

/** Short, high-signal GitHub issue/PR comment drafts and optional dev commands. */
data class OssInteractionDrafts(
    val content: String,
)

/**
 * When to stay local vs open an experimental PR.
 * decision: STAY_LOCAL | NEEDS_WORK | RAISE_PR
 */
data class PublicationAssessment(
    val content: String,
    val decision: String = "NEEDS_WORK",
)

/** Scoped host-side improvement plan when publication decision is NEEDS_WORK. */
data class DogfoodImprovementPlan(
    val content: String,
)

/** Final self-dogfood report delivered to the goal. */
data class DogfoodAnalysis(
    val content: String,
)
