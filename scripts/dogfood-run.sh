#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ROOT}/dogfood.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE} — copy from dogfood.env.example and set DEEPSEEK_API_KEY" >&2
  exit 1
fi

if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  export GH_TOKEN="$(gh auth token)"
  echo "[dogfood-run] using GH_TOKEN from host \`gh auth token\` (not saved to dogfood.env)"
else
  echo "[dogfood-run] gh not authenticated — GitHub memory sync disabled; repo still resolved from git remote"
fi

read_env_var() {
  local key="$1"
  local default="${2:-}"
  local line
  line="$(grep -E "^${key}=" "${ENV_FILE}" 2>/dev/null | tail -1 || true)"
  if [[ -z "${line}" ]]; then
    printf '%s' "${default}"
    return
  fi
  local value="${line#*=}"
  value="${value%\"}"
  value="${value#\"}"
  printf '%s' "${value}"
}

export DOGFOOD_AUTO_IMPROVE="$(read_env_var DOGFOOD_AUTO_IMPROVE "${DOGFOOD_AUTO_IMPROVE:-false}")"
export DOGFOOD_IMPROVE_MAX_ITERATIONS="$(read_env_var DOGFOOD_IMPROVE_MAX_ITERATIONS "${DOGFOOD_IMPROVE_MAX_ITERATIONS:-2}")"
export DOGFOOD_IMPROVE_INVOKE_CURSOR="$(read_env_var DOGFOOD_IMPROVE_INVOKE_CURSOR "${DOGFOOD_IMPROVE_INVOKE_CURSOR:-false}")"
export DOGFOOD_PUBLICATION_AUTO_RAISE_PR="$(read_env_var DOGFOOD_PUBLICATION_AUTO_RAISE_PR "${DOGFOOD_PUBLICATION_AUTO_RAISE_PR:-false}")"

MAX_ITERATIONS="${DOGFOOD_IMPROVE_MAX_ITERATIONS}"
AUTO_IMPROVE="${DOGFOOD_AUTO_IMPROVE}"

run_dogfood() {
  docker compose --env-file "${ENV_FILE}" -f compose.dogfood.yml run --rm dogfood "$@"
}

cd "${ROOT}"
for ((iteration = 1; iteration <= MAX_ITERATIONS; iteration++)); do
  echo "[dogfood-run] iteration ${iteration}/${MAX_ITERATIONS}"
  run_dogfood "$@"

  DECISION=""
  if [[ -f "${ROOT}/build/dogfood/publication-decision.json" ]] && command -v jq >/dev/null 2>&1; then
    DECISION="$(jq -r '.decision // empty' "${ROOT}/build/dogfood/publication-decision.json")"
  fi

  if [[ "${DECISION}" == "RAISE_PR" ]]; then
    echo "[dogfood-run] publication decision RAISE_PR — stopping improve loop"
    break
  fi

  if [[ "${DECISION}" != "NEEDS_WORK" ]] || [[ "${AUTO_IMPROVE}" != "true" ]]; then
    break
  fi

  if [[ "${iteration}" -ge "${MAX_ITERATIONS}" ]]; then
    echo "[dogfood-run] max iterations reached"
    break
  fi

  echo "[dogfood-run] NEEDS_WORK — running host improve step before re-dogfood"
  if "${ROOT}/scripts/dogfood-improve.sh"; then
    echo "[dogfood-run] improve step done — re-running dogfood"
  else
    echo "[dogfood-run] improve step failed — stopping loop"
    break
  fi
done

"${ROOT}/scripts/dogfood-raise-pr.sh"
