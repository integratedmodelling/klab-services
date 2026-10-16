# Identity requirements and resolution eligibility

Owner-approved distinction, 2026-10-16: explicit `abstract` is an independent
restriction. A `requires identity` clause additionally makes an observable
expression abstract until concrete compatible identity evidence satisfies it.
This applies to substantial and dependent observables, including processes.

For example, `process SpecializedProduction requires identity ProductIdentity`
is abstract without an identity and can become concrete when qualified with a
concrete identity specializing ProductIdentity. Adding `abstract` before `process`
keeps the expression abstract even after satisfying that requirement.
ArtifactProduction and ArtifactModification remain abstract broad families.

The existing ODO isAbstract annotation and declaration flag keep their independent
meaning. The reasoner computes effective status on detached expression projections
at atomic resolution, ordinary declaration composition and assisted-builder
composition. It does not change the canonical declaration, create individuals,
or instantiate observations. Worldview concepts remain universals.

Required roots are retained through asserted and classified necessary named
superclass/equivalence closure, including requirements reached by inferred parents.
When such closure is unavailable and identity requirements exist in the semantic
graph, absence of requirements cannot be certified and the expression remains
unresolved. Repeated and inherited requirements are conjunctive. Compatibility is
proved against the complete filler: an identity may be known to specialize a union
without proving one named branch individually. Admission must then hold for every
possible alternative; a proved admitted branch also suffices. An intersection
filler requires one identity satisfying the entire conjunction. Separate identity
witnesses cannot jointly fill one intersection.
Identity evidence is limited to supplied concrete identity predicates and inherited
named concrete hasIdentity fillers. Anonymous existential witnesses, abstract roots,
query selectors, transformed predicates and namespace matches do not suffice.
Independent abstract heads and abstract attributes remain binding.

Compatibility requires satisfiability and proved OWL subsumption. When the backend
cannot establish these, the requirement remains unresolved. Disjunctive superclass
branches containing requirements currently remain conservatively unresolved rather
than flattening or arbitrarily selecting branches. No new semantics are introduced
for requiresRealm, requiresAttribute or requiresExtent by this change.

Authority requirements use configured worldview bridges. Supplied authority
identities must already be admitted by the current captured resolver, with complete
hierarchy evidence; an ordinary identity cannot bypass an authority-specific root.
This mechanism makes no provider calls and changes no authorization or fallback
rules. Existing authorized lookup/materialization remains responsible for admission.
Requirement status is recomputed in the current semantic generation; the compiler's
configuration revision changes to invalidate prior compilation evidence.

Public operand, hierarchy, description/bearer/semantic-clause, relationship endpoint,
applicability, influence and generalization traversals expose the same effective
eligibility for named observables as resolution. Qualified results use captured
syntax where available to preserve the scope of separately supplied identities.
Internal compiler declarations and preliminary syntax descriptors keep their
independently authored flags; those are not effective-resolution status reports.

Tests: IdentityRequirementSupportTest, AuthorityIdentityResolverTest and
SemanticEnvironmentFactoryTest.identityRequirementsSurviveCandidateCompilationAndBothCompositionPaths.
The last test uses private production compiler actors and exercises ordinary and
assisted composition without starting a live service.
