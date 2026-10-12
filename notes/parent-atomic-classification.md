# Parent-atomic classification prerequisite foundation

Current revision: the cache-isolation follow-up below supersedes the earlier seven-file geometry checkpoints. The current implementation changes eleven Java files; its current manifest is ../implementation-file-manifest.txt.

Base: develop, 3b01ceaa907000ffccafac97444029027c81d30b.
Branch: codex/parent-atomic-classification-20261012.

Only parent atomicity is implemented here. Each classification child contributes detached semantic replacements and evidence edges to its enclosing root attempt. Child commit is intermediate; only root commit updates graph semantics and publishes replacements. A failed or cancelled attempt discards tentative replacements and leaves previously committed member semantics intact. Independent families compose on the latest tentative observable; repeated, overlapping, stale, and durable optimistic-update conflicts remain failures. The original committed observable URN remains the expected value for the one root semantic update. Same-family conflict scope remains the actual member being attributed; this is not a rule about co-reference between syntax occurrences.

Transaction.readObservation supplies the current attempt view, not classification completion. Runtime cohort enumeration and classifier invocation apply this view even to members loaded from the persistent graph. Existing classification reuse continues to skip classifier invocation and pending attribution creation. Tests cover existing-only and mixed existing/new execution separately from empty cohort execution.

## Remaining completion interface

Do not adapt CompiledDataflow's boolean or PendingAttribution deltas directly into an expansion closure. The existing executor deliberately returns no delta for already-classified members, and optional null and completed empty membership also produce no delta. None carries complete context, snapshot or provenance evidence. This branch therefore does not implement a typed completed-result adapter or pretend that an empty delta means empty success.

The runtime owner must supply an asynchronous boundary equivalent to:

    classifyPrerequisite(parentAttempt, DependencyExpansion.Request, runtimeContext)
        -> CompletionStage<ClassificationOutcome>

Read-only inspection of task-3's current DependencyExpansion.java confirmed its RequestIdentity(UUID attemptId, List<String> path) and Request(identity, originalObservable, predicate, declaration, contextualization). The original observable is detached and the immutable path identifies bearer/clause/role/list position. Reuse that request when the tracks integrate; do not introduce a competing occurrence schema or copy the helper into this branch. Its syntax attempt UUID must be associated with the enclosing runtime attempt, not confused with the root transaction ID. The runtime context envelope must additionally bind model/source revision, scope/observer, support/event and semantic snapshot. This branch does not wire the independent helper into runtime.

The combined request and runtime context must identify the full semantic target and bearer expression; context/twin identity; observer/framework where applicable; exact support and event; semantic/worldview/authority and graph snapshot references; enclosing attempt; and stable occurrence path including bearer, relationship endpoint, role and position. Equal predicate names, source text or AST object identity do not imply co-reference. Only an explicit semantic binding may share substitution; no binding syntax is introduced here. Cache identity must include these fields or a verified immutable request identity incorporating them. Existing operation-local invocation caches are not a cross-request completion cache. MemberClassifierExecutor strips INHERENT to derive its attribution family; that reduced family is not a sufficient occurrence/request identity. Keep the helper's complete original observable and position identity alongside it.

ClassificationOutcome must preserve the helper's SUCCESS, UNKNOWN and FAILED distinctions and explicit cancellation (which may remain a cancelled completion rather than a new helper status). SUCCESS requires affirmative completion evidence for the complete requested membership/support, and complete per-member qualified concrete results, combining committed and newly staged classifications. Preserve each result's full predicate expression, bearer/position, context, support, event, snapshot and evidence/activity references, and whether its evidence is committed or tentative under this parent attempt. A distinct success with no results requires proven completed empty membership or explicitly modeled optional absence, not missing evidence. UNKNOWN retains unresolved requirements and diagnostics; FAILED retains actual failure; CANCELLED retains cancellation identity. Unsuccessful outcomes cannot expose a reusable successful closure.

No complete-result record can safely be populated from today's boolean and deltas alone. Missing APIs: an authoritative complete member/result read with provenance and snapshot binding; explicit completion/absence evidence; and an unresolved outcome channel from prerequisite resolution/execution. Scheduling policy, retry count, fixpoint, resource limits, deferred resolution, precision and retention remain outside this change. A future UNKNOWN path may stay unresolved before terminal rollback, or terminate its attempt and later create a fresh one; it must never call fail() then resume the same attempt. No UNKNOWN execution path is added here.

## Lifecycle and read boundary

The shared root attempt is OPEN, COMMITTING, COMMITTED or FAILED. Its graph monitor serializes mutation, staging validation/failure, child commit, root durable close, publication and cancellation. Root commit freezes transaction mutations through close/publication; late stage calls are rejected. Cancellation that acquires the monitor first aborts; cancellation arriving after the durable commit has won is a no-op. A reentrant cancellation from a commit callback aborts that commit. Key allocation, preparation, observer lookup and graph-transaction creation errors also make the attempt terminal. Children and grandchildren share root state and never independently create a graph transaction.

readObservation returns read-only API domain interfaces and detached mutable payloads, rather than exposing ObservationImpl or its shallow attribution clone. Metadata, nested collections, arrays, timestamps, histograms, contextualization parameters (including positional keys/template variables), sharding strategies and URN parameters are isolated. Identity memoization preserves participant/syntax cycles and references without JSON serialization. Unknown opaque mutable payload types fail explicitly rather than being shared. This is a transaction read view, not the missing classification completion/provenance result. Existing legacy assets/link APIs are not redesigned here.

## Validation and evidence

All commands run from C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/klab-services unless the baseline directory is stated. Offline Maven uses an isolated writable repository with the shared cache as read-only tail. A restricted-process JUnit JAR denial occurred before tests in the first attempt; normal approval tooling authorized subsequent runs. No permissions or security settings were changed. No live database or services were used; SpatialLifecycleLiveTest was explicitly excluded.

Focused review checkpoint:

```powershell
mvn -o -pl klab.services.runtime -am test '-Dmaven.repo.local=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/m2' '-Dmaven.repo.local.tail=C:/Users/Ferd/.m2/repository' '-Dtest=ClassificationTransactionTest,MemberClassifierExecutorTest,ClassificationExecutionTest,DigitalTwinCommitTest,ConnectionExecutionTest,ClassificationPersistenceTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Duser.home=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/test-home-review-final'
```

../classification-review-fixes-final.log: BUILD SUCCESS, 53 tests, zero failures/errors, one optional generator-source skip. Breakdown: ClassificationExecutionTest 3, ConnectionExecutionTest 8, ClassificationTransactionTest 22, DigitalTwinCommitTest 4, MemberClassifierExecutorTest 12 including one skip, ClassificationPersistenceTest 4. This checkpoint preceded the final lock extension making staging validation/failure atomic; the final aggregate below includes that adjustment and all these focused classes.

The 56-class non-live runtime selector is saved verbatim at ../runtime-comparison-selection.txt and was used unchanged for base and implementation:

```powershell
$classificationRuntimeTests=(Get-Content ../runtime-comparison-selection.txt -Raw).Trim()
mvn -o -pl klab.services.runtime -am test '-Dmaven.repo.local=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/m2' '-Dmaven.repo.local.tail=C:/Users/Ferd/.m2/repository' "-Dtest=$classificationRuntimeTests" '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Duser.home=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/test-home-runtime-review-final'
```

Untouched base: ../klab-services-base, detached HEAD 3b01ceaa907000ffccafac97444029027c81d30b, identical command/selector with home test-home-runtime-base. Log ../classification-runtime-base.log: 218 tests, zero failures, six errors, one skip. The earlier implementation rerun ../classification-runtime-rerun.log had 226 tests with exactly the same six errors; full normalized error comparison and environment data are saved in ../runtime-baseline-comparison-before-review-fixes.json. Only generated Mockito class suffixes were normalized. Both runs used JDK 21.0.1, Windows 11 amd64, user Ferd and the same Maven repositories.

Final review aggregate: ../classification-runtime-review-final.log completed with 233 tests, zero assertion failures, six errors and one skip. All six errors match the untouched base after normalizing only generated Mockito class suffixes; ../runtime-baseline-comparison-final.json contains full messages, environments and totals. The six focused classes also passed within this final aggregate (53 tests, zero failures/errors, one skip), covering the final staging-failure lock adjustment. The aggregate remains FAILED because of the six proven baseline errors.

The six baseline runtime errors are:

- ProvisionalObservationScopeTest.registeredCandidateRoundTripsAsContextWithoutEnteringCommitGraph: missing DigitalTwin in alignObservation.
- SubmissionAdmissionCancellationTest.apiCancellationRemovesQueuedWorkWhileUnrelatedWorkKeepsItsSlot: KnowledgeGraph mock cannot cast to KnowledgeGraphNeo4j in alignObservation.
- SubmissionCancellationTest.apiJobCancellationStopsPendingResolutionAndLeavesSiblingUsable: same cast failure.
- SubmissionCancellationTest.cancellationDuringContextualizationPreventsSubmissionCommit: same cast failure.
- SubmissionCancellationTest.completedSubmissionCannotCancelItsExecutionScope: same cast failure.
- SubmissionCancellationTest.cancellingCoalescedSubscriberDoesNotCancelSharedExecution: same cast failure.

Full reactor command (no test selector):

```powershell
mvn -o -pl klab.services.runtime -am test '-Dmaven.repo.local=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/m2' '-Dmaven.repo.local.tail=C:/Users/Ferd/.m2/repository' '-DargLine=-Duser.home=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/test-home'
```

../classification-aggregate.log: API 58 passed, common 210 passed, core services 657 with four failures, four errors and two skips; runtime skipped. These eight failures were compared with the existing untouched same-base worktree C:/Users/Ferd/Documents/Codex/2026-10-12/task-2/klab-subjective-base (tracked clean, HEAD verified) and its base-aggregate-tests.log. Both select all core-service tests, use JDK 21.0.1 and the same shared tail, with separate task-local Maven/test homes. Full failure messages and types match exactly: ../service-core-baseline-full-failures.json; environment/totals in ../service-core-baseline-comparison.json. Failing tests: ExportDispatchTest (two), BehaviorAnalyzerTest (two), CoverageImplTest, StorageMediationBaselineTest, ExportResponseTest and KlabWebUiControllerTest. The aggregate is not green; failures are proven baseline. It was not rerun after the transaction-only review fixes.

Earlier logs are historical checkpoints, not final passes: classification-focused.log (restricted JAR access), classification-focused-authorized.log (corrected matcher misuse), classification-runtime-aggregate.log (one corrected ConnectionExecutionTest mock error plus six baseline errors), classification-review-fixes.log (one corrected participant-cycle assertion). The final baseline comparisons supersede earlier notes that baseline status had not been established.

## Changed files

The seven source/test files, relative to this worktree, are:

- klab.core.api/src/main/java/org/integratedmodelling/klab/api/digitaltwin/DigitalTwin.java
- klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/DigitalTwinImpl.java
- klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/MemberClassifierExecutor.java
- klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/RuntimeService.java
- klab.services.runtime/src/test/java/org/integratedmodelling/klab/services/runtime/digitaltwin/ClassificationTransactionTest.java
- klab.services.runtime/src/test/java/org/integratedmodelling/klab/services/runtime/MemberClassifierExecutorTest.java
- klab.services.runtime/src/test/java/org/integratedmodelling/klab/services/runtime/ConnectionExecutionTest.java

This note is an additional untracked handoff document. Absolute seven-file manifest: ../implementation-seven-file-manifest.txt. No commit, push, merge, publication, deployment or security changes. Other worktrees and the original checkout's pre-existing dirty work are preserved.
Final whitespace check passed. Final runtime-generated untracked extensions.idx and jte-classes were preserved in ../generated-test-artifacts-review; earlier generated outputs remain in ../generated-test-artifacts. The implementation worktree now contains only the seven intended Java modifications and this handoff note. Untouched task-7 baseline HEAD and tracked cleanliness were rechecked. The original checkout still contains exactly its four pre-existing untracked review-evidence directories.

## Geometry review follow-up (supersedes the earlier final checkpoint)

The reviewer reproduced a real read-contract regression: the generic domain proxy rejected Geometry.key(), preventing GeometryRepository scale lookup and RuntimeService.classificationMembers support intersection. Two executable regressions reproduced that exact exception before the fix: classification-geometry-repro.log (two tests, two errors).

Plain geometry and regular scale now use an operation-specific detached native snapshot reconstructed through the geometry value encoding. This preserves native key/as/merge/scale operations rather than adding names to the generic proxy allow-list. Geometry metadata is copied recursively using the existing identity memo, including parent cycles. Empty geometry is freshly allocated because Geometry.create("X") returns a shared singleton. Regular scales are created directly with the native constructor rather than the configured Scale.create factory, because that factory registers the private result in the shared GeometryRepository cache. Extent and coverage objects are not flattened into plain geometry; their additional domain contracts remain outside this specialized snapshot path.

The new tests cover the real S2 read path, polygon support intersection with inside/outside point members using actual parent/child transactions, scale conversion and merge, unchanged geometry-cache ownership, empty/scalar/universal geometry, and nested/cyclic geometry metadata isolation. Existing mutable rollback and commit race tests remain selected. Bare Geometry.create("S2") itself currently throws a missing-grid NullPointerException in TileImpl.encode during scale promotion, even without a transaction read. The test verifies identical raw/read native behavior instead of claiming S2 became fully specified; concrete polygon/point support is the successful integration test. This native missing-grid limitation is not changed here.

The first post-fix focused attempt stopped at a missing test import (classification-geometry-focused.log); the next found the native bare-S2 limitation (classification-geometry-focused-final.log). Both are historical failed attempts, not passes.

Focused validation passed: classification-geometry-verified.log, 56 tests, zero failures/errors, one optional generator-source skip. The six classes are ClassificationTransactionTest (24), ConnectionExecutionTest (9), ClassificationExecutionTest (3), DigitalTwinCommitTest (4), MemberClassifierExecutorTest (12 including one skip), and ClassificationPersistenceTest (4).

```powershell
mvn -o -pl klab.services.runtime -am test '-Dmaven.repo.local=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/m2' '-Dmaven.repo.local.tail=C:/Users/Ferd/.m2/repository' '-Dtest=ClassificationTransactionTest,MemberClassifierExecutorTest,ClassificationExecutionTest,DigitalTwinCommitTest,ConnectionExecutionTest,ClassificationPersistenceTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Duser.home=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/test-home-geometry-verified'
```

Final runtime comparison: classification-runtime-geometry.log, 236 tests, zero assertion failures, six errors, one skip. All six normalized full errors match the untouched 218-test base; runtime-baseline-comparison-geometry.json records the comparison, environments and focused subset. The aggregate remains FAILED because of those six baseline errors. All 56 focused tests also pass within this final aggregate. Generated outputs are preserved in ../generated-test-artifacts-geometry. Final source hashes are in ../implementation-seven-file-sha256.json.

```powershell
$classificationRuntimeTests=(Get-Content ../runtime-comparison-selection.txt -Raw).Trim()
mvn -o -pl klab.services.runtime -am test '-Dmaven.repo.local=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/m2' '-Dmaven.repo.local.tail=C:/Users/Ferd/.m2/repository' "-Dtest=$classificationRuntimeTests" '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Duser.home=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/test-home-runtime-geometry'
```

No completion adapter, UNKNOWN scheduling, retry policy, deferred-resolution or observation-strategy integration was added. The branch/base and seven-file manifest are unchanged.

## Cache-isolation review follow-up (current revision)

The two new isolated-JVM probes reproduced the residual P1 in classification-cache-repro.log: cache-hit lookup returned the shared scale, and mutating private polygon parameters to a point before a cache miss stored the point under the polygon key. Both probes failed by assertion before this fix. The first implementation attempt then stopped at Java type errors (classification-cache-focused.log), corrected before the passing validation below.

Geometry.LocalSnapshot is now the explicit private promotion/copy boundary. GeometryRepository bypasses both shared lookup and merge caches for these values and ignores their insertion; Scale.create and the runtime configuration factory honor private promotion. The runtime forwards native geometry operations over private backing values, keeps conversions and derived geometries/extents within the boundary, and derives canonical keys from the current encoding. No random keys, cache clearing in production, or changes to ordinary geometry caching are used. Concrete mutable implementation casts are unavailable through the local view; the Geometry/Scale/extent API views and functional operations remain available.

Tests clear only in-memory test-JVM caches to establish deterministic hit/miss conditions. Coverage includes cached-scale metadata aliasing, polygon-to-point poisoning, both promotion entry points, geometry/extent conversions, private intersections, attempted cache insertion, original semantic preservation, real classificationMembers support intersection, and all previous rollback/race tests. Bare S2 now promotes privately without the shared factory's cache canonicalization; this does not supply missing grid information or change unrelated native scale operations.

Passing focused log: ../classification-cache-verified.log, BUILD SUCCESS. Runtime focused subset: 58 tests, zero failures/errors, one optional generator-source skip. Core-service GeometryRepositoryTest: six tests, zero failures/errors. Total: 64 tests with one skip. Earlier geometry-only passing checkpoint is ../classification-geometry-verified.log; ../classification-geometry-focused-final.log was a failed historical attempt and is not a passing handoff reference.

```powershell
mvn -o -pl klab.services.runtime -am test '-Dmaven.repo.local=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/m2' '-Dmaven.repo.local.tail=C:/Users/Ferd/.m2/repository' '-Dtest=GeometryRepositoryTest,ClassificationTransactionTest,MemberClassifierExecutorTest,ClassificationExecutionTest,DigitalTwinCommitTest,ConnectionExecutionTest,ClassificationPersistenceTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Duser.home=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/test-home-cache-verified'
```

Final runtime result: ../classification-runtime-cache.log completed with 238 tests, zero assertion failures, six errors and one skip. All six cases, types and normalized full messages match the untouched 218-test base; ../runtime-baseline-comparison-cache.json records the comparison and environments. All 58 focused runtime tests also pass within this final aggregate. Aggregate status is FAILED solely on these six proven baseline errors. Whitespace checks passed. Generated outputs were preserved in ../generated-test-artifacts-cache. The original checkout retains only its four pre-existing untracked evidence directories.

```powershell
$classificationRuntimeTests=(Get-Content ../runtime-comparison-selection.txt -Raw).Trim()
mvn -o -pl klab.services.runtime -am test '-Dmaven.repo.local=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/m2' '-Dmaven.repo.local.tail=C:/Users/Ferd/.m2/repository' "-Dtest=$classificationRuntimeTests" '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Duser.home=C:/Users/Ferd/Documents/Codex/2026-10-12/task-7/test-home-runtime-cache'
```

Four additional boundary files supplement the previous seven-file manifest:

- klab.core.api/src/main/java/org/integratedmodelling/klab/api/geometry/Geometry.java
- klab.core.api/src/main/java/org/integratedmodelling/klab/api/knowledge/observation/scale/Scale.java
- klab.core.common/src/main/java/org/integratedmodelling/common/knowledge/GeometryRepository.java
- klab.core.services/src/main/java/org/integratedmodelling/klab/configuration/ServiceConfiguration.java

The eleven-file manifest and hashes are recorded in ../implementation-file-manifest.txt and ../implementation-file-sha256.json. The old seven-file manifests/hashes are historical checkpoints. The full reactor's eight previously proven baseline core-service failures were not rerun for this follow-up; the relevant geometry repository tests and runtime aggregate were rerun. No completion adapter, pending/UNKNOWN architecture, retry policy, deferred resolution, deployment or commits were added.
Local commit authorization supersedes the earlier no-commit checkpoint statements: the independently reviewed eleven Java files and this handoff note are now approved for a local commit on the isolated branch. Logs and generated artifacts stay outside the commit. No push, publication, merge, completion adapter or UNKNOWN wiring is authorized or included. The 64-test focused and 238-test runtime evidence above is preserved.
