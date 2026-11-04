# Contribution review: honest disconnect and partial refresh

**Recommendation: Ready for maintainer review of this bounded acceptance slice.**
This is an author-performed contributor-guide review, not independent approval.
Base `3746edd97`, integration `feature/python-experiment-runner`; implementation
candidate `28d2de992` (exact head and artifacts in the evidence receipts).

## Contract and architectural fit

No production SDK/Java, dependency, ontology or algorithm changes. Reuse the
existing GraphProjection/GraphBrowser contracts instead of adding a second cache,
event cursor or recovery API. The named live suite uses the existing separate
minimal consolidation fixture, installed SDK and signed TEST identities.

Two actual Java query selections are retained across an owned Runtime outage.
The failed read must propagate TransportError and invalidate all pages without
losing prior page identity, timestamp or selection metadata. After explicit
Runtime restart/user initialization/owner attach, one refetch must leave the other
selection invalidated. All views remain partial with UNKNOWN consistency and
NOT_RECHECKED authorization; a fresh read is not a revision lease.

Separate signed actors test actual Java denials for private-context attachment
and release; an owner read afterward confirms the attempted foreign release did
not delete the work. Owner and independent session/context cleanup are checked.
No local role decision or synthetic403 substitutes for this live access test.

## Findings and fixed harness risks

- Existing projection behavior already meets the demonstrated stale/refetch
  contract; no product repair was justified by these runs.
- Parent-owned stop/start control is test-only, private-state-local, ordered once,
  and limited to its captured Runtime Popen. It never finds/kills by port/name.
  Restarted Runtime is added to the normal owned PID/creation-time journal and
  final cleanup. Unit tests cover start-before-stop rejection, once-only behavior,
  readiness-before-ack and unready restart failure.
- The functional launcher can have a pytest descendant. On parent deadline/error,
  cleanup now captures and closes only that owned process tree, rather than
  leaving an orphaned test. Service logs/control files remain private.
- HTTP401/403/404/503 failures are contract doubles, explicitly not genuine Java
  scope-expiry tests. They preserve retained metadata, propagate distinct errors,
  and permit explicit later refetch.404 alone cannot identify expiry vs deletion.
- No scientific resubmission, old-Job reuse, imported mutation retry, event replay,
  full graph reconciliation, two-client ordering or durable convergence is claimed.
  Notification hints remain invalidations, not commit proofs. Broader #126 remains.

## Verified evidence and residual gates

Evidence root:
`C:/Users/lumsd/AppData/Local/Temp/opencode/scientific-reliability-queue-20261104/`.
The initial actual-Java `f3-live-v1` passes both ACL denials, outage invalidation,
owner restart/reattach, selected refetch with one stale page, independent releases,
owned process/listener closure and unchanged pre-existing Java identities. Its
startup harness was subsequently hardened/tested; exact frozen-candidate live
readback is recorded separately in `f3-final-live-v2`, not inferred from v1.

`f3-gates` records the installed noneditable wheel/sdist, exact commands, artifact
hashes and all43 SDK module source matches, offline regression, all16 existing
coverage gates and Ruff. The final live result includes functional-source hashes,
Java version, class/test/component identities and captured owned process creation
times. Only explicit pass results constitute acceptance; pending/missing results
must not be inferred from this recommendation.

Frozen candidate results: **2847 installed offline tests**, all16 existing
coverage gates, Ruff and wheel/sdist builds pass (one retained Rasterio warning).
Wheel SHA256 `ac7f1b7ecd2002d51533aa023a2f394fe756f493828b09c917763f5d5bfdfa4b`;
sdist `7ead31711f134c4ad78fc294d689475a0f1483521fc7b153fd1b5dd8fa8486a6`.
`f3-final-live-v2/result.json` explicitly passes every stated live assertion and
cleanup. The two parent controls are exactly `[stop,start]`; no scientific replay.

Suggested PR: “Prove retained graph metadata remains honest during owned Runtime
unavailability; explicit reconnect refreshes one selection, with real private ACL
denials, existing UNKNOWN/partial markers and bounded cleanup.”

Java production remains the accepted87ef80ff5 source. Explicit corrected exporter
archive98441476… and generators e2190a05… retain the established host tuple and
HM0.11.3 baseline; no component#11 adoption. This graph test does not revalidate
all exporter properties. The runner keeps original published-exporter failure
evidence separate.

## Review disposition and next action

No in-scope product defect or overlapping active PR was found. No contributor was
assigned or contacted. Finish exact-candidate installed/live and hosted gates,
merge normally without bypass, verify remote ancestry and postmerge tests, then
retire the branch. Update only the existing acceptance-map queue ledger; retain
broader RC-P3.2/CAP-10/CAP-11/#126 limitations. Automatically continue Feature4
only from the verified merged integration. Reproduce via the partial-refresh
command in `docs/graph-projection.md` (not a shared-service outage test).
