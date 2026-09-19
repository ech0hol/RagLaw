# Routing benchmark evaluation

- Dataset SHA-256: 6a18389cfdf3223aef8dc2caaba14f11f6c7529de6cd72203c9ac2d99e6e770b
- Git commit: bc16d824d6450e07ee029b9377fe4b2378af3437
- Prompt/model/policy: routing-benchmark-prompt-v1 / offline-fixture-no-live-model / routing-safety-gate-v1
- Holdout: 30 samples (route-121 through route-150), reported separately and not tuned

## Safety gate: **True**

| Candidate | Task Macro-F1 | Risk weighted-F1 | High-risk recall | Critical false release | Path accuracy | p50/p95 ms | Model calls |
|---|---:|---:|---:|---:|---:|---:|---:|
| rule-only | 0.581618 | 1 | 1 | 0 | 1 | 16/24 | 0 |
| llm-only | 0.581618 | 1 | 1 | 0 | 1 | 16/24 | 0 |
| hybrid | 0.581618 | 1 | 1 | 0 | 1 | 16/24 | 0 |

The runner is offline: rule-only and hybrid use deterministic policy fixtures; llm-only reads the checked-in fixture stream. No live model or network call is made. The final holdout is emitted in the same candidate records as holdoutSize and remains untouched by tuning.
