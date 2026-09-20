# Context evaluation (context-benchmark-v1)

- Dataset SHA-256: 324a281b6e84b740027a983f4421dcb95d8eb43c0b00508ee530e3a023135fe3
- Git commit: ae03055377b0771148c02b0d2c3a1b6ed58bbaf5
- Frozen scenarios: 100
- Configuration: four context assembly strategies / context-retention-v1

| Safety gate | Result |
| --- | ---: |
| Critical-fact retention | 1 |
| Evidence-pointer retention | 1 |
| Cross-case leakage | 0 |
| Unauthorized history exposure | 0 |
| p95 input-budget utilization | 0.89 |

Gate passed: **True**. Failed cases: . Strategy-level latency, budget, and cost comparisons are in latest.json.
