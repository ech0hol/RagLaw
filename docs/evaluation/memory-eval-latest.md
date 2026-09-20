# Memory evaluation (memory-benchmark-v1)

- Benchmark SHA-256: df4010dc2190920c78bb52b3effa245d178fa8627a271709d3c9f24ed8f61740
- Git commit: 3ed0608a823a07ff21e1f7f386823e6f23121aa5
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
