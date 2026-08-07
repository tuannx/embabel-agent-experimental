# Dogfood DSL — OSS Java/Kotlin expert agent

The dogfood module is **declarative-first**: the GOAP agent graph lives in
`src/main/resources/steps/*.yml`, expert principles in
`src/main/resources/oss-expert/principles.md`, and runtime/provider values in
`application.yml`.

A thin Kotlin runtime adapter loads the YAML graph, binds native primitives, and
writes the final Markdown report plus **interaction memory** and **publication decision**.

## GOAP graph

```
UserInput
  → loadInteractionMemory          (native: local history + GitHub threads)
  → collectGitRemoteContext        (native: six-direction git/GitHub context)
  → collectRepositoryEvidence      (native: bounded git snapshot + memory + context)
  → technicalReview                (LLM)
  → assessContributorQuality       (LLM)
  → assessPublicationContext       (LLM: STAY_LOCAL | NEEDS_WORK | RAISE_PR)
  → planDogfoodImprovements        (LLM: scoped host improve plan when NEEDS_WORK)
  → draftOssInteractions           (LLM: includes draft PR when RAISE_PR)
  → synthesizeDogfoodReport        (LLM)
  → produceDogfoodReport           (exported goal)
```

Secondary non-export goals:
- `raiseExperimentalPullRequest` — host PR path after RAISE_PR
- `iterateDogfoodWork` — host improve loop after NEEDS_WORK

Host orchestration (`dogfood-run.sh`):

```
dogfood (Docker) → NEEDS_WORK? → dogfood-improve.sh (host) → re-dogfood (≤ max iterations)
                → RAISE_PR?   → dogfood-raise-pr.sh (host)

## Six-direction context

`GitRemoteContextService` captures situational awareness before evidence collection:

| Direction | Signal |
|-----------|--------|
| **Up** | upstream branch, ahead/behind, remotes |
| **Down** | current branch, open PR |
| **Before** | recent commits |
| **After** | dogfood module local-only, unpushed work |
| **Inside** | `git status`, dogfood module lines |
| **Outside** | `GH_TOKEN`, experimental draft-PR policy |

`assessPublicationContext` uses this plus the quality gate to choose the right moment to share work.

## Interaction memory

| Store | Location | Notes |
|-------|----------|-------|
| Local | `{output}/history/index.json`, `runs/{processId}.json`, `latest.md` | Survives Docker runs via `./build/dogfood` mount |
| GitHub | `gh issue list/create --label dogfood` | Auto from host `gh auth login` + `gh repo view`; `~/.config/gh` mounted in Docker |
| Claude Code | `CodingSession.sessionId` in history JSON | Links to `embabel-agent-claude-code` for host `--resume` |

Configure via `dogfood.memory.*` in `application.yml`. Repo slug and `gh` auth are
probed from the host at runtime (`gh repo view`, `git remote origin`, `gh auth status`).

Run Docker with host GitHub auth (no manual `GH_TOKEN` in `dogfood.env`):

```bash
./scripts/dogfood-run.sh
```

The script exports `GH_TOKEN` from `gh auth token` for the container (macOS keychain
is not visible inside Docker; `~/.config/gh` is still mounted read-only).

### Host draft PR (optional)

After the container finishes, `scripts/dogfood-raise-pr.sh` may open a **draft PR** when:

1. Report contains `## Publication decision` with `RAISE_PR`
2. `build/dogfood/publication-decision.json` is written by the runner
3. `DOGFOOD_PUBLICATION_AUTO_RAISE_PR=true` on the host

```bash
DOGFOOD_PUBLICATION_AUTO_RAISE_PR=true ./scripts/dogfood-run.sh
```

### Host improve loop (optional)

When publication decision is `NEEDS_WORK`, `scripts/dogfood-improve.sh` may:

1. Read `build/dogfood/improvement-plan.json`
2. Run verify commands (`mvn -pl embabel-agent-dogfood test`, …)
3. Optionally invoke `cursor agent` when `DOGFOOD_IMPROVE_INVOKE_CURSOR=true`
4. Trigger re-dogfood (up to `DOGFOOD_IMPROVE_MAX_ITERATIONS`)

```bash
DOGFOOD_AUTO_IMPROVE=true ./scripts/dogfood-run.sh
```

Artifacts: `improvement-plan.json`, `improve-prompt.md`


Monitoring is done from your IDE or terminal — there is no in-app monitor subsystem.

- Set `dogfood.debug=true` (default) for GOAP planning logs via Embabel verbosity.
- Grep Docker or local logs: `[dogfood]` for run start/complete and native actions.
- Report artifact: `build/dogfood/report.md` (or `{output}/report.md` in container).
- Publication artifact: `build/dogfood/publication-decision.json`
- Improvement artifacts: `build/dogfood/improvement-plan.json`, `improve-prompt.md`

## Step files

| File | Role |
|------|------|
| `load-interaction-memory.yml` | Memory load contract (runtime-bound) |
| `collect-git-remote-context.yml` | Six-direction git/GitHub contract (runtime-bound) |
| `collect-repository.yml` | Evidence boundary contract (runtime-bound) |
| `technical-review.yml` | Top 1% OSS engineer technical review |
| `assess-contributor-quality.yml` | Quality gate vs `oss-expert/principles.md` |
| `assess-publication-context.yml` | STAY_LOCAL / NEEDS_WORK / RAISE_PR decision |
| `plan-dogfood-improvements.yml` | Scoped host improvement plan |
| `draft-oss-interactions.yml` | Short GitHub drafts + host dev workflow commands |
| `synthesize-dogfood-report.yml` | Final `DogfoodAnalysis` envelope |
| `dogfood-report.yml` | Exported goal |
| `raise-experimental-pr.yml` | Non-export goal documenting host PR workflow |
| `iterate-dogfood-work.yml` | Non-export goal documenting host improve loop |

## Domain types (JVM registration)

`InteractionMemory`, `GitRemoteContext`, `RepositoryEvidence`, `TechnicalReview`,
`ContributorQualityGate`, `PublicationAssessment`, `DogfoodImprovementPlan`,
`OssInteractionDrafts`, `DogfoodAnalysis`.

## Tests

`DogfoodGoapGraphTest` verifies graph structure and A* GOAP planning from
`UserInput` to `produceDogfoodReport`.
