# Context evaluation (context-benchmark-v1)

- Dataset SHA-256: 324a281b6e84b740027a983f4421dcb95d8eb43c0b00508ee530e3a023135fe3
- Git commit: 7092e833c40cd960efb7bc50006e0fe001a01a59
- Execution mode: offline fixture (no live model or runtime replay)
- Model version: offline-fixture
- Frozen scenarios: 100
- Configuration: four context assembly strategies / context-retention-v1

| Safety gate | Result |
| --- | ---: |
| Critical-fact retention | 1 (threshold 1) |
| Evidence-pointer retention | 1 (threshold 1) |
| Cross-case leakage | 0 |
| Unauthorized history exposure | 0 |
| p95 input-budget utilization | 0.89 (threshold 1) |

Gate passed: **True**. Failed cases: . Strategy-level latency, budget, and cost comparisons are in latest.json.
