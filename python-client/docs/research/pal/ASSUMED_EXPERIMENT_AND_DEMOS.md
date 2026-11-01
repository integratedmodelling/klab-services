# Combined operational-legend and assumed-scenario PaL programme

## Agreed direction and current state

The user selected combining preserved legacy categories with explicitly assumed
radiation interpretations, and wants a full experiment/demo/video programme.
The first milestone is an **adapted Radiation experiment**, not evidence that
the entire published study has already been reproduced.

Machine-readable contract: [assumed-experiment.json](assumed-experiment.json).
**Current state: SPECIFIED, NOT EXECUTED. No new video has been recorded.**
The contract's tests validate specification integrity, not a scientific run or a
runtime-enforced video-publication policy. No Java/shared-worldview change, remote
publication or producer contact is implied by choosing this direction.

## Faithfulness: separate the claims instead of abandoning the goal

We retain the goal of a faithful full-study reproduction. It must be built from
verified milestones rather than inferred from a successful small demonstration.

| Claim | Required evidence | Current state |
| --- | --- | --- |
| Faithful archived equations and parameters | Pinned source, tracked syntax changes, same formulas, 25 table and 0–1 fraction, independent numerical cases | Reviewed source/equation checks; not field execution |
| Faithful operational legend | Same ten labels/codes, nine overstory codes and exact equality; no guessed species/ancestor matches | Contract and original-input consistency verified; no runtime binding yet |
| Valid adapted-scenario computation | Actual Java outputs under recorded assumptions, pinned grid/masks/inputs, independent expected values and recorded model association | NOT RUN |
| Faithful original radiation/scientific interpretation | Original version and authoritative unit/time/taxon evidence | UNCONFIRMED; bypassed only for the assumption-labelled experiment |
| Faithful original published experiment | Compatible remaining models, original scenario/state/aggregation and independently comparable Puerto/published reference | BLOCKED; not established by this milestone |
| Client capability demonstrated using PaL | Actual SDK notebook actions and their observed results, identity/permission/error checks | Existing capabilities available; PaL demonstration not yet executed |

“Valid” in an early video must mean “numerically verified for these stated
assumptions”, not validated ecological prediction or agreement with the original
study. A beautiful map, FINISHED Job, model discovery result or passing parser
is not numerical/scientific validation.

Working title: **PaL Radiation: real inputs, archived equations, explicit assumptions**.
Opening card and captions must identify the adapted scope. The intended eventual
full-study title can be used only after its separate acceptance gates pass.

## Combined experimental design

### 1. Preserve legend identities; do not repair taxonomy by guesswork

Retain Calluna, FestucaRubra, Helictotrichon, Phragmites, Polypodiopsida, Senecio,
UlexGallii, Carex, Gramineae and UlexEuropaeus as the original **operational legend
entries**. In this adaptation they do not assert observed species, accepted TAXA
identities or global biological classifications. Retain the Festuca/Agrostis
discrepancy as provenance, not a silently corrected label.

Both input layers use the same entry identities where codes coincide, while their
main/overstory roles stay distinct. Overstory has no code 21. Require all 90
valid code pairs: nine equal and 81 unequal. A grass species and the grass-family
entry remain unequal for the archived rule despite taxonomic relatedness.

This deliberately narrows the unresolved taxonomy requirement for the adapted
calculation. It does not approve the earlier nine biological concept mappings or
claim semantic equivalence with the full original ontology. Use separately
reviewed project-local operational definitions, not global `equals` aliases or
changes to the shared worldview. These definitions are not yet authored/admitted.

### 2. Two explicit physical-interpretation scenarios

Both retain the archived 25 threshold as an **assumed daily** threshold and retain
the two archived expressions verbatim. Additional input normalization is a
declared adaptation upstream of those expressions, not a hidden equation change.

| Scenario | Assumption about served raw radiation | Declared normalization before the equations |
| --- | --- | --- |
| A: assumed daily mean | Raw values are MJ/m² for a representative day, averaged over May 2018 | Multiply by 1 |
| B: assumed monthly total | Raw values are May 2018 total energy in MJ/m² | Divide by 31 to obtain a representative daily value |

This is **sensitivity to different physical interpretations**, not ordinary
unit conversion. For a pure unit conversion both radiation and threshold would
need consistent conversion, and the dimensionless ratio should not change.
Here the same raw number is assigned a different physical support while the
assumed physiological daily threshold stays fixed; that is why outputs may differ.
Neither scenario is confirmed as the original producer's meaning.

Do not convert the advertised radiance `W.m-2.Sr-1` into energy by inventing angular
or temporal information. Do not overwrite raw TIFFs, insert units into them or
change native missing-value declarations. Preserve `fr` as a 0–1 fraction, not 0–100.

A scenario label in `Experiment.variation` is only a label. The server must
actually execute a separately registered scenario/model/input-normalization
binding, and recorded results must identify which one. Metadata-only renaming or
postprocessing one result twice cannot demonstrate Java scenario execution.

### 3. Real, bounded fixtures with both branches

The first real radiation crop has 36×36 native cells; QA at 324 native main-layer
centers finds 288 jointly valid inputs, all equal main/overstory codes, and 36
missing-input locations. It cannot by itself exercise attenuation.

A second real crop was now retrieved from the same named production coverage at
requested lon -3.670..-3.664, lat 43.482..43.488. It has 22×22 float32 native cells,
484 valid radiation samples, 4532 bytes, SHA-256
`f10c8851df183043a5a16028299fb59f77f56ad46ecffdb1c3a654d7d46fa7e6`.
Actual bounds/transform and endpoint receipt are retained externally.

At 121 main-cell centers within this second crop, input QA finds 47 jointly valid
locations (44 equal codes, three unequal code pairs 21/7) and 74 missing-input
locations. The native radiation crop contains 100 valid zero samples; none is at
a jointly valid inspected main-cell center. Do not count that as end-to-end zero
output validation or treat valid zeros as nodata. A separate declared control is
still needed if the scientific acceptance contract requires a zero case.

These are **input coverage checks**, not approved output interpolation, computed
model results or a global assertion that masks/transforms coincide. Freeze the
target grid and continuous/categorical resampling policy before references/run.
Native file names containing “50m” do not establish the paper's exact target grid.
Preserve masks and unknown-code failures; do not discard inconvenient locations
solely to make the comparison pass.

### 4. Reference and first-run acceptance

- Reuse the existing archived expressions, scalar known answers and mutation
  checks. Do not port the full Puerto model into Python.
- Independently prepare expected fields under the same declared input
  interpretation, target grid and mask policy. Use the independent Puerto source
  and justified analytical expectations; prefer executable original R reference
  when available. Without it, label the validation as equation-fidelity checking,
  not a completed Puerto run.
- Freeze output dtype, tolerance and every unit/grid/mask expectation before Java
  result retrieval. Wrong metadata, legend identity or coordinate orientation must
  not be absorbed by a loose value tolerance.
- Require actual selected-model/result association and successful recorded
  numerical activity, not just a resolution plan. Disclose partial provenance and
  unavailable durable-root evidence.
- First attempt is sequential, four bounded scenario×patch contexts at most;
  up to three requested outputs per context (12 output Jobs), plus at most four
  context-observation Jobs (16 total). Choose existing Client context reuse if
  requesting all three outputs; independent ExperimentRunner cases create their
  own contexts and must not be misrepresented as sharing one.
- The two patches are **not** a two-resolution experiment. Do not feed them to
  `validate_matrix` as if they were two resolutions of the same area. Any later
  scale test needs its own same-area grids, references and explicit budget.
- Never replay an ambiguous write automatically. Retain Job IDs/failure evidence;
  release only owned work with confirmed outcomes. No service changes to obtain
  a better recording.

## Reuse existing client capabilities, not a parallel SDK

| Demo need | Existing implementation to reuse | Boundary |
| --- | --- | --- |
| Connect/readiness/discovery | Connection/profile, SemanticExplorer, ResourceInspector; `tools/workflows/connection.py` and `resource_inspection.py` | Discovery/HTTP 200 does not prove executability |
| Typed requests and owned computation | Client/Context/ObservationRequest/Job; `experiments.py` for independent cases | Do not relabel a client-side parameter as a server scenario |
| Independent comparisons | RasterOracle and individual raster/evidence checks in `discover_compare_explain.py`; scientific-analysis helpers | Preserve strict gates; do not misuse its two-resolution matrix or fabricate reference values |
| Saved work/restart | Existing handles and `workflows/resume_saved_work.py`; `04_reopen.py` pattern | Fresh process must restore original IDs without another scientific submission |
| Export/analysis/provenance | Existing export discovery, indexed reads, xarray and execution-evidence APIs | Native GeoTIFF unit issue #10 remains; no Python metadata repair to claim success |
| Permissions/cleanup | Existing two-identity examples and owned-session release patterns | Only if two valid identities and actual authorization tests are available |
| Notebook/video capture | Percent-format examples and `record_notebook_demos.py` Jupyter execution/capture mechanisms | Author PaL-specific executable cells only after a runnable fixture exists; no placeholder scientific tests or fabricated output |

The older slide-based release rendering was rejected; it is not the template for
these videos. Existing synthetic notebook footage is not PaL footage. Source
examples and recorded artifacts must carry exact matching run/source identities.

## Full demo/video story

Planned chapters, not completed recordings. Each should show readable executed
Python, actual output and brief domain explanation—not test counts as the story.

| Chapter | What the viewer learns / why it matters | Evidence gate before calling it demonstrated |
| --- | --- | --- |
| 1. A scientific question and an honest reconstruction | Ask what the archived shading rule implies on real data, and why declared assumptions matter | Original pins/legend/equations shown; both physical scenarios visibly labelled; no claim of observed pasture growth |
| 2. Find and inspect the ingredients | Connect, inspect services/models/resources and see why the inputs are suitable or uncertain | Actual SDK queries and responses, explicit units/time/grid/masks and unresolved facts |
| 3. Ask Java for the Radiation result | Typed area/request, owned Job, readable result and modular dependencies | Authentic client-to-Java run; actual result identity, values, model association and independent checks |
| 4. Change an assumption, not the historical evidence | Compare A/B on the same selected patch and explain sensitivity | Two real backend computations with matching input/grid/reference identities; preserve raw bytes |
| 5. Show why the calculation can be trusted | Inspect equal/unequal categories, missingness, independent errors and negative controls | Per-cell/mask checks and all stated assertions pass; deliberately wrong cases are rejected |
| 6. Close Python and continue tomorrow | Reopen the same work and continue analysis without rerunning expensive computation | New kernel/process, original observation/Job IDs and values, no resubmission |
| 7. Use results in other tools and inspect their origins | Scientific selection/plot/export and recorded provenance | Actual export bytes/metadata verified; partial graphs labelled partial. If units fail, show failure honestly and leave export acceptance blocked |
| 8. Share responsibly and move toward the whole study | Authorized collaborator access, private-data denial, cleanup; next PaL stages | Actual two-identity controls/cleanup if available; future model stages visibly not yet executed |

Every video and notebook needs: run/scenario label, exact source/evidence identity,
scope/assumptions card, source attribution, no credentials/private configs, actual
execution, assertions, and decoded/readable output verification. Cuts for loading
are labelled; no timing/performance claim from edited footage. Publication remains
separate authorization, including third-party data rights.

## Full-experiment roadmap remains visible

1. **Foundation:** artifact/source fidelity, operational legend and explicit
   assumptions (this specification).
2. **Radiation:** unchanged-Java adapted three-model computation, numerical
   validation and complete client workflow demonstration (not executed yet).
3. **Remaining growth-limiting factors:** temperature, moisture and nitrogen with
   real compatible inputs, model dependencies and independent references.
4. **Vegetation dynamics / Puerto comparison:** temporal state, initialization,
   original R environment/site inputs, plot/time/mask alignment and justified
   comparisons. Current nonsemantic `number`-model limitation is a separate Java
   migration risk, not solved by the Radiation assumptions.
5. **Broader paper workflows:** livestock/management/nitrogen-cycle coverage where
   required to substantiate “the whole experiment”, independently scoped and
   validated. Do not imply that all 198 reported models run because three do.
6. **Full-study synthesis and recordings:** only promote fidelity claims supported
   by accepted scientific evidence; preserve mismatches and failures. P4 remains
   unaccepted until genuinely comparable independent results exist.

No new broad SDK feature expansion, global ontology rewrite or Java patch is an
implicit dependency of making an attractive demo. When such work is demonstrated
necessary, scope/review/authorize it separately. A faithful reconstruction is the
goal; an assumption-explicit adaptation is the current scientific claim boundary.

## Next implementation gate

Draft and separately review the minimum **project-local operational** concept and
model definitions, with no authoritative taxonomic aliases and no shared-worldview
changes. Freeze grid/mask/normalization semantics, verify actual input binding and
permissions, and establish an independent numerical fixture before live work.
Do not silently implement the earlier biological compatibility proposals under
this different operational-legend scope.

External new-input evidence:
`C:/Users/lumsd/AppData/Local/Temp/opencode/pal-assumed-demo-evidence-20261101/`.
Portable compact retrieval facts, hashes, query recipe, actual grids and input-QA
method/counts: [assumed-input-receipts.json](assumed-input-receipts.json).
These metadata facts do not include the raster payloads or establish redistribution
rights, immutable server hosting or a numerical reference result.
Prior review/retry inputs and failures remain unchanged. Contract tests verify
the specification; they must never be reported as a completed client demo,
Java-backed adapted experiment, validated ecological prediction or full-study reproduction.
