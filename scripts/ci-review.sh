#!/usr/bin/env bash
# Sends the current branch's diff to ai-code-reviewer and fails on REQUEST_CHANGES.
#
# Usage: scripts/ci-review.sh [base-ref]
#   base-ref           what to diff against (default: $BASE_REF or origin/main)
# Environment:
#   REVIEWER_URL       service base URL (default: http://localhost:8080)
#   REVIEW_PATHS       optional pathspec, e.g. "src/main/**/*.java"
#   REVIEW_TIMEOUT     curl max time in seconds (default: 900)
#   GITHUB_STEP_SUMMARY  when set (GitHub Actions), a markdown report is appended to it
#
# Exit codes: 0 APPROVE or COMMENT, 1 REQUEST_CHANGES, 2 usage or service error.
set -euo pipefail

BASE="${1:-${BASE_REF:-origin/main}}"
URL="${REVIEWER_URL:-http://localhost:8080}"
TIMEOUT="${REVIEW_TIMEOUT:-900}"

for tool in git curl jq; do
  command -v "$tool" >/dev/null || { echo "ci-review: '$tool' is required" >&2; exit 2; }
done

pathspec=()
if [[ -n "${REVIEW_PATHS:-}" ]]; then
  read -r -a pathspec <<<"$REVIEW_PATHS"
fi

# Three-dot: only what this branch changed since it diverged from BASE.
diff="$(git diff --no-color --no-ext-diff --unified=3 "${BASE}...HEAD" -- "${pathspec[@]}")"
if [[ -z "$diff" ]]; then
  echo "ci-review: no changes against ${BASE}; nothing to review."
  exit 0
fi

response="$(mktemp)"
trap 'rm -f "$response"' EXIT

status="$(jq -n --arg content "$diff" '{fileName: "changes.diff", content: $content, isDiff: true}' \
  | curl -sS -o "$response" -w '%{http_code}' --max-time "$TIMEOUT" \
      -H 'Content-Type: application/json' --data-binary @- "${URL%/}/api/review")" || {
  echo "ci-review: could not reach ${URL}" >&2
  exit 2
}

if [[ "$status" != "200" ]]; then
  echo "ci-review: service answered HTTP ${status}" >&2
  jq -r '"\(.title // "error"): \(.detail // "")"' "$response" >&2 2>/dev/null || cat "$response" >&2
  exit 2
fi

verdict="$(jq -r '.verdict' "$response")"

report="$(jq -r '
  "## AI review: \(.verdict)\n\n\(.summary)\n",
  (if (.findings | length) > 0 then
     "| Severity | Category | Line | Title | Reviewer |\n|---|---|---|---|---|",
     (.findings[] | "| \(.severity) | \(.category) | \(.line // "-") | \(.title | gsub("\\|"; "/")) | \(.reviewer) |")
   else empty end),
  "\n_Took \(.durationMs) ms. Line numbers refer to the submitted diff._"' "$response")"

echo "$report"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "$report" >>"$GITHUB_STEP_SUMMARY"
fi

if [[ "$verdict" == "REQUEST_CHANGES" ]]; then
  exit 1
fi
exit 0
