# Contribution review: F2 resource/model inspector

## Recommendation

**Needs contract/coordination evidence.** The bounded Python implementation and
all offline regression/coverage/static gates pass. Local additive integration is
supported with an explicitly tracked live dependency. The milestone requirement
to inspect an existing executable model and connect its identifiers to real
computation is not demonstrated. This internal review follows Eric's contribution
guide; it is neither independent approval nor maintainer acceptance.

## Reviewed state

- Repository/worktree: `integratedmodelling/klab-services`,
  `klab-python-discover-compare-explain`.
- Final target `feature/python-client`; milestone base
  `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`.
- Feature base/checkpoint `69c424a3b`; candidate
  `f9e97be36021fcf05a9982063e8bd9ac27a8c7b3`.
- All six feature paths and inherited F1/transport/Resources boundaries included;
  clean tree at read-only review entry. No uncommitted feature code included.
- Contribution guide verified unchanged at `e7659b03193448c6bd1aacc76f257ba0af0842d2`;
  reused the already-read version and applicable repository/planning references.
- Read-only review did not edit code, publish or alter branch history. This report
  is recorded after leaving that phase.
- Permitted environment: isolated Windows Python 3.12.7 tests, source-derived
  MockTransport contracts; no live model mutations, shared admin, imports or restarts.

## Capability and boundaries

Scientists can inspect bounded MODEL/RESOURCE lists and explicit assets through
`ResourceInspector`, see supplied observables/dependencies/versions/provider data,
and retain originating service/scope information. Missing metadata remains missing;
duplicates remain distinct; descriptor URLs are never followed. This is advertised
applicability, not actual Resolver selection or a complete dependency closure.
No recursive traversal, arbitrary filters, global semantic model search, forced
models, authoring or service administration was added.

## Findings, ordered by consequence

1. **Required live evidence gap:** `inspection.py:145–190` and
   `resource-inspector.md:45–49`. The source-derived model/ResourceSet tests verify
   fields and routing, not existence or execution on a deployed stack. Hosted
   reconnaissance failed during Resources connection before semantic assertions;
   exact deployed Java binaries and a compatible executable scientific matrix
   remain unverified. Smallest correction: supply/verify the unchanged compatible
   stack, retrieve the actual selected model and its resources, then connect these
   identifiers to recorded computation evidence. Keep the acceptance status blocked.
2. **Explicit contract boundary:** `inspection.py:31–48,66–80,145–190` records
   partial advertisements and originating service separately from returned provider
   descriptors. Whole-list bounds reject excess rather than pretending pagination.
   Source inspection and tests confirm no descriptor-driven request or credential
   forwarding. Recursive closure/version selection are intentionally not claimed.

No demonstrated remaining blocking/important Python defect was found in this
bounded review. Passing tests do not prove full scientific correctness or deployed
permissions. The optional response-budget additions to existing service methods
are keyword-only and preserve their defaults/return types.

## Validation actually performed

`python` is the isolated Windows 3.12.7 interpreter at
`%LOCALAPPDATA%/Temp/opencode/discover-compare-explain-312/Scripts/python.exe`.
Commands ran from `python-client/`; external records share the approved temporary root.

| Check / command | Revision and environment | Status | What it establishes / limits |
| --- | --- | --- | --- |
| `python -m pytest tests/unit/test_resource_inspector.py -q --cov=klab_client.inspection --cov-branch --cov-report=term-missing` | Exact executable candidate content before commit | Passed, exit 0; 44 tests; 100% lines/branches | Bounded typed metadata, identity drift, null/empty distinction, duplicate URNs, partial responses, provider trust and HTTP failures |
| `python -m pytest tests/unit tests/integration -q --cov=klab_client --cov-branch --cov-report=json:<external>/discover-f2-coverage.json --junitxml=<external>/discover-f2-offline.xml` | Exact executable candidate content | Passed, exit 0; 1,199 tests | No failures/errors/skips; existing Rasterio warning only; inherited API regression checks included |
| `python tools/check_feature_coverage.py <external>/discover-f2-coverage.json --base 1e20fc478f645b5fb5c2fd00ffd223a040ca2878 --new-module src/klab_client/semantics.py --new-module src/klab_client/_inspection.py --new-module src/klab_client/inspection.py` | Candidate, Windows | Passed, exit 0 | Original unexcluded 100% new-module and changed-code gates |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check 69c424a3b HEAD` | Candidate | Passed, exit 0 each | Existing static checks, no exclusions |
| Existing executable model/resource inspection linked to experiment | Selected unchanged deployed Java | Blocked | Live compatibility/model/matrix prerequisites not established; no mock substitutes |
| Installed-wheel, other supported interpreters/platforms | Candidate | Not run | Remain delivery gates |

## Scientific and lifecycle evidence

Available native unit strings remain supplied advertisements, not unit conversion
or successful computation. Resource/dependency absence is not zero dependencies.
Source-derived tests verify exact scope and home Runtime headers, selected Resources
origin, service changes, authorization/missing/unsupported/malformed failures,
byte/item bounds and no newly advertised-origin requests. Raw access keys and known
credential strings are removed/redacted in immutable views. F2 makes no remote
mutation, cancellation, replay, recovery or cleanup-ownership claims.

## Contributor coordination

No Java, component, worldview, ontology or NiFi source changed. Python Resources
wrapping reuses current contracts and adds no independent transport/framework.
Deployment and scientific fixture ownership remain external. No maintainers were
messaged or assigned; broad current remote PR overlap was not reverified.

## Smallest next actions

1. Establish compatible unchanged binaries/model/component and pass live inspection
   with the actual computation identifiers.
2. Run final installed-package/interpreter and combined integration gates.
3. Preserve the explicit partial/advertised boundaries in the shared notebook.

## Suggested PR description

Add typed bounded model/resource inspection using the existing Resources APIs.
Scientists can inspect advertised observables, direct dependencies, versions and
provider descriptors with their selected service/scope origin. Missing fields and
errors remain distinct; no credential forwarding or complete dependency closure is
implied. 1,199 offline regressions and the existing coverage/static gates pass.
Live executable-model association and final packaging remain outstanding.
