# Semantic/query-contract review: preserved Elevation acceptance

Status: read-only review in progress, based on integration `5785c1baa`.
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

## Decision gates

1. Compare actual native closure evidence with the existing pinned-source contract.
   Missing evidence remains unresolved; an inconsistent closure is not a proof.
   First obtain a complete test-only source dependency closure while preserving
   all retained clauses and refusing authority waivers; preserve the original failed
   extraction rather than editing assertions or source declarations to pass.
2. If a non-scientific admission/cache/transport defect is reproduced, document its
   exact boundary and regression before proposing a separately scoped fix.
3. If the source cannot prove the obligation, preserve the failed original request.
   Obtain a separate explicit decision before any query/model/worldview/semantic
   change; do not add a bearer, relabel abstractness or disable guards automatically.
4. Any later genuine numerical/notification acceptance still needs its own reviewed
   correction, current source-matched artifacts, bounded fresh-run authorization
   and independent cleanup. Successful strategy inference alone is insufficient.
