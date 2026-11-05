# Current-tuple async own-wait cancellation

## Q8a contract — before implementation

Accepted baseb41016737; branchfeature/python-async-own-wait-acceptance. Reuse
native AsyncClient/AsyncJob (no thread facade or new public API), existing synthetic
234.5scenario and private context controls. No model/Java/worldview/fixture changes.

Create one owned private session/context and region, submit one numerical scenario
Job. Two asyncio tasks wait on that same original Job. Cancel one task after its
status-read hook has entered; require asyncio.CancelledError, no remote cancel/
release/submission caused by cancellation, and the other wait completes the same
originalJob/observation. Validate all20cells exactly234.5m, original focus/IDs,
two concurrent bounded readbacks and foreign-principal saved-handle denial. Local
close must not dispose work; creator alone releases context/session with actual
confirmation. Audit writes/cancel/release routes and counts around own-wait cancel.

Hook synchronization only controls a local pending read, not Java calculation
timing. Do not claim interruption of a running numerical simulation, cancellation
of another user's wait, multiclient committed-state convergence, durable event
replay, managed-agent incarnation, notebook execution or scale/soak from this test.
Those Q8/CAP11/12/13/RC-P3.1/P3.5/P4.5 constituents remain separately visible.

Existing genuine availability preflight after owned Resources restart may be reused,
never a status override or automatic scientific replay. Independent expected values
come from accepted scenario source; no nonuniform fixture reset. Actual cleanup,
installed/live/review/normalCI/eligiblemerge remain required.

Q7a first hierarchy assertion failed before model/session/compute; branch830a141cd
and receipts are preserved withoutPR/merge or changed semantics. Rootcause unresolved;
this independent async work does not close that gate. A/B/studies remain incomplete.
