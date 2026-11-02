# Contribution review: bounded native async observation workflow

## Recommendation

**Ready for maintainer review within the documented bounded observation scope**,
subject to final-head hosted checks and unchanged executable code. This is an
author-conducted read-only review pass, **not an independent third-party review or
maintainer approval**. User authorization to merge completed, tested feature PRs
is separate from this recommendation.

## Reviewed state

- Integration target: `feature/python-experiment-runner`.
- Base: `e01908d718fc0beb20192f74cb6dfcd57dcff682` (normal PR #104 merge).
- Reviewed executable candidate: `e83e9b61a0b930de54371d669cdef4ec1029ca3e`.
- Complete scope includes the shared sync transport/request/Job/read helpers,
  native async transport, public scope/job/client APIs, optional inspection guards,
  tests, example, docs and existing-coverage-preserving CI extension.
- No production Java, shared worldview or scientific model changes. Fresh Java
  test-only readiness probes preserve the original fixed endpoint/query and merely
  use a new exact private evidence root. Historical probes/evidence are unchanged.

## Capability and boundaries

A Python script/notebook can await typed observation work on explicitly configured
services, keep known Jobs through local cancellation/timeouts, read checked point
values and release only scopes it explicitly chooses to release. Transport and
native request/body iteration are genuinely asynchronous and bounded by configured
request concurrency. No worker-thread facade, background loop or write replay.

This is the first **async observation** feature, not full async parity with all
scientific exports, Connection/Hub credential orchestration, saved restoration,
graph/events or synchronous high-level inspection helpers. Unsupported workflows
fail before starting an operation; no fake success/always-skipped acceptance test.

## Findings and corrections

No unresolved blocking implementation defect found in this bounded review; not
exhaustive proof of correctness. The pass identified and fixed:

- Mixing a sync client into an async scope/job/service could execute synchronous
  I/O before an await failed. Constructors now reject that combination before I/O.
- Dynamically resolved credentials were missing from response-error redaction.
  Callback values now use Endpoint validation and join redaction before a request.
- Timeout coverage initially relied on scheduler timing. A deterministic expired
  wait proves no HTTP call and retains the original Job. Old failing CI retained.
- The external live guard omitted the exact read-only model lookup; it was added
  once without broadening mutation authority. Failed attempt retained.
- The functional fixture used an undefined Region definition. It now uses the
  established `earth:Terrestrial earth:Region`, private explicit persistence and
  original controlled-model namespace. No semantic alias or ontology repair.

## Validation actually performed

| Check | Status and limits |
| --- | --- |
| Focused async tests | PASS: 109 cases; mocked contract cases plus actual loopback HTTP overlap/closure, not environmental accuracy |
| Full offline regression | PASS: 1817 tests, one existing Rasterio warning, 50.38s; current source explicitly selected |
| Existing and async coverage gates | PASS: no exclusions/lower thresholds; new async production modules 100% lines/branches |
| Lint and package build | PASS: existing fatal-error selection; wheel and sdist |
| Fresh installed core-only wheel | PASS: sync/async imports/lifecycle/offline Job reference, no scientific packages, no network or leftover async tasks |
| Genuine Java workflow | PASS at reviewed executable candidate: one session/context, two Jobs, 20 exact controlled values, local wait cancellation and original-Job resume |
| Explicit server cancellation | PASS only as completed-Job race: accepted=false, FINISHED; no running-simulation interruption claim |
| Cleanup | PASS: scope release booleans true; all 13 recorded owned JVM identities absent; no shared service changed |
| Hosted checks | All reported checks at executable candidate green: Python 3.11–3.13, core-only, Java build and Jenkins. Recheck final documentation head before merge |

Live details and raw-record hashes are in
[`native-async-controlled-20261102.json`](../results/native-async-controlled-20261102.json).
The Java binary tuple is explicitly pinned; this is not a blanket claim of source
equivalence with current upstream or a full-roadmap integration test.

## Scientific/lifecycle evidence and remaining limits

The independent expected value is the controlled fixture's predeclared
`100 + 93/4` metres. It is not calculated from the returned field. Exact comparison
is justified for this constant quarter-valued fixture only. Unit/cell decoding
shares existing codecs; parity and negative cases cover valid zero/null/precision,
while the live fixture itself is not nonuniform, categorical, zero or missing-cell
acceptance. No native export was repaired or used to circumvent issue #10.

Cancellation of a Python wait leaves the known server Job, not permission to
resubmit. Transport errors after potentially mutating sends report unknown outcome.
Scope release remains explicit, origin/runtime-bound and server-authorized. The
live owner case is not new two-user async privacy acceptance; two principals were
checked during prerequisite service readiness only.

## Contributor coordination and smallest next actions

No contributor or maintainer is assigned review work by this report. Verify final
CI, retain exact executable-tree identity through documentation closeout, and use
normal merge only under the user's instruction. Continue the next roadmap feature
in its own PR afterward. Later authoring/events/federation work must verify actual
server contracts rather than extrapolate from this feature's successful fixture.

## Suggested PR description

Add bounded native async observation workflows sharing existing DTOs, scope and Job
semantics; retain sync behavior, origin-bound credentials, explicit lifecycle and
unknown-write safety. Include parity/security/loopback tests and a genuine owned
Java controlled-value acceptance run with verified cleanup. Exclude async exports,
saved restoration, Hub orchestration, real-provider and PaL reproduction claims.
