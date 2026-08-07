# OSS Java/Kotlin expert principles (top 1% bar)

Use this rubric when assessing whether work meets the quality bar of a top-tier open-source
Java/Kotlin contributor. Every claim must cite repository paths from the supplied snapshot.

## Core engineering

1. **Correctness & types** — Illegal states are unrepresentable; nullability, concurrency, and
   failure modes are explicit. Kotlin: prefer sealed hierarchies and value types. Java: prefer
   immutability and clear contracts.
2. **Minimal, cohesive diffs** — Each change has one reason to exist. No drive-by refactors mixed
   with feature work unless justified.
3. **Behavior-focused tests** — Tests prove observable behavior and regressions; avoid brittle
   implementation coupling. Prefer table-driven and boundary cases.
4. **API & ABI discipline** — Public surface changes are intentional, documented, and compatible
   unless a major bump is explicit.
5. **Idiomatic platform usage** — Kotlin coroutines/Flow, Spring/Jakarta patterns, Maven module
   boundaries, and Embabel GOAP typed bindings used as intended.
6. **Performance & operability** — Hot paths avoid unnecessary allocation, blocking on async
   threads, unbounded work, and missing timeouts.
7. **Security & trust boundaries** — Untrusted input (files, prompts, HTTP) stays at boundaries;
   secrets never logged; sandbox assumptions stated.

## Open-source interaction quality

8. **Evidence over opinion** — Link claims to paths, commits, or logs. Mark unverified items.
9. **Actionable feedback** — Each finding: impact, reproduction or pointer, concrete fix.
10. **Short, respectful voice** — Dense keywords; no filler. Suitable for GitHub issue/PR comments.
11. **Dogfooding Embabel** — Reference declarative `embabel-agent-spec` steps, GOAP planning, typed
    `IoBinding`, and `embabel-agent-experimental` self-dogfood when relevant.

## Dev workflow actions (host machine; draft only in container)

When suggesting work, map findings to commands the maintainer can run locally:

| Intent | Typical action |
|--------|------------------|
| Inspect tree | `git status`, `git diff`, `git log -5 --oneline` |
| Build & test | `mvn -pl <module> -am test`, `./gradlew check` |
| Format/lint | `mvn spotless:apply`, `ktlint`, `detekt` |
| Docker dogfood | `docker compose -f compose.dogfood.yml build dogfood` |
| GitHub | `gh issue create`, `gh pr create`, `gh pr comment` |
| Discuss upstream | Open issue on `embabel-agent-experimental` before changing core Embabel |

Container dogfood runs are read-only: outputs are **drafts**, not executed commands.
