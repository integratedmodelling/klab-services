# Java Service Gap Register

This is the permanent contract/governance inventory, **not a Java implementation**.
Python implementation, simulation, observed Java compatibility and end-to-end
science are independent. No production SDK dependency, proxy or fallback is added.

The active full-roadmap directive now includes C. Earlier audit exclusions/stop
decisions below describe that historical review only, not the current execution
scope. Maintainer-facing seed and generated all-gap appendix:
[`JAVA_GAPS_FOR_FERDINANDO.md`](JAVA_GAPS_FOR_FERDINANDO.md). `govern.py generate
--check` verifies its inventory/status counts against expanded YAML in CI. Every
new/materially changed simulator contract must update its stable gap and this
companion in the same feature PR. This is a repository attachment, not claimed sent.

## Canonical data and evidence

- `gaps.yaml` is canonical JSON-compatible YAML. `defaults` explicitly supplies
  shared fields; the validator expands each gap before JSON Schema validation.
  Gap-specific overrides win. Unknown dates/builds/routes/schema references are
  `null`, never invented. `last_verified_basis` says whether this is source/evidence
  inspection rather than a fresh Java run.
- `schema.json` validates the expanded registry and structured acceptance records.
- `id-reservations.json` is append-only. Never delete/reuse an ID: retain CLOSED or
  OBSOLETE entries. Legacy aliases preserve discoverability; Python bug fixes are
  not recast as unresolved Java gaps.
- `JAVA_GAP_STATUS.md` and `CONTRACT_MATRIX.md` are generated from canonical data.
- `acceptance-audit.json` contains every original requirement verbatim with separate
  public APIs, paths, PR/provenance and unit/HTTP simulator/real/science evidence.
- `issues.json` preserves reviewed public issue bodies, owners, labels, comment
  references and mapped acceptance conditions. `ISSUE_RECONCILIATION.md` is generated.
  `issue-actions.json` records actual idempotent actions; absence is not closure.

Confidence: JAVA_OBSERVED = attributed actual Java observation; HARNESS_EVIDENCED =
source/harness proof only; SIMULATED_PROVISIONAL = finite proposed substitute;
JAVA_INCOMPATIBLE = attributed observed divergence; NOT_EVALUATED = no contract
comparison. A native source symbol or absent local setup is not proof that every
Java installation lacks a capability. Inspect staging reference and source-vs-wire
distinctions before proposing another simulator feature.

## Execute and validate

Schema-only development dependencies: `python -m pip install jsonschema pytest`
(outside SDK). Executable cases additionally use the installed ordinary SDK,
separate simulator and selected analysis/tabular/notebook/local test extras; the CI
workflow installs these explicitly. Historical native TIFF inspection requires
Rasterio and is never skipped to manufacture a passing evidence matrix.

```console
python developer-tools/java-gap-governance/govern.py validate
python developer-tools/java-gap-governance/govern.py generate --check
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --output gap-results.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --output real-gap-results.json
```

The simulated command executes each gap's linked, existing bounded pytest cases
once, using the production SDK where applicable. It rejects skipped or failed
cases. Some are honest refusal/MockTransport/source boundaries rather than a full
HTTP scientific journey; their assertion scope is explicit in the registry. They
test the substitute/guard, **not the unimplemented Java guarantee**. Detailed HTTP
journeys, failure/cancel and owned cleanup already live in the separate simulator.

Every gap has a unique executable case ID and linked tests. Current real-mode
commands are deliberately unset: missing remote contracts, unsupported requests,
custody/role approval or a pinned producer fixture cannot be invented. Real mode
therefore emits **REAL_NOT_RUN and exit3**, not a green Java result, and never calls
the simulator. Adding a future maintained real command requires a reviewed adapter
using the same bounded request/semantic assertions where the native contract exists,
pinned build/identity/fixture provenance, explicit authorization and independent
cleanup. The present runner does not execute arbitrary commands from registry data.

Tags are SIMULATED_PASS, REAL_PASS, REAL_FAIL, REAL_NOT_RUN and CONTRACT_DRIFT.
Historical captures retain their own result and build separately from **current**
REAL_NOT_RUN. A validation/test job passing says the separation is enforced; it
does not turn the real matrix green. The CI workflow retains both JSON matrices.

## Lifecycle and upstream handoff

DISCOVERED → REPRODUCED → SPECIFIED → JAVA_IMPLEMENTING → REAL_VERIFIED → CLOSED;
CONTRACT_DISPUTED, OBSOLETE and BLOCKED branches remain explicit. Simulation can
reach SPECIFIED only. JAVA_FIXED_VERIFIED/REAL_VERIFIED/CLOSED require pinned actual
REAL_PASS evidence, not simulation or source inspection. Schema/cross-checks enforce
that boundary, but evidence integrity and scientific truth still require review.

To add a gap: reserve the next immutable ID, state the actual known route or null,
link exact original acceptance/issue/test/reproducer evidence, identify provisional
substitute behavior, and add resolution criteria. Avoid one alleged defect per
simulator test. Conversely, distinguish notification replay from committed graph
lineage, or scanner execution from exporter metadata, when resolution differs.

To resolve: run the same Python flow on a pinned real tuple, retain request/schema,
permission/lifecycle/cancel and applicable independent scientific oracle checks,
then update status/evidence/issues. Retire the capability's substitute/fallback
only through reviewed tests; production currently has **no automatic hybrid fallback**.
A distinct Java harness passing is not acceptance of the specific Python HTTP flow.

## Issue policy

Current full-roadmap classification lives in `full-roadmap-issues.json`, linked
from the preserved original issue inventory. Generated reconciliation shows all
actual action URLs, including subsequent scope corrections, rather than hiding
new comments behind the earlier audit marker. Issue127 now has a verified keep-open
update after workerPR268: C is in scope/partially implemented, not formerly excluded
as a prospective instruction, and its native/deployment obligations remain open.

The read-only audit found no safe immediate closure candidates. Live product bugs,
missing native APIs and outstanding explicit deployment/research/release gates remain
open. A tracking migration needs a persistent adequate canonical successor, no lost
conditions, permissions/policy and a truthful bidirectional comment. Never say
"fixed by simulator". Upstream #10 and services #144 remain product/acceptance tickets,
not duplicates to retire for a smaller count. Preserve labels/ownership/priorities.
Check existing action marker and issue state before posting; never double-comment,
close twice or automatically reopen historical tickets.

No roadmap C, study replication, Java repair, release tag/upload or develop promotion
is authorized by this register. See `POST_AB_DELIVERY_REVIEW.md` for the decision brief.
