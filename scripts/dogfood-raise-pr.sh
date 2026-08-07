#!/usr/bin/env bash
# Host-side PR raise after dogfood container run (repo mount is read-only in Docker).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DECISION_FILE="${ROOT}/build/dogfood/publication-decision.json"
AUTO="${DOGFOOD_PUBLICATION_AUTO_RAISE_PR:-false}"
BASE_BRANCH="${DOGFOOD_PUBLICATION_BASE_BRANCH:-main}"
DRAFT_ONLY="${DOGFOOD_PUBLICATION_DRAFT_ONLY:-true}"

if [[ "${AUTO}" != "true" ]]; then
  echo "[dogfood-raise-pr] skipped — set DOGFOOD_PUBLICATION_AUTO_RAISE_PR=true to open draft PRs on host"
  exit 0
fi

if [[ ! -f "${DECISION_FILE}" ]]; then
  echo "[dogfood-raise-pr] no ${DECISION_FILE}"
  exit 0
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "[dogfood-raise-pr] jq required" >&2
  exit 1
fi

DECISION="$(jq -r '.decision // empty' "${DECISION_FILE}")"
if [[ "${DECISION}" != "RAISE_PR" ]]; then
  echo "[dogfood-raise-pr] decision=${DECISION:-unknown} — not raising PR"
  exit 0
fi

if ! command -v gh >/dev/null 2>&1 || ! gh auth status >/dev/null 2>&1; then
  echo "[dogfood-raise-pr] gh not authenticated — run \`gh auth login\` on host" >&2
  exit 1
fi

cd "${ROOT}"
BRANCH="$(git branch --show-current)"
if [[ -z "${BRANCH}" || "${BRANCH}" == "HEAD" ]]; then
  echo "[dogfood-raise-pr] detached HEAD — checkout a feature branch first" >&2
  exit 1
fi

EXISTING_PR="$(gh pr list --head "${BRANCH}" --json url --jq '.[0].url // empty' 2>/dev/null || true)"
if [[ -n "${EXISTING_PR}" ]]; then
  echo "[dogfood-raise-pr] PR already open: ${EXISTING_PR}"
  exit 0
fi

# Stage dogfood module and related wiring only (experimental, narrow blast radius).
git add \
  embabel-agent-dogfood \
  compose.dogfood.yml \
  dogfood.env.example \
  scripts/dogfood-run.sh \
  scripts/dogfood-raise-pr.sh \
  scripts/dogfood-improve.sh \
  pom.xml \
  README.md \
  .dockerignore \
  2>/dev/null || true

if git diff --cached --quiet; then
  if git diff --quiet; then
    echo "[dogfood-raise-pr] nothing to commit"
  else
    echo "[dogfood-raise-pr] unstaged changes remain — review manually before PR"
    exit 0
  fi
else
  git commit -m "$(cat <<'EOF'
feat(dogfood): add declarative self-dogfood OSS expert agent

Automated draft from embabel-agent-dogfood GOAP pipeline.
EOF
)"
fi

git push -u origin "${BRANCH}"

PR_ARGS=(pr create --base "${BASE_BRANCH}" --head "${BRANCH}" \
  --title "feat(dogfood): declarative self-dogfood OSS expert agent" \
  --body "$(cat <<'EOF'
## Summary
Experimental dogfood module: declarative GOAP graph, six-direction git/GitHub context, interaction memory, and host-side draft PR workflow.

## Test plan
- [ ] `./scripts/dogfood-run.sh` completes with report at `build/dogfood/report.md`
- [ ] `mvn -pl embabel-agent-dogfood test` passes
EOF
)")

if [[ "${DRAFT_ONLY}" == "true" ]]; then
  PR_ARGS+=(--draft)
fi

gh "${PR_ARGS[@]}"
