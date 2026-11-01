# Proposed bounded #90 native acceptance batch

**Authorized, not yet executed.** The user explicitly selected “Approve isolated
#90 checks” after being shown the service/identity/object/restart limits, zero
numerical jobs/imports/production changes, fifteen-minute post-readiness budget,
and no automatic retry. Harness safety review precedes execution. This approval
does not authorize #98 or #100 numerical work.

## Isolated environment

- Candidate-built Reasoner, Resources, Resolver and Runtime, each in its own
  hidden native Java process and private configuration directory.
- A separate test-owned GraphDB and signed test-identity issuer. No production
  Hub/account changes and no production credentials. Two ordinary test users in
  one nonlocal identity-sharing group, plus one ordinary outsider as a negative
  control. Administrator authority is used only for setup/owned cleanup, never
  for the ordinary users' positive or negative checks.
- Reserve unused loopback ports at execution. Proposed service ports
  18091–18094, issuer 18095, GraphDB HTTP 18382/Bolt 17687; do not assume they are
  free without checking. Never replace listeners on 8091–8094, 8382 or 7687.
- Record source tree/commit, uncommitted-file hashes if any, Java/JAR hashes,
  component archives, actual dependency versions, worldview revision and all
  effective service advertisements. Readiness failure means stop, not silently
  replace components, import scientific models, or use owner credentials for a
  guest.

## Proposed hard limits

- **Zero numerical observation submissions**, no scientific algorithm changes,
  no resource/model imports, no production service restarts or remote changes.
- At most two test default sessions, eight test contexts, and three ordinary
  test identities. Use two clean test-only creation-order fixtures; maximum four
  contexts per fixture for caller-owned private, explicitly shared, and
  public-with-exclusion controls.
- At most two restarts of the newly created test Runtime, retaining only its
  test graph. No other existing process may be stopped or reconfigured.
- Separate native Java clients for each principal and same-username second
  client. No shared in-process client-scope catalog. At most twelve client
  process launches for the batch.
- Fifteen-minute test budget after readiness, bounded per-operation waits, no
  automatic retry after an unexpected result. Clean up only registered
  test-owned state and stop only agent-created fixture processes.

## Required checks

1. Both credentials and each caller's Reasoner/Resources/Resolver/Runtime
   advertisements work before any authorization denial is assessed.
2. Alice-first then Bob-first default-session/context creation. The default
   session follows the actual nonlocal shared-session contract; each new context
   retains the actual creating caller and its rights.
3. An independent second native client for the same username remains supported.
4. Warm explicit shared access succeeds; private and explicit-exclusion access
   fail with the correct authorization result, not timeouts/5xx/invalid login.
5. Owner-first and collaborator-first reconnect after test Runtime restarts;
   retained rights, private parent ownership and native caller binding survive.
6. Outsider, invalid/expired credential, missing scope and wrong-Runtime controls
   are distinct. Same-wire-ID wrong-origin rejection stays covered by the
   deterministic origin regression; this is not simultaneous multi-origin cache
   or separately deployed installation certification.
7. Own-context release works, unauthorized release is denied, and ordinary
   membership does not permit destroying another member's context or shared
   session. Owner access is rechecked after negative controls.
8. Save redacted exact-candidate evidence; independent final acceptance review.
   Verify test cleanup, original listener PIDs/configurations, and agent-created
   console/window cleanup without closing unrelated terminals.

## Stop conditions

Stop before stateful tests if binary/worldview/component attribution, ordinary
authentication, advertised services, storage isolation or bounded cleanup cannot
be established. Stop at the first unexpected test outcome; retain evidence for
diagnosis rather than spending a new implicit allowance. Missing scientific
fixture material is a blocker, not permission to invent or migrate it.

Even a successful metadata-only batch does not establish #98 recorded numerical
evidence or #100 experiment-runner throughput. Those require their own exact
candidate reviews, independently specified values and explicit bounded numerical
batches before their conditional merge criteria can be satisfied.
