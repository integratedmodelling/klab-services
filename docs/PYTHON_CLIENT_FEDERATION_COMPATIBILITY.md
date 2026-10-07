# Python client integration: federation-session compatibility

## Status and scope

**Open source-level compatibility concern, identified 7 October 2026.** The
existing Java default-session contract and the managed-session authorization
introduced by PR 84 appear inconsistent for two distinct legitimate users in
the same non-local federation. The source path predicts an HTTP 403 when the
second user creates a context through the reused default session. This has
**not been reproduced in an executing service** and is not a claim that all
federation or Java workflows fail.

Keep the contribution in the isolated upstream `feature/python-client` branch
while deciding and validating the intended contract. Promotion to `develop`
requires the acceptance gate below. This note documents the issue and options;
it does not implement a policy change or weaken the context security fixes.

Revision boundary:

- PR: [integratedmodelling/klab-services#84][pr]
- Reviewed PR head: `1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e`
- Original PR integration base: `64754ea2b4d7207d51db454506f51b4b18741817`
- Updated integration baseline: `142cb08e76da133a0396340b7293396bb2dc1e2c`
- Updated baseline adds authority codelists and proposal workflows. Its 35-file
  delta does not change the federation source chain below or overlap PR 84's
  changed paths. Non-overlap reduces textual conflict risk; it is not evidence
  that the combined code compiles or behaves correctly.
- Test reports on the old PR head or baseline must not be relabeled as tests of
  the combined integration commit. Record the actual candidate SHA with every
  new result, using the final merge commit and any later fix commits.

See the companion [Python client roadmap](PYTHON_CLIENT_ROADMAP.md) for the
broader remote-client scope and delivery phases.

## Existing contract and the changed authorization boundary

The relevant path crosses the Java client and the shared Runtime service. It
is not a Python-only edge case:

1. [`UserScope.getUserSession` contract][contract] says the default session is
   federation-wide when a federation is present.
2. [`ClientUserScope.getUserSession`][selection] derives the session ID from
   the federation ID for a non-local federation, replacing dots with underscores.
   Distinct members of the same federation therefore request the same default
   session identifier.
3. [`RuntimeServerController.createSession`][reuse] looks up a session by the
   requested ID and returns an existing session unchanged. The existing code
   explicitly notes reuse by multiple clients and leaves different-user
   bookkeeping/validation as a TODO. The second request does not replace the
   session's owner with the second caller.
4. The Java context-creation request in
   [`BaseServiceClient.declareContextScope`][context-request] carries that parent
   session as its scope header. `ClientSessionScope.createContext` reaches this
   registration path through the Runtime client.
5. PR 84's [`ScopeManager.allowsManagedScope`][managed-policy] distinguishes
   context scopes from other managed scopes. A context uses its configuration
   owner and explicit user/group ACLs, restored from persistence on cold lookup.
   A non-context managed scope uses the restored session owner, if present,
   otherwise its user; it allows only an equal
   username. There is no federation-session distinction in this branch of the
   policy.
6. [`ScopeManager.getScope`][managed-check] applies this rule to a cached scope
   resolved for an authenticated request and throws `KlabAuthorizationException`
   for a different username.
7. [`TokenAuthorizationFilter`][filter] maps that exception to HTTP 403 before
   the controller executes. An unresolved explicit scope has a separate 404
   path; authentication failures, transport errors and 5xx responses are not
   interchangeable with this predicted authorization denial.

Thus, under the current shared-federation-session contract, Alice may create
the common session, Bob may receive its ID successfully, and Bob's subsequent
context creation may fail because the stored session still belongs to Alice.
The first creator can affect which member succeeds. A successful create-session
response alone does not establish that the returned session is usable.

The stricter scope rule is also doing necessary security work. In particular,
when a collaborator is allowed to restore a shared context first, that must
not give the collaborator authority over its owner's private parent session.
Fixing the federation inconsistency by broadly allowing all members to access
all scopes would regress that protection.

## Reproduction specification

This is an executable test specification to implement and run, not a record of
an already executed reproduction. Use an isolated test Runtime and test-owned
state; do not use production credentials or data.

### Preconditions

- Build the exact combined candidate commit, including updated `develop`, PR 84
  and any proposed compatibility fix. Record the SHA and deployment versions.
- Provision two distinct valid ordinary user identities, called Alice and Bob
  here, whose authenticated identities carry the same **non-local federation
  ID** F. The expected session ID is `S = F.replace('.', '_')`; for example,
  choose F = `review_federation` so F and S are identical. Merely giving them
  the same group or using the local federation does not exercise this path.
- Use the established Java `ClientUserScope.getUserSession(runtime)` and
  `ClientSessionScope.createContext(...)` workflow. Do not replace the default
  session with a random per-user ID just to make the test pass. Run the two
  users in separate Java client processes, or rigorously isolated
  `ClientScopeManager` catalogs: the global `INSTANCE` caches sessions by ID,
  so a shared-process cache could shortcut HTTP and reuse the first user's
  client-side scope, invalidating the intended reproduction.
- Demonstrate that both credentials are valid and both users can perform an
  independently permitted operation before testing any denial. Keep issued
  credentials and generated keys outside the repository and recorded reports.
- Start from known test-owned session/context state, and keep the Runtime,
  federation ID, identity names and requested IDs observable in redacted logs.

### Warm-session case

1. Alice requests the default user session. Assert that the returned ID is S
   under the existing shared-session contract; record its effective owner.
2. Alice creates a context successfully through that session. Verify the
   resulting context's owner and explicit permissions.
3. Bob requests his default user session through the same Java API. Assert
   that the server returns the same ID S rather than a newly substituted ID.
4. Bob requests creation of a fresh context through the returned session.
   Capture the actual HTTP status, exception type and server scope-resolution
   result. Under the current code path, the predicted result is 403 at the
   managed-session check because Alice remains owner.
5. If the federation-shared contract is retained, the positive acceptance
   outcome is that Bob can perform the specifically authorized session
   operation and create a context with the correct request identity and
   permissions. The operation must not silently run as Alice. If the contract
   is deliberately migrated to per-user sessions, use the separately agreed
   migration assertions described below rather than pretending that the
   original shared-session test passed.
6. Repeat from clean test-owned state with Bob first and Alice second. Also
   verify a second client for the same username remains supported.

### Restart and restoration cases

Run the same identities and ownership checks after a real Runtime restart with
its graph retained. Cover the owner reconnecting first and the collaborator
restoring an explicitly shared context first. Assert the actual restored
parent-session identity and applicable session policy; do not assume the cold
path takes exactly the same cache branch as the warm case. The result must be
consistent with the chosen contract, and restoration order must not transfer
private session authority. Confirm the owner can still create and release its
own new context after collaborator-first restoration.

### Negative controls that must continue to pass

- Same-federation membership alone does not grant access to another member's
  private context, observations, jobs, scientific storage or private session.
- Explicit user/group sharing grants only the intended context access. It does
  not confer arbitrary parent-session authority or ownership.
- Explicit exclusions on public contexts apply to listing, attachment and
  scoped operations, both warm and cold, including a same-federation user.
- A user from a different federation, an unrelated user, invalid/expired
  credentials and missing scopes receive the appropriate distinct denial or
  missing/authentication result.
- Persisted ownership/rights survive restoration; missing legacy rights stay
  owner-only, and malformed rights fail closed rather than acquiring access.
- Release/disposal checks distinguish a user's own private session, authorized
  context operations and any deliberately shared federation-session lifecycle.
  Creating a context must not accidentally authorize destruction of other
  members' work.

For each negative control, require the specified authorization/missing-asset
response after positive credential validation. A timeout, connection failure,
5xx, protocol error or unauthorized credential is a failed test setup or a
separate defect, not proof of isolation.

## Design options requiring an explicit contract decision

### Option A: model federation-owned sessions explicitly

Retain the federation-wide default session ID, but represent and authorize a
federation session explicitly, separately from a user's private session. Define
which operations verified members may perform, how request identity is retained,
which metadata is persistent, and who may release or administer the shared
session. Check membership from authenticated authority, not caller-supplied
configuration. Cover warm/cold restoration, changed or revoked membership,
concurrent first creation, and cross-service propagation.

This requires a bounded session policy, not a blanket federation bypass in
`allowsManagedScope`. Context ACLs remain independent: the common parent must
not turn all descendant contexts into federation-shared data.

### Option B: migrate default sessions to per-user identities

Make the default session private and user-specific, including within a
federation. Update the Java contract, Java and Python client assumptions,
session identifiers, lifecycle documentation and interoperability tests
together. Define what happens to existing federation-scoped IDs and persisted
contexts, and how old clients fail or migrate without silent identity changes.

This can align with owner-only sessions but is a contract/migration change,
not merely a local authorization adjustment. If federation-wide coordination
is still required, give it a separately named and authorized abstraction.

### In either option

Do not remove the managed-scope authorization check, reassign the cached owner
to the last requester, or treat federation membership as a substitute for
private-context ACLs. Do not rely on a Python-only workaround while the ordinary
Java client remains incompatible. Document the selected contract and its
compatibility/migration behavior before considering this concern resolved.

## Exact-revision acceptance gate before promotion to develop

The isolated integration branch can contain the unresolved concern; it is not
a declaration of production or Java-regression safety. For a promotion
candidate, run and record all checks against the **same exact combined SHA**:

1. **Provenance and diff.** Record the candidate SHA, its updated-develop ancestor
   `142cb08e76da133a0396340b7293396bb2dc1e2c`, PR head
   `1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e`, later fix commits, toolchains,
   dependency/model revisions and selected session-policy decision. Verify both
   parent histories are retained. Review combined changes to units/semantic
   identity, job outcomes, storage finalization, service dependencies,
   authorization, scope reconstruction and lifecycle.
2. **Strict affected Java regressions.** Rerun the strict selection in the
   [PR verification record][review] without `maven.test.failure.ignore` and
   report test counts, skipped tests and failures explicitly. Include
   `PersistedContextAccessTest`, `RuntimeContextAuthorizationTest`,
   `DomainErrorMappingTest`, `ManagedScopeAccessTest`, `ScopeManagerTest`,
   `TokenAuthorizationFilterTest`, `FixtureControlsTest`,
   `StorageRegistrationTest`, `JobManagerTest`, `SemanticsBuilderUnitTest` and
   `Neo4jQueryExecutionTest`. The historical 56-test pass is not a pass here.
3. **Java client and additional coverage.** Run relevant `PollingFutureTest`,
   `KlabErrorHandlerTest` (ordinary and binary Accept paths), and
   `UnitMediationBaselineTest`; exercise native Java client serialization,
   create/attach/focus/read/release and job lifecycle. Add an actual allocated
   same-ID storage-preservation assertion where existing mocks do not prove
   retained finalized data. Validate genuine cancellation and completion races
   through fixtures that reach those assertions.
4. **Federation and ACLs.** Implement and execute the two-user, non-local
   federation reproduction and both creation orders, plus real restart and
   collaborator-first restoration and all negative controls above. Report
   expected and actual statuses, context owners and selected session policy.
5. **Latest baseline functionality.** Run the authority-codelist/proposal and
   relevant controller/language regressions introduced in updated `develop`,
   including `AuthorityCodelistsTest`, `AuthorityProposalsTest`,
   `AuthorityCodelistControllerTest` and `AuthorityProposalLanguageTest`, plus
   modified authority/cache/identity/worldview validation tests. The original
   PR's passing subset predates these changes.
6. **Broader baseline comparison.** Run the same broader test selection on the
   candidate and unchanged updated baseline. Preserve failing test identities,
   exception types and logs, and separate new failures from baseline failures.
   The previously reported 454-case run had 436 passed, 11 skipped and 7 errors
   on the older baseline. Five cancellation-related errors failed before their
   assertions; reproducing them does not validate cancellation behavior.
   A collection run with ignored failures must never be called a green suite.
7. **Python and real-stack acceptance.** Run the Python 3.11/3.12/3.13 offline
   matrix, wheel/sdist builds and installed-wheel checks. On a real isolated
   service stack at the candidate revision, verify independently known
   scientific values, units, nonuniform coordinate mapping, valid zero/missing
   data, job failure, timeout/cancellation, reconnect, explicit release and
   warm/cold ACL behavior. Record cleanup of all test-owned processes/state.
   Keep production onboarding, real-provider accuracy, bulk/temporal/federated
   scale and sustained soak claims separate unless actually tested.

If a code fix, merge, conflict resolution or relevant dependency change occurs
after these runs, qualify prior evidence and rerun affected checks on the new
candidate. A green Python matrix or a Java package build alone cannot close
this compatibility gate.

## Evidence and CI limits

- The source chain was inspected at the pinned PR head. The reported failure
  is a strong source inference with a concrete reproduction specification,
  not an executed test result.
- The author's PR records report 97 offline Python tests per interpreter, 56
  strict Java tests, and a local signed-user synthetic service-stack workflow.
  The historical hosted Python matrix for the PR head passed. These are useful
  prior evidence, but do not establish non-local federation behavior or the
  updated combined tree's runtime compatibility.
- Java/runtime tests and a live non-local federation service stack were not
  rerun in preparing these integration documents. No green full Java suite or
  production deployment safety claim is made.
- The inspected GitHub Java workflow runs Maven with `-DskipTests`; its result
  is a package-build result. The Python workflow tests/builds Python only.
- The inspected Jenkinsfile uses `install` for feature branches and reserves
  artifact publishing for `develop`/`master`. A commit-message container-build
  override also enables service updates, so integration/documentation commit
  messages must not request that override. The intended feature integration is
  non-deploying under these repository-controlled pipeline guards. Independent
  external CI configuration is outside this source-level evidence.

[pr]: https://github.com/integratedmodelling/klab-services/pull/84
[contract]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.api/src/main/java/org/integratedmodelling/klab/api/scope/UserScope.java#L71-L79
[selection]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.common/src/main/java/org/integratedmodelling/common/services/client/scope/ClientUserScope.java#L101-L123
[reuse]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.services.runtime.server/src/main/java/org/integratedmodelling/klab/services/runtime/server/controllers/RuntimeServerController.java#L700-L713
[context-request]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.common/src/main/java/org/integratedmodelling/common/services/client/BaseServiceClient.java#L265-L281
[managed-policy]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/ScopeManager.java#L47-L63
[managed-check]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/ScopeManager.java#L551-L556
[filter]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.services/src/main/java/org/integratedmodelling/klab/services/application/security/TokenAuthorizationFilter.java#L84-L87
[review]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/python-client/docs/review.md
