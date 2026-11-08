# Support matrix and release-evidence policy

**Development preview, version 0.1.0. Not a production support promise or a new
published release.** This policy/tooling feature does not remove scientific or
platform blockers and does not bump, tag, upload or publish the package.

## Verified versus declared compatibility

| Dimension | Evidence / policy |
| --- | --- |
| Python metadata | Package declares Python >=3.11. Installation eligibility is not verified support for every newer Python |
| Hosted Python checks | Ubuntu CI covers Python 3.11, 3.12, 3.13; require green final-candidate checks for these environments |
| Local verification | Windows Python 3.12.7; isolated controlled Java fixtures plus local offline/package tests, not every deployment/OS combination |
| Other Python/OS versions | Not verified by this matrix. Python 3.14+ and macOS must not be labelled supported based only on package metadata |
| Core dependency | httpx >=0.27,<1 declared; this is an allowed range, not tested certification of every version within it |
| Optional analysis/tabular/notebook dependencies | Extras and bounded versions are in pyproject.toml. Core imports do not require scientific libraries; test actual selected versions for a release |
| Java/backend | Controlled receipts pin Java binary/source tuples. No blanket compatibility with all current/future upstream builds, distributions, authorities or worldviews |
| Distribution | Wheel/sdist build and fresh installation must be checked; repository/editable-environment success is not installed-package acceptance |

## Bounded capability matrix

| Capability | Actual accepted scope / remaining limits |
| --- | --- |
| Sync observation workflow | Typed requests, configured origins, scope/Job lifecycle, checked indexed reads; scientific validity remains model/input/reference-specific |
| Native async | Native scope/Job/point reads, own-wait PR210, exact-installed-kernel notebook PR211, saved handles PR218, exports PR219; later provenance/evidence, analysis association, temporal exports, graph/projections, previews, deferred registration, authoring/import and project locks. See the ongoing A+B ledger for exact campaign merge/CI evidence. No async Connection/Hub orchestration, async broker facade, universal exporter, arbitrary kernel-profile or in-flight restart/history guarantee |
| Indexed pandas views (#106) | Object precision, explicit null flags, source metadata, offsets/order; no guessed axes, general categorical dictionary or lazy/chunk/time discovery |
| Recorded graph pages (#107) | Typed bounded sync/native-async pages and projections, membership checks, explicit refresh and stale markers; partial, not complete lineage, snapshot pagination or root-commit proof |
| Native exports / raster readers | Actual Java GeoJSON identity/coordinates/CRS and million-cell streamed numeric export/fault preservation accepted in PR204/206. Checked reader subset only; categorical/time/multi-shard/revision/provider gates remain separate, including upstream geospatial issue #10 |
| Existing Connection routing | Explicit trusted service selection/origin credentials; not automatic failover, migration or twin composition |
| Events/reconnect | Existing best-effort hints remain distinct from unaccepted durable replay/resync/root-commit proof (issue126); historical audit PR108 is not proof of absent current notification code |
| Window/chunk/time access | Explicit point `ScanSlice` and native `TEMPORAL_TRANSITION` export request fields are implemented; `requested_slice` records the request, not a revision/read lease. General remote scan/window contract [#109](https://github.com/integratedmodelling/klab-services/pull/109) remains separate; internal Java scan plans are not a published remote scan protocol |
| Authoring/import/publication | Sync/native-async advertised RESOURCE/STREAM upload with explicit consent and checked Resources Job/byte receipt; ADD-only namespace/strategy/plain BEHAVIOR drafts in confirmed new projects; four-family read-only parser previews; explicitly consented principal-shared project lock booleans. See authoring/import/behavior/project-lock contracts. Historical PR201/209 live slices do not verify every new operation. Ontology/worldview mutation, generic accepted-source conditional updates/history/conflict and publication remain incomplete; Java CRUD or project locks are not universal safe modes |
| Behavior/agent lifecycle | Existing deferred BEHAVIOR registration/native AMQP control and wire fixture; opt-in Hub federation transport prerequisite in PR212, authority readiness corrected in PR213. Genuine managed Runtime/principal-incarnation/independent remote cleanup remain unaccepted; PUBLISHED/STOPPED are not execution/cleanup proof |
| Broader federation | Blocked composition/recovery contracts [#112](https://github.com/integratedmodelling/klab-services/pull/112); attachment is not composition |
| Original PaL / full scientific portfolio | Still blocked by admitted models, binding/units/grid/time/masks/reference contracts. Input QA and synthetic known-answer runs are not published-study reproduction |
| Embedded worker/provider SDK | C is in scope under the renewed A+B+C directive. A separate experimental `python-worker` package and simulator lifecycle/recovery/resource controls are delivered; native provider ABI/deployment and hostile-code/total-disk isolation remain separate gates. Not a production support promise. |

These are bounded development-preview receipts, not whole-row acceptance or
production support commitments. Current integrated evidence and remaining gates
are tracked in [current compatibility evidence](current-compatibility-evidence.md).
The reproducible [source-callable inventory](supported-operation-inventory.md)
identifies explicit unsupported wrappers without treating guarded fallback errors
as missing whole operations. Source classification alone is not executed support.
PR213 restored default owned authority readiness after a discovered PR212 regression;
the red failure is retained, not recast as a pass. Q7's actual hierarchy discrepancy
remains unresolved on its preserved branch and is not hidden by offline success.

### Later Python/reference additions do not expand native support

The roadmap campaign adds explicitly opted-in, advertised **finite reference**
protocols for conditional document revisions/validation/publication, graph
checkpoints/deltas, retained notification cursors, shared READ grants, selected
windows/temporal states, declared raster dictionaries/band labels, copied A/B-to-C
composition and managed-behavior lifecycle/disposal. These are separate from the
native limits in the table above. In particular:

- A reference publication receipt is not Java source admission or public release.
- A completed Job is not a graph checkpoint; gaps/epochs require explicit resync.
- A declared state/band timestamp is not provider time-axis scientific authority.
- Copied source data/identity is not a live upstream grant or federation lease.
- A finite actor-state fixture and declared session disposal do not prove native
  k.Actors execution or actual private-session cleanup.

The reviewed operation registry now links selected assertions for all455
nonabstract source callables;51 abstract declarations remain uncalled. This is not
exhaustive functional acceptance, every branch/input, or a support promise. See
[reviewed assertion limits](reviewed-operation-assertions.md).

### Migration boundaries

Existing configured sync/native-async calls remain available; experimental protocols
require explicit opt-in **and** server advertisement. Do not catch a capability,
identity, permission, expired-reference or unsupported-operation error and silently
switch to the reference service, another provider or guessed protocol.

Saved references are offline identities, not credential/ownership grants. Restore
through current explicitly configured credentials; stale or mismatched service,
scope, incarnation/epoch or selection identities must refuse rather than recreate
work. Keep historical receipts separately from fresh authorized reads.

Scalar snapshots preserve integer/Decimal precision, duplicate offsets, original
wire text and explicit missingness. Raster/vector views preserve source codes,
masks and declared layout; dictionaries/time labels add bounded metadata, not
automatic semantic conversion, resampling or reprojection. Install the relevant
optional extras explicitly; core operation must not invent substitute libraries.

`Job` result typing distinguishes synchronous values from native Python coroutines.
Async waits use task cancellation, not synchronous cancellation callbacks. Cancelling
a local wait or closing a client does not cancel/dispose remote work; use explicitly
authorized operations and preserve uncertain outcomes without replay.

## Release evidence checker (development tool, not public SDK)

`tools/check_release_readiness.py` validates a bounded operator-supplied manifest,
exact candidate identity and byte-pinned local artifacts. It does **not** interpret
test log truth, certify science, authenticate the evidence producer, attest a CI
run or authorize release. A fabricated log/manifest can pass structural checking;
independent human inspection of the evidence is mandatory.

Required gates are `offline`, `coverage`, `core-package`, `security-failure`,
`owned-lifecycle`, `scientific-reference`, `scale` and `server-compatibility`.
Every gate must have an explicit state: PASS, FAIL, UNKNOWN, BLOCKED or NOT_RUN.
Missing gates become UNKNOWN. No gate may be dropped to manufacture eligibility.
PASS requires candidate-matched, SHA-256-pinned evidence; failed evidence can also
be retained. Duplicate gates/JSON keys, stale candidates and changed/missing/
oversized artifacts fail closed. Evidence contents and paths are not echoed.

Manifest structure (illustrative; replace the candidate and paths with actual
controlled evidence, do not use this as a claimed passing result):

```json
{
  "version": 1,
  "candidate": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "checks": [
    {"gate": "scientific-reference", "status": "BLOCKED",
     "candidate": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
  ]
}
```

For PASS, add `"artifact": {"path": "relative/log.txt", "sha256": "<64 lowercase hex>"}`.
Paths must be relative, without parent traversal, absolute/drive syntax or symlink
components; each file is limited to 8 MiB and the manifest to 256 KiB. Supply a
trusted, stable evidence directory: this is not a secure filesystem snapshot or
defence against a privileged process replacing files during validation. Do not
put credentials or private prompts into the manifest or published logs.

```console
python tools/check_release_readiness.py --manifest evidence.json --evidence-root /explicit/owned/evidence --candidate <full-40-character-Git-SHA>
```

Exit 0: all supplied gates PASS with matching artifacts, **eligible for human
review only**. Exit 3: well-formed evidence has incomplete/nonpassing gates.
Exit 2: invalid/unavailable/mismatched evidence. `publication_authorized` is always
false. This tool never runs scientific submissions, follows URLs, contacts services,
changes files, launches a server or publishes a package. It is not part of the wheel.

## Scientific, failure and scale gate ownership

The scientific-reference gate needs frozen admitted model/input/source contracts,
independent values with justified tolerances, mask/zero/category/unit/geometry/time
checks and explicit fidelity limits. A parser success, DataFrame or constant
client fixture is not sufficient for a broader scientific claim. Attach new real
references rather than reclassify historical UNKNOWN/failed experiments as passes.

Failure/lifecycle evidence must cover permission denial, unavailable Jobs, ambiguous
mutations without replay, origin-bound credentials, local versus remote cancellation
and cleanup of owned state. Scale evidence must state hardware, input size, declared
budgets/concurrency and bottlenecks, without extrapolating tiny-grid latency into
an SLA. Server compatibility must identify the tested build/worldview/provider tuple
and allowed scope; no universal cross-version guarantee is inferred.

## Release and support decisions remain human

Maintainers must independently review evidence and choose the supported feature/
environment scope, version and release notes, changelog/deprecation policy,
security-reporting channel and support owner before publication. These are proposals
pending project authorization, not an assigned maintainer commitment or SLA.
Preserve failed runs and exact source/version provenance. Material protocol,
mask/semantic or support-scope changes need explicit review and migration notes.

This tooling can be completed/merged while actual release acceptance remains
BLOCKED. Do not call the whole roadmap complete, sleep the computer under a
completion condition, or publish merely because the offline checker exits zero.
