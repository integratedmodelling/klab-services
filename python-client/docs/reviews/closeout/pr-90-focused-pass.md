# PR #90 focused development pass — 2026-10-31

> Pre-fix assessment, preserved as historical evidence. The user subsequently
> authorized local corrections; see [local fix pass](pr-90-fix-pass.md) for the
> implementation, ten passing regressions, shared broader-suite failure, and
> independent re-review. PR acceptance remains incomplete.

## Outcome

**NEEDS FIXES; acceptance incomplete.** PR #90 remains open/draft. No source,
POM, PR state, deployment, or live-service changes were made. This local report
extends, rather than replaces, the earlier read-only baseline. Nothing here
authorizes publication, merge, closure, or supersession.

## Exact reviewed combination

- Target `feature/python-client`: `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`.
- PR head: `244883d7b04f1eae2cc22e799575d33a9bdb2a43`.
- GitHub test merge: `21da511166f4ef8f97f4a9c351e06e63e53b5421`.
- Combined tree: `49fc9cf1857568930daaf99eacfcef2e4ee79fd6`.
- Locally fetched develop: `e236b1417684f501ce7a140d101f5c20489d8544`;
  not substituted for the actual target.
- Detached, isolated worktrees: `../pr90-combined-java-20261031` and
  `../pr90-target-java-20261031`; product files remain unchanged.

## Findings

### 1. Cached context lookup ignores Runtime origin

`klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/ScopeManager.java:577–596`
looks up the authenticated caller's scope by scope ID, checks its ACL, and returns
a request view without comparing the requested Runtime with the cached host.
Cold reconstruction, by contrast, requires and locates the originating Runtime.

A standalone diagnostic against each revision's compiled classes registered a
mock context hosted by Runtime A, then requested that scope from A, B, and null.
Both target and candidate returned A's cached context in all three cases. The
private-owner negative control denied access on both revisions.

This is a reproduced **inherited origin-selection defect**, not a newly introduced
#90 regression or evidence of general ACL bypass. The diagnostic mocks identity,
configuration, and request views; it is not real authentication, caller-rebinding,
two-user, or native multi-process acceptance. Its zero process exit means the
diagnostic completed, not that the wrong-origin behavior passed acceptance.

Smallest proposed correction: reject explicit cached-host mismatches before
returning a context/session, preserve the ACL checks and caller binding, and
define missing-origin behavior for local versus remote lookup. Add warm/cold
matching/mismatched/missing/unadvertised-origin regressions, including parent
sessions. No product fix has been made; Java-edit authorization remains required.

### 2. Strict build is blocked on both target and candidate

Both commands exited 1 at
`klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/lang/LanguageAdapter.java:1234`:
the `SemanticSyntax.Type` switch is not exhaustive. The installed observable
language snapshot contains `RELATIONSHIP`, which the adapter does not cover.

Installed artifact:
`org.integratedmodelling.languages.observable:1.0.0-SNAPSHOT`, SHA256
`4b89ef600e5d620eaf18553170d11b49f5dd78f93914402d3901e4832f5edcf1`.
`javap` plus comparison with the adapter's cases identified `RELATIONSHIP` as the
missing case. This establishes a local source/artifact incompatibility, but not
which historical dependency tuple should be accepted or when that drift arose.

Do not add a catch-all, weaken compilation, or silently change semantic mapping.
First identify a compatible pinned language tuple, or separately authorize an
explicit adapter compatibility change with semantic tests.

## Validation actually performed

| Check | Target | Combined candidate | Limits |
| --- | --- | --- | --- |
| Selected completed JUnit suites | 144 tests, 0 failures/errors/skips | 186 tests, 0 failures/errors/skips | Partial reactor only; counts differ because test content differs |
| Selected full strict reactor | BLOCKED, command exit 1 | BLOCKED, command exit 1 | Same adapter compilation error; later server/runtime/GraphDB modules skipped |
| External cached-origin diagnostic | Wrong-origin return reproduced | Wrong-origin return reproduced | Mock-based diagnostic, not real federation acceptance |
| Private-owner diagnostic control | PASS | PASS | Mock-based only |
| Real two-user/native workflow | NOT RUN | NOT RUN | Requires separately authorized compatible deployment/fixture |

Java: Eclipse Adoptium 21.0.12.1+1; Maven wrapper 3.9.5. Both worktrees used the
same local Maven repository. Neither command installs artifacts or alters POMs.

Exact reactor command, run once in each worktree:

```powershell
$env:JAVA_HOME='C:/Users/lumsd/AppData/Local/Programs/Temurin21/jdk-21.0.12.1+1'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
$selection='PersistedContextAccessTest,RuntimeContextAuthorizationTest,DomainErrorMappingTest,ManagedScopeAccessTest,ScopeManagerTest,TokenAuthorizationFilterTest,FixtureControlsTest,StorageRegistrationTest,JobManagerTest,SemanticsBuilderUnitTest,Neo4jQueryExecutionTest,PollingFutureTest,KlabErrorHandlerTest,UnitMediationBaselineTest,FederationSessionIdentityTest,FederatedScopeControllerTest,FederatedContextPermissionsTest,IdentityHeaderValidationTest,FederatedIdentityTransportTest,ClientScopeCreationTest,ScopeAdvertisementsTest,ServiceClientCatalogTest,AMQPChannelTest,UserScopeAdvertisementTest,*Authority*Test,CachedAuthorityTest,ConceptSyntaxCacheTest,SemanticSearchSessionTest,SemanticsBuilderTraitTest,Worldview*Test'
./mvnw.cmd -B -ntp -nsu -pl klab.services.resources.server,klab.services.reasoner.server,klab.services.resolver.server,klab.services.runtime.server,support/klab.support.graphdb -am "-Dtest=$selection" '-Dsurefire.failIfNoSpecifiedTests=false' test
```

`surefire.failIfNoSpecifiedTests=false` permits reactor modules without a matching
test class; it does not skip failing tests. Completed-suite counts were extracted
from fresh JUnit XML. This selection must not be represented as all repository
tests or a completed strict/native gate.

Private local evidence directory:
`C:/Users/lumsd/AppData/Local/Temp/opencode/pr90-closeout-evidence-20261031/`.
It contains `combined-strict.log`, `target-strict.log`, per-revision
`*-test-suites.json`, `OriginCacheProbe.java`, `*-origin.args`,
`*-origin-probe.log`, `semantic-syntax-type.txt`, and
`language-enum-comparison.json`. Probe classpaths came from each revision's JUnit
`java.class.path`, passed through Java argument files to avoid Windows command
length limits. The final probe completed with exit 0 on both revisions. Earlier
diagnostic attempts encountered command-length, fixture-compatibility, and old
target maintenance-thread termination issues; these were diagnostic setup issues,
not product fixes. The final diagnostic explicitly stops only its own manager's
maintenance executor.

## Independent review

An independent read-only general reviewer assessed this exact combination in
session `ses_e6ce7b892ffeURVCWsSvpfoC1n`. It did not edit files or run Maven,
services, or probes. Its recommendation was **NEEDS FIXES**, confirming the
inherited cached-origin source defect and the limits of mock-based evidence.
It recommended focused origin tests, compatible dependency evidence, authorized
two-principal checks, and re-review of the final corrected candidate.

It also identified an unverified audience-validation contract gap in
`ServiceAuthorizationManager.java:196–201,348–350`. This is source-inspection
evidence, not a reproduced token exploit; establish the intended contract and
negative tests rather than silently expanding the origin fix.

This is an independent preliminary assessment, **not approval of a final fix**
or maintainer acceptance. The final candidate still needs independent re-review.

## Smallest next actions

1. Authorize local-only #90 Java identity/cache fixes and regressions, with no
   publication or service changes; settle local/remote missing-origin behavior.
2. Identify/pin a compatible language dependency tuple in isolation, or authorize
   a separate explicit adapter compatibility fix. Do not alter the shared Maven
   repository or original services as a shortcut.
3. Rerun the strict/native selection on the exact corrected combination; obtain
   independent review of that final tree.
4. Agree the real two-user fixture and bounded native-process workload before
   setup, service restarts, remote objects, computation, or cleanup mutations.
   Required evidence includes owner/shared success, private denial, caller job
   attribution, warm/cold reconnect, origin mismatch rejection, release boundaries,
   both callers' service advertisements, and exact source/binary attribution.
5. Only after those results, seek an explicit PR publication/disposition decision.
   Keep #90 open and the other outstanding work paused; #102 remains unmerged.
