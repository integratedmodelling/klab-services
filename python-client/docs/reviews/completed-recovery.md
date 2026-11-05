# Contributor review: installed fresh-process completed-work recovery

2026-11-05 UTC; author/CLI-conducted review, **not independent human approval**.
Branch `feature/python-fresh-process-recovery`, integration base
`fe46ddc5ec3c188f62cd1d0b0703c6e54a4b9241`; no competing open services PR at baseline.
One coherent acceptance gap: use existing handles to recover completed scientific
work in a fresh installed process without disposing/recreating original work.

## Fit, usefulness and boundaries

Scientists can verify retained completed Job/observation identity and original
values after closing a creator transport, instead of merely deserializing a handle
or constructing a second Client in the same process. Public SDK APIs are reused;
no new restoration layer, Java/controller/DTO, dependency, worldview or remote
algorithm is added. All Python workflow code is harness-only. This does not close
the full retained in-flight/history/incarnation criterion or certify original
historical science, a public release, production Hub or managed multi-provider use.

## Consequence-ordered findings and controls

1. Fresh process must not be a synthetic fake: the live test uses actual Java,
   installed SDK, a different OS PID and original Job/observation/focus IDs. The
   creator transport closes first. Unit fakes test only instrumentation/failure
   controls, never scientific acceptance.
2. A successful request is insufficient: independently specified20 asymmetric
   values include zero, negatives, missingness and a value beyondfloat32exactness.
   The restored Job result and separate observation each read that oracle.
3. Restoration must not silently acquire disposal ownership, replay creation or
   release scopes. Request method/path records are checked after child close;
   existing POSTquery/value-read routes and POSTconnect restricted to the original
   saved root are allowed; arbitraryGET operations/cancel/release are refused. No request
   headers, bodies or token values are saved in the success report. Explicit
   user-scope initialization precedes the audit, rather than masquerading as an
   implicit restoration effect.
4. Owner positive controls after local closes and foreign/deleted-context failures
   check authorization and availability separately. They do not establish an
   entire collaborator mutation/credential-expiry matrix or Runtime restart lease.
5. Only creator-captured contexts/session may be explicitly released. All release
   attempts run even if one fails; failed cleanup is recorded and preserves the
   primary error. No unrelated user context/service is disposed. Child launch and
   parent service launches use CREATE_NO_WINDOW, with existing owned process-tree
   cleanup and private state ACLs.
6. Unchanged installed package/source is verified by the existing gate driver;
   full unit/integration/coverage/lint and real-stack checks are distinct. No
   broad Java reactor rebuild is needed; component pins remain unchanged.

## Checks so far (not a predeclared pass)

Initial installed gates:2856offline tests,16coverage gates, lint, wheel/sdist and
source parity pass; one retained Rasterio warning. Preliminary wheel SHA256
`072ae74d7e7f6bd4cc7354417610310949c829342943b55f881ab55335939cf9`.
Subsequent harness review tightened creator-close/cleanup evidence and allowed the
existing read-only POSTquery route; final frozen harness verification remains
required. Exact commands/receipts live under external `ab-three-study-20261105/`
and the prior gate driver's `scientific-reliability-queue-20261104/ab-q1-first/`.
Actual Java, final candidate and hosted/merge results will be appended, not inferred.

Initial `q1-live-v1` reached original scientific readback but failed the overly
strict harness audit: Client.attach_context explicitly uses POST `/api/v1/connect`
(`client.py:459–464`), not GET. One evidence-led correction admits only that route
with the exact saved root ID and restricts allGETs to capabilities/status/retrieve.
New negative tests refuse wrong/missing root, creation/submission/cancel/release.
This corrects the acceptance harness, not scientific Java behavior. Initial failure
and successful owned-process/listener/foreign-Java preservation remain recorded.

Corrected `q1-live-v2` also fails the harness guard before attach: ScopeRequest
stores the root ID at `configuration.id` (`Client._scope_request:414–415`), not at
the JSON top level. This is a second internal harness defect, not an external
blocker or evidence that Java restoration fails. The extraction and unit fixture
now use the actual envelope shape. **No third live repair loop in this pass**;
Q1 remains IMPLEMENTED_UNVERIFIED/acceptanceFAILED, is not eligible for merge, and
the queue moves to independent work. Required next Q1 evidence is one frozen
candidate live run after this known source-grounded correction, not a new API.

After independentQ5actualparseracceptance (PR201 is externally packaging-blocked),
Q1sourcepreflight identified the controller's documented missing-root reply:
HTTP200configuration with `Cannot find a digital twin with requested ID`, raised
as ServerError by check_notifications. The deleted-context control now accepts
only that exact notification or typedMissingAssetError, rejecting arbitrary500s
and generic authorization failures. This follows RuntimeServerController478–483,
RuntimeService4960–4971 and explicitdispose4656–4662, not a fitted pass criterion.
Five new negative/positive unit vectors cover that distinction. Q1 has no dependency
on Q5code, no Java changes and no new claimed retention/restart capability.

## Resumed exact-candidate acceptance

Implementationd8ae23b3d passes `q1-live-resume`: creatortransportclosed, distinct
workerPID, same originalJob/observation/focus, two independent20cellreadbacks,
restoredownershipfalse, no submits/releases/cancels in worker, creatorread after
localclose, actualforeignrefusal, exactdeleted-context ServerError notification
and confirmed creatoronlycleanup. Ownedprocesses/listenersclosed and preexisting
Java identities unchanged. Runtime restart/in-flight retention remainNOT_TESTED.

Final installed noneditable package/source parity PASSED: **2874offline tests,
16coverage gates, lint, wheel/sdist**; one retained Rasterio warning. WheelSHA256
`1be8630f9b1297ff1d51f2497eda4505590bcaa4be509737adfd89bfefbd16a6`,
sdist`445aecd8539f3b3a6b414b4577c3e471ef4ba2a1e07f36b4efdc66599683de51`.
External exactcommands/identities: priorqueue`ab-q1-resume/` and currentprogram
`q1-live-resume/result.json`; both earlierfailedruns retained, not recast aspasses.

**Ready for bounded maintainer review**, not humanapproval/fullRC-P1.2/P3.3.
Normalmerge currently faces the separately demonstrated packagingartifact gate
fromPR201; no bypass permitted. This branch has no Q5product dependency. No new
historical science, worldviews, Java product code or dependencyversions changed.

## Suggested bounded PR

“Verify installed fresh-process completed Job and observation recovery on owned
Java work, preserving identities, exact values and explicit creator-only cleanup.”
Ready to propose only when final exact-candidate live/negative checks pass. Full
RC-P1.2/P3.3 and all other A/B scenarios remain visible and incomplete.

## Integrated candidate review and acceptance

Reviewed base `76b4d28a4fb87c7a4325fce5138344b3ac09ef25`, executable candidate
`91adcaba4d465e91f1c000a0e8a4aca6bcf5139b`; author/CLI review only. Complete
feature diff and six conflict resolutions inspected. Both parser and recovery
suite/scenario entries are retained; no production SDK/Java changes. Earlier
packaging blocker is resolved by accepted PR202, not waived.

No new blocking or important defect found within this bounded slice. Existing
findings/limits above remain: method/path audit is harness instrumentation, not
a substitute for server authorization; completed same-incarnation work only.
Scientific fidelity uses independent 20-cell Decimal oracle (zero, negative,
missingness and float64 precision). Identity, permissions, local-close lifecycle,
state association, bounded child waits, owned cleanup, private evidence and
maintainability were rechecked. No public API duplication or concurrent contributor
ownership claim; no reviewers assigned. Scale/production/retained-history support
are outside scope, not proved by this run.

| Check | Exact candidate/environment | Result and boundary |
|---|---|---|
| `run_gates.py --repo <checkout> --label ab-q1-integrated` | 91adcaba4, noneditable Python3.12.7 | PASS: 2,886 tests,16 established gates,lint/build/install/source parity; retained Rasterio warning |
| Focused recovery/parser/map tests | Integrated conflict resolution | PASS:39 tests |
| `run_reliability_acceptance.py --server-repo <checkout> --java <Temurin21> --components <corrected-components.json> --state-dir <q1-integrated-live> --scenario completed-recovery` | 91adcaba4, same pinned Java/core/components and synthetic consolidation worldview | PASS: original Job2/observation15/focus restored in distinct PID,20 cells each,zero child submissions/disposal,foreign/deleted refusals,creator releases |
| Owned processes/listeners and existing Java | q1-integrated-live/result.json | PASS: owned closed, unrelated Java unchanged |
| Hosted checks/normal merge | Final documentation head | Pending; required before eligible merge |

Wheel SHA256 `9a03a4e48e1641d2ea622238c543276b6ec8d790dd27e199912c2cf52ff9430c`;
sdist `0bc5fba4891433cf843295336c6e709f6054421af37e7bb7fcffec47d9960a82`.
Evidence roots remain external `ab-q1-integrated/identity.json` and
`q1-integrated-live/result.json`; earlier failures retained. Subsequent edits are
documentation only and must be verified as such before reusing this evidence.
**Ready for bounded maintainer review**; no independent human approval, whole-row
acceptance, upstream release or historical replication claim.
