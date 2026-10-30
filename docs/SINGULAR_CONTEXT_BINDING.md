# Singular context binding: bounded S7a capability

The executable subset references the current already observed singular Subject/Agent and
runs one native quality **OBSERVE** producer. The child has the same observable and full
support as the enclosing request. It travels through ordinary model resolution and keeps
the same actual context identity; it cannot fulfill a request in another bearer or support.

```text
reference $context to known
within known as ctx {
  observe $this within $ctx to child
  yield child
} into bound
yield bound
```

The source grammar and portable ContextPlan already existed. Lowering recognizes this
closed shape at rank 1 or higher. `$context` elsewhere remains the context Observable;
only this reference form denotes the actual scope context Observation. Semantic captures
are never used to select an individual. No callback function or new reference API is added.

The Reasoner's complete OWL native bearer clause proof is required; containment admission
alone is insufficient. A typed SingularContextBinding carries context identity, native
observable, support, Reasoner identity and knowledge revision through ObservationStrategy
and Actuator JSON. This is trusted Reasoner-to-service operational transport, not a signed
certificate accepting arbitrary user-created bindings. Model validation and its receipts
remain on the ordinary production path. No persistent cache or reusable binding is added.

Binding lookup is lazy in the Resolver, after rank-zero candidates fail to cover the
request. Matching reads already available scope semantics without fetching an observation,
creating one or discovering members. A fully successful Tier-0 model/data candidate never
reads the later candidate's binding or operations. Inaccessible, stale, cancelled or failed
attempt bindings fail closed. Runtime rechecks before allocation, staging ownership links
and execution, including initialization. The source context must have a positive identity,
nonempty support and an existing initialization/event timestamp, not registration alone.

This capability requires exact context/request support equality. It does not choose a
clipping or extent-selection policy. Failure and cancellation do not become successful
empty output. A cancelled bound execution cannot publish SUCCESS or commit. A request-local
bounded active-candidate guard is cleaned in finally; it is not a general recursion engine.

## Explicitly unavailable

A same-context, same-observable RESOLVE can reuse the unresolved root identity, creating
Q -> strategy -> Q in the existing graph. This slice rejects it as no progress, in both
lowering and Resolver admission, rather than publishing a cyclic dataflow. A future
continuation/result representation must express a native child result without fabricating
outer fulfillment. Different context selection and different child result obligations are
also unavailable until that representation is implemented and tested.

Discovery, collective iteration, certified member snapshots, revision-aware persistent
reuse, partial-support scoped composition, regional mapping, exact union, merging, identity
policy, scientific reducers and consequence execution remain future stages. Existing cohort
membership is not a producer-result certificate. See COLLECTIVE_COMPLETION.md and the
resolution/observation strategy designs for those separate contracts.
