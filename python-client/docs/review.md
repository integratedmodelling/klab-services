# Maintainer revision review

Reviewed locally on 6 October 2026 against committed checkpoint `540fef93d`.
These are separate local assessments of
the server boundary, Python library, and optional development tools.

## Bounded server changes

* Cold context connection queries the exact persisted ID and checks persisted
  owner/user/group ACLs before reconstruction. Federation membership and caller
  configuration are not authorization. Listing uses the same ACL rule.
* Restored descriptors retain their actual rights; missing legacy rights mean
  owner-only, and malformed rights fail closed.
* When a collaborator restores a shared context first, its parent session retains
  the persisted owner's authority. A live check denies collaborator session use
  and proves the owner can still create/release a new context.
* Domain exceptions map to 400/403/404 at the HTTP boundary without exposing
  internal diagnostics. Core/runtime code uses domain rather than HTTP exceptions.

Verification: **29 targeted Java tests passed**, zero failures/errors/skips
(17 core services, 2 Reasoner, 10 Runtime). This includes graph query/ACL restoration,
managed scopes, token/error mapping, job outcomes, unit semantics, storage, and
test-fixture controls. `PersistedContextAccessTest` executes actual embedded-Neo4j
Cypher and adapts its records through a mocked driver result; Bolt is disabled in
that test due to harness Netty incompatibility. The fresh live deployment exercises
the actual service/driver path and a real Runtime restart with graphdb kept alive.

Review boundary: context listing currently scans candidate contexts and filters
ACLs in Java. Large-graph listing performance/pagination is not established.
Malformed persisted permissions can fail a listing rather than return partial
results; this is fail-closed behavior. Full Java reactor regression is not claimed.

## Python client

* Submission rejects foreign Runtime/contextless scopes before HTTP. Returned
  jobs and observations bind to the submitting client, including same-Runtime
  submissions through a second client after the original client has closed.
* Focus rejects foreign-Runtime observations; scientific reads require geometry.
* `.contextualization` preserves the exact transported enum value. Legacy
  description types use explicit compatible mappings; absent/future/unrepresentable
  remote values raise unsupported-operation errors rather than inventing semantics.
* Public method annotations/docstrings and abstract return contracts describe
  actual checked DTOs, job outcomes, attachment and explicit lifecycle operations.

Verification: **91 offline tests passed, 4 live tests deselected** on Windows
Python 3.11 and 3.12. An isolated Python 3.11 environment installed the built wheel
and passed the same 91 tests from outside the checkout. Wheel and source
distribution builds succeeded; the wheel contains the library, while the source
distribution includes concise docs and development tooling. Detailed historical
audit material is retained under repository `notes/python-client/`.

## Optional local tooling and CI

* Access-denial checks accept only explicit authorization/missing-asset errors.
  The second valid user first proves an allowed operation; 5xx, protocol, timeout,
  transport and authentication failures cannot satisfy isolation checks.
* PID inspection distinguishes absent processes from unverifiable live processes.
  Child steps, inspection and termination have finite deadlines; partial startup
  and interruption invoke cleanup, which must confirm owned process exit.
* Pinned worldview revision, model/source/compiled hashes and resolved SNAPSHOT
  provenance are recorded. TEST authority credentials remain outside Git.
* Nonuniform coordinate-coded storage includes valid zero and missing data,
  checked against independent curve/orientation oracles. Cancellation uses an
  explicit computation-entry/release gate. Controls exist only on test classpaths.
* Offline GitHub CI installs test/local extras and covers Python 3.11/3.12/3.13,
  then builds the package. GitHub execution and Linux/3.13 results are pending.

Fresh run `klab-revision-live3`, **15:57:00–15:59:31 UTC**:
two live acceptance tests passed; private/shared cold-restart checks passed;
full-workflow c1/c2/c4 and repeated-read smoke phases passed; cleanup exit was 0
with all five JVM exits confirmed. Invocation used `--samples 1 --duration 1
--step-timeout 180`. Evidence (`acceptance.xml`, `cold-context-check.json`, phase
JSON reports, `run-report.json`, logs) is in the external run-state directory.
These short revision smoke phases do not replace the separately labeled
[throughput baseline](throughput.md).

## Remaining acceptance boundaries

Evidence establishes an isolated signed-ROLE_USER TEST deployment and synthetic
scientific fields. Production hub onboarding, browser scoped credentials,
actual-provider terrain accuracy against an independent reference, bulk/large
data, temporal/federated workflows and sustained soak/capacity remain unverified.
See [contracts](contracts.md) and [provider acceptance](live-acceptance.md).
