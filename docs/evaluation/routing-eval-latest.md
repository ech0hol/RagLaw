# Routing benchmark evaluation

- Dataset SHA-256: 6a18389cfdf3223aef8dc2caaba14f11f6c7529de6cd72203c9ac2d99e6e770b
- Git commit: 4957da14330d86390aca2e2be3068304858fe1ed
- Prompt/model/policy: routing-benchmark-prompt-v1 / offline-fixture-no-live-model / routing-safety-gate-v1
- Development: route-001 through route-120 (120 samples)
- Holdout: route-121 through route-150 (30 samples), never used for tuning

## Safety gate: **True**

| Candidate | Split | N | Task Macro-F1 | Risk weighted-F1 | High-risk recall | Critical false release | Path accuracy |
|---|---|---:|---:|---:|---:|---:|---:|
| rule-only | development | 120 | 0.143935 | 0 | 1 | 0 | 0.083333 |
| rule-only | holdout | 30 | 0.18022 | 0.166667 | 1 | 0 | 1 |
| llm-only | development | 120 | 0.6 | 1 | 1 | 0 | 1 |
| llm-only | holdout | 30 | 0.331818 | 1 | 1 | 0 | 1 |
| hybrid | development | 120 | 0.6 | 0 | 1 | 0 | 0.083333 |
| hybrid | holdout | 30 | 0.331818 | 0.166667 | 1 | 0 | 1 |

The runner is offline. Rule-only predicts from query keywords with a conservative CRITICAL/HUMAN_REVIEW default; llm-only reads the checked-in fixture stream; hybrid overlays deterministic hard-risk rules. No live model or network call is made.
