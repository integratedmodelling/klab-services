# Python delivery-base stabilization

Candidate parents: Python integration `e09a7de7cac6b48526fd8b7ac72879abb36a77a3`
and upstream `919dcf4ba92d843cf4c31fdecc5dedd55f3ab920`.
The current candidate integrates merged stabilization PRs #143, #146 and
#148–#154 through develop `e6b3a643977fa624c2c7842dd18a2ac9acdc4402`.
This candidate is not release acceptance. No scientific input is migrated.

## Resources follow-up (2026-11-03)

PRs #149–#154 are merged after their head checks passed (GitHub Actions and
Jenkins). Author review only. Targeted results: temporal 33; semantic fixtures
32; pipeline/shared helper 27; authoring 20; builder 19; authority 6 tests pass.
These are separate selections, not an additive full-suite count.

The fresh develop-based Resources selection before #153/#154 ran 445 tests:
one failure, six errors, eight skips. Reports are preserved under
`C:/Users/lumsd/AppData/Local/Temp/opencode/resources-followup-evidence-20261103`.
Builder fixes use real OWL helpers and explicitly replace the obsolete ordinary
Subject observer expectation with Subject rejection and Agent retention.
Authority fixture publication retains version mismatch and no-installation checks.

Three containment tests pass with `klab.test.core.directory` set to the absolute
path of `klab.services.reasoner/src/main/resources/knowledge`. Classification/query
acceptance passes with `klab.test.staging.directory` pointing to an external checkout
of `https://github.com/integratedmodelling/klab.staging.vxii` at
`65e077475301e1aad86025ba7ced6fb427842df2`. The older documented staging revision
`baa9717f8c118501b77a6460e4c885bdf456a7cd` lacks `queries.kim`; classification source
is identical between these revisions. No source fixtures or oracle values changed.
The test selectors are `ContainedTierZeroAcceptanceTest` and
`ClassificationSourceAcceptanceTest`, using `-pl klab.services.resources -am`
and `-Dsurefire.failIfNoSpecifiedTests=false test` with normal failure handling.

The integrated full Resources selection with both configured paths is running;
see `C:/Users/lumsd/AppData/Local/Temp/opencode/resources-integrated-configured-20261103.log`.
This does not establish complete Java-suite, live-stack, or original-study acceptance.

## Why integrate upstream

Upstream `0e77650abc2bfb5db072fc00eb4f5a44f2d2cd19` deliberately removed
`requires extent` and `requires attribute`; see its changes to
`docs/ONTOLOGY_LANGUAGE.md` and `docs/IDENTITY_REQUIREMENTS.md`. The old Python
base expected those enum constants and cannot compile with the current language
artifacts. Restoring arbitrary historical JARs would conceal this version mismatch.
Adopting upstream does not automatically translate old scientific clauses into
identities or establish that original-study inputs are accepted.

## Conflict resolutions

- `RuntimeService`: retain activity notifications alongside upstream formatting.
- `ResourcesProvider`: retain synchronized workspace/project creation alongside
  upstream formatting and administration changes.
- `AcceptedSemanticRuntime`: retain exact accepted-generation restoration and
  preflight-cache invalidation; forced administrative reload bypasses restoration
  and backoff. Closing also releases upstream's retained semantic cycles.

Two existing fixture assumptions failed during the first focused run, retained in
`upstream-integration-regressions-20261103.log`: the federation ACL fixture did
not identify its Runtime, and the shared-observation-cache mock did not implement
upstream's currentness predicate. The fixtures now declare their intended valid
identity/currentness. The cache test additionally proves a stale observation is
rejected even when cached. Production identity/currentness checks are preserved.

## Verification and remaining gate

Temurin 21.0.12.1 / Maven 3.9.5, Windows:

- Runtime-server dependency reactor compilation PASS with current language JARs.
- Focused Java regression selection: **161 PASS**, no failures/errors/skips.
- Python 3.12 unit/integration selection: **2,208 PASS** (one Rasterio warning).
- Broad Java reactor: **FAIL**, 144 API tests pass, common module runs 360 with
  one failure in `QuietHttpPostTest.requiredCommandsStillReportHttpStatusAndStructuredErrorDetails`.
  Downstream modules are skipped by Maven, not accepted.
- The same three-test HTTP class on a clean unchanged upstream checkout fails
  identically (one failure). Source and reproduction are in
  [issue #142](https://github.com/integratedmodelling/klab-services/issues/142).
- A separate failure-continuing run is diagnostic inventory only, never a merge
  gate. Required tests must ultimately run without ignoring failures.

### Stabilization follow-up

Issue #142 is closed by test-only [PR #143](https://github.com/integratedmodelling/klab-services/pull/143),
merged to `develop` as `4bca4fe14fb07cca5a373cc1064b36973bfd8805` after
489 API/common tests and both GitHub Actions and Jenkins checks passed.
The correction honors `8c675521a`'s explicit status-only error contract and is
now incorporated in the candidate.

Four additional core-service failures reproduce in the upstream control checkout
with only #143's unrelated test correction:

- [#144](https://github.com/integratedmodelling/klab-services/issues/144): remote
  `ClientKnowledgeGraph` inherited an unsupported observation-currentness default
  called by `ServiceContextScope.getObservation`. Merged PR #148 implements an
  uncached authorized read and a paired server-side native currentness check on
  the existing numeric asset endpoint. 45 selected tests passed plus one added
  real HTTP503 regression (four client currentness tests passed after addition).
  GitHub Actions and Jenkins passed on final head `578c6556f`. Deployed acceptance
  is still outstanding and older Runtime compatibility is unverified; #144 stays
  open. HTTP failures report to scope and return null, rejecting currentness;
  this boolean API does not distinguish absence from unavailability.
- [#145](https://github.com/integratedmodelling/klab-services/issues/145): authority
  selection fixture, optional Web UI asset dependency, and a commented-out stop
  invocation in the shutdown test. Merged PR #146 fixes all three; 28 clean
  selected tests and GitHub build passed. The authority defect was a missing
  published registry view after direct writer injection, not missing selection.

Combined candidate `20a1beb8f5d8cc3f89809d6d514f35a3abed6dda` passes **103**
selected tests covering these unblockers, native cessation/membership, identity,
scope caches and forced reload/restoration. No failures/errors/skips were ignored.
The full API/common/core-services reactor passed: **1,697 tests, zero
failures/errors, two skips** (144 API, 364 common, 1,189 core services), with
normal failure handling. Log: `upstream-integrated-core-full-20261103.log`.

The temporal numeric fixture's missing currentness behavior is now diagnosed:
its graph mock defaulted to false for every registered observation. A local
correction models its committed inventory and delegates pending state to the
actual transaction, rejecting foreign scopes and unknown observations. All 16
temporal tests pass, including a new absent-process/no-write regression, without
changing any original numerical, rollback or restart assertions.

Broader verification exposed an inspector test trying to reopen immutable INIT
storage merely to inject corruption. It now verifies that reopening is rejected
and injects the same nonfinite/missingness corruption through a retained fixture
buffer. **33 tests pass** across temporal execution, inspector integrity, native
constant initialization and process-birth accounting. This test-only correction
is pending separate delivery; it is not a full-runtime acceptance claim.

A new forced-reload/restoration interaction regression in
`AcceptedSemanticRestorationTest` passes: 18 tests across that class and
`WorldviewReloadTest`, with no failures/errors/skips. It proves forced validation
does not silently restore accepted status, while retaining the accepted actor.

The full candidate diagnostic inventory completed: **4,357 tests, 161 failures,
132 errors, 32 skipped**. Its Maven success label is caused by failure-ignore
mode and is not acceptance. Raw module summaries and copied XML reports agree.
The immutable local evidence directory is
`upstream-integration-inventory-evidence-20261103` beside this worktree;
`upstream-integration-diagnostic-inventory-20261103.log` retains the complete run.
The control comparison is recorded in
[#147](https://github.com/integratedmodelling/klab-services/issues/147):
4,171 control tests, 160 failures, 132 errors, 32 skips. There are 291 identical
failing test identities. Candidate-only failures were the HTTP expectation
(now fixed) and pinned ODO hash. Control-only failure was the shared-scope-cache
mock's missing currentness behavior, already corrected in this candidate.
Parity with failing upstream is not acceptance. In particular, failures in
temporal execution, classification, semantic admission and pinned fixture hashes
must not be dismissed as stale mocks without reproducing and diagnosing them.

The pinned core-ontology failure has a verified explanation: upstream
`0e77650ab` added nine lines defining `odo:impliesPredicate` to
`klab.services.reasoner/src/main/resources/knowledge/odo.owl`. Its source and
compiled-resource SHA256 both equal
`5c020dac9bd9d1cd4f9673b8075cf08f75ecc7194c74378bdb8f0498110ddba3`, versus
the captured fixture's
`496bc6634392d7e2eb2a6749bca6a1570333ea607b9b8e52ffaf5b619bcf2a57`.
Index and working tree both use LF under an explicit `eol=lf` attribute.
This is an ontology-source change, not checkout corruption. The old fixture/hash
assertion remains preserved; a new versioned compatibility fixture needs explicit
semantic-impact verification before adoption. See issue #89's follow-up evidence.

Reproduce from this candidate checkout; Windows uses `mvnw.cmd`, and PowerShell
requires quotes around each complete `-D...` argument:

```sh
./mvnw -B -pl klab.services.runtime.server -am -DskipTests compile
./mvnw -B -pl klab.services.runtime.server,klab.services.resources -am -Dtest=AcceptedSemanticRestorationTest,CandidateRestorationCoordinatorTest,AcceptedSemanticRuntimePreflightTest,WorldviewReloadTest,AcceptedSemanticCycleTest,WorkspaceDeletionTest,WorkspaceRemovalTest,FederatedIdentityTransportTest,NativeReconnectParentTest,ClientScopeCreationTest,ClientScopeShutdownTest,FederationSessionIdentityTest,IdentityHeaderValidationTest,TokenAuthorizationFilterTest,LocalRuntimeColdScopeTest,CachedScopeRuntimeOriginTest,ScopeManagerTest,ServiceSessionScopeTest,ManagedScopeAccessTest,UserScopeAdvertisementTest,RuntimeContextReleaseTest,RuntimeContextAuthorizationTest,ResolutionCycleRestorationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -B -fae -pl klab.services.runtime.server,klab.services.resources.server,klab.services.reasoner.server -am test
python -m pip install -e './python-client[dev,local,analysis,tabular,notebook,events]'
python -m pytest -q python-client/tests/unit python-client/tests/integration
```

Language JAR SHA256s at the successful compile (all `1.0.0-SNAPSHOT`; unchanged
from the earlier failing old-base build):

| Artifact suffix | SHA256 |
| --- | --- |
| observable | db1a58cc9a3ab8f3f56db1855b9cc3812edc352154d377883031558c23db88af |
| worldview | d45c9a49dc262a1963e8391283fd1377b6da9c35cc4237d497b729bc361a7816 |
| kim | ebf4f0caba31e1074aca03f7a21d329babf07808aab6617cf822b4e14c9106e5 |
| observation | 291e1a6c07a0900d1581fe8b514bde35f75dd48fc98f6ef7625dd24d5f605b23 |
| kactors | d0f458916438dd83f75dd70a800ace6a96755cf3a3bfe99ec73f02ad253e1245 |

Full artifact IDs start `org.integratedmodelling.languages.`. Capture new hashes
if SNAPSHOT resolution changes. These hashes are reproducibility checks, not a
maintained dependency lock or proof of language-source provenance.

Jenkins remains separately unexplained: console access returns 403. Obtain
authenticated logs/operator diagnosis. The portable live-stack fixture and
original scientific acceptance remain outstanding; compile and offline tests
cannot substitute for them. Existing failed/UNKNOWN historical mutations remain
unchanged and must not be replayed.
