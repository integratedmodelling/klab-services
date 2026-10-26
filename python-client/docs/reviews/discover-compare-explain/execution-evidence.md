# Contribution review: F3 execution-evidence viewer

## Recommendation

**Needs contract/coordination evidence.** Bounded decoding, scoped activity
retrieval and the existing offline/static/coverage gates pass. The required real
Java computation exposing its own interpretable persisted evidence has not passed.
Continue local additive integration with that dependency explicit; do not claim
accepted scientific explanation or a complete milestone. This is an internal
guide-based review, not independent approval or maintainer acceptance.

## Reviewed state

- Repository/worktree: `integratedmodelling/klab-services`, isolated
  `klab-python-discover-compare-explain`.
- Final target `feature/python-client`; milestone base
  `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`.
- Feature base `e4580f17f`; candidate
  `ebcc88c03ebff8ec03474393e89c191bbf498f1a`.
- Five feature paths plus inherited provenance/transport/handle membership and
  F1/F2 boundaries reviewed; clean tree, no uncommitted feature changes included.
- Verified contribution guide unchanged at `e7659b03193448c6bd1aacc76f257ba0af0842d2`;
  reused already-read guide/repository references. Review inspection/checks were
  read-only; this report is recorded after leaving that phase.
- Permitted environment: isolated Windows Python 3.12.7, source-derived offline
  HTTP payloads. No unscoped numeric retrieval, shared mutations, admin/restart,
  Java/component changes or contributor communication.

## Capability and boundaries

Scientists can decode received Dataflow plans, inspect explicit Java resolution
outcomes and actuator occurrences, or fetch a selected observation's recorded
neighborhood and bounded currently scoped Activities. FlowChart structure preserves
node/port/link identities, parallel/hyper/reference relationships and labels/raw
metadata. Text and escaped notebook tables use the same decoded views.
Separate plans never masquerade as earlier execution. Activity failures and absent
diagrams remain explicit; activity completion is not durable root commit proof.
No replay, exhaustive lineage, rejected-alternative explanation, arbitrary metadata
execution, `encode_dataflow`, new resolve inside recorded evidence, or Java routes.

## Findings, ordered by consequence

1. **Required live retrieval/meaning gate remains blocked:**
   `evidence.py:233–296`; `execution-evidence.md:60–63`. Source inspection confirms
   the metadata keys and query path, but not deployed persisted Activity metadata.
   The hosted prerequisite connection attempt did not reach assertions; exact
   compatible binary/component/worldview identities and the scientific matrix are
   not established. Obtain a real computed observation, verify its own recorded
   activity/fixture computation, and test authorized positive control plus valid
   nonowner denial. Do not substitute a separately requested plan or mocked chart.
2. **Partial/consistency boundary explicitly retained:**
   `ExecutionEvidence.partial/durable_root_commit` and reused `ProvenanceView`.
   Membership, link and Activity reads are separate scoped requests without a
   shared revision/read lease. Diagram absence and exhausted budgets do not prove
   absent computation/dependencies. No durable commit or snapshot guarantee is
   inferred from a successful child activity or positive identifier.

No demonstrated remaining blocking/important Python defect was found within the
bounded contribution. This is not exhaustive proof. Default per-response/diagram
and graph/item/request budgets are documented; they are not a total process-RSS
guarantee or a generalized large-graph protocol.

## Validation actually performed

`python` denotes the isolated Windows Python 3.12.7 interpreter under
`%LOCALAPPDATA%/Temp/opencode/discover-compare-explain-312/Scripts/`.
Commands ran in `python-client/` and retain external JSON/JUnit records.

| Check / command | Revision and environment | Status | What it establishes / limits |
| --- | --- | --- | --- |
| `python -m pytest tests/unit/test_execution_evidence.py -q --cov=klab_client.evidence --cov-branch --cov-report=term-missing --cov-report=json:<external>/discover-f3-focused-coverage.json` | Initial F3 content before diagnostic presentation follow-up | Passed, exit 0; 55 tests; 100% lines/branches | Real-shape source-derived topology, identities, references, byte/item bounds, scoped fetches, missing evidence, failed Activities; not actual Java execution |
| `python -m pytest tests/unit tests/integration -q --cov=klab_client --cov-branch --cov-report=json:<external>/discover-f3-coverage.json --junitxml=<external>/discover-f3-offline.xml` | Exact executable candidate content | Passed, exit 0; 1,254 tests | All follow-up changes included; no failures/errors/skips; one existing Rasterio warning |
| `python tools/check_feature_coverage.py <external>/discover-f3-coverage.json --base 1e20fc478f645b5fb5c2fd00ffd223a040ca2878 --new-module src/klab_client/semantics.py --new-module src/klab_client/_inspection.py --new-module src/klab_client/inspection.py --new-module src/klab_client/evidence.py` | Exact executable candidate | Passed, exit 0 | Existing unexcluded 100% new-module lines/branches and changed-code gates |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check e4580f17f HEAD` | Candidate | Passed, exit 0 each | Existing lint/whitespace gates |
| Real unchanged Java computation with persisted matching plan/effect, nonowner denial | Selected compatible stack | Blocked | No computed result or exact stack established at this milestone |
| Installed wheel / supported interpreter/platform matrix | Candidate | Not run | Final delivery checks remain required |

## Scientific and lifecycle evidence

The source-derived chart retains a model identifier, shared/parallel/reference
structure and active-node metadata; Dataflow fixtures retain explicit coverage,
requirements, call versions and repeated actuator IDs as distinct occurrences.
These are DTO checks, not independent scientific computation. Tests distinguish
RESOLVED/NO_MODEL/FAILED and retain failed Activities. Observation association,
current scoped membership, home Runtime binding, malformed/foreign Activity IDs,
authorization/unsupported failures, and explicit absent evidence are checked.
No server cancellation, remote cleanup, root commit or replay claim is made.

## Contributor coordination

Python-only integration with existing Resolver/Runtime/provenance contracts.
Authority, runtime diagnostics/persistence and component deployment remain owned
by their existing components; no Java source, tests, Maven, worldview or ontology
was changed. No reviewer assignments/messages or publication occurred. Current
remote PR overlap outside the fetched milestone target was not reverified.

## Smallest next actions

1. Verify a compatible unchanged Java/component/worldview tuple and actual persisted
   Activity retrieval with the expected real scientific computation represented.
2. Pass associated permissions, notebook and final package/interpreter gates.
3. Keep partial evidence and plan-versus-execution labels in final workflow reports.

## Suggested PR description

Add read-only bounded Dataflow/FlowChart projections and scoped recorded execution
evidence for an original observation. Preserve identities, parallel/reference
topology, resolution outcomes, failed activities and explicit missing metadata.
Plain text and escaped notebook tables need no optional UI package. All 1,254
offline regressions and original coverage/static gates pass. Actual persisted Java
diagram/scientific/permission acceptance and final packaging remain blocked/unrun.
