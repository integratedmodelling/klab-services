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

For supervised learning, each declared output defines a target slot. Its `named` alias is the
slot name; otherwise its stable reference name is used. Target acquisition uses this precedence:

1. An explicit observation binding under that name in the training request.
2. A preceding `set to` or contextualizer explicitly producing that target.
3. Ordinary resolution of the declared output observable.

Predictor and ancillary dependency slots use the same naming convention. Names identify slots;
ordinary resolution supplies their values according to the declared semantics. Explicit bindings
are validated against the declared type and representation. An invalid explicit binding fails
instead of falling through. Missing or ambiguous bindings fail; collection order never chooses
among several candidates. No additional target-mapping annotation is required.

Every required target and non-optional dependency must be resolved before fitting. Missing optional
dependencies remain absent, subject to the engine's declared requirements. Unsupervised engines
explicitly declare that they require no targets and receive an empty target list. Resolution is
checked independently from data completeness: the engine still validates missing values,
sampling alignment and adequate training samples.

Binding a target is distinct from producing the normal model output. A target resolved in advance
must remain available as training evidence even if subsequent contextualizers write predictions
to an output of the same semantics. The learner must not silently train on its own predictions.
All learning models are excluded from ordinary resolution. Previously generated learning previews
are excluded from automatic training-evidence acquisition; explicit reuse requires a deliberate
binding. Excluding learner models alone does not prevent an existing prediction from being reused
as evidence.

The runtime executes preceding target-producing actions before the learner. After fitting and
Resource commit, it regenerates the declared outputs using the exact committed Resource version,
by default. These predictions are inspection outputs, separate from the evidence. A multi-output
learner explicitly identifies its supported targets and resource outputs. Calibration can use
target trajectories or several observations to fit parameters without treating each calibrated
parameter as a supervised label; its inspection outputs are modeled quantities or trajectories
obtained using the calibrated parameters.

Predictors and targets must refer to compatible sampling domains. The engine must declare how it
aligns geometries, times, object identities, missing values, units and categorical encodings.
Equal array lengths do not establish alignment. Training-time transformations required at
inference are part of the learned Resource, including feature order and category dictionaries.

## Explicit training and observer-specific outputs

Training is a dedicated runtime operation, conceptually
`train(model, context, bindings, options)`, selecting an admitted learning model explicitly.
Ordinary observation requests, including explicit-model shortcuts, never trigger training. The
runtime orchestrates prerequisite resolution, preparation, fitting, commit and optional preview
generation. A result identifies the attempt, exact committed Resource version, preview observations
and separate fitting/commit and preview diagnostics.

A model-level `@learning(observer = ...)` annotation specifies the semantic Agent class for
generated outputs. Its value is an Agent concept expression or its semantic URN. An explicitly
configured learning-observer default from the active worldview is used when the argument is
absent. No class is invented from the model name; a missing or invalid class rejects training.
The target observable's own `observed as` clause continues to describe the training evidence and
does not specify the prediction observer.

The runtime creates or reuses an actual Agent observation for the model's stable learned-resource
identity in the current context. The semantic class describes its perspective; the actual Agent
distinguishes this model's outputs even when several learning observers share a class. Graph
identity and reuse preserve that actual observer, output slot and observation support. Predictions
are stored as `observed as <observer Agent class>` with the actual Agent in provenance. Resource
versions are provenance, not new observer classes.

Successive successful trainings may replace the current prediction values for the same model,
actual observer, output slot and support, retaining revision and activity provenance. Canonical
observations, training evidence and other observers' outputs are preserved. Failed preview
generation preserves the previous successful preview. A preview of an older Resource version
cannot replace one already generated from a newer committed version.

Inspection outputs are available through explicit selection of their learning observer. They do
not become ordinary fallback observations just because observer compatibility would permit it.

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

The runtime stages a learned Resource and its payloads, validates the adapter and schema, then
requests resolver registration. Each attempt has a stable ID. The resolver atomically allocates
the next Resource version and registers it; fitting does not hold the commit lock. Retrying an
already committed attempt returns the same version, including after later versions are committed.
Concurrent attempts produce distinct versions. Committed versions and their payloads are immutable.
Failure or cancellation before commit releases staged artifacts and preserves earlier versions.

Resource commit is a distinct provenance Activity, `RESOURCE_COMMIT`, linked to the initiating
training activity, actor, source model, input evidence and exact committed Resource URN/version.
It is an orchestration activity rather than an observation contextualization. Recording this
activity in the knowledge graph is a separate, retryable step when resource storage and graph
persistence belong to different services. A durable commit receipt permits reconciliation without
fitting again or allocating a duplicate version.

Resource commit and preview generation have separate outcomes. A committed Resource remains
available if preview generation or its graph recording fails. The result reports that partial
outcome explicitly; preview generation can be retried using the committed version. Successful
preview activities reference both the committed Resource version and its commit activity.

The resolver owns the local Resource, its access rules, scope visibility and version history.
Existing temporary-resource persistence settings determine how long it survives. Resources are
available for IDE inspection, metadata editing, publication to a resources service, or deletion.
Publishing transfers all required payloads and records the relation to the local source version;
machine-local paths cannot be the only references in a published Resource. Editing or deleting a
resource must respect consumers using a specific version and the applicable retention policy.

Training does not automatically publish a Resource or make it globally discoverable. Its
visibility follows the submitting scope and authorized resource-management actions. Recorded
provenance and diagnostics must respect the access rules of the training observations.
