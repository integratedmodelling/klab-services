# Model and observation admission

Admission is the set of checks made before a discovered model or an incoming scope can be
used. There are several independent decisions: permission to see a model, validity of its
source, availability of its implementation, ownership of a planning focus, and permission
to execute against current observations. Passing one does not imply the others.

This document describes the implementation in this repository. See also
[resolver internals](RESOLUTION.md), [observation strategies](OBSERVATION.md), and
[conditional model contexts](CONDITIONAL_MODEL_CONTEXT.md).

Post-commit CHANGE submission follows the same distinction between existence and scientific
completion: a positive result ID alone cannot justify APPLIED. The handler requires typed
RESOLVED evidence or separately loaded complete native reuse evidence, current native lifecycle,
compatible CHANGE/observer semantics and complete resolved support. Optional typed NO_MODEL is
nonfatal; absent or partial completion remains deferred. Recomputations retain native/cycle/source
admission even when duplicate receipt metadata
is present. See the [admission audit repair checkpoint](CONSEQUENCE_ENGINE_ADMISSION_AUDIT.md)
for local verification and the remaining live acceptance boundary.

## The model path

1. **Discover visible candidates.** Resources checks catalog readiness, user permissions,
   namespace/project visibility, scenarios, and the requested observable. A private model
   requires the matching `ResolutionNamespace`; project-private models require the matching
   `ResolutionProject`. These constraints express lexical visibility, not transaction ownership.
2. **Prove the source snapshot.** The Resolver asks `ModelValidationGate` for a
   `PreparationReceipt` through `validatedSourcePreparation`. The saved namespace must be
   present, active, and free of blocking/deferred validation. Admission binds the source
   timestamp and digest, executable model digest, and Reasoner semantic snapshot. A matching
   publication receipt may be reused; otherwise the source is validated against the selected
   semantic snapshot. A preparation receipt permits prerequisite preparation, not execution.
3. **Prepare prerequisites.** `ResolutionCompiler.prepareCandidate` asks Runtime to resolve
   the candidate's contextualizers, components, and resources under the model's namespace
   and project. Missing prerequisites or error notifications reject the candidate. The
   preparation cache includes the model and its scope witness; it is not a global model grant.
4. **Validate the installed implementation.** `validatedPreparableAdmission` checks the
   executable model with installed methods available. It returns an `AdmissionReceipt`,
   including any retained semantic obligations. The Resolver checks that preparation and
   installed admission describe the same source/production snapshot before ranking.
5. **Resolve dependencies and compile.** Mandatory dependencies must themselves obtain
   relevant coverage. Entering an explained subject changes the context, so the Resolver
   prepares and validates again where needed. Context-dependent obligations and pending
   classifier/member-quality proofs still have to be discharged by their corresponding
   resolution paths. A catalog hit or a pending obligation alone cannot authorize execution.
6. **Execute and commit.** Runtime verifies the applicable model proof and actual inputs.
   Operative lifecycle checks and transaction ownership remain necessary. Only successful
   work selected for the dataflow is staged and committed.

`PREPARABLE` catalog discovery deliberately includes candidates whose prerequisites can
be prepared. It does not certify them as installed or executable. Compatibility/admission
precedes semantic distance and ranking.

## Receipts and semantic cycles

| Evidence | What it establishes | What it does not establish |
| --- | --- | --- |
| Namespace/source validation | Source semantics against a particular Reasoner snapshot | Installed methods, context suitability, or execution success |
| `PreparationReceipt` | Stable source/model snapshot for controlled prerequisite preparation | Execution permission |
| `AdmissionReceipt` | Executable model validation and its retained obligations | Completion of unresolved dependencies or pending proofs |
| Resolution-cycle binding | Runtime, context, cycle, and retained semantic binding | Ownership of an arbitrary observation supplied by a caller |

`ModelValidationGate.matches` rechecks receipts against the model and semantic snapshot.
`sameSnapshot` compares the preparation and executable contracts. A source edit, executable
change, or semantic-generation mismatch invalidates reuse. Entering another legitimate
context is handled by checking each receipt in its own scope, rather than treating a receipt
as transferable authority.

The cycle-bound Reasoner facade sends semantic operations, including capabilities used by
admission, to the retained semantic actor. It propagates the authenticated context, transaction,
focus, and cycle. It must not silently substitute a current-generation Reasoner when the
retained actor is unavailable. A semantic receipt is not an authentication credential.

## Planning ownership and operative lifecycle are different

| Observation state | Where its identity is held | Permitted use |
| --- | --- | --- |
| Unassigned (`-1`) or query sentinel (`0`) | No owned observation identity | Cannot be an admitted scope focus |
| Registered planning candidate (ID below `-1`) | Root transaction's provisional registry | Planning and semantic admission within that owned transaction |
| Staged birth | Canonical object in the transaction graph | Native lifecycle checks can recognize the owning transaction's new asset |
| Committed observation | Durable knowledge graph | Use only while authoritative lifecycle status is current |
| Failed/closed transaction candidate | No active registration authority | Cannot be restored as a planning focus |

`RuntimeService.register` allocates a temporary ID and registers a candidate without putting
it in the transaction graph. This is intentional: candidates can be rejected during model
selection, and rejection must not leave durable observations behind. The provisional registry
is shared by scopes and child attempts belonging to the same root and is removed when that
root transaction is unregistered.

`ServiceContextScope.getObservationForAdmission` can retrieve such a candidate from an active,
registered, non-failed transaction. `getObservation` continues to require operative lifecycle
status. In particular, planning registration alone does not make
`KnowledgeGraph.isObservationCurrent` true, create a staged birth, or authorize scheduling.

## Restoring a scope across HTTP

The receiving service authenticates the request and resolves its context. `ScopeManager`
restores the cycle binding, transaction, observation path, observer, and focus from transport
headers. A negative planning focus is not included in the durable observation path.

For a prospective focus, the receiving service asks the owning Runtime through
`GET /api/v1/asset/{id}/admission` (under the Runtime service's context path). Runtime checks:

- An active transaction is attached, registered by object identity, and not failed.
- The transaction exposes its server-held execution scope.
- Authenticated user, context, observer, and resolution-cycle binding match that owner.
- The requested identity is actually retrievable in that transaction's admission scope.

The response is a detached transport observation. Its ID, headers, or body cannot create
ownership. Missing or foreign ownership fails rather than using a client cache.

Ordinary `GET /api/v1/asset/{id}` reads retain lifecycle validation. Client currentness checks
use that ordinary endpoint, never the planning-admission endpoint. This separation prevents
a successful planning lookup from certifying a candidate as executable.

The admission endpoint and its client must be deployed together. Rebuild the shared API,
common, and core-services libraries and restart the services using them, including Runtime,
Reasoner, Resources, and Resolver. An older Runtime cannot serve the new planning lookup;
clients must not work around that by accepting a cached observation or bypassing ownership.

Anonymous/local operation follows the same ownership checks using the identity established
by service authentication. A single available service per type does not waive them. Neither
additional services nor authenticated network credentials are a remedy for missing
transaction-owned planning identity.

## The cotechino regression

The November 3, 2026 live trace found both `staging.vxii.basic.terrestrial-region-explainer`
and `staging.vxii.basic.each--freshwater-region-instantiator`. Discovery and private-namespace
visibility worked. During dependency admission, the Reasoner tried to restore temporary
focus `-4` (then `-5`). Runtime found a registered planning candidate but passed it through
operative lifecycle validation. The candidate was not yet a graph vertex, so it disappeared
from the lookup.

The resulting HTTP failure became `no stable source admission`, then a mandatory-dependency
failure, and finally `NO_MODEL`. Those messages described the downstream consequences,
not the absence of a source model. The repair separates owned planning lookup from current
asset lookup. It does not stage rejected candidates or relax lifecycle validation.

The next live attempt exposed a separate initialization failure: `Collective consumer has no
actual input plan`. The terrestrial explainer has dependencies but no computation of its own,
so it has no executor from which the scheduler can obtain its input bindings. A committed
portable plan also cannot serve initialization before durable IDs have been allocated.
`CompiledDataflow.store` therefore registers the actual input plan in the transaction even
for dependency-only consumers. The scheduler validates cohort edges against that plan during
initialization; portable snapshots retain the same bindings after ID allocation. Cohort edges
carry the temporary producer identity initially and are rebound to its durable identity before
commit. Registering an input plan does not invent a contextualizer or bypass cohort validation;
failure clears the transaction's plans and rolls back the saved snapshot metadata.

When diagnosing similar failures, start with Resources' actual candidate query, then follow
the first admission/HTTP exception across Resolver, Reasoner, and Runtime logs. A client
submission-success message is insufficient evidence of explanatory coverage: inspect the
resolution outcome and resolved coverage as well.

## Implementation map and regression checks

- `ModelValidationGate` in `klab.core.services`: source, installed-method validation, receipts,
  semantic snapshots, and receipt comparison.
- `ResolutionCompiler` in `klab.services.resolver`: preparation, ranking, dependencies, and
  scope propagation.
- `ModelVisibility` in `klab.services.resources`: permission and lexical visibility checks.
- `ServiceContextScope` and `ScopeManager` in `klab.core.services`: provisional ownership,
  lifecycle lookup, and incoming scope restoration.
- `RuntimeServerController` in `klab.services.runtime.server`: authenticated asset/admission
  endpoints and transaction-owner validation.
- `ClientKnowledgeGraph` and `RuntimeClient` in `klab.core.common`: separate remote planning
  and current-asset reads.
- `DigitalTwinImpl.TransactionImpl.currentStatusFor` and `KnowledgeGraphNeo4j` in
  `klab.services.runtime`: canonical staged births and authoritative lifecycle status.

`ProspectiveFocusTransportTest` exercises the pinned HTTP scope headers and Runtime
admission, including rejection for foreign users, contexts, observers, cycles, missing IDs,
and missing, failed, or unregistered transactions. It also checks that planning admission
does not make a candidate current or add it to transaction assets. Lifecycle and client
currentness tests separately protect ordinary asset reads.
