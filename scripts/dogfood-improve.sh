#!/usr/bin/env bash
# Host-side improvement iteration after NEEDS_WORK (repo mount is read-only in Docker).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DECISION_FILE="${ROOT}/build/dogfood/publication-decision.json"
PLAN_FILE="${ROOT}/build/dogfood/improvement-plan.json"
AUTO="${DOGFOOD_AUTO_IMPROVE:-false}"
INVOKE_CURSOR="${DOGFOOD_IMPROVE_INVOKE_CURSOR:-false}"

if [[ "${AUTO}" != "true" ]]; then
  echo "[dogfood-improve] skipped — set DOGFOOD_AUTO_IMPROVE=true to enable host improve loop"
  exit 0
fi

if [[ ! -f "${DECISION_FILE}" ]]; then
  echo "[dogfood-improve] no ${DECISION_FILE}"
  exit 0
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "[dogfood-improve] jq required" >&2
  exit 1
fi

DECISION="$(jq -r '.decision // empty' "${DECISION_FILE}")"
if [[ "${DECISION}" != "NEEDS_WORK" ]]; then
  echo "[dogfood-improve] decision=${DECISION:-unknown} — no improvement iteration"
  exit 0
fi

if [[ ! -f "${PLAN_FILE}" ]]; then
  echo "[dogfood-improve] no ${PLAN_FILE} — re-run dogfood after adding planDogfoodImprovements step"
  exit 1
fi

cd "${ROOT}"
PROMPT_FILE="${ROOT}/build/dogfood/improve-prompt.md"
jq -r '.content // empty' "${PLAN_FILE}" > "${PROMPT_FILE}"
echo "[dogfood-improve] wrote ${PROMPT_FILE}"

BEFORE_HASH="$(git status --porcelain | shasum | awk '{print $1}')"

# Stage allowlisted paths (does not commit).
while IFS= read -r path; do
  [[ -z "${path}" ]] && continue
  if [[ -e "${path}" ]]; then
    git add "${path}" 2>/dev/null || true
  fi
done < <(jq -r '.scopedPaths[]? // empty' "${PLAN_FILE}")

if [[ "${INVOKE_CURSOR}" == "true" ]] && command -v cursor >/dev/null 2>&1; then
  echo "[dogfood-improve] invoking cursor agent with improvement plan"
  cursor agent --print "$(cat "${PROMPT_FILE}")" || {
    echo "[dogfood-improve] cursor agent failed — continue with verify commands"
  }
fi

VERIFY_OK=true
if jq -e '.verifyCommands | length > 0' "${PLAN_FILE}" >/dev/null 2>&1; then
  while IFS= read -r cmd; do
    [[ -z "${cmd}" ]] && continue
    echo "[dogfood-improve] verify: ${cmd}"
    if ! bash -lc "${cmd}"; then
      VERIFY_OK=false
    fi
  done < <(jq -r '.verifyCommands[]?' "${PLAN_FILE}")
else
  echo "[dogfood-improve] default verify: mvn -pl embabel-agent-dogfood test"
  if ! mvn -pl embabel-agent-dogfood test -q; then
    VERIFY_OK=false
  fi
fi

AFTER_HASH="$(git status --porcelain | shasum | awk '{print $1}')"
WORKSPACE_CHANGED=false
if [[ "${BEFORE_HASH}" != "${AFTER_HASH}" ]]; then
  WORKSPACE_CHANGED=true
fi

if [[ "${VERIFY_OK}" == "true" ]]; then
  echo "[dogfood-improve] verify passed"
  exit 0
fi

if [[ "${WORKSPACE_CHANGED}" == "true" ]]; then
  echo "[dogfood-improve] workspace changed; re-dogfood may reassess"
  exit 0
fi

echo "[dogfood-improve] verify failed and no workspace changes — manual fix required"
exit 1
