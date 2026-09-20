# Workflow state evaluation (workflow-state-benchmark-v1)

- Dataset SHA-256: c101867d03dbf39067d6df3e248860cb474c801aa33d52efcfe3216e153de9de
- Git commit: 7092e833c40cd960efb7bc50006e0fe001a01a59
- Frozen scenarios: 60
- Configuration: offline fixture / workflow-state-safety-v1

| Metric | Result | Gate |
| --- | ---: | ---: |
| Completion rate | 1 | >= 0.95 |
| Expert top-1 accuracy | 1 | >= 0.90 |
| No-candidate accuracy | 1 | >= 0.98 |
| p95 context budget utilization | 0.82 | <= 1.0 |
| Safety violations | 0 | 0 |
| p50 / p95 latency (ms) | 525 / 1420 | observe |
| Average cost (USD) | 0.0085 | observe |

Gate passed: **True**. Failed cases: .
