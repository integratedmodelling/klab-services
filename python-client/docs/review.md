# Maintainer revision review

**Re-reviewed 7 October 2026:** both PR-blocking findings are fixed and covered
by regressions. No additional contribution-specific blocker was identified.
The candidate integrates develop `64754ea2b`. This document is the current
verification record; earlier audit drafts remain available in Git history.
These are separate local assessments of
the server boundary, Python library, and optional development tools.

## Bounded server changes

* Cold context connection queries the exact persisted ID and checks persisted
  owner/user/group ACLs before reconstruction. Federation membership and caller
  configuration are not authorization. Listing uses the same ACL rule.
* Restored descriptors retain their actual rights; missing legacy rights mean
  owner-only, and malformed rights fail closed.
* Warm managed scopes and Runtime configuration lookup use username/group ACLs,
  including public-resource exclusions, consistently with persisted lookup.
* When a collaborator restores a shared context first, its parent session retains
  the persisted owner's authority. A live check denies collaborator session use
  and proves the owner can still create/release a new context.
* Domain exceptions map to 400/403/404 at the HTTP boundary without exposing
  internal diagnostics. Core/runtime code uses domain rather than HTTP exceptions.
  Authenticated requests with an unresolved explicit scope return 404 before
  reaching controllers; scoped absence/denial cannot become job-controller 500.

Verification: **56 targeted Java tests passed**, zero failures/errors/skips
(28 core services, 2 Reasoner, 26 Runtime). This includes graph query/ACL restoration,
managed scopes, token/error mapping, job outcomes, unit semantics, storage, and
test-fixture controls. `PersistedContextAccessTest` executes actual embedded-Neo4j
Cypher and adapts its records through a mocked driver result; Bolt is disabled in
that test due to harness Netty incompatibility. The fresh live deployment exercises
the actual service/driver path and a real Runtime restart with graphdb kept alive.

Review boundary: context listing currently scans candidate contexts and filters
ACLs in Java. Large-graph listing performance/pagination is not established.
Malformed persisted permissions can fail a listing rather than return partial
results; this is fail-closed behavior. A broader 454-case selection produced
**436 passed, 11 skipped, 7 errors**. All seven errors match test identities and
exception types reproduced on unchanged current develop. The broad run used
`-Dmaven.test.failure.ignore=true` to collect all modules, so its BUILD SUCCESS is
not a green test-suite claim. The 56-test gate ran without that flag. Full Java
reactor regression is not claimed.

## Python client

* Submission rejects foreign Runtime/contextless scopes before HTTP. Returned
  jobs and observations bind to the submitting client, including same-Runtime
  submissions through a second client after the original client has closed.
* Focus rejects foreign-Runtime observations; scientific reads require geometry.
* Service-level session/context release rejects foreign bound handles before
  HTTP; same-Runtime second-client handles and explicit raw IDs remain supported.
* `.contextualization` preserves the exact transported enum value. Legacy
  description types use explicit compatible mappings; absent/future/unrepresentable
  remote values raise unsupported-operation errors rather than inventing semantics.
* Public method annotations/docstrings and abstract return contracts describe
  actual checked DTOs, job outcomes, attachment and explicit lifecycle operations.

Verification: **97 offline tests passed, 4 live tests deselected** on Windows
Python 3.11/3.12 and Ubuntu/WSL2 Python 3.11/3.12/3.13. An isolated Python 3.11
environment installed the built wheel and passed the same 97 tests from outside
the checkout. Wheel and source
distribution builds succeeded; the wheel contains the library, while the source
distribution includes concise docs and development tooling. A short repository
resume note lives under `notes/python-client/`; earlier audits remain in Git history.

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
  then builds the package. That matrix was executed locally on Ubuntu/WSL2:
  97 tests and isolated wheel/sdist builds passed on all three versions. GitHub-hosted
  execution is tracked separately on the branch/PR; local Linux evidence is not an Actions run.

Fresh integrated run `klab-pr-remediation-live2`, **14:43:25–14:47:11 UTC**:
two live acceptance tests passed; private/shared/public-excluded cold-restart
checks passed; full-workflow c1/c2/c4 and repeated-read phases passed; cleanup
exit was 0 with all five JVM exits confirmed. Invocation used `--samples 8
--duration 15 --step-timeout 900`. The preceding attempt rebuilt servers/Web UI/
classpaths and exposed the cold-header 500, then cleaned up successfully.
Evidence (`acceptance.xml`, `cold-context-check.json`, phase
JSON reports, `run-report.json`, logs) is in the external run-state directory.
See [throughput measurements](throughput.md).

## Reproduce the Java verification

From the repository root with JDK 21 and the documented Maven prerequisites:

```powershell
.\mvnw.cmd -B -ntp -pl klab.services.runtime,klab.services.reasoner -am '-Dtest=PersistedContextAccessTest,RuntimeContextAuthorizationTest,DomainErrorMappingTest,ManagedScopeAccessTest,ScopeManagerTest,TokenAuthorizationFilterTest,FixtureControlsTest,StorageRegistrationTest,JobManagerTest,SemanticsBuilderUnitTest,Neo4jQueryExecutionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

This is the strict 56-case passing selection. For the broader collection:

```powershell
.\mvnw.cmd -B -ntp -pl klab.services.runtime,klab.services.reasoner -am '-Dtest=org/integratedmodelling/klab/services/runtime/**/*Test,TemporalProcessIntegrationTest,SettingsImplTest,StackImplTest,ShardBufferIOTest,BulkShardStorageTest,StorageManagerImplTest,StorageReconstructionTest,StorageScanTest,StorageMediationBaselineTest,StorageReferenceFixturesTest,ConformantScanTest,SpatialScanTest,TemporalStorageTest,KeyedStorageTest,ValueMediationTest,HistogramUtilsTest,DomainErrorMappingTest,ManagedScopeAccessTest,ScopeManagerTest,TokenAuthorizationFilterTest,JobManagerTest,SemanticsBuilderUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.failure.ignore=true' test
```

The seven errors, also reproduced on unchanged `64754ea2b`, are:

* `StorageMediationBaselineTest.nativeAndAdaptedCursorsShareOnePositionAndReadonlyWritesCannotAdvanceIt`:
  obsolete expectation that an exhausted scanner's `nextLong()` succeeds.
* `ProvisionalObservationScopeTest.registeredCandidateRoundTripsAsContextWithoutEnteringCommitGraph`:
  fixture supplies no digital twin for current grid alignment.
* Four `SubmissionCancellationTest` cases and one `SubmissionAdmissionCancellationTest`
  case: their generic graph mock cannot be cast to KnowledgeGraphNeo4j by current
  grid alignment. This prevents those fixture cases reaching the cancellation assertions.

Failing test identities and exception types matched the unchanged-base runs exactly.
Reproduce the baseline comparison on a separate checkout of `64754ea2b` with
`-Dtest=StorageMediationBaselineTest` in core services and
`-Dtest=ProvisionalObservationScopeTest,SubmissionCancellationTest,SubmissionAdmissionCancellationTest`
in Runtime. The 11 skips are ten opt-in Neo4j live cases and one generator-source
case. Existing Java CI builds with `-DskipTests`; it is not a regression test gate.

The completed review examined the full branch plus remediation: warm/cold ACLs,
owner/session authority, explicit unresolved-scope errors, origin-bound disposal,
job outcomes, scientific readback, fixture negative controls, cleanup, package
imports and documentation claims. No further contribution-specific blocker was
identified. Raw IDs carry no origin information; bound handles are checked before
destructive calls. General service-scope permissions were not redefined globally.

## Remaining acceptance boundaries

Evidence establishes an isolated signed-ROLE_USER TEST deployment and synthetic
scientific fields. Production hub onboarding, browser scoped credentials,
actual-provider terrain accuracy against an independent reference, bulk/large
data, temporal/federated workflows and sustained soak/capacity remain unverified.
See [contracts](contracts.md) and [provider acceptance](live-acceptance.md).
