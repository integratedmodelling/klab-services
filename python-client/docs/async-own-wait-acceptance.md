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
two concurrent bounded readbacks and foreign-principal root-context attachment denial. Local
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

First live run exposed a harness call to explicitly unsupported
AsyncClient.restore_handle. Do not implement a fake restoration layer or count that
refusal as authorization. One bounded correction uses supported attach_context on
the original known root plus explicit known focus/Job IDs. Same-owner reader must
remain unowned, retain Runtime/observation/focus identity and close without disposal;
foreign attach must be explicitly denied. Saved-handle serialization is checked,
but native async saved-handle restoration remains unsupported, not certified here.

## Verified bounded outcome

At744a37f03:3,004installed/17gates/lint/build/parity+9focusedPASS. Correctedactual
JavaPASS:oneownwaitcancel/otherwaitsameJob+focus+20exact234.5m cells,tworeads,
foreignattachdenied,sameownerunownedexplicitroot/focus/Jobreadback,readerclose
preservescreatorwork. Exactly2scientificsubmits,0remoteJobcancels;waitauditGETonly.
Creatorcontext/sessionrelease+ownedprocess/listenercleanupPASS,unrelatedJavaunchanged.
Wheel `571eb2dba8341532f028ea7b77c56d42f5659ec8b55618a755725f7185e8a3ef`.
First unsupportedrestore-callfailure retained. Normalhosted/merge/release separate;
see [author/CLI review](reviews/async-own-wait-acceptance.md). FullQ8/A/B/notebook/
agents/events andasyncsaved-handle restoration incomplete.
