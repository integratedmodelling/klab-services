# Model dependency admission and semantic distance

This document describes the separate 2026-10-20 validation extension. Test and review
status is recorded with the implementation handoff; it does not claim deployed or
live acceptance. The bearer contract is defined in [Identity requirements](IDENTITY_REQUIREMENTS.md).

## Semantic source checks

A model dependency must be compatible with the observation being modeled. The
primary substantial supplies the modeled subject; a dependent primary supplies its
established effective bearer. A classifier's explicit application operand supplies
its subject, independently of the bearer of the quality that its predicate describes.

For a dependent/classifying dependency, the modeled subject must prove membership
in every applicable inherited bearer bound. A known subtype is allowed. A proved
impossible conjunction is reported as `MODEL_DEPENDENCY_INCOMPATIBLE`; missing proof
is `MODEL_DEPENDENCY_UNRESOLVED`. Possible overlap is not proof. Diagnostics belong
to the dependency's source occurrence, with document identity and lexical range.

The modeled subject can discharge an inherited bearer-directed identity category
requirement through a detached eligibility projection. It does not change the
shared dependent declaration or transfer process identities. Abstract category
roots can constrain the bearer; explicit authored abstractness and actual concrete
identity fulfillment remain separate obligations.

An existing, proved `affectedBy` relation permits an occurrent to use an affected
quality with its own bearer. This preserves the process contract even when that
quality's bearer differs from the process context. The quality must independently
meet its identity and concrete/generic obligations; the effect relation cannot
waive them or lend it the process's identity.

A necessary source-bound bearer constraint can establish category eligibility
without asserting an instance. Applicability alone is not identity evidence.
Nonsemantic learners retain their existing nonsemantic declaration contract.

## Templates versus executable models

Authored abstract primaries and explicitly generic/pattern or query-selector occurrences remain valid
template declarations. Deferred dependency obligations are marked `MODEL_DEPENDENCY_TEMPLATE`.
An ordinary nonabstract declaration with an unresolved identity obligation is not
automatically promoted to a template.

Before matching a template to an executable request, the primary can be bound only
where its capability head and a proved bearer refinement allow that binding.
A request matching a secondary/dereified output does not replace the model's primary
process or substantial. Dependencies of the actual runtime model are revalidated;
retrieving a newer valid source cannot hide invalid dependencies in an older runtime
model. Unbound abstract/generic primaries cannot obtain executable admission.
The validation request and matching response explicitly distinguish authoring from
executable admission. Executable validation permits no template exemption, including
for a concrete primary with an unbound generic dependency; an authoring receipt
cannot be replayed as an executable receipt.
Secondary outputs undergo the same bearer proof and detached eligibility projection
as dependencies. A concrete modeled Region can fulfill a secondary quality's
bearer category obligation without changing that quality's shared declaration.
Unbound secondary outputs cannot obtain executable admission.
The unbound-syntax check covers semantic expression positions (heads, traits, roles,
bearer/other modifiers, comparison concepts, boolean operands and about targets).
It does not define binding rules for concept-valued operands inside value operators.
Those operands can express category filters rather than observations to instantiate;
their executable binding contract remains an explicit limitation, not a guarantee
provided by model bearer admission.

## Guarantee point before ranking

Catalog publication uses `Reasoner.validateDocument` and requires a COMPLETE,
error-free reply matching document/source/version identity. Unavailable, failed,
stale or mismatched validation is not admission. The document catalog validates
outside its publication monitor and rechecks source version before publishing a
namespace generation. Existing/historical entries are not exempt: discovery and
resolver prioritization revalidate before invoking distance.

Discovery's initial core-head lookup is an over-approximation, not a distance or
compatibility proof. Source and request-bound validation produce a knowledge-revision
receipt. Executable ranking uses the revision-aware distance operation; the backend
pins that accepted generation, checks the receipt and only then calls arithmetic.
A changed generation stops matching. Observer hierarchy arithmetic uses the same
receipt. The strict `/distance/validated` route requires the updated server; it
cannot silently fall back to a legacy server that ignores an optional parameter.

The public distance entry also recomputes canonical request eligibility before
cache/reflexivity shortcuts. Caller type flags cannot turn an incomplete quality
into a predicate exemption. Abstract predicate/identity categories remain legal
structural comparisons. Pure internal metric reflexivity is not eligibility.
A negative integer admission score alone does not distinguish unknown membership
from proved incompatibility; source validation provides that distinction.

Collective bearer hierarchy traversal preserves the member's collective mode while
walking canonical parents. Collective-to-collective subtype matching retains the
same hop cost as singular matching; mixed collective/singular matching is rejected.
No ranking weights are changed.

## Operational and remaining limits

The owner's Resources operational-startup behavior is unchanged. The approved
namespace-isolation policy keeps successfully validated namespaces discoverable
while withholding invalid, unavailable or stale namespaces. Source documents and
their diagnostics remain inspectable. Corrected source must be revalidated before
re-admission; prior catalog entries must not bypass a valid-to-invalid edit.
Namespaces currently have no interdependencies, as confirmed by the language owner.
The legacy SQL catalog's replacement is not made transactionally atomic by this
extension; matching still revalidates entries before distance. The document catalog
publishes a persistent withheld marker before deleting an invalid namespace's old
models, so failed cleanup cannot expose them. Unchanged complete rejections remain
withheld; transient unavailable/stale/failed validation retries after five seconds
on the next service check, or immediately after source/knowledge revision changes.
Storage failure that prevents safe withdrawal still blocks global readiness.

This is not validation of resource-produced data types or of composed computation
paths. Those intended contracts and their open decisions are recorded as pending
in [Semantic modeling](SEMANTIC_MODELING.md#pending-model-validation-contracts-2026-10-20).
Whole-worldview acceptance requires authoritative provider snapshots. Private
source subsets, eligibility/strategy tests and ranking tests are not executed
freshwater/elevation/slope contextualizations.

At the pinned Strawman `35a0df73` and staging source `basic.kim` SHA-256
`dc77e5e090d4b1112152d140edec3658b23fead1025c096d3a43be322206ece2`, the exact
cotechino model supplies `earth:Terrestrial earth:Region`. Elevation's complete
effective bearer is `earth:Crust earth:Terrestrial earth:Region`; the modeled subject
does not prove that narrower type. The source probe retains
`MODEL_DEPENDENCY_UNRESOLVED` at the Elevation occurrence (offset 4425, length 24),
rather than waiving the bound or claiming incompatibility. Choosing a narrower
model subject versus revising description-derived applicability is an owner
semantic decision. Bare Elevation eligibility does not settle it.
