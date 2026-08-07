# Dogfood self-issue 001: Host dev actions & YAML Duration parsing

**Status:** open for discussion on `embabel-agent-experimental` before upstreaming to core Embabel.

## Context

OSS expert dogfood GOAP graph (`embabel-agent-dogfood`) now declares dev workflow
actions (git, mvn, gh, docker) as **draft outputs** in `draft-oss-interactions.yml`.
The Docker sandbox is read-only and cannot execute them.

## Problems found during configuration

### 1. Host dev actions are draft-only

- **Expected:** Agent graph models full maintainer workflow (inspect, test, PR, issue).
- **Actual:** Only `collectRepositoryEvidence` runs natively; LLM steps emit Markdown drafts.
- **Proposal:** Add optional native steps (or `embabel-agent-sandbox` tool groups) behind
  `DOGFOOD_HOST_MODE=true` for local runs outside read-only Docker.

### 2. Spring `Duration` in step YAML (`timeout: 10m`)

- **Bug:** `SpringClasspathStepSpecRepository` failed to load steps until a
  `DurationStyle.SIMPLE` deserializer was added.
- **Proposal:** Move duration-aware YAML loading into generic
  `embabel-agent-spec` `ClasspathStepSpecRepository` so all DSL consumers benefit.

### 3. `oss-expert/principles.md` not wired via DSL `references`

- **Gap:** Principles are duplicated in step prompts; `PromptedActionSpec.references`
  is not populated from classpath in dogfood runtime.
- **Proposal:** Extend `StepSpecContext` / loader to register classpath references by name.

### 4. Multi-step LLM graph latency

- Five LLM hops (review → gate → draft → synthesize) multiply DeepSeek latency.
- **Proposal:** Export intermediate goals (`technicalReview`, `draftOssInteractions`)
  for partial runs; add `DOGFOOD_FAST_PATH` single-step mode for CI smoke tests.

## Labels

`dogfood`, `embabel-experimental`, `goap`, `dsl`, `self-issue`
