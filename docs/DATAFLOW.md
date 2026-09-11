# Reconstructing executable dataflows from provenance

Status: initial design and API skeleton, September 2026. The document model and its structural
builder exist; live graph extraction, observation-language source generation, import and replay
do not yet exist through these entry points. This document develops the implementation plan in
[OBSERVATION.md, section 7](OBSERVATION.md#72-provenance-to-dataflow-builder), rather than defining
a second dataflow language. See also [RESOLUTION.md](RESOLUTION.md) for resolution and compilation.

## Two distinct dataflow contracts

This distinction is an architectural requirement, not two names for the same serialized object.

| Contract | Contextual resolution Dataflow | Graph-reproduction dataflow |
|---|---|---|
| Origin | Resolver response to a resolution request | Extraction from the knowledge graph's recorded provenance |
| Purpose | Perform the work needed for that resolution in the current graph | Reproduce the exported graph contents from scratch |
| Starting state | Existing observations, identities, coverage and context may be prerequisites | A fresh graph; required submitted inputs, definitions and dependencies must be supplied or explicitly obtainable |
| Contents | Chosen computations and contextualizations, including effects on existing observations | Ordered recorded work plus definitions, bindings and dependency closure across the exported graph |
| Completeness | Complete for the requested resolution given the existing context | Complete for reconstruction; unexplained references to old runtime IDs make it incomplete |
| Representation | Existing portable `Dataflow`/`Actuator` service contract, extended as necessary | Provenance-built document/source (currently `DataflowDocument` design), compiled for execution |

Every runtime mutation must result from execution of a resolved Dataflow. Classification and
characterization therefore extend the contextual Dataflow's explicit operation and target contract;
they do not require a separate resolution or mutation endpoint. Runtime validates contextualizer
signatures against that plan rather than discovering the operation's meaning only at execution.

A contextual Dataflow may legitimately reuse an existing observation. A reproduction export must
include how to recreate it, package it as a submitted input, or declare how the prerequisite is
obtained without relying on an observation already present in the destination graph. Extracting a
complete reproduction plan requires collecting relevant work across resolutions, not merely
serializing the latest Resolver response. Shared execution concepts or syntax do not erase this
scope difference. Partial exports must state their selected contents and close their prerequisites.

Strict reproduction retains recorded computational choices and does not silently rerun strategy
selection. Any later adaptive mode is a separate, explicit contract. Reproducibility still depends
on the supplied inputs and pinned resources/components; packaging requirements must make these
conditions visible. The runtime Dataflow retains its interface-based transport contract. Transport
registration for the reconstruction document remains a separate implementation milestone below.

## 1. Intended result

Start at a contextualized knowledge graph's **PROVENANCE** node, visit root activities in order of
submission, follow their activity trees and collect the actuators that actually ran. Reconstruct
a portable executable document in the `dataflow` persuasion of the observation language (`.obs`).
Persist enough definitions, dependencies, identities and computation bindings to load it into
another digital twin and run the recorded plan without selecting models again.

The first target is strict replay of finite, successfully completed work. A persisted plan is
not a value snapshot: supplied inputs may need packaged values, while computed outputs are normally
recomputed. An adaptive plan with explicit future resolution is a later capability and must be
declared as such. Reproducibility remains subject to pinned dependencies, supplied inputs and the
recorded spatial/temporal validity envelope.

```text
authorized contextual graph + consistent committed snapshot
  -> provenance scanner (submission roots, nested activities, actuator fragments)
  -> reconstruction builder (identity mapping, definitions, dependency closure)
  -> completeness and execution validation
  -> portable DataflowDocument
  -> canonical observation-language serializer + payload manifest
  -> parse/validate + import bindings + runtime compiler
  -> executable plan in a fresh digital twin
```

Scanning, semantic construction, source generation and Resource registration are separate stages.
An incomplete scan can produce a diagnostic draft, but cannot be labeled an executable export.

## 2. What the current implementation establishes

| Area | Evidence and consequence |
|---|---|
| Runtime plan | `api.services.runtime.Dataflow` has ordered root actuators, requirements and coverage. `Actuator` has ordered child/call lists, observation, data, strategy, annotations and sharding. Reuse these execution contracts when compiling an imported document; do not serialize their live graph identity directly. |
| Root selection | `KnowledgeGraph.provenance()` supplies the contextual provenance root. `DigitalTwinImpl.TransactionImpl` attaches untriggered activities with `PROVENANCE -HAS_CHILD-> Activity`; nested/triggered activities use `Activity -TRIGGERED-> Activity`. |
| Plans | `CompiledDataflow` records `Activity -HAS_PLAN-> root Actuator`, `DATAFLOW -HAS_CHILD-> root Actuator`, and `Activity -RESOLVED-> Observation`. Thus root activities may need descent through `TRIGGERED` before finding plans. |
| Dependencies | `CompiledDataflow` records consumer actuator `-HAS_CHILD->` dependency actuator. Observation `AFFECTS` edges carry scheduling rank. The actuator edge write does not currently preserve child-list position or explicit named ports. |
| Observation binding | `CONTEXTUALIZED_BY` is a passive relationship: logical observation-to-actuator linkage is stored in the graph's reversed direction. Use the graph direction contract, not an assumed arrow based on the relationship's name. |
| Structured computation | `KnowledgeGraphNeo4j` restores version-1 actuator `computationJson`, `dataJson`, annotations and sharding. `ActuatorPersistenceTest` verifies structured call parameters, including internal parameters that text encoding omits. These records are a starting point, not proof that every runtime binding is captured. |
| Legacy computation | Old textual computation is deliberately not reconstructed into service calls. Its empty hydrated call list must not be mistaken for a valid no-op actuator. Extraction needs access to schema/completeness evidence as well as hydrated assets. |
| Existing graph facade | `DataflowGraph.getComputation()` returns an empty list and `adapt()` returns null. Neither reconstructs a committed plan today. |
| Existing encoder | `DataflowEncoder` emits a partial preamble, no definitions and `obs<runtime ID>` for RESOLVE. Activity descriptions can contain this text. Treat it as inspection output, not authoritative replay source. |
| Language | The sibling `klab-languages` `Observation.xtext` contains separate strategy and executable document forms, including define/observe/reference/apply, versions and ports. Its header still labels it a design draft. Verify generated parser, adapter and deployed artifact together before claiming source support; earlier sections of OBSERVATION.md also describe the legacy grammar. |

Concrete source locations:

- [KnowledgeGraph.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/data/KnowledgeGraph.java)
- [DigitalTwinImpl.java](../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/DigitalTwinImpl.java)
- [CompiledDataflow.java](../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/CompiledDataflow.java)
- [KnowledgeGraphNeo4j.java](../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/neo4j/KnowledgeGraphNeo4j.java)
- [DataflowEncoder.java](../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/resolver/DataflowEncoder.java)

## 3. Extraction algorithm and required capture work

### 3.1 Snapshot and submission order

1. Authorize the context and obtain one consistent committed snapshot. A sequence of independently
   paged live reads is not sufficient when submissions may commit between pages. The first provider
   should be server-side, with a snapshot token bound to a database transaction or a materialized
   committed export view. Do not rely on a partially loaded client graph.
2. Read direct activity children of PROVENANCE. Distinguish context initialization, submissions,
   failures and in-flight work. The initial context-creation activity is not a submitted plan.
3. Select successful committed submission roots and order by **Activity.getStart()**, the persisted
   start timestamp. This is the primary sequence-reconstruction key for ordinary sequential submissions;
   a new sequence property is not a prerequisite. Keep activity start order distinct from completion
   order and respect causal dependencies when assembling the executable graph.
4. A snapshot with an earlier started submission still unfinished needs an explicit policy: initially
   reject strict export with a diagnostic rather than quietly exporting a purported complete history.
   Millisecond timestamp ties or overlapping submissions need additional evidence when their relative
   order matters. Use recorded causal links first. For proven independent work, a stable identity
   tie-break gives deterministic presentation without claiming an observed execution order.

If dependent submissions have indistinguishable start times and no causal ordering evidence, report
the ambiguity. A persisted context-local submission ordinal can supplement timestamps if needed;
adding such graph properties is within scope. The same applies to invocation identity and port/order
fields identified below. `GraphModel.Fields.SEQUENCE` elsewhere does not by itself establish an
activity sequence. Do not block otherwise complete historical contexts merely because they lack a
new optional ordinal. Check timestamp/causality conflicts, including clock skew, explicitly.

### 3.2 Activity and actuator collection

For each selected root, traverse its `TRIGGERED` descendants, with visited sets and context bounds.
Collect `HAS_PLAN` fragments only where successful committed execution evidence exists. Follow
actuator `HAS_CHILD` links to close the dependency graph. Use `RESOLVED` and direction-aware
`CONTEXTUALIZED_BY` links to recover observations. Do not assume the hydration of an Actuator has
already loaded its observation, children or context.

Record which activity introduced each fragment. Repeated links to the same plan must not replay it
twice; repeated executions or scheduled updates to the same observation must not collapse into one
execution. Observation identity, actuator occurrence and invocation identity are distinct. The
current actuator ID convention and graph persistence need an audit for repeated resolutions before
choosing an occurrence key. A naive map keyed only by observable or observation ID is insufficient.

Within a fragment use dependency order, persisted sibling order and ordered call lists. Add explicit
edge/input/output bindings and invocation ordinals where they are missing. Do not turn a scheduler
rank into an invented port binding. Preserve reference occurrences instead of recursively cloning
their producer. Unsupported cycles, unresolved continuation fragments and legacy opaque computation
are blocking diagnostics. Support for feedback/time transitions is a separate stage.

### 3.3 Portable reconstruction

- Assign stable local symbols from the ordered selected occurrences, with collision handling. Runtime
  numeric IDs may appear in a diagnostic lineage map, never as portable observation identities.
- Emit `define observation` for submitted objects with no producer: full observable (including units),
  portable identity, context, geometry/time, metadata, links/endpoints and supplied value or payload.
  Acknowledgement with no calls is meaningful and must not be confused with missing legacy calls.
- Emit executable `observe` for chosen computations, preserving call order, actual arguments,
  implementation versions and explicit ports. Strategy URNs remain provenance annotations.
- Local dependencies use symbols. External dependencies either bring their producers into the closure
  or become declared observation prerequisites, depending on the request policy. Never satisfy an
  unresolved reference by semantic search during replay. Verify context closure in both policies.
- Gather versioned worldview/resource/component/namespace/capability requirements. Do not derive a
  pinned implementation version solely from a service-call URN or from whatever is installed today.
- Preserve effective execution geometry, partial coverage, mediation, missing-data behavior,
  annotations and sharding when they influence execution. Distinguish coverage of resolution from
  actual execution support. Missing partial-coverage policy prevents certifying those exports.
- Export large supplied inputs as immutable payloads with checksums, media/representation and
  dependency entries. Never use arbitrary Java serialization as the portable value format.

## 4. Initial semantic API and entry points

The initial model is [DataflowDocument](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/lang/dataflow/DataflowDocument.java).
It uses immutable serializable value records rather than live runtime assets or parser nodes:

| Element | Initial representation |
|---|---|
| Header | name, version, replay/adaptive mode, ordered requirements and declarations |
| Requirements | kind, identity, optional version and checksum |
| Definition | named observation, full observable source, portable identity, optional context, geometry and value |
| Executable observation | named observable, context, strategy provenance, geometry, ordered apply calls |
| Apply | implementation, required version, named arguments, explicit output port |
| Reference | external identity and observable; local references are symbol/port values |
| Values | text, decimal number, boolean, closed observable source, local port, external payload |

[DataflowDocumentBuilder](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/lang/dataflow/DataflowDocumentBuilder.java)
preserves insertion order and checks duplicate symbols, forward/missing references, context symbols,
output ports, argument names, mandatory implementation versions and payload descriptors. A later call
within an observation may reference an earlier output of that observation. Documents and nested lists
are detached from subsequent builder/list mutation. Direct record construction remains possible;
all import/export boundaries will therefore need validation, not trust in how a document was created.

This is **structural validation only**. It does not parse observable strings, validate source identifiers,
resolve versions, check geometry, verify payloads or decide whether a computation can execute. Mode
ADAPTIVE is reserved in the header, but no continuation node is supported in this subset.

Missing model surfaces include metadata, object links, structured/list/map values, standalone value
definitions, merges, typed continuations, coverage manifest, complete source lineage and payload packaging.
These must be added before extracting graphs that need them. A scanner must report an unsupported
field instead of projecting such a graph into the smaller model and losing information. The records
are not yet registered for polymorphic JSON/YAML transport or adapted from the generated parser.

[DataflowExtractor](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/runtime/DataflowExtractor.java)
defines the planned server-side entry point with graph, authorized scope, requested roots, mode,
snapshot and external-reference policy. Its result is explicitly a diagnostic **Draft**, optionally
without a document, plus the snapshot actually read. There is no provider, service endpoint or new
`DataflowGraph.adapt()` behavior yet. A future validated export result should be separate from Draft.

## 5. Serialization shared with other document types

[DocumentSerializer<T>](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/lang/DocumentSerializer.java)
is the initial canonical-source contract. No serializer implementation is registered in this stage.
It produces a complete string after validation, so callers can persist only a successful result.
It does not write files, register Resources, or certify runtime readiness.

Build the first implementation for the finite dataflow subset against the generated observation parser.
Share a small source writer for indentation, escaping, identifiers, typed values and observable source.
Keep document-specific visitors and validators: serializing bean getters reflectively cannot choose
correct k.IM or observation-language syntax. The second implementation can target `KimNamespace` and
its model declarations. Expand by explicit supported node kinds with useful unsupported-kind diagnostics.

Canonical source may normalize whitespace/comments; preserving original editing trivia is a separate
source-aware concern. Require semantic parse/serialize round trips and stable re-encoding. Unknown
required fields must fail before any persistent file is replaced. Payload staging and final publication
should be atomic as a package. Resource registration follows source validation and package checks.

## 6. Stages and acceptance gates

| Stage | Work | Completion evidence |
|---|---|---|
| 0 — current | Document design; initial immutable semantic model, structural builder, extractor and serializer contracts | Focused builder tests; no claim of live extraction or source replay |
| 1 — capture audit | Verify start-timestamp ordering; supplement ambiguous cases if needed; capture execution occurrence identity, ordered dependencies, actual ports/versions, schema/completeness markers and snapshot contract | Neo4j contract tests including equal timestamps, concurrency, restart, repeated resolution and legacy records |
| 2 — scanner | Context-bound PROVENANCE traversal, root filtering, nested activities, plans and dependency closure | Real graph fixtures: ordered roots, nested submissions, failed/in-flight roots, repeated references, cycles and missing records |
| 3 — reconstruction | Definitions, portable identity/context bindings, requirements/payload closure and complete supported semantic nodes | No unresolved symbols or runtime IDs; precise gaps for unsupported captures; deterministic document per snapshot |
| 4 — source | Parser adaptation, explicit polymorphic transport mappings, validators, canonical dataflow serializer | Bean/source/parser round trip, deterministic source, preservation of every supported executable field |
| 5 — replay | Import into fresh context, input binding, requirement verification and lowering to runtime Dataflow/Actuator | Execute without fresh model selection; compare observations, identity relations, geometry and values |
| 6 — generalization | Resource packaging and k.IM serializer; later adaptive/temporal/composition capabilities | Resource reload/replay and k.IM semantic round trips; separate tests for every added capability |

Each stage should ship a bounded supported subset and explicit diagnostics for the rest. Avoid a
single success flag that means only that a text file was written. Suggested stable extraction codes
include `submission_order_unavailable`, `snapshot_incomplete`, `legacy_computation`,
`missing_binding`, `missing_implementation_version`, `unresolved_reference`, `unsupported_cycle`
and `unsupported_document_field`.

## 7. Full-stack tests and open decisions

The user's `klab.staging.vxii` workspace contains the project suite at
`testcases/klab/staging/vxii/testsuite.kactors`, with submitted-region identity and direct/implicit
quality paths. Its `samples/dataflow.obs` is entirely commented historical design material, not a
parser fixture. Neither file is changed by this stage.

Extend that suite once export/import actions exist: submit the Ruaha region, resolve Elevation and
the Normalized transformation, export the successful snapshot, parse/import into a fresh context,
execute and compare identity relationships, units, geometry, viable data and values within declared
tolerance. Check that replay does not select models again. Add sequential independent submissions,
shared dependencies, external inputs and negative cases for missing versions, failed work and legacy
provenance. Run against the fully deployed local Resources/Reasoner/Resolver/Runtime configuration.
Java tests remain useful for graph contracts, ordering and serializer edge cases; they do not
substitute for this deployed-service test. No such full-stack test is run or claimed in stage 0.

Decisions to settle before the corresponding implementation stage:

1. Which timestamp-tie/concurrency cases need an additional persisted ordinal, and what snapshot
   semantics should be exposed for concurrent work? Should export require a quiescent context initially?
2. Can current persistence distinguish successive invocations for one observation, and where should
   occurrence IDs and actual call bindings be recorded without changing observation identity?
3. How are submitted observation identities rebound: preserve catalog identities, remap namespace,
   or require caller-supplied bindings? Conflicts must never silently overwrite target observations.
4. Which literal/structured definition types, metadata and links are required for the first full-stack
   fixture, and which payload store supplies immutable checksummed inputs?
5. Which execution capability/version contract pins calls and mediations? Which geometry and
   partial-coverage cases can replay safely now?
6. Which generated/deployed observation grammar version will be the first supported serialization
   target? Freeze that together with the AST adapter and round-trip fixtures.

These questions do not block the current structural skeleton. They do block promises of a complete,
portable executable export from arbitrary existing contexts.
