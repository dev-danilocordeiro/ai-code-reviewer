#!/usr/bin/env bash
# Runs the eval set against a running ai-code-reviewer.
#   evals/run.sh --label ollama-qwen2.5-coder-7b [--base-url http://localhost:8080] [--only 01,18]
# Writes evals/results/<label>.md (scores) and evals/results/<label>.json (raw reports).
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B -q test-compile
./mvnw -B -q exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=com.danilocordeiro.reviewer.evals.EvalRunner \
  -Dexec.args="$*"
