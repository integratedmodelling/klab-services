# Contribution review: bounded typed recorded graph pages

## Recommendation

Ready for maintainer review within the documented single-page synchronous scope,
subject to final-head CI. This is an **author-conducted read-only review**, not an
independent third-party approval. No maintainer is assigned work by this record.

- Base: normal PR #106 merge `1329debbe0412a5deeca17f843a00a79cf2fbe1f`.
- Integration target: `feature/python-experiment-runner`.
- Reviewed/live-tested executable: `f1cc60a84458f58ce4b059472a9146cb5106dc85`.
- Public API: `GraphBrowser`, `GraphPage`, reusing ProvenanceNode/ProvenanceLink.

## Contract and implementation review

The current Java KnowledgeGraphQuery DTO and Neo4jQueryCompiler admit typed LINK
queries at exactly one hop. The compiler parameterizes anchors, applies context
visibility to both endpoints and uses an elementId tie-breaker. Deterministic
ordering is not a revision lease; offset pagination in a changing graph may skip
or repeat results. Pages therefore stay partial even when empty.

Each explicit page request revalidates membership and checks the selected context
identity before/after I/O. Returned links must match the anchor, direction and
relationship filter. Budgets bound page rows/response bytes; no hidden recursive
crawl, write, retry, arbitrary query text or background async facade is added.

Existing provenance codecs freeze/redact recorded data, preserve planned versus
recorded evidence, and reject unknown node/activity contracts. Supported nodes are
observations, activities, actuators and cohorts, not every RuntimeAsset. Detached
mediated views are rejected as native anchors. Raw metadata/IDs remain passive,
not authority to evade current server access checks.

## Findings and fixes

No unresolved blocking defect found within this bounded review; not exhaustive
proof. Review and genuine acceptance exposed and corrected:

- A detached mediated observation could be mistaken for a native identity: reject
  it before I/O, consistent with existing native-provenance/export boundaries.
- Returned node metadata are deeply immutable. A shallow dict copy failed when
  the real activity contained nested mappings/tuples. Reuse the existing bounded
  evidence `_plain` helper before checked re-decoding; add nested-map/list and
  malformed-user-descriptor regression cases. Do not relax immutable projections.
- Attempt one was correctly stopped by the clean-source gate before services
  launched. Attempt two completed observations and the first graph page but failed
  activity navigation. Both remain FAIL; no ambiguous work was replayed.

## Verified validation

| Check | Actual result and limits |
| --- | --- |
| Focused graph tests | PASS: 49 cases, exact DTO selections, membership and identity failures, direction/filter checks, nested frozen metadata, redaction and malformed/budget cases |
| Full offline regression | PASS: 1940 tests, one existing Rasterio warning, 61.31s |
| Coverage | PASS: new module 100% unexcluded lines/branches; existing/async/table gates retained |
| Lint/package build | PASS: existing fatal-error lint, wheel and sdist |
| Installed core-only wheel | PASS: typed nested-metadata navigation through mocked HTTP; no scientific optional packages imported |
| Genuine Java navigation | PASS: two controlled Jobs, observation-to-MEASURE-activity and activity-to-observation, each with membership + link lookup |
| Owned lifecycle | PASS: both scope release booleans true; all 13 recorded owned JVM identities absent; no shared service changed |
| Executable-head hosted CI | All reported Python 3.11–3.13, core-only, Java build and Jenkins checks pass; final documentation-head check remains required |

Sanitized facts and hashes:
[`graph-controlled-20261102.json`](../results/graph-controlled-20261102.json).
Java binaries are explicitly pinned to the preserved fixture tuple; source
equivalence with every upstream build is not assumed. The live run covers
MEASURED links, not every supported relationship/type, denial scenario or mutation
during pagination. Controlled numerical readback is a fixture sanity check, not
new real-provider or PaL validation.

## Acceptance limits and next steps

An activity SUCCESS is recorded activity state, not independent root-commit proof
or scientific validity. No shared export/graph revision, complete lineage, async
browser, graph mutation, arbitrary filter language or durable event recovery is
claimed. Keep these limits visible rather than silently expanding the feature.

Verify final documentation-head CI and unchanged executable tree, then use only
normal merge under the user's sequential-feature instruction. Begin the next
feature's server-contract verification afterward. Native GeoTIFF unit issue #10
and original scientific reproduction gates remain unchanged.
