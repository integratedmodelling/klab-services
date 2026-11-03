# Semantic/query-contract review: preserved Elevation acceptance

Status: technical admission investigation continues; semantic acceptance remains unresolved.
Based on integration `5785c1baa`.
No product code, scientific query, model, worldview, algorithm, oracle or deployment
changes are included. This review is separate from diagnostic PR120 and does not
approve merging the blocked notification/fixture PRs108/117/118.

## Question and evidence boundary

The preserved acceptance request is `geography:Elevation in m` inside an actual
Region context. The fixed numerical reference remains twenty cells at123.25m.
Adding an explicit `of Region` operand changes the request and is not an accepted
substitution merely because strategy selection succeeds.

PR120's read-only probes established that the served original Elevation expression
is abstract and receives no strategy, with or without snapshot focus. Aspect and
explicitly qualified Elevation are concrete and receive `dependent.direct` with
the same snapshot focus. No models were evaluated, observations registered or
scientific Jobs submitted. Seven native offline diagnostics and final PR120 CI
pass. Those results do not establish complete bearer closure or scientific validity.

## Existing contract, not a new semantic proposal

- [Identity requirements](../docs/IDENTITY_REQUIREMENTS.md), especially its
  bearer-directed rules and strawman example, distinguish authored abstractness
  from effective request eligibility. A dependent's inherited identity requirement
  must be proved on its effective bearer, not invented on the dependent itself.
- [Cotechino acceptance](../docs/COTECHINO_BEARER_ACCEPTANCE.md) pins a different
  named source revision (`35a0df73...`) and expects bare Elevation to become eligible
  **if its complete declared/composed/inherited description closure proves Areal
  evidence**. That checklist is explicitly not evidence of live success.
- It does not introduce generic context replacement of an unspecified bearer.
  `context.exists()` checks scope focus; it is not a proof that the original
  observable's identity obligations have been discharged.
- `IdentityRequirementSupport.project/abstractHead/bearerSatisfies` derives a
  detached effective status from canonical declarations, closure and explicit
  semantic operands. `ConceptImpl.withExpressionStatus` preserves declarations.
- `ObservationPatternMatcher.field` checks abstractness; the existing
  `ObservationReasoner` fully-specified guard rejects abstract/generic/Nothing
  requests. Successful explicit qualification therefore cannot establish that
  the original request was complete, or that the guard should be weakened.

Thus the previous query-specific rejection is consistent with the guard's input,
but it does not establish whether that input correctly reflects the complete
fixture closure. The review must not assume either a required query rewrite or
a stale-cache/translation defect without comparing the actual source and closure.

## Exact fixture facts

Read-only native adaptation in PR120 retained these facts:

- `physical:Height` is authored abstract and requires `(imod:Areal or imod:Volumetric)`.
- `geography:Elevation` is authored concrete, `is physical:Height of earth:Location`.
- `earth:Location` is a Geostationary Feature without declared spatial identity;
  Region specializes Location and declares Areal.
- The preserved geography file declares Terrain as SpatialArrangement of a
  Crust/Terrestrial Region and says Terrain `emerges from` Elevation/Slope/Aspect.
  That wording alone must not be silently equated with the description-derived
  proof claimed for another pinned source revision.

| Preserved copied source | SHA-256 |
| --- | --- |
| `imod/src/geography.kwv` | `00e4c41b4a3a5be1b9a139e28b64415032f8d89720486144b8f13eb67d17b040` |
| `imod/src/physical.kwv` | `e7a8bab62c645420bc51c155c35cb645e71d590d63b8476de130e704a7fc113e` |
| `imod/src/earth.kwv` | `a05fef9cd32d1d0c6dda48f56576b3309891d3a01810179aac2e6dc163b207e5` |

## Existing native acceptance check

The unmodified `StrawmanBearerSourceAcceptanceTest` provides an opt-in
`actualDeclarationSubsetEligibilityAndStrategies` method. It reads source strings
without modifying the checkout, retains selected declaration clauses, refuses
authority waivers, compiles a dependency subset and records:

- consistency and unsatisfiable named classes;
- effective Elevation bearer/identity entailment;
- canonical, fresh-projection and initial cached atomic abstractness;
- actual strategy selection and authored-abstract Height rejection.

It was run on the exact preserved compatibility fixture and captured core,
not a substituted/relabelled ontology. No assertion or input has been rewritten to
force the older pinned-source expectation to pass. **The test failed before semantic
compilation**, at bean capture (`expected VALID, got INVALID`), reporting undefined
`imod:Anthroposphere`, `imod:Biosphere` and `imod:Technosphere`. Its selected-source
report does not include those roots. It never reached its Elevation eligibility,
consistency, bearer proof or strategy assertions. This is a selected-closure test
failure, not evidence that those concepts are absent from the whole fixture, and
not a reproduced product semantic/cache/transport defect. The original failed
report and log remain retained. All217 pinned input files were unchanged.
A dependency subset is not whole-worldview admission.

Source inspection narrows this extraction issue: the omitted names are declared
under the preserved `imod` source and are referenced by ontology-level `in domain`
expressions (for example economics/sociology/valuation use Anthroposphere,
genetics/ecology use Biosphere, infrastructure uses Technosphere). The existing
`selectSourceClosure` helper seeds roots and follows statement references, while
retaining ontology objects and their domain metadata. It does not separately seed
each retained ontology's domain expression. That is a test-only completeness gap
to investigate; no missing source declaration or need to remove a domain follows.

Reproduction, with explicit operator-owned paths:

```text
./mvnw -B -pl klab.services.resources -am test
  -Dtest=StrawmanBearerSourceAcceptanceTest#actualDeclarationSubsetEligibilityAndStrategies
  -Dsurefire.failIfNoSpecifiedTests=false
  -Dklab.test.strawman.directory=<preserved-compatible-imod>
  -Dklab.test.core.directory=<captured-core-directory>
```

The command above is shown across lines for readability; supply it as one command
or use the shell's continuation syntax. The harness pins source/core hashes before
testing and checks them afterward. No HTTP services or scientific Jobs were launched.

The first separate regression command passed23 bearer-rule cases, but two subtype
cases errored because the command omitted their required `klab.test.core.directory`.
The Resources/source-fact test was not reached. That failed launch is retained;
no product code or assertion changed. A corrected command with the same captured
core passed26 cases: the unchanged source-fact method and existing
`IdentityRequirementSupportTest`, `BearerIdentityRequirementTest` and
`SubtypeInherencyRegressionTest` separately (1 source-fact,14 identity,9 bearer and2
subtype cases; no failures/errors/skips). All217 pinned inputs remained unchanged.
Those passes verify bounded rules/source facts, not repair or waive the failed
selected-closure test. This PR remains documentation/evidence only, with no changes
to production code, tests, dependency policy or fixture inputs.

## Private domain-aware diagnostic arms

Two private offline diagnostic arms reused the existing native parser, clause-
preserving extractor and no-authority-waiver check without editing the original
test. The first seeded the retained ontology-domain references; the second also
retained the exact Terrain and Aspect declaration trees and their dependencies.
Both used the current Maven test classpath, pinned source/core bytes and fresh
private semantic state. Two earlier helper compilation failures (wrong import,
then querying type flags on Observable instead of its semantics) are retained as
diagnostic setup failures, not product failures. No public API was widened.

Both completed bean capture and compilation with `VALID` status, were OWL-consistent
and had no unsatisfiable named classes. **Neither was a clean semantic-acceptance
pass**: five compiler diagnostics remain in each result:

- Fundamental-type warnings for the Quality, Time and Space core aliases.
- `DESCRIPTION_BEARER_UNRESOLVED` for Physical/PhysicalSystem.
- `DESCRIPTION_BEARER_UNRESOLVED` for Period/Frequency.

The first three are emitted by `CoreOntology.validateRootDomain` as warnings, not
an OWL inconsistency result. Keep their exact wording and distinguish them from
the separate `isConsistent()` result; do not suppress or reinterpret them as passes.

In both arms, native proof reported a satisfiable effective Elevation bearer but
no Areal evidence and no entailment of Height's `(Areal or Volumetric)` requirement.
The recorded Elevation bearer constraints named Location. Canonical Elevation was
not authored abstract, while a fresh effective projection was abstract. The second
arm also recorded initial atomic resolution as abstract. A stale cached flag alone
therefore does not explain this subset result. No general cache defect is proved.

The supplementary Terrain/Aspect arm returned:

| Query in the same snapshot-focused scope | Effective status | Strategies |
| --- | --- | --- |
| Original `geography:Elevation in m` | abstract | none |
| `geography:Aspect` | concrete | `dependent.direct` |
| Explicit-bearer Elevation diagnostic | concrete | `dependent.direct` |
| Authored-abstract `physical:Height in m` | abstract | none |

In the first, smaller arm, Aspect was absent from the selected declarations and
resolved to Nothing. That is retained as a subset limit, not an Aspect product
failure. The supplementary arm establishes the concrete Aspect control.

Source inspection also distinguishes the preserved Terrain `emerges from` clause
from the description-derived evidence claimed for the older named source revision:
`ReasonerService.registerEmergent` records trigger descriptors; that method does
not add an Elevation-describes-Terrain bearer axiom. This is a boundary observation,
not permission to add such an axiom or alter the worldview's scientific meaning.

Both arms preserved all217 source/core inputs and all9,828 current test-classpath
input hashes. Their two recorded JVM identities were independently absent afterward,
with no corresponding live-owned visible windows or unrelated process/window
changes. No HTTP services, model evaluations or scientific Jobs were launched.

These private subsets corroborate PR120's served query-specific rejection. They do
not prove whole-worldview completeness, select a model, establish correct scientific
meaning, repair all compiler diagnostics or reproduce the original Job's complete
failure chain. The original fixed query and failed receipts remain unchanged.

## Follow-through: actual fixture source and native admission gates

The investigation is not waiting on routine authorization. Five further offline
arms traced the actual unchanged `python.fixture` source through native parsing,
source validators, the public Reasoner validation entry, source-publication admission
and selected-model preparation. They retain the five compiler diagnostics above.

The original namespace parses with no diagnostics. Native authoring and executable
validation with a Terrestrial Region execution context both report
`MODEL_OUTPUT_UNRESOLVED` at document `python.fixture`, offset95,length24, on its
Elevation primary. Original source SHA-256 remains
`9bc51d4fa89c20136fe6b0951a4840120dce0da948156a08203868fb1b92db70`.
Adding context to the validation request does not repair source admission.

An explicitly labelled **in-memory exploratory snapshot**, not a new accepted
fixture, adds an explicit Terrestrial Region bearer to that one model primary.
It preserves the exact `[100 + 23.25]` expression and all other namespace text;
its SHA-256 is `04c3f69e58fc48f734043d84d5ace49a58f8d5a9ce176a53fc83525111f423d8`.
Neither snapshot is evaluated or written back to the fixture.

| Native check | Original source | In-memory explicit-bearer snapshot |
| --- | --- | --- |
| Parser diagnostics | none | none |
| Authoring/executable source diagnostics | `MODEL_OUTPUT_UNRESOLVED` | none |
| Public validation source-semantics flag | false | true |
| Production `sourceAdmission` gate | rejects | accepts source-only receipt |
| Selected model `validatedSourcePreparation` | no receipt | receipt present |
| Executable hash during preparation | unchanged | unchanged |

Both public validation responses have status COMPLETE, so **COMPLETE alone is not
successful admission**: the original contains an Error notification. Both also
have incomplete namespace-wide execution footprints: the namespace contains the
separate opaque Java FixtureControls expressions. The exploratory source-only
receipt is not used as a whole-namespace execution certificate; the selected
constant model goes through its separate preparation gate. No catalog is written.

The exploratory snapshot has a different generated model URN. It cannot silently
replace an existing model/handle/Job identity or count as a passing original
acceptance. Original receipts, sources, numerical oracle and scientific algorithms
remain unchanged. No model has been executed, data read back or activity delivered.

All218 pinned source/core/model inputs and9,828 current-classpath input hashes per
arm stayed unchanged; all five additional recorded JVM identities were independently
absent, with no matching live-owned visible windows or unrelated process changes.

PR120 now contains three self-contained native source-admission cases. Together
with its seven earlier diagnostics and27 existing gate/index/source-boolean cases,
37 focused tests pass locally. They exercise parser/compiler and the real admission
gates without evaluating expressions or modifying the original fixture. Final-head
CI is required for that expanded suite.

The read-only served-source audit completed against the actual full private service
worldview, not this subset. The retrieved original source hash matched exactly,
with no parser notifications and no transported namespace admission receipt.
Authoring and executable-with-Region validation both returned the same
`MODEL_OUTPUT_UNRESOLVED` Error, source semantics incomplete, and matching source
hash. It used one retrieval, two validation calls and zero sessions, contexts,
publications or scientific Jobs. Runtime context listing and HTTP closure passed;
all14 JVM identities were independently absent and3,905 backend hashes unchanged.

This confirms an operational incompatibility of the preserved synthetic fixture
with current native source admission, not only a subset-harness artefact. A separate
opt-in versioned fixture migration is being implemented under PR118. Original and
v2 fixture evidence remain preserved; numerical expression/oracle remain unchanged.
Any migrated fixture is labelled as a new synthetic contract, not a passing original
request or scientific reproduction. Its native and real-service admission preflight
must pass before a fresh bounded execution. No shared worldview, scientific algorithm
or admission guard is changed. This is continuing technical work within the existing
scope, not an established external or routine-authorization blocker.

## Decision gates

1. Compare actual native closure evidence with the existing pinned-source contract.
   Missing evidence remains unresolved; an inconsistent closure is not a proof.
   Domain-aware extraction and unchanged Terrain/Aspect controls are now captured;
   preserve the original failed extraction. Whole-worldview completeness and the
   five compiler diagnostics remain outside a clean acceptance claim.
2. If a non-scientific admission/cache/transport defect is reproduced, document its
   exact boundary and regression before proposing a separately scoped fix.
3. If the source cannot prove the obligation, preserve the failed original request.
   Obtain a separate explicit decision before any query/model/worldview/semantic
   change; do not add a bearer, relabel abstractness or disable guards automatically.
   Exploratory source/query variants must remain labelled as such, never a passing
   rerun or reproduction of the original question. No shared scientific source or
   production semantics is changed by these in-memory checks.
4. Any later genuine numerical/notification acceptance still needs its own reviewed
   correction, current source-matched artifacts, bounded fresh-run authorization
   and independent cleanup. Successful strategy inference alone is insufficient.
