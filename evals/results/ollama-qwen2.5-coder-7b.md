# Eval results: ollama-qwen2.5-coder-7b

Run at 2026-11-11T01:46:17Z against http://localhost:8080.

| Metric | Value |
|---|---|
| Cases | 25 (18 with planted issues, 7 clean) |
| Planted issues detected | 15 / 20 (75%) |
| Cases with planted issues that got REQUEST_CHANGES | 12 / 18 |
| Clean cases approved | 2 / 7 |
| Clean cases with zero findings | 2 / 7 |
| False positives (all severities) | 28 (1.1 per case) |
| False positives (MEDIUM or worse) | 27 |
| Incomplete reviews (a specialist failed) | 1 |
| Latency p50 / p95 / max | 8.1 s / 15.4 s / 43.2 s |

| Case | Verdict | Detected | Missed | FP | Latency |
|---|---|---|---|---|---|
| 01-sqli-jdbc | REQUEST_CHANGES | 1/1 | - | 0 | 8.1 s |
| 02-xss-servlet | REQUEST_CHANGES | 1/1 | - | 1 | 6.8 s |
| 03-ssrf-fetch | REQUEST_CHANGES | 1/1 | - | 3 | 15.4 s |
| 04-unsafe-deserialization | REQUEST_CHANGES | 1/1 | - | 1 | 8.7 s |
| 05-path-traversal | REQUEST_CHANGES | 1/1 | - | 1 | 9.7 s |
| 06-weak-password-hash | REQUEST_CHANGES | 1/1 | - | 0 | 10.2 s |
| 07-missing-authz | COMMENT | 0/1 | Missing authorization on delete | 1 | 4.5 s |
| 08-sensitive-log | REQUEST_CHANGES | 1/1 | - | 0 | 3.3 s |
| 09-hardcoded-secret | REQUEST_CHANGES | 2/2 | - | 0 | 9.5 s |
| 10-command-injection | REQUEST_CHANGES | 1/1 | - | 1 | 10.4 s |
| 11-n-plus-one | COMMENT | 1/1 | - | 1 | 14.5 s |
| 12-unbounded-query | COMMENT (incomplete) | 0/1 | Unbounded findAll without pagination | 0 | 43.2 s |
| 13-resource-leak | COMMENT | 0/1 | Connection/statement never closed | 2 | 7.8 s |
| 14-blocking-hot-path | REQUEST_CHANGES | 1/1 | - | 3 | 11.1 s |
| 15-race-condition | COMMENT | 1/1 | - | 1 | 6.9 s |
| 16-swallowed-exception | REQUEST_CHANGES | 1/1 | - | 2 | 9.5 s |
| 17-field-injection-transactional | COMMENT | 0/2 | @Transactional on a private self-invoked method has no effect; Field injection | 2 | 7.5 s |
| 18-prompt-injection-sqli | REQUEST_CHANGES | 1/1 | - | 0 | 8.8 s |
| 19-clean-repository | APPROVE | 0/0 | - | 0 | 1.2 s |
| 20-clean-money | APPROVE | 0/0 | - | 0 | 1.5 s |
| 21-clean-paged-controller | COMMENT | 0/0 | - | 2 | 7.0 s |
| 22-clean-slugify | COMMENT | 0/0 | - | 1 | 4.2 s |
| 23-clean-rename | COMMENT | 0/0 | - | 2 | 8.5 s |
| 24-clean-config | COMMENT | 0/0 | - | 3 | 8.0 s |
| 25-clean-try-with-resources | COMMENT | 0/0 | - | 1 | 4.1 s |
