# Review: explicit owned authority health after federation lookup

Accepted based3453dd42(PR212); branchfix/python-fixture-authority-health.
Author/CLI review, not independent human approval. Direct regression correction,
not a new scientific/managed-agent capability.

## Red proof / cause

PR212 introduced do_GET for authenticated user-services lookup. The previous
BaseHTTPRequestHandler default returned501 for /health; new handler returned404.
Owned run_reliability_acceptance.ready accepts200 or historicalauthority501, so
new runs would time out before Java startup. Installed/HTTP gates in212 did not
test this route; their pass is not current whole-run acceptance.

Two new actualloopbackHTTP /health assertions FAILED404 against accepted212,
federation both disabled and enabled. Preserve this failure; no scientific writes
or live managed-agent execution occurred. Scope is an explicit200 JSON TEST_ONLY
health response with no credentials; authenticated federation route unchanged.
Do not add404 to readiness acceptance or bypass genuine Java readiness.

## Required validation and boundaries

Green realHTTPtests for both modes; unchangednegativeauth/defaultoff/enrollment;
actual runner readiness call against the owned authority; fullinstalledgates,
lint/build/parity, exactcandidate review and normalfinal-headCI/eligiblemerge.
Health proves only authority HTTP startup, not broker/runtime/scientific health.
No productionSDK/Java/grants/models/worldviews/dependencies changed. No public
credential exposure, added broker or replay. All ownedserver/thread resources
must close. Q7/managedagents/fullA-B/studies remain incomplete.

## Intermediate validation retained

After the source fix, both direct realHTTP200/body assertions passed. The subsequent
in-process ready() call failed pytest's intentional offlinehttpx guard; this was a
test environment boundary, not another health404. Fail-fast orchestration stopped
before commit/fullgates. Keep the guard unchanged. Run the exact readiness helper
in a bounded hidden childinterpreter against only this test-owned ephemeral server,
then require confirmed process exit and existing thread/servercleanup. This does
not mark these tests live/skip them or weaken application readiness acceptance.
