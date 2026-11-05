# TA-1/TA-2 contribution review — initial development pass

Guide: `docs/contributor-guides/KLAB_CLI_Contribution_Review.md`.
Contributor self-review, not independent maintainer sign-off.

**Recommendation: Needs bounded implementation correction / validation.**
Base937c758b62743a0ab68e48792870a9261cf7cc87; independent branch
`feature/python-scientific-scenario-contract`. No PR/merge created. Recorded GitHub
ref/rules creation blocker remains unresolved; no workaround attempted.

## Scope and scientific boundaries

Retain the existing pytest families, owned Windows harness, N verifier and S plan
extractor. Add repository-only JSON validation/receipt functions and a thin adapter
to that harness. No SDK/Java/ontology/model/component changes, new dependencies,
scientific inference, deployment abstraction or public API. Source-derived selection
claims cannot turn mocks into L3 or pass a scientific check that did not run.
Existing synthetic matrix and scenario constant are selected, not reauthored.

## Findings / unfinished work

- 35 focused L1 tests pass, including hash tampering, missing source/input, traversal,
  duplicate/nonfinite JSON, unsafe combinations, L5 source refusal, incomplete or
  credential-shaped receipts, separate unknown/failed axes and42-ID preservation.
- Runner still needs fixture-level tests for its wheel/source identity check,
  failure-time receipt path and sanitized evidence projection. Its budget declarations
  must be demonstrably consistent with existing workflow limits before live use.
- Family matrix preserves conservative test-boundary defaults and gaps, but detailed
  module discovery/current measured durations remain to be captured. Historical
  D6/D7 durations explicitly are not comparative effectiveness measurements.
- Current N and S workflows do not yet emit explicit expectation/axis records.
  Adapter intentionally marks missing checks NOT_RUN, never L4 PASS from harness exit0.
- Startup failure before scientist issuance needs an honest unavailable-identity
  record; unavailable fields must not support passing scientific acceptance.

These are local engineering obligations, not external blockers. Do not call TA-2
complete or stop independent work because they are unfinished. No new Java run
performed in this pass. Real N/S acceptance, selection, integration and report remain.

### Second bounded pass

41 focused tests now pass, including controlled adapter projection, missing-input
preflight, absent-check NOT_RUN instead of L4 promotion, unclassified harness-failure
receipt, and exact wheel/installed/source-byte matching. Ruff clean. Context/job
budgets below the existing adapter's requirements are refused; actual elapsed time
is recorded and an over-budget run fails after existing bounded harness cleanup.
The harness retains its own startup/import/functional deadlines; no second service
or process manager was introduced. This is not a new hard overall watchdog.
Remaining validation: installed suite/coverage, detailed discovery inventory, live
N/S explicit expectations and failure receipts on actual unavailable prerequisites.

### Installed result and independent-branch adapter

Candidate5af6668b14e13418b2bca2182db082503682a6d7:3461installed tests/all17gates/
build/install/lint PASS,78.46s offline suite (not total gate duration). Wheel SHA256
d9a1d5ff2ce33d41ab61f78260671e696cabbd50f12b309dccd77e3ee233280c.
Added explicit workflow-package selection restricted to server-repo/python-client
so independent N/S branches can reuse the thin adapter without a cascading PR stack;
41focused tests stillPASS. This adapter delta is not covered by the earlier whole
suite receipt; distinguish its source hash and subsequent focused check.

### Actual N/S reuse and inventory expansion

Both independent accepted-base scenarios ran once through adapter753b7629b and
passed explicit real-Java expectations/cleanup. Later adapter reporting adds a
whitelisted workflow projection, loads failed workflow reports if the harness did
not aggregate them, and records its own source hash at invocation. These changes
are not retroactively attributed to the original N/S receipts; supplemental indexed
evidence binds the original raw workflow report and its exact hash without replay.
Per-module inventory now expands156testmodules, with109unchanged-source modules
matched to actual baseline JUnit timings,47unmeasured modules left unknown.42focused
tests PASS before final installed suite. Scientific PASS comes from explicit N/S
oracle assertions, not these42unit tests.

## Validation and compatibility

Command: installed WindowsPython3.12 `pytest -q tests/unit/test_scientific_scenario.py`:
35passed; Ruff E9/F63/F7/F82 clean. No hosted CI, installed full-suite or merge
acceptance inferred. Existing coverage, version matrix, core-only and Windows gates
unchanged. Credential-shaped output refusal complements—not replaces—known-secret
checks; raw service logs stay private and only their hashes enter sanitized summaries.

## Next actions

Test/fix the adapter within scope, capture inventory discovery and bounded durations,
pin input/oracle references, run installed gates. Then normally publish/review/merge
if repository ref evaluation is restored; otherwise preserve this independent
candidate and execute N/S changes on separate accepted-base branches, associating
their receipts without a cascading PR stack. No branch protection/permission edits.
