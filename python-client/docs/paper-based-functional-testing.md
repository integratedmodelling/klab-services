# Paper-based functional testing: first research batch

Reviewed 31 October 2026. “k1” is interpreted as the earlier k.LAB/ARIES
generation. This is an initial selection, not an exhaustive bibliography. A
paper's date does not establish its exact k.LAB binary version. The 2014 ARIES
paper is a methodological predecessor, not asserted to have used k.LAB v1.

## Current conclusion

**No paper experiment has been reproduced.** The hosted inventory exposed 54
distinct model URNs but did not establish the papers' trained models, original
resource versions or independent reference outputs. Runtime did not advertise
native numerical export schemata. Do not import projects, alter worldviews,
retrain algorithms or substitute synthetic elevation and call it replication.

Existing Python interfaces support useful infrastructure: semantic discovery,
model inspection, explicit grids, independent runs, indexed cells, recorded
execution evidence, reference comparisons and advertised native exports. Client
API availability is not proof the scientific models/data are deployed.

## 1. Sicily wildfire risk: strongest first migration candidate

**Marquez Torres et al. (2023), “Fire risk modeling: an integrated and data-driven
approach applied to Sicily.”**
[Primary full text](https://nhess.copernicus.org/articles/23/2937/2023/),
[DOI](https://doi.org/10.5194/nhess-23-2937-2023),
[supplement](https://doi.org/10.5194/nhess-23-2937-2023-supplement).
The methods explicitly describe k.LAB, k.IM and ARIES.

- **Experiment:** wildfire activity in 2007–2020; learned hazard combined with
  exposure/vulnerability; current versus future climate, including 2050 RCP8.5.
  The reported 45% of vulnerable areas with high fire probability in 2050 is a
  regional aggregate, not the expected value of a tiny tile.
- **Artifacts inspected:** [Zenodo 7616451](https://doi.org/10.5281/zenodo.7616451),
  [record metadata](https://zenodo.org/api/records/7616451) and
  [source tag v0.1](https://github.com/amarqueztorres/fire_risk_sicily/tree/v0.1).
  The record describes a 14,256,699-byte ZIP, MD5
  `6afd070ac6127da3ca7d0c1193fbe2af`. Metadata/source text were inspected, not
  installed or executed.
- **Verified model prerequisite:**
  [`ignition.kim`](https://github.com/amarqueztorres/fire_risk_sicily/blob/v0.1/src/fire/sicily/ignition.kim)
  references `local:amarquez:im.fire.sicily:fire.ml.ignition.bayes.fireml`, weather,
  land cover, distances to roads/settlements/protected areas, slope and elevation.
  A public source tag does not prove that local learned resource resolves for
  our principal on the new Java stack.
- **Proposed test:** one author-approved tile/date/model snapshot; compare mask,
  probability cells in [0, 1], category labels and sample coordinates against
  independently exported original results. Then compare separately specified
  current/future inputs, not arbitrary observer labels. Require recorded model
  association, units and compatible native exports before interpreting changes.
- **Blocked by:** trained resource, compatible ontology/model migration, fixed
  dates/input checksums, reference raster/tolerances, native export readiness and
  a new bounded workload agreement.
- **Rights:** article CC BY 4.0. Zenodo software metadata literally reports
  `apgl-v3`; confirm repository licence and each dataset's terms before copying.
  No paper source/data is vendored here.

## 2. South African household resource dependence

**Balbi et al. (2019), “Human dependence on natural resources in rapidly
urbanising South African regions.”**
[Primary full text / DOI](https://iopscience.iop.org/article/10.1088/1748-9326/aafe43).
Section 2.2 explicitly identifies WEKA integrated into k.LAB; Figures 4/5 show
k.LAB results. The exact k.LAB release remains unverified.

- **Experiment:** derive dependence on five provisioning services from 2011
  census wards; WEKA BayesNet/K2 with ten-bin discretisation; 2011–2036 population
  scenario; reduced two-input model for eThekwini/Cape Town data-scarce settings.
- **Published independent checks:** Table 3 has 95 correct/8 incorrect training
  instances, 70 correct/33 incorrect in ten-fold cross-validation, and 6 correct/
  4 incorrect validation instances. These have different denominators; do not
  turn them into one interchangeable accuracy claim.
- **Proposed test:** fixed original ward/cell subset, metric inputs and trained
  model; verify fraction versus percentage scale, ward IDs, no-data and ten-class
  boundaries. Compare full/reduced-input outputs to original independent exports.
  The population scenario also requires the original demographic inputs.
- **Blocked by:** model/resource URNs, compatible trained model, versioned census
  and geometries (103 wards in 2011 versus 113 in 2017), original folds,
  seed/version and reference outputs. Do not expect a new tiny refit to reproduce
  the publication's aggregate accuracy.
- **Rights:** article CC BY 3.0; openly accessible census data does not establish
  blanket redistribution rights for every input and model artifact.

## 3. ARIES source–sink–use benefit flows: predecessor

**Villa et al. (2014), “A Methodology for Adaptable and Robust Ecosystem Services
Assessment.”**
[Primary full text / DOI](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0091001).
Do not label every earlier ARIES application as a verified k.LAB v1 experiment.

- **Experiment:** spatial service production, flow and use, with applications
  including Puget Sound/Madagascar. Sources, sinks, beneficiaries and transmission
  distinguish potential/actual benefits. A land-cover match alone is insufficient.
- **Proposed test:** a small original spatial tile or independently specified
  flow network with fixed sources/sinks/uses/routing; compare the named benefit
  outputs, units, masks and paths against original reference exports. Apply
  conservation/inequality assertions only when justified by that model, not as
  universal rules over all ecosystem services.
- **Blocked by:** original model/library version, input bundle, current-Java flow
  algorithm availability, reference numerical oracle and reviewed migration.
- **Rights:** primary page identifies paper CC0; external datasets and software
  dependencies are not automatically licensed by that dedication.

## Suite plan: do not inflate passing coverage

| Planned regression | Existing infrastructure to reuse | Current status / concrete unblock |
| --- | --- | --- |
| Sicily tile and current/future comparison | `provider-reference`, independent milestone fixture, inspection and recorded association | **Blocked; not implemented:** trained resource, reference exports, fixed inputs and deployment verification |
| eThekwini dependence/classification and reduced-input comparison | Independent runner and scientific reference checks | **Blocked; not implemented:** trained model, folds/ward versions and reference outputs |
| Original source–sink–use flow | Inspection, explicit contexts, indexed/reference outputs | **Blocked; not implemented:** reviewed original model migration, inputs and independent oracle |

No placeholder/always-skipped paper tests are registered. No scientific
algorithm was ported into Python. Once a compatible fixture exists, implement
one small opt-in case: explicit workload, recorded model/result association,
units/coordinates/mask, independently specified values/tolerance and owned
cleanup. Register it in `run_functional_acceptance.py` and test consent routing.
Absent fixtures must never create green scientific acceptance badges.

## Actual findings in this pass

- [Four earlier exact hosted compatibility checks passed](results/hosted-worldview-compatibility-20261031.json).
- [New runner acceptance failed](results/hosted-runner-acceptance-20261031.json):
  first sequential case, three timed-out numerical waits and one context Job
  failure, seven Job handles, zero processor-validated numerical cells. Two-worker
  and sibling-failure cases did not run. Later FINISHED statuses did not pass the
  test. All retained test-owned context/session releases returned true.
- [Metadata snapshot](results/hosted-capability-summary-20261031.json): 54 visible
  distinct model URNs; Resources semantic search advertised; Runtime only
  `component.jar` export schemata. Visibility is not global completeness or
  paper-model executability.
- No automatic rerun, paper-scale simulation, service administration or Java,
  component, ontology, worldview or scientific-algorithm source change occurred.

The user replaced email delivery with the standalone portfolio progress page.
Its offline saved-evidence notebooks/videos are presentation, not new live
scientific executions. Preserve that label alongside every demo.
