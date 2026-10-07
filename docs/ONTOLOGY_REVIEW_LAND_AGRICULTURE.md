# Land and agriculture: a domain-boundary proposal

Research assessment supplied for this documentation revision, **7 October 2026**. This is a
source-grounded proposal for human review, not approved ontology declarations or an implementation.
It illustrates the [review/integration design](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md) and shared
[OR-03](ONTOLOGY_REVIEW_EDITING.md#or-03). No `imod` files were changed.

## Recommendation and reproducible basis

**DETAILED WORKFLOW TO BE DECIDED — OR-03.** Add **agriculture** as a candidate domain, transferring
the agricultural substance of the current land bootstrap into a proposed agriculture dossier.
Rebuild **land** around cross-sector land use, management, occupation, conversion, competing uses
and rights, and changes in land condition. This is domain organization, not a declaration that
agriculture is a subclass of land: agricultural holdings may be landless and depend on biological,
water, institutional and production meanings.

Do not simply rename the dossier. Preserve its cover/use distinction and reusable candidates while
correcting its agricultural sampling bias. Broaden land evidence to settlements, infrastructure,
extraction, forestry, conservation, energy and recreation as well as farming. Specialized activities
remain in the appropriate domains; land supplies shared land-related observables and predicates.

The assessment inspected the [land bootstrap][bootstrap], [question register][questions] and
[land namespace][namespace] at `imod` commit **28ee04c25829684aa0e78127d694564bbc94194d**. The research
reported that the branch directory returned bootstrap blob
`02a392821273f5a52801ad1bf80bde9646464695`, matching the named snapshot. Branch-head resolution was
not independently established; use the pinned commit as the reproducible research basis.

All five proposed processes are agricultural: irrigation, tillage, sowing, harvesting and residue
retention. Three of the five proposed events are agricultural operations; land-use conversion and
cover change are broader. The dossier acknowledges weak urban-use, forestry-management, pastoral-
tenure and land-rights coverage. **All 27 entries remain candidates**, with their blocked/provisional
status retained. The existing namespace comment mentions agriculture, land use and food systems,
but its temporary `LandCover` identity does not settle the boundary.

## Evidence and its limits

| Source | What it supports | What it does not establish |
| --- | --- | --- |
| [FAO LCCS definitions][fao-cover] | Cover concerns biophysical features; use concerns human activities, arrangements and inputs. | Greenness does not prove farming; the definitions do not prescribe k.LAB semantic types. |
| [EEA, Land use and land take][eea-land] | Land demand crosses settlement, nature restoration, biomass, wind, solar and farming; uses may be multifunctional. | Settlement area is a proxy in the briefing; national LULUCF definitions vary. Land take is not every land-use change. |
| [EEA, Landscape fragmentation][eea-fragmentation] | Structural connectivity can be assessed under a specified barrier geometry. | It does not establish organism-specific functional connectivity or universal high/low predicates. |
| [EEA, Soil resources][eea-soil] | Sealing, organic-matter loss, erosion, compaction and contamination cross agricultural, urban, wetland and forest settings. | Agriculture does not own soil observables merely because farming supplies examples. |
| [UNCCD PRAIS4 2026, strategic objective 1][unccd] | Degradation interpretation needs a baseline/context; stable previously degraded land can remain degraded. Invasive woody growth can raise productivity while worsening condition. | Reporting algorithms and class rules do not become universal ontology axioms; reduced productivity need not mean failed recovery. |
| [FAO WCA 2010 definitions][fao-holdings] | Holdings differ from their parcels, and livestock holdings may be landless. | A holding is not necessarily a terrestrial region. This is a historical framework, not asserted to be the latest census specification. |
| [FAO Voluntary Guidelines on Tenure][fao-tenure] | Tenure concerns people and rights/governance over land, fisheries and forests. | Overlap, occupation, management, ownership and permission are not interchangeable. |
| [FAO Land Evaluation basic concepts][fao-evaluation] | Suitability relates to a specified use; characteristics, use-related qualities and evaluation criteria differ. | Assessment procedures and class limits do not become universal intrinsic properties. |

## Proposed scope and dependencies

**DETAILED WORKFLOW TO BE DECIDED — OR-03.** For **land**, retain relations between people,
activities and terrestrial places; concurrent/successive uses; actual management and conversion;
surface occupation and changes; cross-sector land condition; and conflicts over specified uses or
rights. Keep a place's cover, current use, planned use, legally permitted use, tenure and condition
separately expressible.

For **agriculture**, begin with deliberate cultivation and husbandry, agricultural operational
units, managed biological populations, interventions, outputs and use-specific observable qualities.
Add livestock and grazing evidence: moving crop examples alone does not establish agriculture
coverage. Decide how forestry, aquaculture and downstream food processing connect rather than
silently including them under an expansive label.

Reuse upstream meanings: earth/geography for spatial bearers and topology; biology/ecology for
organisms, populations, vegetation, habitats and ecological relations; soil/hydrology for substrate
and water; agency/society for actors, management, institutions, rights and disputes; infrastructure
for installations. Economics/valuation is relevant where meaning concerns costs, benefits or
preferences. Do not recreate those concepts locally or silently introduce reverse dependencies.

## Disposition of the 27 existing candidates

**DETAILED WORKFLOW TO BE DECIDED — OR-03, OR-04, OR-08.** These are proposed integration
dispositions, not approvals, source edits or executable declarations. Preserve original IDs and
source/base references when moving a dossier entry, and retain every existing semantic blocker.

| Existing land candidate | Proposed treatment and retained review issue |
| --- | --- |
| ManagedField | Agriculture. Its cultivation-boundary meaning is narrower than generic managed land; resolve surface versus volume parent first. |
| LandEvaluationUnit | Replace as a domain primitive with the actual bounded bearer and, if needed, a contextual evaluation role. An assessment unit alone should not import scientific workflow into land. |
| CropStand | Agriculture specialization of an agreed biological population/configuration; unity remains unresolved. |
| VegetationPatch | Reuse an ecology/biology definition. Mapping delineation alone does not establish subject unity. |
| AgriculturalHolding | Agriculture with operational/institutional parentage from society/economics; neither a region nor the manager itself. |
| Irrigation | Agricultural specialization of deliberate water application; preserve horticultural/non-agricultural applicability. |
| Tillage | Agriculture; reuse upstream soil-disturbance observables. |
| Sowing | Agriculture; distinguish deliberate placement from natural dispersal. |
| Harvesting | Agriculture for the current cultivated-production meaning; forestry/other harvesting require explicit broader or specialized meanings. |
| ResidueRetention | Agriculture; distinguish residue presence from an actual retaining intervention; retain the blocker. |
| OccupiesField | Agriculture specialization of upstream spatial occupancy only if the specialization adds meaning. |
| ManagesField | Agricultural endpoints on an explicit management relation; distinguish responsible agent from operational holding. |
| OverlapsEvaluationUnit | Upstream topology/context composition; no new land/agricultural primitive merely to meet coverage. |
| AdjacentField | Upstream symmetric adjacency with agricultural endpoints; relationship/bond semantics remain to be settled. |
| SupportsCropStand | Prefer explicit rooting/location relations across soil/ecology/agriculture; avoid ambiguous functional support or implied yield. |
| SowingEpisode | Agriculture bounded event. |
| HarvestEpisode | Agriculture bounded event. |
| IrrigationEpisode | Agriculture bounded application event; justify broader water-application meaning upstream where needed. |
| LandUseConversion | Retain in land with actual source/target uses and temporal bounds; distinguish plan/permission changes. |
| CoverChangeEpisode | Retain cross-sector land meaning or compose upstream changes; two maps alone do not establish a bounded physical event. |
| SurfaceCoverFraction | Generalize for land surfaces with explicit covering material and denominator; agriculture specializes it. |
| DisturbedAreaFraction | Reusable surface-disturbance quality with operation/reference surface specified; tillage-specific meaning remains agricultural. |
| PlantAvailableWater | Soil/hydrology with plant/root-zone context; do not equate total soil stock with plant-available water. |
| HarvestableBiomass | Agriculture; separate biomass quantity from crop/product-, stage- and criterion-specific harvestability. |
| ManagedArea | Generic management extent reused by agriculture; distinguish managed, owned and occupied area. |
| AppliedWaterAmount | Reuse the appropriate dimensional water quantity; identify bearer/application context rather than making a process parameter its bearer. |
| SeedPlacementDepth | Agriculture specialization of positional depth relative to a stated surface; identify seed/placement bearer. |

## Fresh questions for a revised land dossier

**DETAILED WORKFLOW TO BE DECIDED — OR-03, OR-05.** These ten source-grounded prompts begin a new
question register. They are not a completed fifteen-question packet or runnable k.LAB expressions.
Expand from evidence, record full expressions and gaps, and justify coverage shortfalls. The guides'
five-per-kind targets are diagnostic aids, not quotas for inventing primitives.

1. Which parts of this site are actually used for grazing and solar generation at the same time?
   Require independent, time-scoped use relations rather than one exclusive use identity.
2. Did housing construction replace an existing use, increase impervious cover, both or neither?
   Keep use conversion and sealing separate.
3. Was a surface physically unsealed, and which soil/ecological properties subsequently changed?
   Removing cover is not automatically successful restoration.
4. Which actor manages, occupies, owns or holds a specified use right over this land? These relations
   may have different endpoints, extents and times.
5. Which claimants dispute which rights or uses? An actual social dispute can be observed; overlapping
   polygons alone do not establish it.
6. Which planned uses cannot coexist under stated spatial, temporal, technical or legal constraints?
   Keep analytical compatibility results/methods outside the worldview and distinct from actual disputes.
7. Did a road divide a continuous surface, and for which organism does it obstruct movement?
   Distinguish geometric from organism-relative functional connectivity.
8. Which specified land qualities declined relative to which reference, over which interval?
   Expose changes before applying a scoped degradation predicate.
9. Did farming cease while ownership stayed constant and vegetation changed later? Separate use
   cessation, rights and succession; fallow is not automatically abandonment.
10. Do different forest/mixed-use labels reflect a changed place, changed authority definition or
    changed observation scale? Classification disagreement is not a new physical process.

Derive primitive shapes transparently from the upper ontology: actual places as grounded subjects;
interventions/ongoing physical changes as processes; bounded conversions as events; management,
occupation and rights as relationships; measured extents/fractions/material conditions as qualities.
Exact parents and role/configuration or relationship/bond choices remain review gates, not guessed
syntax. Processes, events, changes and implications retain the runtime boundaries in the role guides.

## Predicates, authorities and rejected inferences

**DETAILED WORKFLOW TO BE DECIDED — OR-03, OR-05.** For degraded, restored, suitable, intensive,
fragmented, sustainable and high-yielding, state the relevant properties, baseline, use/organism,
timescale and assessment/authority scope. Distinguish a degradation process or measured decline from
a classified degraded state; stability can remain degraded relative to a reference. Distinguish
restoration activity from achieved recovery; intention does not establish outcome.

EEA land-take and UNCCD degradation classes are external interpretations, not necessary-and-sufficient
universal identities. [Official CORINE nomenclature][corine] includes mixed categories such as complex
cultivation patterns and agriculture with substantial natural vegetation. Preserve authority/version
and mapping conventions; do not force these into a universal exclusive cover hierarchy.

Any jargon module is alias-only: `equals` requires exact meaning. Broader, narrower or
convention-dependent terms are not exact aliases. Exclude hypotheses, equations, suitability models,
transition matrices, reporting workflows, observation algorithms and scientific assessment objects
from domain vocabulary; these can consume its observables elsewhere. The question about causes of
poor crop performance tests semantic boundaries, not a request for a causal model in the worldview.
No-till implying sustainability remains an explicitly rejected inference, not a new axiom.

## Worked contribution and integration example

**DETAILED WORKFLOW TO BE DECIDED — OR-04, OR-06, OR-08.** Treat this assessment as one proposal
against the pinned dossier base. A second reviewer could propose a different boundary for irrigation;
a third could challenge a holding's institutional parent. Each keeps its own proposal ID, evidence,
base and revision. None silently overwrites the research packet or another person's interpretation.

An editor-owned integration task should account for all admitted proposals. It could recommend
moving cultivated meanings, retain generic land-use conversion, defer the surface parent and record
dissent about a broader harvesting meaning. Its integrated proposal names every disposition and
exact source contribution. The editor can then issue revised dossier material for another public
round, or judge the organization sufficient to move to the next permitted design stage. Neither
exit grants scientific acceptance or authorizes ontology edits. New source material after the
cutoff belongs to an explicit later batch/round, with a retained receipt.

## Decisions for discussion

**DETAILED WORKFLOW TO BE DECIDED — OR-03, OR-05, OR-08.**

1. Approve agriculture as a candidate domain with crops and livestock; decide forestry/aquaculture connections.
2. Settle the terrestrial surface bearer before specializing fields/parcels/extents; do not silently inherit volumetric `earth:Region`.
3. Agree population/configuration, institutional operational-unit, use-role and rights/conflict semantics.
4. Choose complementary land evidence families: EEA conversion/sealing/fragmentation, FAO cover/use/tenure and UNCCD reference-relative condition.
5. Retain existing blockers and test fresh questions before drafting declarations. This note alone recommends no implementation change.

[bootstrap]: https://github.com/integratedmodelling/imod/blob/28ee04c25829684aa0e78127d694564bbc94194d/experiments/strawman-2026/bootstrap/land/BOOTSTRAP.md
[questions]: https://github.com/integratedmodelling/imod/blob/28ee04c25829684aa0e78127d694564bbc94194d/experiments/strawman-2026/bootstrap/land/QUESTIONS_FIRST.md
[namespace]: https://github.com/integratedmodelling/imod/blob/28ee04c25829684aa0e78127d694564bbc94194d/src/land.kwv
[fao-cover]: https://www.fao.org/4/x0596e/x0596e01e.htm
[eea-land]: https://www.eea.europa.eu/en/europe-environment-2025/thematic-briefings/biodiversity-and-ecosystems/land-use-and-land-take
[eea-fragmentation]: https://www.eea.europa.eu/en/analysis/indicators/landscape-fragmentation-pressure-in-europe
[eea-soil]: https://www.eea.europa.eu/en/europe-environment-2025/thematic-briefings/biodiversity-and-ecosystems/soil-resources
[unccd]: https://prais4-reporting-manual.unccd.int/en/2026/SO1.html
[fao-holdings]: https://www.fao.org/4/a0135e/A0135E07.htm
[fao-tenure]: https://www.fao.org/tenure/resources/publication/vggt/en
[fao-evaluation]: https://www.fao.org/4/x5310e/x5310e03.htm
[corine]: https://land.copernicus.eu/content/corine-land-cover-nomenclature-guidelines/html/index.html
