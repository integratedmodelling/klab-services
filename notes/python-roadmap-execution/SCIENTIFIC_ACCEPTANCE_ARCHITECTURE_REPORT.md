# Scientific Acceptance Test Architecture Milestone — 2026-11-05

## Executive outcome: MILESTONE_INCOMPLETE

**N and S each executed once against real Java and passed their bounded independent
oracles**, including N's numerical/geographic checks and S's actual private-principal
negative. The milestone is **not complete**: implementation contributions are
unpublished/unmerged under the preserved GitHub ref/rules blocker, and scheduled
L4 execution needs an authorized pinned Java worker. Existing full offline CI is
retained, not replaced by the feedback selector. No A+B or study completion claimed.

A scientist can demonstrate an asymmetric synthetic field's physical values,
coordinates, units and missing mask, and inspect source-justified active-model
selection with its actual result and a real authorization contrast. This is not
measured terrain accuracy, universal model ranking or published-study replication.

## Exact baseline, artifacts and contribution state

Accepted remote remains `937c758b62743a0ab68e48792870a9261cf7cc87` (#231), verified
read-only;31previous queue PRs merged/0open. Main prior checkpointfc5cca637 and all
seven prior local A+B candidates/union failures preserved. No reset or broad cleanup.

| Coherent contribution | Independent base / branch | Tested state / delivery |
|---|---|---|
| TA-1/2 inventory, contract/adapter |937c758b6 / feature/python-scientific-scenario-contract|Initial5af6668b1:3461installed/all17gates; laterc7bdc97fa:3462installed/all17gates; corrected6fdf97e28:43focusedPASS; corrected final installed run pending|
| TA-3 numerical N |937c758b6 / feature/python-scientific-scenario-n|Executed9c3d14878, docs95793070b;18focused,3421installed/all17gates and actualN PASS|
| TA-4 model-selection S |937c758b6 / feature/python-scientific-scenario-s|Executed20d3bc368, current62db4e3bc;167focused,3420installed/all17gates and actualS PASS|
| TA-5/6/7 selection/effectiveness/integration |937c758b6 / feature/python-scientific-test-selection|5049ae65f;14focused plus4measured suites PASS; local linked-artifact assembly failure corrected, final installed rerun pending|

All four have contributor-guide self-reviews, not invented human approvals. None
is published/merged or approved superseded. No cascading PR stack: N/S reuse the
adapter from its independent worktree, with explicit source/workflow selection.

Actual N/S installed noneditable wheel SHA256
`d9a1d5ff2ce33d41ab61f78260671e696cabbd50f12b309dccd77e3ee233280c`, built at5af6668b1.
Its SDK bytes equal both scenario source trees; the scenario test/tool sources are
separately identified. Temurin21.0.12.1+1, unchanged compiled Java hashes recorded.
Corrected **unpublished** geospatial component
`9844147666bbb53071dfcd54db284aef48be9c79315b73415e74af8c1d70126f`, generators
`e2190a05eb478f4e8859faeddf5d25935bf53de0aaf12f135652e6e23c37aa03`.
No component#11 adoption, Java/ontology/worldview/model/research-input changes.

## Existing architecture: retained, reused, not replaced

TA-1 family inventory (`python-client/docs/testing/scientific-acceptance-inventory.md`)
is on the contract branch until delivery; its machine map preserves all24roadmap+
18capabilityIDs. Expanded per-module inventory contains156modules:109unchanged
modules matched to measured baseline JUnit durations,47unmeasured left unknown.
Case-duration sums exclude collection/process overhead; actual suite wall times are
reported separately. File hashes/source matches prevent assigning old timings to
changed tests. No flakiness rate inferred from one sample.

Retain unit/property/mock suites (L1), socket/files/process/private-kernel components
(L2), named actual contract workflows (L3), independent Java use cases (L4), future
original studies (L5). Historical research L1–L7 numbering is separate and unmodified.
Retain17coverage gates, Python3.11/12/13, core-only installation, Windows fixture
byte checks and optional-dependency boundaries. A lower pass never implies L4.

Reuse one owned stack harness, named functional runner, N verifier/oracle, S plan
extractor and existing request-to-plan test. **Zero duplicate Java fixtures/models
introduced; zero valuable tests deleted.** Four existing workflow/fixture components
reused. Separate fresh N/S stacks are intentional: N's Java producer is single-use.
No Java full-suite campaign, deployment abstraction, identity platform or Python
scientific replacement. New JSON schema/receipts are repository testing tools.

## Scenario N: nonuniform numerical/geographic result — PASS

Question: does Python request/retrieve preserve a real Java field's values and
location? Independent source is the pre-existing separately authored north-first
oracle, not the exported artifact or a Python roundtrip. SHA256
`f0848f723b485547c4b37f725e0e1aed63db1c29c49280481e7bcd0439ac9213`.
4x5matrix includes16777217.125,-100,-0.125,2.25,valid0 and two missing cells.
EPSG3857;100m cells; edges200000–200500/6000000–6000400; centres200050…200450 and
6000350…6000050. Native Java visits columns south-to-north; export is north-first.
Exact binary fractions permit exact float64/Decimal comparisons and catch float32
truncation, row flip, column reorder, zero-to-null and missing-mask corruption.

Fresh `ta-n-live-01`, executed source9c3d14878, observation15, actual model
`consolidation.fixture.elevation_of region-quantifier`, successful activities18/19/20.
Exact requested observation handle associated to recorded plan; unchanged Java
test source/class and model/fixture/build hashes retained.20cells/two missing/
one valid zero; native TIFF691bytes, SHA256
`fc442366732cb8fe47b050c76e7e4c53739a8d754527a0aeaca430f18f41fba0`.
All5checks PASS; five ownership axes PASS within the bounded tuple.

L2 controlled copies with missing/wrong units, bad CRS and missing nodata were
rejected; original native bytes unchanged. These are not Java-side negative
applicability checks. Owned scopes, captured processes/listeners cleaned; unrelated
Java unchanged. Partial plan/rootcommit unknown, single-shard, initialization-only,
not provider terrain accuracy/general GeoTIFF correctness. Published exporter's
historical failed representation remains preserved, not relabeled passing.

## Scenario S: source-justified selection and output — PASS

Independent contract reviewed **before** execution: existing `docs/KIM.md` scenario
activation semantics and unchanged model declarations. Lexical model literal66.5m,
active scenario literal234.5m, same Elevation-of-Region observable/context grid.
Expectations come from sources, not response/client behavior or a fabricated
ontology. Oracle hash
`cac9ade5cabef8b20ca5931659f4a9fda9ead39b1bbf9599fa3fa6cef05bf4c8`.
Contributor attestation is explicit; no forged maintainer/cryptographic signature.

Fresh `ta-s-live-01`, executed20d3bc368. Baseline observation15 retained original
nonuniform offsets; lexical33 returned66.5m for20cells and scenario51 returned234.5m
for20cells. Actual successful plan namespaces match each request; exact original
observation handles/context/focus/units/observable checked. Plans remain partial,
rootcommit unknown; no invented complete ranking explanation or lineage.

A distinct valid signed principal created/released its own positive-control session,
then private attach received **AuthorizationError**. One audited connect request,
zero scientific submissions/foreign mutations in denial phase. Existing local
forced-model/conflicting-option negatives and grid-normalization check retained.
All4expectations/five axes PASS; owned scopes/processes/listeners closed, unrelated
Java unchanged. No forced-selection API or hidden reasoning. The Region/Scientist
union failure is separate **BLOCKED contract**, preserved and not rerun/relaxed.

## Failure ownership and evidence integrity

No new Java/scientific defect demonstrated in N/S. Python contract/adapter defects
remain local FAIL and were corrected in bounded passes. An initial .gitattributes
edit displaced existing byte policies; restored immediately before final regression,
without changing ontology/model bytes. New oracle byte preservation is scoped to
the new file. Existing fixture hashes pin this Windows tuple; changed bytes fail
rather than normalize silently. Cross-platform L4 is not claimed.

A local receipt probe found NOT_APPLICABLE science could aggregate to PASS; fixed
to NOT_RUN with regression. Original N/S axes allPASS and are unaffected; no Java
rerun. Final selector integration caught a missing linked index/report in its own
worktree (1FAIL/3433PASS), corrected by assembling verified artifacts. These are
preserved local failures, not environmental excuses or relaxed tests.

[Indexed sanitized evidence](evidence/scientific-acceptance-index-20261105.json)
binds original execution/harness/workflow/compiled/fixture/component/oracle hashes,
actual identities, commands/start/end, per-check/axis statuses and cleanup. Raw
credential-bearing logs remain private; index contains only safe projections and
hashes. Supplemental workflow projection was built from original retained bytes,
**not a rerun**. N/S loaded adapter753b7629b; later reporting improvements are not
retroactively attributed to those runs. Known fixture tokens checked absent.

GitHub environment blocker: two preserved provenance-ref creation rejections,
InternalServerError then `commit_refs_rules`, exact ref/PR absent on readback.
No third unchanged push, renamed-branch bypass, rule/protection edit or invented
merge. Need restored normal evaluation or an explicitly authorized remedy.
Scheduled L4 worker/artifact provisioning is also unavailable; not a fake skipped
test or a reason to block independent local work.

## Selection safeguards, commands and measured cost

`python tools/select_scientific_tests.py --changed PATH` or `--capability CAP-10`,
`--git-base EXACT_SHA`, `--full`; execute printed pytest command for feedback.
Unknown/shared transport/DTO/scientific-value/selector/test/CI changes broaden.
Declared capability cannot suppress risky paths. Focused runs retain smoke and
origin/binding/lifecycle negatives. Mandatory existing full CI/coverage unchanged.
Java DTO changes require affected source/module gates, not automatic full Java.
Manual/weekly broad **offline** validation added; L4 on scheduled CI remains an
explicit provisioning gap, not an implied pass. Release still needs broader matrix.

Same source5049ae65fc927001db7fc9acb5de66fb9e5da1d7/interpreter, serial runs,
no coverage on either side;0failures/errors/skips:

| PR-like impact | Executed tests | Measured wall seconds | Selection |
|---|---:|---:|---|
| Full offline baseline |3434|72.0315|Full unit/integration|
| Python documentation |351|2.0636|Focused smoke/negative/map checks|
| Scientific-data DTO decoding |3434|67.4729|Conservative broad|
| Java API/DTO |3434|67.6977|Conservative broad plus Java-contract obligation|

One measured selector-only invocation per dry-run:0.0736/0.0707/0.0708seconds
respectively, not a latency distribution. Docs sample has lower measured feedback
cost; the first baseline overlaps approximately13seconds of N's stack tail, so this
is not a quiescent-machine causal speed benchmark. Broad-run time variation is not
a productivity gain. **Zero Java families
executed in either baseline or selected offline samples**, so no measured reduction
in irrelevant Java churn. Actual scientific executions2/pass2, flaky reruns0.
No architecture-wide speedup inferred. Required CI still bears full validation cost.

## A+B deltas, remaining worktrees and final gates

Add scoped N/S L4 evidence to RC-P2.1/P2.5/CAP08/09/15 and RC-P1.5/P1.3/CAP05/06;
client implementation, real Java contract, independent result and remaining external
gates stay separate. **No primary status promoted.**23in-scopecriteria remain
1accepted/20partial/1missing/1blocked,+TargetCexcluded;18CAP4accepted/13partial/
1excluded. Broader provider/time/category/history/revision/import/managed/release
gaps remain. Luxembourg/Kaduna/PaL remain historicalL3PARTIAL/L4BLOCKED/L5–L7NOT_RUN;
no original-study execution, tolerance fabrication or artifact sweep.

Owned core retains compiled outputs on S branch; separate lightweight contract,
N and selector worktrees preserve their independent candidates. No branch retirement
before eligible merge/postmerge proof. Cleanup verified14owned process identities
absent; no services/kernel/windows/power helper created for ongoing work. No unrelated
terminal/service stopped. Final installed per-candidate runs are in progress at
this report revision; no success predeclared. Final merge gate remains BLOCKED.

## Five prioritized next steps

1. Restore normal repository ref/rules evaluation or authorize a specific remedy;
   publish four qualified contributions and verify required checks/merges/ancestry.
2. Provision an authorized pinned scheduled Java worker/artifact tuple; run N/S as
   a real periodic gate, retaining failures/unknown states and cleanup evidence.
3. Extend independent provider/multishard/time/category oracles only with genuine
   supported contracts; settle union semantics separately without changing this oracle.
4. Obtain Runtime history/revision/replay and managed-principal cleanup contracts,
   plus Resources import/edit/publication semantics/permissions for broader A+B.
5. Obtain authentic original model/input/reference/geometry/units/rights bundles,
   then approve later genuine study replication/release scope; never use synthetic
   engineering PASS as a published scientific result.
