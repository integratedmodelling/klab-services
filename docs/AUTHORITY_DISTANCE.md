# Configured authority semantic distance

The shared `Authority.semanticDistance(configurationId, targetId, candidateId)` hook participates in
the existing Reasoner semantic-distance routes. No LAND-specific HTTP route is needed. It answers
the directional question: **can the candidate supply the target?** A positive overlap or symmetric
similarity score is insufficient for this purpose.

## Provider contract

`SemanticDistance` carries `DistanceStatus`, a bounded integer distance and explanations:

| Status | Meaning | Matching behavior |
| --- | --- | --- |
| COMPATIBLE | Candidate is admitted for target; 0 means equivalence, 1..100 ranks specializations. | Use the provider distance. |
| INCOMPATIBLE | Provider has established incompatibility. | Reject this match. |
| UNKNOWN | Evidence, policy or supported semantics cannot establish resolution. | Reject this match without claiming disjointness. |
| UNSUPPORTED | Provider does not implement this comparison. | Retain ordinary asserted-hierarchy matching. |

All statuses except COMPATIBLE use distance -1. The default hook is UNSUPPORTED, preserving legacy
providers. Providers supporting this operation advertise `Capabilities.isSemanticDistanceSupported()`.
The normal integer Reasoner result cannot distinguish UNKNOWN from INCOMPATIBLE; its negative value
means the candidate cannot currently resolve the target. The typed provider result retains the reason.

## Dispatch and composition

The authority identity resolver dispatches only identities it materialized in the current live
binding. It uses canonical provider codes and the exact provider-held configuration handle; caller
metadata or a namespace spelling cannot select a provider. Different bindings return UNKNOWN even
when their provider URNs match. Cross-binding mediation needs a separately specified, explicit bridge.
Provider exceptions or null responses become UNKNOWN, not hierarchy fallback.

`SemanticMatcher` delegates stripped predicate heads, authority identity traits and inherent/modifier
identity comparisons. Ordinary observable-head, contextualization and inherency restrictions still
apply. A compatible authority identity cannot turn one measurable observable into another. The
provider's 0..100 head distance is scaled by the existing head factor (50); trait distances are added
at the existing predicate level. Reflexive identity comparisons remain zero without consulting
provider policy, including identity traits inside modifier fillers. Candidate trait alternatives are
checked independently: UNKNOWN or INCOMPATIBLE rejects that pair, while UNSUPPORTED allows only
that pair's ordinary hierarchy fallback. An unrelated cross-binding failure cannot veto another
valid trait. The same comparison applies to traits within inherent and other modifier fillers;
legacy hierarchy scores, ordinary extra-trait restrictions and filler head checks remain in place.

`CachedAuthority` translates its stable bridge handle back to the transient provider handle and
forwards every comparison. Distance is deliberately neither persisted nor cached. Aggregate semantic
distance caching is bypassed while authorities are configured because a comparison policy can change
without changing OWL knowledge revision. A future policy-revision contract can narrow that cost.

Accepted captured environments can call the owned live provider when interactive authority access
is enabled. Candidate compilation remains replay-only: a provider advertising distance returns
UNKNOWN there until distance capture/replay is specified. Legacy providers retain UNSUPPORTED.
This boundary prevents candidate compilation from consulting mutable live providers.

## Provider policy and remaining work

Tolerances and thresholds belong to each provider configuration, not global Reasoner settings.
Providers must retain definition thresholds separately from decision/uncertainty policies and explain
their decisions. LAND currently supplies exact structural/interval entailment only. Unknown fuzzy
quantity relationships are not assigned an invented score.

Still to specify: cross-binding mediation, policy-revision cache fingerprints, captured distance
replay, remote standalone provider transport, cancellation/timeouts and richer explanation transport
through the normal integer matching API. No fuzzy arithmetic or general disjointness solver is
introduced by this shared hook.

Complex authority terminals retain a bounded maximum of 131072 code characters (raised from 2048
for typed rule envelopes). Bracket/backslash escaping remains unchanged; no truncation or lossy
hash-only reference is used. The LAND rule codec additionally enforces its own 128 KiB bound.
