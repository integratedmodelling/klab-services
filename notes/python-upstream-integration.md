# Python delivery-base stabilization

Candidate parents: Python integration `e09a7de7cac6b48526fd8b7ac72879abb36a77a3`
and upstream `919dcf4ba92d843cf4c31fdecc5dedd55f3ab920`.
This candidate is not release acceptance. No scientific input is migrated.

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
The correction honors `8c675521a`'s explicit status-only error contract. It has
not yet been incorporated in this in-progress merge candidate.

Four additional core-service failures reproduce in the upstream control checkout
with only #143's unrelated test correction:

- [#144](https://github.com/integratedmodelling/klab-services/issues/144): remote
  `ClientKnowledgeGraph` inherits an unsupported observation-currentness default
  now called by `ServiceContextScope.getObservation`. This requires a supported
  remote lifecycle contract, not bypassing currentness checks.
- [#145](https://github.com/integratedmodelling/klab-services/issues/145): authority
  selection fixture, optional Web UI asset dependency, and a commented-out stop
  invocation in the shutdown test. The issue records reproduction and source
  diagnosis; those corrections are not yet implemented.

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
The upstream control comparison is still outstanding. In particular, failures in
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
