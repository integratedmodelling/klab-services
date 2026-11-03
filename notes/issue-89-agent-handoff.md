# #89 — cross-service identity: current status and reproducible handoff

Updated 3 November 2026. **OPEN acceptance/integration work, not an assertion that
all remaining work is external or requires a Java change.** This body supersedes
the old status; historical comments and failed evidence are retained.

## Exact source and delivered corrections

Use integration branch `feature/python-experiment-runner` at
`e09a7de7cac6b48526fd8b7ac72879abb36a77a3` for this handoff. This is not a tag,
publication or promotion to `develop`.

| PR | State | Delivered scope |
| --- | --- | --- |
| #90 | MERGED `d86f9927d32aba983060760fdc9953feaf532631` | Shared integration and request-local authority; not still draft |
| #128 | MERGED `94447b8d8ce8328754c78c0875d2aed6ce495b4e` | Deny explicit cached Runtime-origin mismatch |
| #131 | MERGED `802e8cce2ce1648f79ce766f5cd3f2cc400ac535` | Native reconnect uses descriptor parent without creating unrelated default session |
| #132 | MERGED `e09a7de7cac6b48526fd8b7ac72879abb36a77a3` | Cold owner restoration on exact local Runtime, registered-root host binding and bounded disposal diagnostics |

## Reproduce current Java behavior without a deployment

Prerequisites: Git, JDK21 (`JAVA_HOME` set), Maven wrapper and network access to the
repositories declared in `pom.xml`. Run from the repository root. No user tokens,
Hub, scientific datasets, broker or persistent service is needed for these selected
tests. Tests that use loopback HTTP own and close their listeners.

```sh
git clone https://github.com/integratedmodelling/klab-services.git klab-issue89
cd klab-issue89
git checkout --detach e09a7de7cac6b48526fd8b7ac72879abb36a77a3
java -version
./mvnw -version
./mvnw -B -pl klab.services.runtime.server -am -Dtest=FederatedIdentityTransportTest,NativeReconnectParentTest,ClientScopeCreationTest,ClientScopeShutdownTest,FederationSessionIdentityTest,IdentityHeaderValidationTest,TokenAuthorizationFilterTest,LocalRuntimeColdScopeTest,CachedScopeRuntimeOriginTest,ScopeManagerTest,ServiceSessionScopeTest,ManagedScopeAccessTest,UserScopeAdvertisementTest,RuntimeContextReleaseTest,RuntimeContextAuthorizationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

On Windows use `mvnw.cmd` instead of `./mvnw`. Expected fixed behavior: selected
tests PASS, no ignored failures. Inspect each module's `target/surefire-reports/`
for counts/errors/skips; reactor modules with no matching selected tests may run
zero tests. This is a selected regression suite, **not** a full Java-suite claim.
Snapshot dependencies can change: retain `dependency:tree`, resolved JAR SHA256s,
Java/Maven versions and the source commit with a reproduction. An unavailable or
incompatible language SNAPSHOT is setup evidence, not reproduction of this bug;
do not combine old dependency closures to force a run.

### Current repeated build failure — exact dependency trigger

The command above was actually repeated on3 November in a fresh worktree at the
pinned source. **It currently fails at `klab.core.services:compile`, before that
module's tests**, with the resolved language artifacts below. Do not report this
repeat as PASS or count the earlier green CI as current dependency compatibility.

Minimal reproduction (same JDK21/setup, no services):

```sh
./mvnw -B -pl klab.core.services -am -DskipTests compile
javap -classpath "$HOME/.m2/repository/org/integratedmodelling/org.integratedmodelling.languages.observable/1.0.0-SNAPSHOT/org.integratedmodelling.languages.observable-1.0.0-SNAPSHOT.jar" 'org.integratedmodelling.languages.api.ConceptDeclarationSyntax$RequiresType'
```

Resolved artifact: `org.integratedmodelling:org.integratedmodelling.languages.observable:1.0.0-SNAPSHOT`,
SHA256 `db1a58cc9a3ab8f3f56db1855b9cc3812edc352154d377883031558c23db88af`.
`javap` exposes only `IDENTITY`, `REALM`, `AUTHORITY`. Services source
`klab.core.services/src/main/java/org/integratedmodelling/klab/runtime/language/LanguageAdapter.java`
lines2006/2007 also refers to `EXTENT`/`ATTRIBUTE`, so the actual compiler errors are:

```text
LanguageAdapter.java:2006 EXTENT cannot be resolved or is not a field
LanguageAdapter.java:2007 ATTRIBUTE cannot be resolved or is not a field
```

Historical accepted observable artifact SHA256 was
`4b89ef600e5d620eaf18553170d11b49f5dd78f93914402d3901e4832f5edcf1`.
All five integratedmodelling language JARs changed at the same SNAPSHOT coordinates;
see `notes/roadmap-handoff-verification-20261103.json` for all current/historical
hashes. The cause of replacement and correct supported source/artifact pairing are
not proven. Do not overwrite another user's Maven cache, recover arbitrary old
JARs or remove scientific requirement cases to make compilation pass.

Next Java/build agent: establish a coherent maintained `klab-services` /
`klab-languages` revision pair supporting the required semantic contract, record
source commits and artifact hashes, rerun this compile and the selected suites,
and demonstrate that identity/realm/extent/attribute/authority behavior is preserved
or obtain explicit approval for a semantic change. If the artifact bytes differ,
report the new hash/enum and actual outcome rather than assuming this exact failure.
This is now a demonstrated build prerequisite, **not** proof that the remaining
deployment acceptance gates require new Java behavior.

### Reproduce the repaired cached-origin defect (red/green)

This isolates a proven defect, not a hypothetical remaining failure. Save the
following as `repro_cached_origin.py` outside the checkout. It creates a *new*
detached worktree and copies only the committed regression test into it, leaving
the accepted checkout and original evidence untouched:

```python
from pathlib import Path
import subprocess
import sys
repo = Path(sys.argv[1]).resolve()
scratch = Path(sys.argv[2]).resolve()
assert not scratch.exists(), "Use a fresh worktree; never reset existing work"
base = "19668515a7307aec4b1e671207f5b2d595572a14"
fixed = "e09a7de7cac6b48526fd8b7ac72879abb36a77a3"
test = "klab.core.services/src/test/java/org/integratedmodelling/klab/services/scopes/CachedScopeRuntimeOriginTest.java"
subprocess.run(["git", "worktree", "add", "--detach", str(scratch), base], cwd=repo, check=True)
data = subprocess.check_output(["git", "show", f"{fixed}:{test}"], cwd=repo)
target = scratch / test
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(data)
```

Run `python repro_cached_origin.py /absolute/path/klab-issue89 /new/path/issue89-red`.
Then from that scratch worktree:

```sh
./mvnw -B -pl klab.core.services -am '-Dtest=CachedScopeRuntimeOriginTest#sessionAThenB+sessionBThenA+contextAThenB+contextBThenA+directSessionAThenB+directSessionBThenA' -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected baseline: six failures, `Expected ...KlabAuthorizationException to be
thrown, but nothing was thrown`. Run the same selection on the fixed checkout:
six PASS. No remote mutations or scientific Jobs are submitted. Keep the red XML
and logs; this is not permission to reset or replay an old live run.

## Java change locations and regression expectations

Paths are relative to the pinned checkout:

- `klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/ScopeManager.java`:
  `getScope`, `getOrCreateSessionScope`, `requireCachedRuntimeOrigin`,
  `findOriginatingRuntime`. Explicit origin mismatch must fail before cached reuse;
  exact self Runtime may use its authorized persisted lookup without caller ads.
- `klab.core.common/src/main/java/org/integratedmodelling/common/services/client/RuntimeClient.java`:
  `connectContext`. Returned descriptor context/Runtime must match; no
  `getUserSession` call to manufacture an unrelated parent during reconnect.
- `klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/ServiceContextScope.java`:
  `forRequest`, `closeContextResources`. Request authority and registered-root
  lifecycle ownership are different; local host binding is not parent ownership.
- `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/RuntimeService.java`:
  `getConfiguration`, `declareContextScope`, `releaseContext`. Preserve persisted
  ACL checks and caller identity; close the registered root, not a request copy.
- `klab.core.services/src/main/java/org/integratedmodelling/klab/services/application/security/`:
  `EngineAuthorization`, `ServiceAuthorizationManager`, `TokenAuthorizationFilter`.
  Headers are assertions, not replacement credentials.

Executable test files are under the matching module's `src/test/java`, with names
in the command above. Focused repros:

- `NativeReconnectParentTest#reconnectUsesTheAuthorizedDescriptorParentNotTheCallersDefaultSession`
  failed on `94447b8d8...`: expected `actual-parent`, got `caller-default`.
- `NativeReconnectParentTest#reconnectDoesNotNeedDefaultSessionCreationCapability`
  failed on that baseline with an unexpected `getUserSession` call.
- `LocalRuntimeColdScopeTest#hostingRuntimeResolvesItsOwnOriginWithoutUserServiceAdvertisements`
  and `#coldOwnerContextCanUseHostAuthorizedConfigurationWithoutReAdvertising`
  failed on `802e8cce2...` with null origin/context. Copy the fixed test into a fresh
  baseline worktree using the same byte-preserving procedure above to reproduce.
- `LocalRuntimeColdScopeTest#coldRegisteredRootRetainsItsExactLocalHostForDisposal`
  failed on `988d48f33ef1472616c581d03e35c10d02ded9a0` with
  `KlabServiceAccessException: No suitable service for request of RuntimeService`
  at `ServiceContextScope.closeContextResources`. It passes after exact host binding.

## Acceptance ledger — do not infer more than the evidence

- [x] Integration published/merged (#90), not production promotion.
- [x] Controlled nonlocal-group default-session workflow in separate ordinary native
  Java processes, both creation orders, including a second process for one username
  (#131, fresh26/27).
- [x] Controlled graph-retaining **single Runtime** restart in both creation orders,
  cold owner access and native owner/outsider reconnect, owner/Runtime/group metadata,
  privacy/sharing/public-exclusion and complete fresh cleanup (#132, fresh32/33).
- [x] Explicit cached-origin denial, fixed local regressions and bounded HTTP
  matching/wrong/matching200/403/200 (#128).
- [x] Ordinary shared-session release denied; privileged owned cleanup confirmed in
  those fixtures. This is not every private-session disposal case.
- [ ] Job ownership/roles across the full nonlocal matrix; the recent membership
  and restart runs deliberately submitted **zero scientific Jobs**.
- [ ] Full invalid/expired-credential and missing-scope deployment matrix with
  distinct outcomes and positive credential controls. Unit tests do not substitute.
- [ ] Two independently deployed Runtime same-wire-ID fixture (both request orders,
  wrong principal/origin, cold and warm caches). Mock origins are not this fixture.
- [ ] Complete private-parent/disposal authority matrix, including collaborator-first
  restoration beyond the recorded owner/outsider cases.
- [ ] Broader unchanged-latest-baseline versus candidate Java comparison, including
  authority/language tests with all failures/skips reported, and rollout support
  decisions. Do not waive a baseline failure or claim every Java test passed.

For each unchecked gate, the next agent must first add a bounded fixture/test that
demonstrates the exact unsupported/failing behavior; do not assume a Java fix is
required. Retain expected/actual status, source/dependency identities, owned mutation
receipts and independent cleanup before proposing a correction.

## Committed evidence versus portable reproduction

Committed evidence is available at the pinned revision:
`notes/cached-scope-runtime-origin.md`, `notes/native-reconnect-parent.md`,
`notes/local-runtime-cold-scope.md` and their `*-results-20261103.json` files.
Historical live runner scripts and private isolated graphs are **not a fresh-clone
deployment recipe**; they must not be required from an author's temporary folder.
The commands above reproduce the Java regressions portably, not the full historical
live stack. Vendoring a sanitized, source-matched live fixture is remaining test
infrastructure work under this issue, not an external Java blocker.

Earlier runs24/25/28/29/31 remain overall FAIL with unconfirmed historical disposals.
Fresh later success does not repair those records. Never restart retained graphs,
retry an ambiguous mutation, weaken ACLs or use a server key as an ordinary-user
positive control. Review recorded so far is author/agent-conducted, not independent
maintainer approval. Broader composition/migration is #124, not this issue.
