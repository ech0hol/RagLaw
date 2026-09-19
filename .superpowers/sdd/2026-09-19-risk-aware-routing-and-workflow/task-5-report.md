# Task 5 report — shadow task-route observation

## Implementation

- Added `TaskRouteDecisionEntity` and Spring Data repository with trace/time/task/risk lookup methods.
- Added Flyway `V39__task_route_decision.sql` with the required audit fields and indexes.
- Added `TaskRouteObserver` using a bounded `ThreadPoolExecutor` (queue capacity 200 by default). Queue rejection and persistence failures are counted and logged without escaping into request handling; candidate data is serialized as JSON and the actual expert context is never mutated.
- Added `TraceQueryService.listTaskRouteDecisions(String)` as an additive read path, preserving existing trace DTO constructors and the legacy document/A2A shadow path.
- Added an optional facade hook immediately after `ExpertRouter.resolve`; it submits a non-authoritative, placeholder-provenance candidate to the bounded observer and leaves the existing expert/SSE execution path unchanged. The hook is optional so existing focused facade tests and deployments without routing beans remain compatible.
- Added `TaskRouteShadowIT`, a focused fictional-data contract proving a changed candidate is persisted while the `ExpertRouter` expert object used by execution remains unchanged. Added an explicit error-code observer overload and serialization fallback (`SERIALIZATION_FAILED`) so auditable rows retain safe shadow-failure evidence where persistence remains possible.
- Added an optional `TraceRecorder` setter hook. Successful shadow rows emit a `task_route_shadow` stage containing candidate task/risk/mode, agreement, and safe error-code fields; trace-recording failures are swallowed and logged without touching SSE execution.
- Persistence failures now also emit `task_route_shadow` with `PERSISTENCE_FAILED` when trace recording remains available. The facade uses a public additive `observeTaskRouteShadow` seam immediately after expert resolution, allowing focused integration verification without changing the execution path.

## Tests and validation

- Added fictional-data focused `TaskRouteObserverTest` coverage for persistence fields/JSON, repository failure isolation, and bounded-queue rejection counting.
- Incremental compilation and focused test invocation passed before a clean rebuild. A subsequent clean reactor build was blocked by the managed Windows/JDK compiler reporting an opaque failure in the unchanged `raglaw-common` module; no source diagnostic was emitted.

## Integration and scope concerns

The existing `ExpertRouter`/SSE path remains authoritative. This task adds persistence and trace exposure without enforcing candidate routes or executing workflows. The facade hook uses explicit `unknown-*` provenance placeholders because a concrete `TaskRoutingService` bean and complete classifier provenance are not currently available at that boundary. No user query or legal document text is persisted by the new observer.

Commits: `7e4c024` — `feat: observe task routing in shadow mode`; `59215f2` — `fix: record shadow route errors and isolation`; `017643b` — `fix: strengthen shadow route isolation coverage`; `bc16d82` — `fix: emit shadow route trace failures`.

## Final isolation-coverage update

- Updated `TaskRouteShadowIT` to call `AguiReactRunFacade.observeTaskRouteShadow` with a mocked current `ExpertRouter` result, verify exactly one persisted `CONTRACT_REVIEW` candidate, and assert the actual `ExpertContext` remains unchanged.
- The test now loads `V39__task_route_decision.sql` from the classpath and checks the table plus all required indexes.
- Focused Maven verification reached the existing managed Windows/JDK compiler failure in the unchanged AgentScope compilation (`unknown compilation problem`); no test execution was possible in that run.
- Commit: `017643b` — `fix: strengthen shadow route isolation coverage`.
