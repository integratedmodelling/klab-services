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
