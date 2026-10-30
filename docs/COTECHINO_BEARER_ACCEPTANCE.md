# Cotechino bearer identity acceptance

Prepared 2026-10-20. This is a review and execution checklist, not evidence of live
success. The running user stack has not been stopped, restarted, installed into,
or deployed to by this work.

## Source and runtime baseline

- Services baseline: `7e80184b5`, including the owner's Resources startup fix.
- IDE baseline: `69d840e`.
- Worldview: `35a0df73a093aea47be812e35c6151f798ede769`, archived privately as
  `task-2/strawman` from the active Resources workspace without changing it.
- Active staging source inspected read-only:
  `.klab/services/resources/workspaces/klab.staging.vxii/src/staging/vxii/basic.kim`
  and `src/staging/vxii/test/classification.kim`.
- Staging source SHA-256 at inspection: `basic.kim`
  `dc77e5e090d4b1112152d140edec3658b23fead1025c096d3a43be322206ece2`;
  `classification.kim`
  `41464369ead0c35bd67ed1cc59c3bc856494e75876bdb22852271b54fe6a8a85`.
- Before live acceptance, the parent and user must identify the precise candidate
  service build and worldview snapshot being tested. Unit tests alone cannot
  establish that the running services use those bytes.

## Effective bearer evidence

`physical:Height` requires `(imod:Areal or imod:Volumetric)`.
`geography:Elevation` inherits Height and is `of earth:Location`.
At the pinned source, Location is Geostationary Feature and has no declared or
inherited spatial identity. Region specializes Location and declares Areal.

The full effective bearer includes description-derived restrictions:
Elevation describes Terrain, and Terrain is a SpatialArrangement of a
Crust/Terrestrial Region. The privately compiled source subset exposes an effective
Terrestrial Region bearer with inherited Areal identity evidence. Its OWL proof
satisfies the complete `(Areal or Volumetric)` requirement. The directly authored
Location bound alone supplies no such identity. The subset report retains the
complete `effectiveElevationBearers` and existential entailment evidence.

Consequently:

- Location alone has no proof of Height's requirement. Do not make it eligible
  merely because one Location subtype does. A necessary effective Region bound
  derived from the complete declaration is different evidence and must be retained.
- Elevation with a proved effective Region bearer may satisfy the category
  obligation. The Areal category's abstractness is not itself a failure.
- Cotechino requests bare Elevation inside an actual Region context. For these
  pinned declarations, the complete inherited bearer closure supplies the needed
  identity evidence; no new generic context-refinement rule is required. Initial
  atomic resolution must see that completed closure, without retaining a stale
  incomplete result cached during staging.
- A different worldview declaration on Location would be a separate scientific
  commitment requiring owner approval; it is not part of this acceptance checklist.

## Isolated source and strategy evidence

The opt-in test `StrawmanBearerSourceAcceptanceTest` in Resources reads an explicit
local source path, copies source strings into its temporary parser workspace, and
never contacts a live service or performs an authority lookup.

Suggested Maven properties, with the parent's private repository/cache arguments:

```text
-pl klab.services.resources -am test
-Dtest=StrawmanBearerSourceAcceptanceTest
-Dsurefire.failIfNoSpecifiedTests=false
-Dklab.test.strawman.directory=<task-2>/strawman
-Dklab.test.staging.directory=C:/Users/Ferd/.klab/services/resources/workspaces/klab.staging.vxii
-Dklab.test.core.directory=<task-2>/services/klab.services.reasoner/src/main/resources/knowledge
```

The source-fact test checks actual parsed Height, Elevation, Location, Feature,
Region and Transformation declarations, including native process identity
preservation. The classification-source test parses the actual reported model
against the actual worldview descriptors and records notifications in
`klab.services.resources/target/strawman-classification-source-acceptance.txt`.

The `wholeWorldviewWithoutAuthoritySnapshotsReportsBlocker` method attempts genuine
whole-source capture without providers and asserts the explicit missing-authority
diagnostics. Its retained report is
`klab.services.resources/target/strawman-bearer-acceptance.txt` and contains source
SHA-256 values and capture diagnostics. This source-only harness has no owned
authority catalog; it does not compile or accept the full worldview.

`actualDeclarationSubsetEligibilityAndStrategies` additionally computes a closure
of the exact parsed declarations needed by Height, Elevation, Slope, Region,
PhysicalEnvironment, Period and Frequency. It retains every clause on selected declaration trees,
includes their core/domain and lexical dependencies, and refuses to waive an
authority obligation if one enters that closure. The report
`klab.services.resources/target/strawman-bearer-subset-acceptance.txt` names every
selected root and explicitly labels this scope as a subset. It checks the
production source validator as well as composition and satisfiability. Its report
records whole selected-closure consistency and every named class's satisfiability,
including generated/internal classes, with any unsatisfiable IRIs retained before
the test fails. The Period/Frequency correlation remains in this audit so its
missing bearer-evidence diagnostics remain visible. It checks
the actual atomic Elevation request and classification.members strategy selection
using the real `strategies/observations.obs`, and reports the effective Elevation
bearer and identity evidence. It also checks that explicitly abstract Height stays
ineligible. Passing
this method is useful evidence for actual examples, and does not establish
admission of the entire authority-bearing worldview or live runtime execution.

The actual source declares CHEM, TAXA and UN.M49 authority providers. A full private
worldview compile requires matching owned provider bindings/snapshots. Missing
provider evidence must remain a reported blocker. Do not bypass preflight, silently
remove authority declarations, or describe an uncompiled worldview as accepted.

## Live acceptance, coordinated by the parent

Perform only after the user approves any required build installation/restart and
the parent verifies that unrelated live testing is preserved.

1. Record the running Services build, loaded worldview revision, semantic
   generation, service health and relevant source hashes. Confirm the startup fix
   remains present and the expected physical source metadata is retained.
2. Submit the exact classification test model:

   ```kim
   model earth:PhysicalEnvironment of each earth:Region
       using klab.generators.random.categories();
   ```

   Require no inherited-inherency incompatibility for Region specializing
   Location. Require valid classification strategy/cohort handling, rather than
   merely a non-Nothing concept. Preserve all authored independent constraints.
3. In a Region context, inspect Elevation's authored and effective abstractness,
   inherited identity requirement, effective bearer, and evidence. Require an
   actual matching observation strategy after effective bearer evaluation. Record its
   strategy name and the selected model before submitting the full workflow.
4. Submit `staging.vxii.basic.cotechino` with its pinned geometry, 1 km grid,
   year 2014 and 1 day step. Record the observation/request identifier.
5. Verify the existing `each earth:Freshwater earth:Region` dependency instantiates
   successfully, with its identity, geometry and cohort preserved.
6. Verify `geography:Elevation in m` reaches model discovery, selects the expected
   generated terrain model, and produces data with coverage matching the Region
   context. No identity obligation or authored abstractness check may be waived.
7. Verify `geography:Slope in degree_angle` is reached and resolves with its expected
   model and coverage. The staging source says its generated slope does not
   correspond to the generated DEM; do not claim physical consistency between the
   two synthetic surfaces.
8. Record completion status and evidence for all three dependencies and the parent
   observation. A selected strategy, constructed graph, or unit-test pass alone is
   not an executed and successful contextualization.

## Negative checks

- Missing identity evidence remains incomplete/unresolved.
- A dependent's own identity qualifier cannot stand in for bearer identity.
- `(A or C)` bearer identity evidence cannot establish `(A or B)` merely by overlap.
- Independent inherited clauses remain conjunctive; one clause's OR stays an OR.
- One AND filler requires a suitable single identity witness.
- A proven disjoint bearer is rejected; unknown membership is distinguished from
  proven incompatibility and is not silently accepted.
- An explicitly abstract head remains abstract after its bearer obligation is met.
- `physical:IntransitiveProcess` and related native identities stay on processes;
  successful projection must not add them to bearers or mutate declarations.
- Authority-specific identity obligations continue to require captured admission.

## Outcome recording

For each stage record `passed`, `failed`, `pending`, or `not attempted`, plus exact
diagnostics and request identifiers. Report separately: source parsing, private
compilation, eligibility, strategy selection, model discovery, runtime execution,
and final coverage. The current document makes no live-success claim.
