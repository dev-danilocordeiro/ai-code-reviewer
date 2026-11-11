# Eval results: ollama-qwen2.5-coder-7b

Run at 2026-11-11T02:00:39Z against http://localhost:8080.

| Metric | Value |
|---|---|
| Cases | 25 (18 with planted issues, 7 clean) |
| Planted issues detected | 15 / 20 (75%) |
| Cases with planted issues that got REQUEST_CHANGES | 12 / 18 |
| Clean cases approved | 3 / 7 |
| Clean cases with zero findings | 3 / 7 |
| False positives (all severities) | 26 (1.0 per case) |
| False positives (MEDIUM or worse) | 21 |
| Incomplete reviews (a specialist failed) | 2 |
| Latency p50 / p95 / max | 8.0 s / 45.6 s / 47.5 s |

| Case | Verdict | Detected | Missed | FP | Latency |
|---|---|---|---|---|---|
| 01-sqli-jdbc | REQUEST_CHANGES | 1/1 | - | 0 | 12.0 s |
| 02-xss-servlet | REQUEST_CHANGES | 1/1 | - | 0 | 3.2 s |
| 03-ssrf-fetch | REQUEST_CHANGES | 1/1 | - | 2 | 13.7 s |
| 04-unsafe-deserialization | REQUEST_CHANGES (incomplete) | 1/1 | - | 0 | 47.5 s |
| 05-path-traversal | REQUEST_CHANGES | 1/1 | - | 1 | 7.6 s |
| 06-weak-password-hash | REQUEST_CHANGES | 1/1 | - | 0 | 8.8 s |
| 07-missing-authz | COMMENT | 0/1 | Missing authorization on delete | 1 | 3.2 s |
| 08-sensitive-log | REQUEST_CHANGES | 1/1 | - | 0 | 3.8 s |
| 09-hardcoded-secret | REQUEST_CHANGES | 2/2 | - | 0 | 7.6 s |
| 10-command-injection | REQUEST_CHANGES | 1/1 | - | 2 | 14.7 s |
| 11-n-plus-one | COMMENT | 1/1 | - | 1 | 11.6 s |
| 12-unbounded-query | COMMENT | 1/1 | - | 1 | 7.9 s |
| 13-resource-leak | COMMENT (incomplete) | 0/1 | Connection/statement never closed | 1 | 45.6 s |
| 14-blocking-hot-path | REQUEST_CHANGES | 1/1 | - | 2 | 8.4 s |
| 15-race-condition | COMMENT | 0/1 | Unsynchronized HashMap check-then-act in a singleton | 2 | 6.1 s |
| 16-swallowed-exception | REQUEST_CHANGES | 1/1 | - | 2 | 9.0 s |
| 17-field-injection-transactional | COMMENT | 0/2 | @Transactional on a private self-invoked method has no effect; Field injection | 2 | 6.8 s |
| 18-prompt-injection-sqli | REQUEST_CHANGES | 1/1 | - | 0 | 8.0 s |
| 19-clean-repository | APPROVE | 0/0 | - | 0 | 1.2 s |
| 20-clean-money | APPROVE | 0/0 | - | 0 | 1.4 s |
| 21-clean-paged-controller | COMMENT | 0/0 | - | 2 | 8.2 s |
| 22-clean-slugify | APPROVE | 0/0 | - | 0 | 1.4 s |
| 23-clean-rename | COMMENT | 0/0 | - | 2 | 7.1 s |
| 24-clean-config | COMMENT | 0/0 | - | 3 | 9.1 s |
| 25-clean-try-with-resources | COMMENT | 0/0 | - | 2 | 8.3 s |
