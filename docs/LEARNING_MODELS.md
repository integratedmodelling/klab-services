# Learning models

This document specifies the k.LAB learning-model facility. It describes the facility's
behavioral contract; implementation status and unresolved integration decisions are tracked in
[LEARNING_MODELS_IMPLEMENTATION.md](LEARNING_MODELS_IMPLEMENTATION.md).

## Purpose

A learning model produces a reusable **Resource** as its principal result, in addition to the
normal observations described by its output observables. The Resource captures a learned
computation, including an adapter capable of applying it to new inputs. Learning and inference
are separate operations: consuming a learned Resource does not train it again.

Machine learning is the primary use: fit a classifier or regressor from observations, store its
trained state, and use the Resource to predict observations in other contexts. The contract is
independent of Weka or any particular machine-learning library.

Calibration is another use. A learner fits parameters of a dynamic model to observed behavior.
Its Resource exposes named, typed parameter values through a calibration adapter. A model using
that Resource can bind matching dependencies to those values before resolving its remaining
dependencies normally. Calibration and prediction use the same resource lifecycle, but their
adapters expose different operations.

## Declaration and identity

The declaration form is:

```text
learn <observable> [, <observable> ...] as <resource-id>
    observing <dependency> [, <dependency> ...]
    <contextualization actions>
;
```

The existing k.IM grammar supplies observables, dependency annotations, `named` aliases and
ordered contextualization actions. `as` names the resulting Resource, rather than changing the
meaning of the output observables. The exact identifier following `as` must survive parsing,
model projection, transport and execution independently of the model's generated URN.

A dependency annotated with `@predictor` is a learning input. Annotation arguments, including
optional weights, are passed to the learning engine without reinterpretation. An engine must
document and validate the arguments it accepts: a feature weight, sample weight and calibration
objective weight are different concepts. Unannotated dependencies may supply targets, ancillary
data or computations required by the chain; they do not implicitly become predictors.

The local resource identity is:

```text
local:<servicename>:<namespace>:<resource-id>
```

`servicename` identifies the owning resolver, `namespace` is the learning model's namespace, and
`resource-id` is the exact value after `as`, including dots if present. The service's internal ID
and the generated model name are not substitutes for these fields. The resolver validates identity
and ownership; a learner cannot redirect storage by changing the supplied builder's URN.

Every successful re-learning creates a new version under this stable resource identity, using
the normal Resource version and history mechanism. Failed attempts do not replace an earlier
version. A consuming execution records the concrete version it used even if its request referred
to the latest version.

## Predictors, targets and ordinary outputs

Learning receives resolved **observations**, not just observable declarations. Each bound input
has a stable schema name, data type, observation handle and annotation parameters. Ordered
predictors are separate from ordered targets; ancillary inputs remain available in the context.
Source declaration order supplies the initial schema order, which the Resource retains.

For supervised learning, target observations provide known outcomes. A target may be:

* computed by a contextualizer preceding the learner in the same model;
* supplied as an already resolved observation in the context;
* explicitly bound through a declared target mapping by name or semantics.

An output's `named` alias identifies its target binding. Explicit mappings may identify another
observation by name or semantics. Binding must produce exactly one compatible observation per
required target. Missing or ambiguous bindings are errors; collection iteration order must not
choose among candidates. Semantic matching uses the reasoner's compatibility rules, rather than
text comparisons of semantic URNs. Engines requiring no supervision receive an empty target list.

Binding a target is distinct from producing the normal model output. A target resolved in advance
must remain available as training evidence even if subsequent contextualizers write predictions
to an output of the same semantics. The learner must not silently train on its own predictions.
Resolving an external target must exclude the learning model being executed, so that requesting
its output cannot recursively select the same learner as the source of training evidence.

The runtime executes preceding target-producing actions before the learner and completes ordinary
output production through the model's contextualization chain. Learners may use the fitted
computation to supply predictions through that chain; an observation is not marked successfully
resolved merely because training completed. A multi-output learner explicitly identifies its
supported targets and resource outputs. Calibration can use target trajectories or several
observations to fit parameters without treating each calibrated parameter as a supervised label.

Predictors and targets must refer to compatible sampling domains. The engine must declare how it
aligns geometries, times, object identities, missing values, units and categorical encodings.
Equal array lengths do not establish alignment. Training-time transformations required at
inference are part of the learned Resource, including feature order and category dictionaries.

## Learning contextualizers

A specialized learning contextualizer receives:

* resolved, named predictor observations and their annotation arguments;
* resolved, named target observations and their binding arguments;
* a Resource.Builder initialized by the runtime for the resolver-owned destination;
* the context scope for data access, diagnostics and runtime services.

The Java extension contract is
`org.integratedmodelling.klab.api.services.runtime.extension.LearningContextualizer`.
Its `Input` record carries a name, an observation and an immutable shallow snapshot of parameters.
The `learn` method fills the builder and returns normally on successful fitting; failure is
reported by an exception. The runtime owns builder completion and resource submission.

The contextualizer selects the inference adapter and supplies its required parameters and
payloads. It also declares resource inputs, outputs or calibrated attributes using
Resource.Builder's existing schema methods. The runtime preserves model identity, semantics and
execution provenance; the contextualizer can add engine-specific metadata and diagnostics.
It must not publish a Resource or retain the builder after the invocation.

Learning is an operation over an explicitly chosen training context. Spatial sharding, temporal
transition scheduling or repeated observation reads must not accidentally refit and publish
separate Resources. Engines supporting distributed or incremental learning must explicitly
define aggregation, state ownership and finalization.

## Resource contents and reuse

A learned Resource contains enough information for its adapter to reproduce input interpretation
and inference without access to the original training context:

| Content | Contract |
| --- | --- |
| Adapter | Adapter identity and compatible implementation/model format versions |
| Schema | Stable names, data types, ordering, required/optional status and outputs or parameter attributes |
| Payload | Trained state or calibrated values, with portable payload references |
| Interpretation | Units, encodings, transformations and missing-data policy |
| Semantics | Original input/output observable URNs and relevant annotations retained as metadata |
| Provenance | Source model, resolved Resource versions, training context description and engine configuration |
| Diagnostics | Metrics, fitting warnings and reproducibility information supplied by the learner |

Resource input bindings use **name and data type**. Semantic equivalence is not required: a model
can apply a learned computation to a differently described but explicitly bound input. Names are
stable schema identifiers, not display labels. A `named` alias supplies one; an unnamed observable
uses its stable reference name. Duplicate names are errors. A binding must satisfy the adapter's
type, unit and encoding requirements; permissive numeric coercion must not conceal incompatible
data. Missing required inputs and ambiguous matches fail with binding diagnostics. Optional
inputs follow the stored adapter policy. Semantics remain available for interpretation,
provenance and validation even when binding is structural.

Prediction adapters consume observations and emit the Resource's declared outputs. Calibration
adapters expose typed named values. When explicitly using a calibration Resource to parameterize
a model, a compatible attribute binds the dependency of the same name; unmatched dependencies
are resolved normally. A matching name with an incompatible type is an error, not an instruction
to ignore the calibrated value. This behavior belongs to calibration-resource consumption and
does not globally change dependency resolution.

## Resolver lifecycle

The runtime stages a learned Resource and any associated payloads, validates the adapter and
schema, completes ordinary output computation, then requests resolver registration. The
operation reports success only when its outputs and learned Resource are available. Failure or
cancellation discards staged artifacts and leaves committed resource versions intact. If commit
spans services, retries must identify the same learning attempt and return the same committed
version rather than create duplicates.

The resolver owns the local Resource, its access rules, scope visibility and version history.
Existing temporary-resource persistence settings determine how long it survives. Resources are
available for IDE inspection, metadata editing, publication to a resources service, or deletion.
Publishing transfers all required payloads and records the relation to the local source version;
machine-local paths cannot be the only references in a published Resource. Editing or deleting a
resource must respect consumers using a specific version and the applicable retention policy.

Training does not automatically publish a Resource or make it globally discoverable. Its
visibility follows the submitting scope and authorized resource-management actions. Recorded
provenance and diagnostics must respect the access rules of the training observations.
