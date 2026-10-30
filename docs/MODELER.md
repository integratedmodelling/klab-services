# Modeler

The Modeler is the client controller shared by k.LAB front ends. `Modeler` in
`klab.core.api` defines its public actions; `ModelerImpl` in `klab.modeler`
implements them on top of `AbstractUIController` and a client `EngineImpl`.
It coordinates authentication, connected services, workspace navigation,
document editing, digital twins, and observation submission. Views and panels
register with the controller and receive UI events; the Modeler does not own
runtime resolution or storage.

Maintain this document when changing Modeler actions, classified definitions,
submission events, or the client contract for returned observations. Related
contracts are [k.IM](KIM.md), [observable expressions](OBSERVABLES.md), and
[the observation cycle](../klab.services.runtime/OBSERVATION.md).

## Engine, services, and digital twins

`boot()` initializes the engine and registered views. Authentication supplies a
user scope. Engine status and service status are dispatched to the views;
when local services become available the controller can establish the local
federation. The built-in controllers cover services, authentication, resources,
runtime, context inspection, and knowledge inspection. Document editors are
registered as panels.

`openNewContext(configuration, runtime, dispatchEvent)` creates a context through
the user's session on the selected runtime. The controller retains contexts by
ID and can dispatch `ContextCreated`. `createDefaultContext()` requires an
authenticated user and a local runtime. Its generated configuration uses idle
timeout persistence. The grid-aware overload freezes the grid as part of context
creation. Context focus and observer selection belong to the supplied
`ContextScope`; they affect dependent observations, queries, and mediation.

## Observation submission

The Modeler entry point is currently named
`observe(ContextScope, Object asset, boolean adding)`. It converts a selected
asset to a canonical `Observation` and calls
`RuntimeService.submit(observation, submissionContext)`. The `adding` flag is
available for front-end gestures but is not currently interpreted by this method.
URN discovery is the caller's responsibility: this method accepts resolved
assets and unwraps `NavigableKlabStatement` delegates, rather than looking up
arbitrary string URNs itself.

| Selected asset | Conversion and constraints |
| --- | --- |
| Runtime `Observable` | `ObservableSubmission` derives geometry and, where needed, collective semantics from the selected scope. |
| `KimModel` | Builds from the model's first observable; adds namespace, project, and `UsingModel` constraints. |
| Classified `KimSymbolDefinition` | Builds from its map; adds namespace and project constraints. Classification selects the behavior below. |
| `KimObservable` | Resolves the declaration through the reasoner and builds an unresolved observation. |
| Other statements | Only implemented statement conversions should be submitted; unsupported assets can produce an empty observation. |

Direct runtime observables follow additional rules. Qualities and processes
require a context observation and inherit its geometry. Enumerable substantials
require an observer with perceived geometry; singular semantics are promoted to
collective semantics without modifying the selected observable. Predicates
require a reasoner-resolved inherent: qualities use context geometry, while
substantial inherents use observer geometry and are promoted to collective
semantics when necessary. Invalid inputs return failed futures.

### Classified `define` statements

k.IM's definition class is an identifier, so `query` requires no new grammar
keyword. The shared `ObservationBuilderImpl` handles the observation-bearing
classes, including callers outside the Modeler.

| Definition class | Initial ID | Behavior |
| --- | --- | --- |
| `observation` | `Observation.UNASSIGNED_ID` (`-1`) | Normal runtime submission, including reuse or full resolution as needed. |
| `observer` | `-1` | Same map conversion, with `Metadata.IM_OBSERVER_TAG` and the Modeler's `UseAsObserver` constraint. |
| `query` | `Observation.QUERY_ID` (`0`) | Read-only lookup/mediation against existing knowledge; no resolution of missing coverage. |
| `grid` | No observation submission | Calls `configureGrid` on the current digital twin and returns an informational empty observation, including alignment warnings. |

Observation-bearing definitions require a map body. `semantics` identifies the
requested observable, including units, currency, or range modifiers. `space`
and `time`, or a nested `geometry` map, define the requested geometry. Spatial
keys include `shape`, `grid`, and `projection`/`crs`; temporal keys include
`year`, `start`, `end`, and `step`. The builder also handles `contextualization`
data, which cannot be combined with explicit geometry. Additional properties
are retained as metadata. The definition supplies identity as
`<namespace>:<name>`; it need not repeat identity inside its map.

For example, in a namespace importing the relevant observable:

```kim
define query temperature_view as {
  semantics: {{climate:Temperature in K}},
  time: {year: 2020}
};
```

Use the actual observable and spatial support appropriate to the selected twin.
The runtime fills absent geometry from the scope. Annotations on the definition
and on embedded observable/concept syntax pass through the same collection and
precedence rules used for observations and observers. Definition annotations
retain definition priority through service transport. Consequently a query can
exercise existing storage mediation with requested semantics, geometry, and
computation options without computing a new observation.

### Completion and events

Before submission the Modeler sends `ObservationSubmissionStarted` to the
context and dispatches the corresponding UI event. It derives a submission
scope with the accumulated resolution constraints. Exceptional completion
sends and dispatches `ObservationSubmissionAborted`; cancellation avoids an
additional error report. Successful completion forwards returned notifications
and dispatches `ObservationSubmissionFinished` with the returned observation.

The runtime publishes committed graph changes. The Modeler does not fabricate
a commit event, and the runtime client synchronizes only positive-ID results.
ID-0 results remain detached: the IDE opens their observation cards and ends
the submission progress state without inserting them into graph navigation,
recording a commit, selecting them as observers, or deduplicating all queries
under the numeric ID `0`.

## Query views, visualization, and export

A quality query returns a detached ID-0 view with the requested observable and
geometry, request annotations, and exactly one durable storage source in
`Metadata.IM_QUERY_SOURCE_IDS`. Reads use that source's storage with the
consumer view's mediation contract. Native histograms are omitted because they
describe the source's values/support. Collective queries select a proven
coverage fragment and report its source and cohort, exact coverage, and requested
geometry in query metadata. They do not manufacture coverage between disjoint
fragments. No match returns an empty observation with ID 0 and its notifications;
the IDE ends progress without opening a card or reporting a failed resolution.

Use `RuntimeService.exportObservation(view, mediaType, parameters, scope)` for
detached results. Exporting only their URN would retrieve the source and discard
the view. The client POSTs the portable observation and export parameters to
`ServicesAPI.EXPORT_OBSERVATION`, with the desired media type in `Accept`.
The runtime revalidates the request through read-only querying in the authorized
context before dispatching the normal exporter. It reconstructs source ownership
server-side instead of trusting transported storage pointers or adapter data.
Revalidated source IDs must match the view; a changed focus or source selection
requires submitting the query again instead of silently exporting another
observation. This is a fresh read of those sources, not a retained storage
snapshot. Positive-ID exports retain the existing URN-based path.

PNG, GeoTIFF, point-value, and other quality exporters use the same storage
scanner binding as resolution-time reads. Collective GeoJSON export obtains
members through the runtime member API using the view's support, rather than
trying to look up graph node `0`. The IDE passes the full observation to export
for maps and point values. Multiple query results can be opened independently.
The older runtime visualization controller endpoints still contain placeholder
implementations; the working IDE path uses discovered export schemas.

## Workspaces, documents, and local publication

The Modeler exposes project/workspace creation, project import, deletion and
management, document creation, editing and submission, and resource-set handling.
Updates preserve document type, source, identity, and selected project, including
different k.Actors behavior types. Front ends should drive these actions through
the controller so service results and UI navigation remain coordinated.

`publishLocally` serves generated files through a local HTTP server, grouping
related files by workspace when supplied. Shutdown coordinates the engine and,
when requested, local services. Some UI actions and the generic visualization
action remain inherited placeholders; inspect the implementation before relying
on an advertised interface action in a new front end.

## Regression checks

`ClassifiedDefinitionSubmissionTest` covers query/observation/observer IDs,
constraints, identity, geometry, metadata, and annotation transport.
`ObservableSubmissionTest` covers direct observable conversion. Runtime query,
storage read, export transport/controller, and IDE submission/card tests cover
the detached-result path, including requests with different fill curves, units,
currency rates, and ranges. IDE card tests exercise PNG, point-value, and GeoJSON
requests with the complete view.
Changes to the definition map contract must keep the shared builder and these
client/runtime contracts consistent.
