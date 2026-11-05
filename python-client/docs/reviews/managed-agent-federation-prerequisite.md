# Contributor review: opt-in owned Hub federation lookup prerequisite

Basec7dea3373; branchfeature/python-managed-agent-preflight. Author/CLI review,
not independent human approval. One harness prerequisite only; no managed-agent
acceptance or new productionSDK/Java/scientific algorithm/ontology/worldview change.

## Contract / usefulness

Trace ScopeManager.createUserIdentity to authenticated Hub group lookup and
Klab.getFederationData custom properties before adding fixture behavior. Existing
fixture supplied no GET; inventing JWT claims would not exercise the Java path.
Implement exact existing route/header only when an explicit bounded private TEST
manifest is present before startup. Keep default enrollment/source science unchanged.
Reuse existing Java lifecycle rather than create a Python Runtime/broker service.

## Security / state / lifecycle

Pin RS256 verification to the fixture authority key and exact service issuer,
subject,audience,roles and live integeriat/exp. No scientist or Bearer fallback.
Allow only bounded explicit TEST identities/authenticated loopback broker URI;
extra config fields/implicit consent/externalhosts/ambiguousport/query/fragment
refused. The response necessarily carries operator credentials to the authenticated
service; keep it private,no-store,nohandlerlogging. It proves distribution, not
broker authorization. No broker created or remote writes/retries introduced.

Refactor authority construction only to expose a loopback ephemeral test server;
normalserve still binds18090 and retains finallyclose. RealHTTPtests close their
own server/thread, never unrelated services. Twenty-eight focusedtestsPASS;
lintPASS. Full installedgates pending. No demonstrated important/blocking defect
in bounded source review; actualRuntime group decoding and maintained broker ACL,
principal/incarnation and independent remote cleanup remain separate gates.

## Delivery boundaries

No STOPPED→cleanup or PUBLISHED→execution inference. Clientremote_cleanupUNKNOWN
unchanged. No claims about Qpid confirms or productionHub/license onboarding.
No reviewer/contributor assignment or ownership inferred. Q7hierarchy unresolved;
fullQ8/A/B/studies incomplete. Before normal PR/merge: exact installed gates,
finaldiff/identity review and all selected final-head hostedchecks SUCCESS.

## Exact candidate / bounded recommendation

**Ready for bounded maintainer review**, author/CLI only. Executable
`f35e984deafe2f73f4486fae00a576a29ad29dd1`; acceptedbasec7dea3373.
Final diff reviewed across usefulness/architecture/contracts/science/identity/
lifecycle/state/performance/maintainability/contributor fit. No remaining
demonstrated important/blocking defect in this opt-in fixture transport contract.

| Command/check | Environment | Verified outcome / boundary |
|---|---|---|
| `run_gates.py --repo <checkout> --label ab-q8c-federation-prerequisite` | Exact noneditablePython3.12.7, f35e984de | PASS:3,041tests/17coveragegates/lint/build/sourceparity; inherited Rasterio warning retained |
| `pytest -q tests/unit/test_fixture_federation.py tests/integration/test_fixture_federation_http.py` | Same interpreter, package test root | PASS:28; actualloopbackHTTP, serviceJWT/negativeauth, exactgroupfields, manifestguardrails, defaultoff and server/threadcleanup |
| Java source trace | Accepted source, read-only | Existing route/header/group lookup identified; not actual Runtime decoding/connection acceptance |
| Scientific/managed Runtime run | Not applicable to this prerequisite's claim | NOT_RUN; no broker, agent or scientific mutation |
| Final-head hostedCI/normaleligiblemerge | Documentation head | Pending; required before merge |

Wheel `8717f496aa9a2acbc5ce1927bd650043d10ca206e0be6abfa40db7e04d4c82e9`;
sdist `4c2b3c9b305a702f83927d0ddf6ec71b2a2b37441d823155552cf381e4a7b1ec`.
Installed identity/gate receipts are retained privately under
`ab-q8c-federation-prerequisite`; no broker credentials or live manifest are public.
Only Markdown edits follow this tested executable; reverify raw tool/test hashes
and unchanged enrollment handler before evidence reuse. No new hosted scientific
test is replaced by these HTTP tests. Acceptance rows are not promoted.
Next: normalPR/CI/exact-headmerge, ancestry/postmerge; then separately provision
owned broker and verify Runtime group decoding/ACL/lifecycle/independent cleanup.
