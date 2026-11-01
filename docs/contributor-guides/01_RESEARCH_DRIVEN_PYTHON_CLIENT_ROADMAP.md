# k.LAB Python Client — Research-Driven Roadmap

**Status:** Proposed planning document; no implementation or PR action authorized by this document.  
**Prepared:** 2026-10-31  
**Audience:** Contributor CLI agents, scientific reviewers, project maintainer, and project owner.

## Human-readable overview — what we are changing and why

The existing roadmap correctly aims for (1) a dependable scientific Python user client, (2) a broader remote Engine/Modeler client, and (3) a separate, optional provider/worker SDK. Preserve these product boundaries and the prior technical work. The change is **how we demonstrate value**: use published scientific applications to choose and validate capabilities, rather than treating working API calls and large test counts as proof of scientific readiness.

First close out the existing contributions on their own agreed criteria. In parallel, recover original research artifacts. Then use a small Pasture and Livestock (PaL) scientific computation to validate Python-to-Java integration and progressively pursue published R-versus-PaL vegetation-growth comparisons. Other published studies remain future candidates, **not promises that historical models run on modern k.LAB**.

## Authority and reading order for CLI

1. Read repository `AGENTS.md` and contributor rules; preserve working-tree changes.
2. Read `docs/contributor-guides/KLAB_CLI_Contribution_Review.md` (mandatory, read-only review of every contribution).
3. Read `docs/contributor-guides/KLAB_Python_Client_Planning_Guide.md` (2026-10-14 modified guide); retain its full historical-roadmap Part II and current architectural boundaries.
4. Read `docs/PYTHON_CLIENT_PLANNING_GUIDE.md` and `docs/PYTHON_CLIENT_ROADMAP.md`, where present; reconcile divergences rather than overwrite history.
5. Read `docs/contributor-guides/KLAB_Python_Discover_Compare_Explain_Milestone.md`, API/contracts, scientific-analysis and functional-testing docs; inspect current source, current branches, PRs and deployed services.
6. Read companion specs `02_OUTSTANDING_PR_CLOSEOUT_SPEC.md` and `03_PAL_RESEARCH_MILESTONE_SPEC.md`.

**Precedence:** Maintainer direction, actual current code/service contracts and repository instructions outrank planning assumptions. This document is not an acceptance or merge instruction.

## Existing three target products — unchanged

| Target | Plain-language outcome | Boundary |
|---|---|---|
| A. Dependable scientific client | Scientists connect, request models, obtain correctly described data, understand why/how it was computed, and recover work | First release target |
| B. Full remote user client | Python exposes supported semantics, resources, graphs, authoring, events and behaviors | Only implemented server contracts; not Java method-count parity |
| C. Python worker / provider SDK | Python *hosts* model computation or adapters | Separate project with execution, isolation, security, publication and lifecycle contracts |

## Two complementary work tracks

**Engineering track:** transport, authenticated service selection, semantics, observations, scope/jobs, exports, provenance, recovery, scientific adapters, compatibility, robustness, and later authoring/behaviors. Keep unit/contract/security/lifecycle tests. Never invent Java-native data, units, coordinates or semantic decisions inside Python.

**Scientific-validation track:** source papers -> exact archived source/data -> version/dependency manifest -> runnable independent reference -> compatible Java execution -> Python orchestration -> scientifically appropriate comparison -> archived regression evidence. Preserve all failures and missing dependencies explicitly.

The tracks feed each other. An experiment can establish an engineering requirement; an engineering change must have its own bounded, maintainer-reviewable acceptance criteria independently of experiment success.

## Delivery sequence and gates

| Stage | Work | Exit condition |
|---|---|---|
| 0. Baseline | Inventory actual open PRs, branch SHAs, deployed Java/worldview/component versions, working tree and current test evidence | Versioned facts; no stale acceptance claims |
| 1. Closeout | Execute companion PR closeout spec including independent Java identity work and Python F3/F4/delivery | Every targeted PR: accepted/merged OR documented blocked/superseded state, never silently labeled done |
| 2. PaL artifact compatibility | Recover Puerto R and PaL archives; inspect resource files, parser/version/dependencies; attempt small Radiation model | Explicit go/no-go and compatibility classification; no scientific replication claim merely from parsing |
| 3. PaL pilot | Python discovers, resolves, executes and reads a small authentic PaL model against unchanged authorized Java services | Provenance and independent numerical checks; one genuinely reproducible run |
| 4. PaL vegetation growth | Run comparable Puerto R and PaL scenarios, assess spatial/temporal aggregation and differences | Science report with justified tolerances and documented discrepancies |
| 5. Expand breadth | Select next research cases based on distinct missing capabilities, available evidence and maintainers' priorities | Cross-domain scientific regression suite, not just repeated observation calls |
| 6. Full user-client coverage | Expand authoring, graph, events, federation where server contracts allow | Demonstrated supported user workflows, not speculative platform behavior |
| 7. Optional worker SDK | Separate feasibility/spec after demand and contracts | Independent approval, no implicit prerequisite for Python user client |

Stages 1 and 2 may run in parallel. Stage 3 cannot claim compatibility until Stage 2 passes. Java changes are a separately approved cross-project track; no stealth alterations to make historical science appear compatible.

## Complete scientific research and functional-test inventory — 49 entries

**Human-readable explanation:** This is the **full discovered inventory**, not merely the small shortlist for our first milestone. It retains **19 published/application research leads (R01–R19), 19 platform behaviors and reusable model families (C01–C19), and 11 active/historical project leads (P01–P11)**. They are not all independently verified research replications. Nothing in this backlog is assumed runnable on the current Java stack. Scientific coverage and executable status must be demonstrated separately.

**Provenance:** Transcribed without omitting rows from `aries_klab_research_inventory_2026-10-31.md`, discovery snapshot dated October 31, 2026. Source URLs and the original R/C/P identifiers are retained for traceability. Readiness markers are preliminary, not claims that code or datasets have been recovered.

**Readiness key:** `A` = source-backed with a potentially checkable published result; `B` = credible test contract, but model/data/reference artifacts unverified; `C` = discovery lead only. **All 49 are pending artifact-level audit and current-Java execution verification.** No item is marked `REPRODUCED`.

**Future CLI requirements:** Preserve this entire catalog in subsequent edits. For each case, track publication verification, artifacts, exact dependencies, original reference data, Java interoperability, Python gaps, scientific oracle, and priority. Do not promote a case to an executable regression suite simply because its literature exists. Treat `C` entries as testable capabilities that can be exercised by multiple `R` or `P` cases, not as extra published case studies.

## Published research applications / case studies
| ID | Research case | Candidate end-to-end Tess acceptance behavior | Readiness | Source |
|---|---|---|---|---|
| R01 | Sicily wildfire risk, 2023 | Reproduce mapped fire-risk output for published area/scenario, inspect inputs and compare reference | B | https://aries.integratedmodelling.org/hub-cateogory/publications/ |
| R02 | South African natural-resource dependence, 2019 (existing project shortlist) | Reproduce household/dependence classification with documented input variables | C | Existing progress page research shortlist: https://eric-resume.up.railway.app/python-client-progress/ |
| R03 | Eastern Madagascar water supply and quality | Check published water-supply/quality storylines, benefit flows and output maps | B | https://pmc.ncbi.nlm.nih.gov/articles/PMC3953216/ |
| R04 | Washington State open-space value (early ARIES) | Check proximity/beneficiary flows against published spatial output | B | https://pmc.ncbi.nlm.nih.gov/articles/PMC3953216/ |
| R05 | Puget Sound five-service supply/demand/flow analysis | Distinguish theoretical from actually delivered services; published aggregate delivered range 16–66% for non-carbon services | A | https://www.usgs.gov/publications/theoretical-actual-ecosystem-services-mapping-beneficiaries-and-spatial-flows |
| R06 | San Pedro River carbon, water, scenic views | Reproduce scenario comparison of urban growth and mesquite management; compare published ARIES vs InVEST contrasts | A | https://www.usgs.gov/publications/comparing-approaches-spatially-explicit-ecosystem-service-modeling-a-case-study-san |
| R07 | Puget Sound urban expansion (2016) | Reproduce natural-capital stock and service-flow changes under urban expansion | B | https://aries.integratedmodelling.org/hub-cateogory/publications/page/4/ |
| R08 | Southern Rocky Mountains biophysical models and preferences (2015) | Check spatial ecosystem-service result with preference-weight choices | B | https://aries.integratedmodelling.org/hub-cateogory/publications/page/5/ |
| R09 | Urban flood early warning Bayesian network (2016) | Test scenario of early warning and exposure; compare published probabilistic results | B | https://aries.integratedmodelling.org/hub-cateogory/publications/page/4/ |
| R10 | Landscape aesthetics Bayesian networks (2017) | Reproduce mapped aesthetic scores under participatory indicator weighting | B | https://aries.integratedmodelling.org/hub-cateogory/publications/page/4/ |
| R11 | Ecosystem service hotspot mapping comparison (2016) | Compare hotspot boundaries/ranks under alternative mapping methods | B | https://aries.integratedmodelling.org/hub-cateogory/publications/page/4/ |
| R12 | Trentino livestock and agricultural payments (2015) | Reproduce livestock/ecosystem-service scenario and policy comparison | B | https://aries.integratedmodelling.org/hub-cateogory/publications/page/4/ |
| R13 | Italian ecosystem regulating and cultural service changes (2022) | Check temporal changes and classifications over published periods | B | https://aries.integratedmodelling.org/hub-cateogory/publications/page/2/ |
| R14 | Cantabrian pasture/livestock PUERTO model integration (2023) | Demonstrate semantic orchestration and selective execution of published modularized R workflows | B | https://aries.integratedmodelling.org/aries-hub/new-aries-article-demonstrates-key-steps-needed-to-create-more-interoperable-models-in-agricultural-systems/ |
| R15 | ARIES urban heat resilience (2024) | Reproduce spatial exposure/urban heat results and change in green-infrastructure scenario | B | https://aries.integratedmodelling.org/hub-cateogory/publications/ |
| R16 | ARIES urban cooling / blue-green infrastructure (2025) | Test cooling service under changing land cover and beneficiary distribution | B | https://aries.integratedmodelling.org/hub-cateogory/publications/ |
| R17 | Tidal marsh climate adaptation (2024) | Reproduce marsh ecosystem-change/scenario outputs | C | https://aries.integratedmodelling.org/hub-cateogory/publications/ |
| R18 | Landscape water / virtual watershed coupling (2015) | Test linked watershed models and river-management outputs | B | https://aries.integratedmodelling.org/hub-cateogory/publications/page/5/ |
| R19 | Agricultural ecosystem-service trade-offs (2013) | Compare services under agricultural production scenarios | B | https://aries.integratedmodelling.org/hub-cateogory/publications/page/5/ |

## Documented platform behaviors and reusable model families (not separate published replications)
| ID | Capability | Candidate test contract | Readiness | Source |
|---|---|---|---|---|
| C01 | Riverine flood flow | Supply, routing, exposure and actually protected beneficiaries | B | https://pmc.ncbi.nlm.nih.gov/articles/PMC3953216/ |
| C02 | Coastal flood regulation | Coastal protective service connected to exposed people/assets | B | https://pmc.ncbi.nlm.nih.gov/articles/PMC3953216/ |
| C03 | Carbon storage/sequestration | Unit-correct stock/flux and changes under land-cover variation | B | https://pmc.ncbi.nlm.nih.gov/articles/PMC3953216/ |
| C04 | Sediment retention / reservoir benefit | Watershed pathways and reservoir beneficiary effects | B | https://pmc.ncbi.nlm.nih.gov/articles/PMC3953216/ |
| C05 | Freshwater supply | Supply/demand and hydrological flow | B | https://pmc.ncbi.nlm.nih.gov/articles/PMC3953216/ |
| C06 | Scenic viewsheds | Topography-based visibility; beneficiary/line-of-sight constraints | B | https://pmc.ncbi.nlm.nih.gov/articles/PMC3953216/ |
| C07 | Open-space proximity | Access distance and beneficiaries, not just nearby open land | B | https://pmc.ncbi.nlm.nih.gov/articles/PMC3953216/ |
| C08 | Subsistence fisheries | Service pathways between aquatic production and beneficiaries | B | https://pmc.ncbi.nlm.nih.gov/articles/PMC3953216/ |
| C09 | Recreation | Accessible recreation opportunities vs raw landscape supply | B | https://pmc.ncbi.nlm.nih.gov/articles/PMC3953216/ |
| C10 | Pollination global baseline | Supply/demand linkages and effect of changing pollinator habitat | B | https://github.com/integratedmodelling/im.aries.global |
| C11 | Automatic model selection | Same requested observable in differing places selects appropriate resource with documented rationale | B | https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0091001 |
| C12 | Service-flow under uncertainty / stochastic SPAN | Stable bounds/distributions with fixed seeds and independently checked toy network | B | https://aries.integratedmodelling.org/hub-cateogory/publications/page/6/ |
| C13 | Geographic scale sensitivity | Compare regional summary/changes at two resolutions and inspect aggregation logic | B | https://aries.integratedmodelling.org/hub-cateogory/publications/page/5/ |
| C14 | Ecosystem extent account | Geographic area totals, classifications, and year-over-year account reconciliations | B | https://aries.integratedmodelling.org/aries-for-seea-user-guide/ |
| C15 | Forest ecosystem condition account | Original variable, normalized 0–1 indicator, equal-weight condition index | B | https://aries.integratedmodelling.org/aries-for-seea-user-guide/ |
| C16 | Ecosystem service physical accounts | Supply and quantities by region/year, units and source trace | B | https://aries.integratedmodelling.org/aries-for-seea-user-guide/ |
| C17 | Ecosystem service monetary accounts | Valuation scenario, currencies, units and method trace | B | https://aries.integratedmodelling.org/aries-for-seea-user-guide/ |
| C18 | Temporal fallback | Missing input years replaced by closest available year, provenance remains visible | B | https://aries.integratedmodelling.org/aries-for-seea-user-guide/ |
| C19 | Spatial-temporal interoperability | Combine independently authored models with differently scoped input data | B | https://aries.integratedmodelling.org/aries-hub/new-aries-article-demonstrates-key-steps-needed-to-create-more-interoperable-models-in-agricultural-systems/ |

## Official project leads (not yet verified executable research cases)
| ID | Project | Research workflow to investigate | Readiness | Source |
|---|---|---|---|---|
| P01 | World Bank Nigeria accounts | Nasarawa and Kaduna accounts vs reports/reference tables | C | https://aries.integratedmodelling.org/project/world-bank/ |
| P02 | Global vegetation carbon 2001–2020 | Annual above/below-ground carbon stock grid and temporal consistency | B | https://aries.integratedmodelling.org/project/world-bank/ |
| P03 | ARIES for SEEA Rwanda/Senegal/Nigeria | Country-scale extent, condition, service accounts | C | https://aries.integratedmodelling.org/project/aries-for-seea/ |
| P04 | World Ecosystem Extent Dynamics (WEED) | STAC-driven ecosystem extent change; validation against reference maps | C | https://aries.integratedmodelling.org/project/world-ecosystem-extent-dynamics-weed/ |
| P05 | PEOPLE-EA | OpenEO-to-ARIES ecosystem extent/condition/service account pipeline | C | https://aries.integratedmodelling.org/project/people-ea/ |
| P06 | OBServ | Pollinator biodiversity scenario, compare statistical and mechanistic predictions | C | https://aries.integratedmodelling.org/project/observ/ |
| P07 | ALICE | Landscape change and blue-green infrastructure case studies in four countries | C | https://aries.integratedmodelling.org/project/alice/ |
| P08 | IHOBE / Urdaibai | Local ecosystem service and biodiversity monitoring from Geoeuskadi inputs | C | https://aries.integratedmodelling.org/project/ihobe/ |
| P09 | NBSOIL | Soil organic matter, structure and hydrological scenario model coupling | C | https://aries.integratedmodelling.org/project/nbsoil/ |
| P10 | MARBEFES | Marine service provisioning scenarios and spatial-temporal model integration | C | https://aries.integratedmodelling.org/project/marbefes/ |
| P11 | LIFE PYRENEES4CLIMA | Mountain water and air-quality adaptation scenario | C | https://aries.integratedmodelling.org/project/life-pyrenees4clima/ |


### Priority is a separate decision from inventory completeness

The initial implementation portfolio remains: (1) PaL Radiation feasibility then Puerto/PaL vegetation comparison, (2) San Pedro scenarios, (3) Puget Sound delivered flows, (4) SEEA accounts, (5) Sicily wildfire, and (6) WEED. These **do not replace or reduce** the 49 entries listed above; change priority only with recorded scientific feasibility and contributor feedback.

### Candidate test selection rule

For each candidate, score source and input availability, reference oracle strength, compatibility with current k.LAB, distinct semantic/scientific coverage, incremental engineering cost, and ongoing project relevance. Record the rationale and blocking dependencies. A candidate that requires extensive Java migration may be kept in the catalog while the team implements a lower-risk study first.

### Research-case record schema for future expansion

Each case must have: `case_id`, DOI/title/authors/year, scientific question, original software/version, licenses, archive URLs + hashes, reproducible setup and dependencies, original datasets and access rights, geographic/temporal domain, target observable(s) and scientific units, expected output source, comparison method/tolerances, Java compatibility, required Python capabilities, provenance, execution identity, permission and cleanup expectations, status and blockers, cost/runtime estimate, reviewer/owner, and reproducibility scripts/evidence paths. Record unknown explicitly.

### Test levels — don't conflate them

1. **Unit and schema tests:** pure Python codecs/errors; never claim Java/scientific acceptance.
2. **Service contract tests:** exact authorized Java versions, independent payloads.
3. **Connected integration:** genuine submissions/results/permissions/lifecycle; no synthetic data counted as published science.
4. **Scientific component oracle:** physical invariants, known-answer examples, independently computed reference.
5. **Published study replication:** comparable domains/inputs, calibrated differences, reproduced findings with complete artifact lineage.

Preserve the existing 34 functional test selectors, but classify which level each actually tests. Count tests separately from independent cases and accepted replication outcomes.

## Scientific fidelity and anti-shortcut rules

- No invented scientific values, coordinate transforms, resolution decisions, accepted models, graph links or units. Read provenance from authoritative Java outputs.
- Do not reproduce the R model *in Python* as a surrogate for running k.LAB; an independent Python oracle may be used only when clearly labeled.
- Never mistake archived pre-1.0 k.IM code for verified k.LAB 1.0 executability.
- Geographic comparisons require CRS, axis, pixel alignment, nodata/mask, aggregation, and units validation. Temporal comparisons require matching period, timestep and state initialization.
- Preserve raw artifacts with hashes and the full stack tuple. Test with offline fixtures where possible, live services only when explicitly authorized.
- Separate a missing backend feature from a Python defect; avoid unreviewed changes to Java/ontologies/components.
- Do not quietly lower acceptance criteria to make a milestone green. Allow `BLOCKED`/`PARTIAL` only when labeled and accompanied by remaining requirements.

## Governance, review, and progression

Each engineering PR receives an independent **read-only** review using `KLAB_CLI_Contribution_Review.md`: exact source/base SHAs, feature-specific diff, current contracts, scientific meaning, lifecycle, permissions, evidence, blockers, concise maintainer-ready explanation. Fix and re-review before asking for maintainer acceptance. Keep feature branches narrow and independent; do not merge/close/publish without explicit authorization.

The weekly/periodic report should say in plain language: what new research can actually be done from Python, what the current real Java stack did, what remains missing, what is Java rather than Python work, and what research test becomes possible next.

## Primary research/source links

- PaL publication: https://doi.org/10.1371/journal.pone.0281348
- Puerto R archive: https://doi.org/10.5281/zenodo.6786418
- PaL archive: https://doi.org/10.5281/zenodo.7533051
- Current source/project context: https://github.com/integratedmodelling/klab-services
- 31 Oct recorded client status: https://eric-resume.up.railway.app/python-client-progress/
- Research bibliography should be reconciled to ARIES publication lists, paper references and source repositories before claiming completeness.
