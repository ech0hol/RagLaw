# Memory evaluation (memory-benchmark-v1)

- Benchmark SHA-256: $hash
- Git commit: $commit
- Frozen scenarios: 100
- Runtime status: fixture frozen; runtime scoring requires labeled event replay

## Safety gates

| Gate | Result |
| --- | ---: |
| Cross-case leakage | 0 |
| Unauthorized history exposure | 0 |
| Protected-fact retention | 1.0 |
| Superseded value recalled as current | 0 |

The report intentionally leaves model-dependent precision/recall metrics null until replay data is supplied. The benchmark composition and safety invariants are executable in MemoryBenchmarkTest.
