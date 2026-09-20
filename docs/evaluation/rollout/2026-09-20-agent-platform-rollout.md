# Agent platform rollout scorecard — 2026-09-20

Status: **HOLD — no production behavior enabled**

The implementation remains reversible. Routing and context defaults stay in `SHADOW`; no workflow-specific persistent shared-state or governed-context enforcement was enabled because the repository-wide backend gate is not green in the current Java 22 environment.

| Stage | Traffic | Current state | Rollback flag | Evidence |
| --- | ---: | --- | --- | --- |
| Dynamic expert resolution | 0% | Shadow/observation only; fixed-expert path remains authoritative | `RAGLAW_ROUTING_MODE=SHADOW` | `docs/evaluation/workflow-state/latest.json` |
| Persistent shared state | 0% | Not enabled | `RAGLAW_ROUTING_MODE=SHADOW` | `docs/evaluation/workflow-state/latest.json` |
| Governed context | 0% | Not enforced; context candidate remains shadow-only | `RAGLAW_CONTEXT_MODE=SHADOW` | `docs/evaluation/context/latest.json` |

## Scorecard

- Workflow fixture gate: passed, 60 scenarios, all declared safety counters zero.
- Context fixture gate: passed, 100 scenarios, critical-fact and evidence-pointer retention 1.0, p95 budget utilization 0.89.
- Frontend unit tests: 43 passed.
- Frontend production build: passed with existing PDF worker and chunk-size warnings.
- Focused `IngestPipelineTest`: 6 passed after removing stale Mockito stubs.
- Full backend `mvn test`: **blocked** by the Java 22 compiler failing while compiling `raglaw-memory` tests; this is not treated as a quality pass.
- Live model replay and API acceptance: not counted as passed in this environment.
- Playwright smoke: blocked as expected because the Vite proxy received `ECONNREFUSED` for `/api/v1/auth/login`; no backend service was running.

## Release rule

Do not move any row above 0% until the full backend gate, live replay, API acceptance, and Playwright smoke are green. Each later enablement must change one mode at a time, retain the rollback flag, and attach a fresh scorecard with the measured traffic percentage and safety metrics.
