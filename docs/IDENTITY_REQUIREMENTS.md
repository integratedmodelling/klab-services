# Identity requirements and resolution eligibility

This contract supersedes the 2026-10-16 dependent-targeting rule. Authored
abstractness, permitted bearer types, and satisfaction of identity obligations
are separate semantic questions.

## Bearer-directed requirements (2026-10-20)

For a dependent observable (a quality or process), `requires identity R` constrains
the effective bearer of that observable. It does not assign R to the dependent,
and the dependent's own identity traits do not discharge this bearer obligation.
For a substantial, the existing requirement for a concrete identifying predicate
on the substantial remains in force. A process's native identities (for example
`physical:IntransitiveProcess`) remain identities of the process; they are never
transferred to its bearer.

The effective bearer combines an explicitly supplied `of` operand with inherited
necessary bearer restrictions that describe the same semantic bearer. Separate
imported existential restrictions retain separate witnesses; their fillers are
never intersected to invent a shared bearer. Every such witness must independently
establish the requirement under any universal bearer bounds. Applicability says which bearer types are permitted;
permission alone is not identity evidence. A necessary universal bearer restriction
can establish the identity category of the permitted bearer type; it does not
assert a bearer instance exists or supply its concrete identifying value. An override must respect every inherited
bound. A known specialization is permitted: Region specializes Location, so a
Location bound alone cannot reject Region or `each Region`. Collection status does
not change the member-type comparison.

Each independently inherited `requires identity` clause must hold (AND). A boolean
filler retains its scope: `A or B` needs proof of that whole alternative; `A and B`
requires one identity witness satisfying both categories. Two separate existential
identity witnesses do not establish that single conjunction. Identity categories
are matched by subsumption, never by name, namespace, or possible overlap.

An abstract required root is a category constraint. Declared or inherited bearer
evidence of that category, or of a specialization, can satisfy it without creating
an instance of the abstract identity. This is distinct from fulfilling a concrete
identifying value or an authority identity. Captured authority admission remains
mandatory; an abstract authority bridge is not an admitted identifying value.

Explicit abstractness remains binding on the declaration where it is authored.
`Height` can remain abstract while a non-abstract child becomes eligible after its
bearer requirement is satisfied. Neither a category constraint nor an abstract
parent automatically makes that child explicitly abstract. Missing evidence is
unresolved, and is different from a proved impossible bearer conjunction. Neither
case establishes eligibility.

Worked examples and counterexamples:

| Requirement and evidence | Result |
| --- | --- |
| Height requires `(Areal or Volumetric)`; bearer Region has Areal | The bearer obligation is satisfied, even though Areal is an abstract category. |
| Same requirement; bearer has an identity specializing Areal | Satisfied by subsumption. |
| Same requirement; bearer has `(Areal or Lineal)` | Unresolved: overlap at Areal does not prove the required alternative. |
| Parent requires A; child separately requires B; bearer has only A | Unresolved: the inherited B obligation remains. |
| One clause requires `(A and B)`; bearer has separate `hasIdentity A` and `hasIdentity B` witnesses | Unresolved unless a single qualifying witness is proved. |
| The dependent itself is qualified with Areal, but its bearer has no qualifying identity | Unresolved: the qualifier is on the wrong target. |
| Explicitly abstract Height with a qualifying bearer | Still abstract. |
| Location bound; Region specializes Location | Compatible specialization. |
| A candidate bearer is proved disjoint from an inherited bound | Incompatible; reject it. |
| No subtype proof and no disjointness proof | Unresolved; do not report a proved incompatibility or infer satisfaction from possible overlap. |

At strawman `35a0df73`, `earth:Region` declares Areal, but `earth:Location` is
Geostationary Feature and has no declared or inherited spatial identity.
One qualifying Region does not prove that all Locations qualify. Elevation also
describes Terrain, whose qualified Region bearer contributes a stronger effective
bound through the description closure. The compiled effective Elevation bearer
therefore exposes Areal evidence. Ignoring this derived evidence would wrongly
reject Elevation; inferring that Location itself has Areal identity would also be
wrong. An explicit plain Region override may broaden a more narrowly qualified
inherited Region and must not automatically be treated as compatible.

This change does not introduce automatic contextual replacement of an unspecified
bearer. Eligibility uses the actual declared, composed and inherited bearer
closure. Observation strategy matching consumes that effective eligibility through
the existing request representation. Live cotechino acceptance must still verify
the running build, strategy choice and actual dependency execution.

The current public status transport retains the existing effective `ABSTRACT`
projection for unresolved identity obligations. That flag on a detached request
is an eligibility report, not a new authored abstract declaration. Canonical
`isAbstract` and declaration flags remain independent. A separate explicit
completeness-status transport is not introduced here.

Predicate source applications are checked against their application domain and
independently authored/inherited bearer restrictions. A generated bearer of a
described/classified quality is not substituted for the substantial being
characterized. `PhysicalEnvironment` classifies Type of EarthCover but applies
to Location: characterizing Region must not require Region to specialize
EarthCover. Authored restrictions are retained separately during translation so
this distinction cannot waive a separately stated `within` obligation.
This source-validation distinction applies to predicate requests targeting an
enumerable substantial. Predicate-over-quality transformations keep their existing
clause handling.

This change does not alter the existing OWL representation of composed predicate
requests or their descriptor restrictions. The tested requests remain satisfiable;
it corrects source validation's choice of bounds. A general replacement of those
OWL relations is outside this change.

Requirements are collected through asserted and classified necessary superclass
and equivalence closure. Unavailable closure, unmaterialized complex bearer
syntax, or unavailable satisfiability/subsumption proof leaves eligibility
unresolved. No individuals are created, no authority lookup is triggered, and no
rules change for `requires realm`, `requires attribute`, or `requires extent`.

The source translator preserves boolean identity requirements as anonymous OWL
boolean fillers. General generated union/intersection classes currently use
one-way subclass definitions; demanding membership in such an implementation
class would be stronger than the source `A or B` expression. Only source boolean
syntax is translated this way. A named identity that merely specializes a union
is not equated with that union, and arbitrary imported categories are not expanded.

Regression coverage: `IdentityRequirementSupportTest`,
`BearerIdentityRequirementTest`, `SubtypeInherencyRegressionTest`, and the opt-in
`StrawmanBearerSourceAcceptanceTest`, plus the actual-parser/compiler boolean
regressions in `SourceIdentityBooleanCompilationTest`. Whole-worldview acceptance additionally
requires captured TAXA, CHEM and UN.M49 authority validation; a source dependency
subset cannot stand in for that or for live freshwater/elevation/slope execution.

The pending live procedure is recorded in [Cotechino bearer acceptance](COTECHINO_BEARER_ACCEPTANCE.md).

The separate model-source and pre-ranking admission boundary is documented in
[Model dependency validation](MODEL_DEPENDENCY_VALIDATION.md). Semantic distance
ranks admitted candidates; its reflexive score cannot discharge identity obligations.
