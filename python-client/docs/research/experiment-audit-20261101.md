# Current-Java experiment audit — 1 November 2026

## Result and scope

**No authentic scientific reproduction has yet cleared the compatibility gate.**
This is a 49-entry screening ledger, not 49 completed execution attempts and not
proof that all experiments are impossible on every modern deployment. Cases with
missing artifacts or access-limited source inspection remain UNKNOWN/BLOCKED,
not demonstrated Java failures. No synthetic demonstration counts as a published
reproduction. Platform behaviors C01–C19 are not 19 additional published studies.

User authorized auditing the full list, logging shortcomings and trying bounded
unchanged-Java execution where prerequisites exist. No scientific/ontology migration,
Java changes, shared deployment changes or publication was performed. No eligible
candidate reached a live Job in this pass. No push or PR exists for this audit.
PaL remains preserved at `9b286f14b`; its failed/UNKNOWN evidence is not replaced.

## Version boundaries

- Python integration baseline: `6bcee78ee268fe321d2b417ee4403b8abfefa0b9`.
- Audit branch begins at `9b286f14b2b692c7c447d18067d46e4fc6ba89bf`; only this report
  and compact audit evidence are added, no production code changes.
- Current upstream `develop`, read from origin: `4d5d9028b4de4f4b04f7ad0d3228087208a01bfd`.
  Its `LanguageAdapter.java` Git blob is identical to baseline:
  `2a36df9e3058cdb2265112f1ef54c72ab9c8357d`. Other production files differ;
  this is NOT full build/runtime equivalence or deployment attestation.
- Existing unchanged compiled candidate: `a0a013a188fb752907f2d143a61b86f4a9cb0a0b`.
  No new service launch was justified by the failed artifact/compatibility gates.
- Parser tuples: existing G0-pinned k.IM snapshots 20261016-58 and 20261029-67,
  installed Temurin 21.0.12.1+1. The latter means current **local** dependencies,
  not verified latest upstream artifacts or the exact hosted deployment.
- Selected modern worldview: `d6c37c0b82afadd004d94bc5311983b66677532c`.
  Missing name matches are source-level findings, not live Reasoner rejection.

## What was actually checked

1. Preserved all 49 IDs from the supplied `ARIES_Roadmap_Revised.md` inventory.
2. Read prior PaL/SEEA and hosted acceptance evidence. Hosted model visibility and
   completed Jobs do not establish original paper inputs or numerical accuracy.
3. Fully enumerated 117 public repositories in the integratedmodelling Bitbucket
   workspace and 49 GitHub organization repositories at this inspection time.
   Visibility scope is anonymous/public or the existing GitHub principal, not
   proof of completeness across private/personal/other-host repositories.
4. Inspected metadata/source trees for promising Bitbucket repositories; requests
   eventually returned HTTP 429. Source metadata failures are preserved separately
   from scientific incompatibility. No throttling workaround or aggressive retry.
5. Rechecked six official publication index pages, all ten distinct official project
   pages backing P01–P11, and 16 selected publication articles. Crossref identified
   the exact urban heat/cooling DOIs; later Crossref requests also hit HTTP 429.
6. Independently recovered 45 pinned GitHub files, including 43 k.IM sources.
   Each file's SHA-256 and Git blob SHA-1 were checked against its tree. Raw
   third-party source/data remain external, not vendored into this repository.
7. Actually parsed all 43 selected sources under both Java parser tuples, then
   parsed all four unchanged scientific namespaces from the checksum-verified
   Sicily archive under both tuples. Positive controls pass in every run.

### Fresh parser matrix (same outcomes in both tuples)

| Pinned project | Inspected source scope | Parse pass | Parse fail | Scientific interpretation |
| --- | --- | ---: | ---: | --- |
| `im.aries.global` GitHub `f527195d9e7143e67e62e49b2d52b5cd653cc62f` | All 16 active src k.IM files | 4 | 12 | Three passing namespace shells have no model; the hydrology lookup's dependency table fails. Not a runnable global service bundle. |
| `esa.aries.peopleea` `ee4c167914f4041ca8d565564749d00d8f01da78` | All 16 active src k.IM files | 0 | 16 | Forest condition, variables, tables and supporting sources need migration. |
| `esa.aries.sandbox` `7112fe5317f7c0420b5aadbba2e6a6fec4570eee` | Seven selected inference/primitive-characteristic sources | 0 | 7 | Sampled subset fails; not a claim that every source in the large repo was parsed. |
| `klab.imod.showcase` `16daa78bf73ef60c3b631ce206b12150476cfba5` | Active scalar namespace | 1 | 0 | Stipulated synthetic 20 g, probability 0.25 and true. Useful engineering demonstration, not a listed scientific reproduction. |
| `klab.staging.vxii` `1fe0a13746cd6a4ad6965f5787574d04c60f9f93` | basic, dependencies, STAC basic | 3 | 0 | Test/demo sources; STAC source explicitly assigns wrong placeholder semantics to LULC. Do not execute that as scientific land-cover analysis. |
| Sicily immutable Zenodo `7616451`, source `24da506` | All four scientific src namespaces | 0 | 4 | Additional to the missing trained model and private WCS descriptors already found. |
| **Fresh total** | **47 source documents** | **8** | **39** | **Zero scientific reproduction passes.** |

Diagnostics are parser-recovery reports, not independent-defect counts. Harness
exit 0 means its report completed, not model acceptance. PaL's previous 12/12
scientific-namespace failures and SEEA's 4/4 selected-document failures remain
separate historical evidence, not silently reclassified as fresh tests.

The Bitbucket global-model head `97f2b27e58c376b9705d6bedd1b7eec0263894ec` differs
from the 2018 GitHub mirror. Bitbucket inventory was inspected, but the fresh
global parser results apply only to the pinned GitHub source. They must NOT be
advertised as a test of the newer Bitbucket files.

## Complete shortcomings ledger

`SOURCE_FAIL` = an identified source/selected exemplar actually failed parsing;
it is not proof all possible implementations of that case fail.
`ARTIFACT_GAP` = original executable model/input/reference not recovered.
`ACCESS_LIMITED` = an attempted relevant source inspection hit HTTP 429.
`REFERENCE_GAP` = observed software/data do not yet supply an independent oracle.
`CONTRACT_GAP` = scientific meaning/runtime behavior remains unverified.
Every row is scientifically **BLOCKED**, not REPRODUCED.

### R — 19 published/application research leads

| ID | Case | Current gate | Specific shortcomings and concrete unblock |
| --- | --- | --- | --- |
| R01 | Sicily wildfire 2023 | SOURCE_FAIL | Four original namespaces fail both parsers. Archived trained Bayesian resource is not bundled; 12 WCS descriptors point at private-network GeoServer. Need original trained artifact, predictor snapshots, compatible semantics/ML adapter and numerical reference tile; retraining is not equivalent. [E1,E5] |
| R02 | South African household dependence 2019 | ARTIFACT_GAP | Original BayesNet/K2 resource, folds/seed, ward-version geometry, population inputs and exported predictions absent. Training/CV/validation counts use different denominators. Country-support repo lookup was access-limited; it is not identified as the paper bundle. Recover exact bundle before compatibility claims. [E1,E2] |
| R03 | Eastern Madagascar water supply/quality | ARTIFACT_GAP | Original source–sink–use configuration, routing library, data and cellwise reference not recovered. A global water model is not the original Madagascar study. Recover exact 2014 predecessor assets and verify the flow algorithm on current Java. [E1] |
| R04 | Washington open-space value | ARTIFACT_GAP | Original proximity/beneficiary geometry, travel/flow rules, valuation coefficients and result grid absent. A distance or land-cover layer alone does not reproduce benefits/value. Recover original scenario and reference. [E1] |
| R05 | Puget Sound five-service flows | ARTIFACT_GAP | Published 16–66% delivered-supply range is regional and service-specific, not a tiny-tile oracle. Original sources/sinks/beneficiaries, routing and reference maps missing; current SPAN execution not demonstrated. [E1] |
| R06 | San Pedro ARIES versus InVEST | ARTIFACT_GAP | Carbon metrics are the most comparable published subset, but original scenario rasters, carbon parameters and reference totals remain unrecovered. Global carbon is not an interchangeable original model. Need archive before preferring this over SEEA on feasibility. [E1,E3] |
| R07 | Puget urban expansion 2016 | ARTIFACT_GAP | Publication lead verified; original urban-growth surfaces, baseline service models and per-scenario stock/flow outputs absent. Old flow-engine/version compatibility unknown. Recover scenario bundle and named reference outputs. [E3] |
| R08 | Southern Rockies preferences 2015 | ARTIFACT_GAP | Publication lead verified; original preference weights/survey-derived choices, biophysical grids and baseline output missing. Recreating weights would change the experiment. Recover both scientific and preference inputs. [E3] |
| R09 | Urban flood early warning 2016 | ARTIFACT_GAP | Exact publication lead found; trained spatial BN/CPTs, warning-response assumptions, exposure and validation outputs absent. Current probabilistic adapter unverified. Crossref lookup rate-limited; no invented DOI or model. [E3,E6] |
| R10 | Landscape aesthetics BN 2017 | ARTIFACT_GAP | Publication lead verified; elicited indicator weights, CPTs/trained network, geographic input snapshot and original score map absent. Generic recreation source fails, but is not asserted to be the exact paper model. [E3,E4] |
| R11 | Hotspot methods comparison 2016 | ARTIFACT_GAP | Publication lead verified; original service surfaces, hotspot definitions, thresholds/ranks and reference overlaps absent. A new threshold over arbitrary layers would not reproduce the comparison. [E3] |
| R12 | Trentino livestock/payments 2015 | ARTIFACT_GAP | Publication lead verified; farm/land-management inputs, payment rules, scenarios and service reference grids absent. PaL is not established as this study's model. Need original policy/scientific bundle. [E3] |
| R13 | Italian service changes 2022 | ARTIFACT_GAP | Official publication lead and relevant ISPRA repo exist, but source inspection was rate-limited. Historical input versions, change/valuation rules and published numerical outputs still unverified; Crossref lookup also limited. [E2,E3,E6] |
| R14 | Cantabrian Puerto/PaL 2023 | SOURCE_FAIL | Reviewed syntax-only Radiation derivative parses, unchanged PaL does not. Modern scientific mappings/time support/import unresolved. Original R site environment/data and S12 plot alignment absent; full-growth nonsemantic models rejected by adapter. Original vegetation rasters and independent arithmetic cases recovered, not a reproduction. [E7] |
| R15 | Urban heat resilience 2024 | ARTIFACT_GAP | DOI 10.1016/j.scitotenv.2024.171215 identified against official PII. Exact heat-supply/demand, health/exposure data, original model revision and output reference not recovered. Article date does not establish modern Java compatibility. [E3,E6] |
| R16 | Urban cooling / UGBI 2025 | ARTIFACT_GAP | DOI 10.1016/j.ufug.2024.128660 identified against official PII. Need original twelve-city predictors, fitted relationships, climate scenarios, model resource and cooling reference. No exact modern compatible source bundle yet. [E3,E6] |
| R17 | Tidal marsh adaptation 2024 | ARTIFACT_GAP | Official source describes hybrid process-based/ML model and eco-geomorphic feedbacks; exact trained model, elevation/tidal inputs, dynamic-state rules and scenarios absent. Marine repo inspection limited; no verified Java coupling. [E2,E3] |
| R18 | Virtual watershed coupling 2015 | ARTIFACT_GAP | Publication lead verified; original coupled solver, parameterization, forcing data, state exchange and reference hydrographs absent. A watershed concept is not an executing solver. Hydrology repo access limited. [E2,E3] |
| R19 | Agricultural trade-offs 2013 | ARTIFACT_GAP | Publication lead verified; original production scenarios, multi-service models and trade-off reference surfaces missing. Old platform identity/current Java support not attested. Recover assets before any replacement workflow. [E3] |

### C — 19 platform behaviors/model families (not separate papers)

| ID | Capability | Current gate | Specific shortcomings and concrete unblock |
| --- | --- | --- | --- |
| C01 | Riverine flood flow | SOURCE_FAIL | Selected global flood-regulation source fails parsing. Need actual supported hydrological routing/service-flow component, original supply/exposure masks and reference protected-benefit output. No claim about all possible flood models. [E4] |
| C02 | Coastal flood regulation | CONTRACT_GAP | Passing global coastalprotection source is only a namespace shell (43 bytes), not an implementation. Coastal model/data/reference and current algorithm support unverified. A parser pass cannot satisfy this capability. [E4] |
| C03 | Carbon storage/sequestration | SOURCE_FAIL | Selected global carbon and lookup table fail parsing; modern biological/land-cover classes, vegetation/soil inputs and reference stocks/fluxes missing. Total-stock addition is simpler than full service flow, but not yet a complete compatible fixture. [E4] |
| C04 | Sediment retention / reservoir benefits | SOURCE_FAIL | Selected global sediment model/table fail parsing. Need terrain/soil/cover/routing, reservoir beneficiaries and retained-vs-delivered reference, plus verified component. Raw soil loss is not reservoir benefit. [E4] |
| C05 | Freshwater supply | CONTRACT_GAP | One hydrology lookup parses but depends on a table source that fails; water/provision is an empty shell. Need demand, routing, water balance and independent flow quantities; current model resolver/component support unproved. [E4] |
| C06 | Scenic viewsheds | ARTIFACT_GAP | Original viewshed/beneficiary model, observer heights, DEM and visibility oracle absent. Existence of a geospatial component does not verify viewshed or service-benefit support. [E1,E8] |
| C07 | Open-space proximity | ARTIFACT_GAP | Need actual access-distance model, barrier/transport rules, beneficiaries and fixed reference. Generic recreation source fails; no exact compatible proximity experiment recovered. [E1,E4] |
| C08 | Subsistence fisheries | ARTIFACT_GAP | No matching executable supply-to-household pathway fixture, catch/use inputs or independent reference. Global mariculture source fails but is a different service, not proof about subsistence fisheries. [E1,E4] |
| C09 | Recreation | SOURCE_FAIL | Global recreation and lookup tables fail parsing; accessibility, landscape supply and beneficiary/use distinctions require real data and reference. Not interchangeable with aesthetic score or nearby green area. [E4] |
| C10 | Pollination baseline | SOURCE_FAIL | All selected global pollination sources/table fail parsing. Need flowering/nesting classifications, distances, weather, crop dependence and independent predictions. OBServ trained resources not established. [E4] |
| C11 | Automatic model selection | REFERENCE_GAP | Existing Java engineering/test evidence is not the requested real same-observable/different-place scientific fixture. Modern demos parse, but no paired original geographic model selection plus numerical oracle recovered. Do not count synthetic fixture classifications as scientific reproduction. [E4,E8] |
| C12 | Stochastic SPAN / uncertainty | ARTIFACT_GAP | Original SPAN implementation, supported current-Java component, fixed network/seed/distribution and uncertainty reference absent. Toy generated flow network would be a new test, not original replication. [E1,E3] |
| C13 | Scale sensitivity | REFERENCE_GAP | Prior constant elevation fixture is explicitly synthetic and failed native-export validation; not an ecological scale experiment. Need authentic fixed source/grid, two approved scales, aggregation rules and independently calculated results. Native export units defect remains. [E8] |
| C14 | Ecosystem/land-cover extent | CONTRACT_BLOCKED | Original sources fail. Follow-up syntax-only static subset now parses under both tuples, but current standard builder excludes table definitions and scope getReport() returns null. New upstream CORINE binding does not establish CCI2020 mapping. Real crop/km² oracle available; metadata defect retained. Java/component repair or an existing alternative compiler must be demonstrated. [E9,E10] |
| C15 | Forest condition accounts | SOURCE_FAIL | Entire recovered PEOPLE-EA active source bundle fails parsing. Need reference thresholds/normalization, masks and independently reproduced indicators/index; an average of arbitrary layers is not the original condition account. [E4] |
| C16 | Physical service accounts | CONTRACT_GAP | SEEA legacy reporting path, service models, spatial/year aggregation and units unresolved. No original table/reference fixture beyond prepared raw land-cover areas. Need compatible service computation and table contract. [E9] |
| C17 | Monetary service accounts | ARTIFACT_GAP | Valuation coefficients, currency/base year, beneficiaries and original tables absent; same SEEA reporting gap. Carbon price × arbitrary stock is not a verified monetary service account. [E3,E9] |
| C18 | Temporal fallback | CONTRACT_GAP | Official workflow documents fallback, but no compatible controlled real-year fixture with independently expected chosen source and provenance. Must test explicit nearest-year/tie/missingness policy, not silently substitute available years. [E3,E9] |
| C19 | Spatial-temporal interoperability | SOURCE_FAIL | PaL and SEEA source evidence shows unresolved legacy semantics and temporal mediation. Need separately authored compatible real models and a reference alignment; matching metadata/source equations do not establish live mediation. [E7,E9] |

### P — 11 active/historical project leads

| ID | Project | Current gate | Specific shortcomings and concrete unblock |
| --- | --- | --- | --- |
| P01 | World Bank Nigeria accounts | ARTIFACT_GAP | Official page still says state accounts report coming soon; original Nasarawa/Kaduna account bundle, boundaries and reference tables not recovered. Do not substitute global aggregates. [E3] |
| P02 | Global vegetation carbon 2001–2020 | ARTIFACT_GAP | Official dataset/report lead and legacy 0.11.0 `wb.cwon.aries` repo found; its src tree contains only a mangrove model. It is not established as the complete global-carbon generator. Postprocessing code is not Java scientific model execution. Need full original generator/input/reference-cell manifest. [E2,E3] |
| P03 | ARIES for SEEA country accounts | SOURCE_FAIL | Authentic general SEEA project recovered, selected sources fail. Country-specific model/data snapshots, permissions and original country outputs missing; a 324-cell raw reference does not reproduce Rwanda/Senegal/Nigeria accounts. [E9] |
| P04 | WEED extent dynamics | ARTIFACT_GAP | `esa.weed.aries` GitHub tree at 6958b00976c8b89770262bd540c4df024a533a8e contains no active model source, only project/empty resource documentation scaffolding. Official page reports selected-tester alpha. Related sandbox's seven sampled sources fail; full WEED source/data/reference not established. [E3,E4] |
| P05 | PEOPLE-EA / OpenEO | SOURCE_FAIL | Exact GitHub project all 16 active namespaces fail both parsers. Bitbucket sandbox has only locations.kim; not the full accounting pipeline. Need compatible OpenEO/resource bridge, thresholds, pinned inputs and independent accounts. [E2,E4] |
| P06 | OBServ pollinator scenarios | ACCESS_LIMITED | Official scope verified and `observ.sandbox` commit fd491374f87acd222f52aa6330ff094baf88f84d pinned, but source retrieval rate-limited. Statistical/mechanistic training data, fitted artifacts and held-out references not recovered. No Java incompatibility conclusion yet. [E2,E3] |
| P07 | ALICE four-country cases | ARTIFACT_GAP | Four public repositories identified; Spain vegetation files recovered earlier. Exact chosen service/scenario/model and independent output not established; PaL missing radiation endpoint and modern mapping issues cannot stand in for every ALICE case. [E2,E3,E7] |
| P08 | IHOBE / Urdaibai | ACCESS_LIMITED | Official Geoeuskadi/local-accounting project verified; exact study/repo association unestablished and candidate Basque repo inspection limited. Need bounded named model, data terms and original reference rather than project-level narrative. [E2,E3] |
| P09 | NBSOIL | ACCESS_LIMITED | Official integration scope verified; aries.nbsoil.sandbox pinned at af271aba69d87b231692cf4a056bd9e6244a0a46 but source fetch hit 429. Need exact SOM/structure/hydrology model, forcing/calibration and validation outputs; no current-Java verdict yet. [E2,E3] |
| P10 | MARBEFES | ACCESS_LIMITED | Official marine valuation/scenario scope verified; candidate marine-global repo pinned at 1a63d1702cda72a2f70e32b92fce39b06ecf8589, fetch limited. No exact demonstration site, model/input bundle or numerical oracle established; repo association itself not attested. [E2,E3] |
| P11 | LIFE PYRENEES4CLIMA | ACCESS_LIMITED | Official air/water case-study scope verified; Pyrenees repos pinned at 4789a60e0f4b3b99f29c86e7a012c83d42c55891 and 267ee19766322d4482c90844f372d74ea88a6a99, fetch limited. Need an actual selected predictive case, original input/output and component requirements. [E2,E3] |

## Least-bad options, not acceptance claims

1. **C14: single-year land-cover extent subset.** Strongest already recovered real
   data plus independent numerical oracle; avoids full IUCN classifier and dynamics.
   Exact blockers: approved classification/authority bindings, modern static-table
   path, metadata/permission contracts. Follow-up E10 demonstrates missing table
   handling in the inspected standard Java path; an alternative existing compiler
   or separately scoped Java/component work is required. Cannot call Python areas
   a Java-produced SEEA account.
2. **R14: narrow adapted PaL Radiation.** More traceable than unrecovered studies:
   source transformation and equation checks already reviewed. Still missing
   approved scientific mappings/time support and authoritative radiation payload.
   Full vegetation/R/P4 is much larger and is NOT this option.
3. **C03: bounded carbon stock model family.** Smaller numerical structure than
   flow models; parameter/input and semantic scope still substantially unverified.
   A generic global carbon example would not be San Pedro or P02 replication.
4. **R06 carbon only if a named paper comparison is mandatory.** Original archive
   recovery is a prerequisite, so it currently has less demonstrated readiness
   than the above options despite the attractive ARIES/InVEST comparison.

These are qualitative priorities based on demonstrated prerequisites, not invented
effort estimates or guarantees that no Java change is needed. Uninspected/newer
sources could outrank them once access and exact artifacts are established.

## Access follow-up (earlier 429 failures retained)

A single useful NBSOIL tree request after the independent GitHub/publication work
succeeded. A low-concurrency source-tree follow-up then recovered complete inventories
for NBSOIL (11 files), Pyrenees (17), Pyrenees MEG (one 25-byte source), OBServ
(eight), older SEEA EEA (eight), and ALICE Spain (19). The marine repository exists
but its `src/` URL returned 404; that does not prove the repository or models absent.
Remaining country-support/Basque/hydrology/Sicily/ISPRA/HeCo tree requests again
returned 429. Actual model bytes from these newly inventoried projects have not
yet all been recovered or parsed. Their ledger access limits therefore refer to
the **remaining source/compatibility inspection**, not a claim that no tree was found.
See `bitbucket-final-source-recheck.json` and `bitbucket-source-followup.json`.
The subsequent bounded model-byte recovery stopped on its first request when
NBSOIL health source returned 429 again, with no Retry-After header. No uninspected
source was counted as parsed; see `bitbucket-source-recovery-followup.json`.

Additional GitHub searches for urban cooling, NBSOIL, San Pedro, Puget and Trentino
did not identify an exact runnable ARIES study bundle. MARBEFES search returned
six other project tools, not an attested current-k.LAB reproduction. The known
PaL/Sicily author's public repositories add no heat/cooling archive. Negative
searches are limited discovery evidence, not proof of nonexistence.

## Next actions / what remains genuinely blocked

- Resume only the rate-limited project inspections after access is available;
  keep old 429 results rather than overwriting them with assumed failures.
- Resolve exact source/input/reference bundles for artifact-gap R/P rows before
  treating their family exemplar as the paper implementation.
- For the lowest-complexity candidate, verify actual modern classification and
  reporting components. `klab.authority.land` is relevant but its README describes
  tentative draft catalogs and unimplemented automatic host wiring; it is not an
  already accepted CCI/SEEA crosswalk. Similar names do not authorize migration.
- If any candidate clears source, semantics, input, reference and permission
  gates, run a bounded isolated unchanged-Java Job with owned cleanup and report
  actual numerical comparison. Otherwise stop at a separately approved repair
  scope instead of hiding the blocker behind new Python code.

No services or console windows were launched in this pass. Existing services,
user terminals, source/data payloads and failure evidence were left untouched.

## Evidence index

External root: `C:/Users/lumsd/AppData/Local/Temp/opencode/experiment-audit-evidence-20261101/`.
Compact source/diagnostic summaries accompany this report, without raw third-party payloads.

- **E1** Existing `python-client/docs/paper-based-functional-testing.md` and its primary source links.
- **E2** `bitbucket-repositories.json`, `project-inventory.json`, per-repo inventories.
- **E3** `official-pages.json`, `publication-leads.json` (URLs, hashes, selected text/links).
- **E4** `github-repositories.json`, exact `github-*-tree.json`, `github-source-recovery.json`,
  `parser-pinned.json`, `parser-current-local.json`, `parser-commands.json`, logs.
- **E5** `sicily-original.zip`, `sicily-probe-manifest.json`, both `sicily-parser-*.json`/logs;
  archive SHA-256 `4a86ec3a1ed1b1cbf9c16d068f30ec7d539e297c36f9f938dc051736134d08b1`.
- **E6** `crossref-leads.json`; exact title/PII match required, search result alone not article validation.
- **E7** Checked-in PaL G0/adaptation/review records and external `pal-g0-evidence-20261101/`,
  `pal-radiation-evidence-20261101/` (including prior alternative-candidate assessment).
- **E8** Existing hosted/local result records; failed `pr102-isolated-slice-3-20261101/`
  synthetic fixture is preserved, not promoted. Native TIFF units defect remains upstream issue #10.
- **E9** External `seea-extent-evidence-20261101/README.md`, pinned source manifests,
  parser reports, real crop receipt, raw-class area oracle and verification manifest.
- **E10** [SEEA static-table second attempt](seea-static-extent-attempt-20261101.md)
  and external `seea-extent-attempt-2-20261101/`: narrow syntax success, current
  source contract barriers, failed semantic probe and newer CORINE-worldview evidence.

Initial official-page output hit Windows console encoding after saving complete
JSON; later display used escaped JSON. Initial code search referenced a nonexistent
module path; corrected repository-wide searches were used. These tooling failures
are not model failures. No offline SDK regression suite was rerun for this
documentation-only increment; actual Java parser checks are itemized above.
