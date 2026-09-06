# Flow charts

`org.integratedmodelling.klab.api.documentation.FlowChart` in `klab.core.api` describes
process structure for visualization and reporting. It adds no dependencies. It is a concrete,
mutable Java bean with a public no-argument constructor, as are all its nested transport classes.
It does not implement `KlabAsset`, carry runtime services, or require serialization annotations,
polymorphic type tags, a custom serializer, or an object-reference resolver.

## Transport and ELK

A chart contains `root` (a `FlowChart.Node`) and chart-level `metadata`. The root is an ordinary
node: it can have ports even when there are no child nodes, documenting an isolated component.
Containment is a tree; connectivity is expressed exclusively through string IDs.

The root follows the [Eclipse ELK JSON graph format](https://eclipse.dev/elk/documentation/tooldevelopers/graphdatastructure/jsonformat.html):

| Java type/property | JSON meaning |
| --- | --- |
| `Node.children` | Nested nodes, including compound processes |
| `Node.ports` | Ports owned by this node |
| `Node.edges` | Links contained in this node |
| `Link.sources`, `Link.targets` | Lists of node or port IDs (ELK extended edges) |
| `Port.role` | `INPUT` or `OUTPUT`, relative to the owning node |
| `labels` | Label beans containing `text` and optional geometry |
| `x`, `y`, `width`, `height` | Node, port, and label geometry |
| `layoutOptions` | String-valued ELK layout options |
| `Link.sections`, `junctionPoints` | Optional routing geometry returned by a layout engine |
| `metadata` | Extension properties on the chart, nodes, ports, links, labels, and sections |

Nodes, ports, and links require graph-wide unique IDs. Section IDs should also be unique when
routing is supplied. Labels may omit IDs. Points are simple `x`/`y` coordinate values.
Geometry starts at zero; clients should supply meaningful node and label sizes before layout.
ELK does not measure text. Coordinates follow ELK's containing-node coordinate conventions.

Serialize the whole chart for transport. Pass `chart.root` to ELK in JavaScript, or serialize
`chart.getRoot()` for an ELK JSON importer. This selects the graph from its envelope without
renaming or transforming any fields. `role` and `metadata` are application extension properties;
clients should preserve the original payload when a layout importer drops extension fields.
ELK dependencies and layout execution belong in the consuming application, not the API module.
Not every ELK algorithm supports hyperedges or hierarchy-crossing links.

For example, using an ordinary Jackson mapper supplied by an application:

```java
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(chart);
FlowChart received = mapper.readValue(json, FlowChart.class);
received.validate();
String elkJson = mapper.writeValueAsString(received.getRoot());
```

Metadata values must be JSON values: strings, booleans, finite numbers, null, lists, and
string-keyed maps recursively. Do not insert service objects, domain beans, or cyclic references.
The generic model leaves this contract to its producers; it does not adapt arbitrary objects.

## Fluent construction

```java
import org.integratedmodelling.klab.api.documentation.FlowChart;

FlowChart chart = FlowChart.builder("process")
    .metadata("reportTitle", "Conversion")
    .root(root -> root
        .label("Conversion")
        .layout("elk.algorithm", "layered")
        .layout("elk.direction", "RIGHT")
        .port("external-input", FlowChart.Role.INPUT)
        .port("external-output", FlowChart.Role.OUTPUT)
        .node("convert", node -> node
            .label("Convert").size(160, 60)
            .metadata("kind", "operation")
            .port("convert-in", FlowChart.Role.INPUT)
            .port("convert-out", FlowChart.Role.OUTPUT,
                port -> port.getMetadata().put("mediaType", "application/json")))
        .link("feed", "external-input", "convert-in")
        .link("result", "convert-out", "external-output",
            link -> link.getLabels().add(FlowChart.labelOf("result"))))
    .build();
```

`node` callbacks can recursively add children. Port and link callbacks receive the actual beans,
so every extension and geometry property remains available. The builder returns its mutable
result without copying; reuse of a builder changes the same chart. Callers can also construct
all beans with setters and mutable lists. Keep collections non-null (empty means absent).

`build()` calls `validate()`, which checks node/port/link IDs, containment reuse/cycles, port roles,
nonempty endpoint lists, and endpoint existence. After direct edits or deserialization, call it
again. Validation checks topology, not metadata, geometry, or algorithm-specific layout rules.
Process cycles, self-links, disconnected nodes, and multiple endpoints are supported. Role is
relative to the port owner: a root input may be the source of a link to a child input, and a
child output may feed a root output. Roles therefore do not impose a global edge-direction rule.

## Adaptation

`FlowChart.Adapter<T>` is a functional interface with `FlowChart adapt(T source)`. Adapters run
outside the data model and never become serialized state. Supply an adapter explicitly; there
is no global registry or runtime dependency discovery.

```java
FlowChart.Adapter<MyProcess> adapter = process -> FlowChart.builder(process.id())
    .root(root -> root.label(process.name()))
    .build();
FlowChart chart = FlowChart.adapt(myProcess, adapter);
```

The first supplied adapter projects a workflow schema:

```java
import org.integratedmodelling.klab.api.documentation.WorkflowFlowChartAdapter;

FlowChart chart = FlowChart.adapt(workflow, new WorkflowFlowChartAdapter());
// Equivalent: new WorkflowFlowChartAdapter().adapt(workflow)
```

Every workflow state becomes a child with input and output ports, including disconnected states.
Map keys are authoritative state and transition identifiers. Each transition sourced from `INIT`
creates an input port on the root and connects that port directly to the transition's target
state. Each state that is not the source of any transition is terminal: its output connects to a
corresponding output port on the root. This makes the root the external workflow interface while
states remain the internal process structure. A disconnected state is terminal and therefore has
an output unless a transition names it as a source.

Each transition source gets a separate binary link to the target, preserving alternative origins
rather than implying simultaneous synchronization. Self-transitions and cycles are retained.
State, input-port, and output-port IDs are length-prefixed to avoid collisions with arbitrary
schema keys; ordinary link IDs follow sorted transition/source order. Terminal links use stable
state-derived IDs. Original schema IDs remain in metadata. Adding an ordinary transition may
renumber ordinary link IDs.

Root metadata includes workflow ID, version, description, asset types, and source metadata under
`properties`. Root input ports carry `workflowInput`, transition, and target-state metadata. Root
output ports and their links carry `workflowOutput` and terminal-state metadata. State metadata
includes description, instructions, completion criteria, open flag,
roles, admitted groups, asset types, attachment rules, and `properties`. Link metadata includes
transition ID, source/target state, description, roles, admitted source asset/media types, and
`properties`. Source metadata is recursively copied; enum values become names and collections
become lists. Unsupported objects, non-string map keys, cycles, and non-finite numbers are
rejected. The source workflow is never mutated or stored in the chart.

This adapter reports the complete configured structure. It does not evaluate permissions,
completion conditions, or current execution state, and does not replace `Workflow.validate()`.
Missing referenced states and empty transition sources are rejected. A workflow with no states
or transitions produces an empty root. For runtime status or application-specific styling,
provide another adapter or enrich the resulting metadata and layout options.


## Service layout and PNG rendering

`klab.core.services` supplies `FlowChartService` in
`org.integratedmodelling.klab.services.application.flowchart`. It uses ELK 0.12 for layout and
Java2D/ImageIO for PNG rendering. It needs neither a browser nor a display server; headless JVMs
are supported. The API module remains free of ELK, rendering, and JSON-library dependencies.

```java
var diagrams = new FlowChartService();
FlowChart positioned = diagrams.layout(workflow);
BufferedImage image = diagrams.image(workflow);
byte[] png = diagrams.png(workflow);
```

The built-in registry accepts `Workflow` and `FlowChart`. Register additional process adapters
on a service instance with `service.flowCharts().register(MyProcess.class, myAdapter)`.
Later registrations take precedence. Layout works on a detached JSON copy, preserving metadata,
and provides default node/port sizes and font-measured labels where needed. Explicit layout
options override defaults. A temporary parent accommodates ports on the chart root; this parent
is removed from the result. Node and label dimensions are interpreted as minimum dimensions.
For deterministic report styling, use the same fonts on the rendering hosts.

`Link.container` records the ELK coordinate container when layout returns one. It may differ
from the node that owns the edge in the transport tree. Both renderers account for it and retain
multiple edge sections. Labels use plain text. PNGs have a white background and are downscaled
when necessary to fit within 4096 by 4096 pixels. Layout supports the features of ELK's layered
algorithm; unsupported layout algorithms/options or malformed topology fail rather than being
silently drawn with invented routes.

`BaseService.info(urn, knowledgeClass, FlowChart.class, scope)` returns a positioned chart.
Request `BufferedImage.class` for a raster image. The source object is resolved through the
service's existing typed information path, including its access checks. Common service objects
can also be adapted. Subclasses with other source-resolution requirements may override
`flowChartSource`, retaining their normal authorization checks.

Over HTTP, the common information endpoint uses content negotiation:

```text
GET /api/v1/info/WORKFLOW/{encodedWorkflowUrn}?infoClass=org.integratedmodelling.klab.api.documentation.FlowChart
Accept: application/json
Authorization: Bearer ...

GET /api/v1/info/WORKFLOW/{encodedWorkflowUrn}
Accept: image/png
Authorization: Bearer ...
```

The PNG response contains actual PNG bytes, not JSON/Base64, and requires an authorized user
scope. It returns 404 for a missing object and `Cache-Control: private, no-store` for an image.
The URN must be the identifier expected by the service's ordinary typed `info` lookup.

## Embedding the Vue/Sprotty component

The shared web UI contains `src/components/FlowChartViewer.vue`. It uses Sprotty for SVG
rendering, selection and viewport navigation. It accepts a FlowChart URL and uses ELK in a
Web Worker by default. Neither the fetched object nor the API data model is changed in place.

```vue
<script setup lang="ts">
import FlowChartViewer from "@klab-dashboard/components/FlowChartViewer.vue";
</script>

<template>
  <FlowChartViewer url="/my-process/flowchart" />
</template>
```

The import alias above is available to built-in dashboard extensions. Applications outside the
repository can bundle the component and its `flowchart/` support modules with Vue, Sprotty,
`sprotty-protocol`, Inversify, `reflect-metadata`, and `elkjs`. The built dashboard also exports
`FlowChartViewer` from its stable `/assets/klab-webui-api.js` browser module (the existing
`@klab/webui` plugin import-map entry), with CSS at `/assets/flowchart-viewer.css`. Use the host's
shared Vue runtime when loading that module. The raw-chart layout worker assets are emitted by
Vite alongside the viewer and must be deployed with it.

| Property / event | Contract |
| --- | --- |
| `url` | URL returning the whole FlowChart envelope, including `root` |
| `layout` | Defaults to `true`; set `false` for a server-positioned chart |
| `load(url, signal)` | Optional async loader returning a FlowChart; supply bearer authentication here |
| `select` | Emits `{ id, kind, metadata }` for an inspected element |
| `loaded` | Emits the chart after successful layout/rendering |
| `error` | Emits a load/layout error, also displayed with a retry action |
| exposed `refresh()` / `fit()` | Reload the URL or fit the diagram to its viewport |

The default loader uses same-origin cookies and sends no bearer token. For a dashboard extension,
pass `(url, signal) => context.api.request(url, { signal })` as `load`. The component supports
multiple independent instances, aborts stale requests and layout workers, and releases its
observer/container on unmount. Scroll to zoom, drag the background to pan, and click an element
or use the inspector's keyboard-accessible selector to see its metadata. This is a read-only
viewer; moving nodes or editing a workflow is not supported.

## Resources workflow browser

The Resources server adds an authenticated **Workflows** page at `/ui/workflows`, implemented in
`klab.services.resources.server/src/main/webui/extensions/ResourcesWorkflows.vue`. It lists visible
workflow definitions, visualizes the selected workflow, and downloads its PNG using the current
user token. The shared UI build automatically includes this service extension.

| Route | Response |
| --- | --- |
| `GET /api/v1/workflows` | Workflow definitions visible to the caller |
| `GET /api/v1/workflows/{workflowId}/flowchart` | Positioned FlowChart JSON |
| `GET /api/v1/workflows/{workflowId}/flowchart.png` | PNG bytes |

All three routes require the normal workflow user authorization. The diagram and image routes
use `getWorkflow` before adaptation, preserving workflow visibility rules. The page uses
`layout=false` so the browser displays the server's geometry directly.

Build the web assets from `klab.core.services/src/main/webui` with `npm ci` and `npm run build`,
or use the existing Maven `webui` profile when packaging the server:

```shell
./mvnw -pl klab.services.resources.server -am -Pwebui package
```

Focused Java checks can be run from the repository root:

```shell
./mvnw -pl klab.services.resources.server -am test -Dtest=FlowChartServiceTest,FlowChartInfoTest,FlowChartTest -Dsurefire.failIfNoSpecifiedTests=false
```

Browser tests run from `klab.core.services/src/main/webui` with `npm run test:flowchart` after
`npx playwright install chromium`. Alternatively, set `KLAB_TEST_BROWSER_CHANNEL=chrome` to use
an installed Chrome. The test harness runs locally with fixture responses; it checks browser
and Java ELK geometry, metadata inspection, URL changes, error recovery, multiple instances and
the Resources workflow selector without requiring a live Resources service or user credentials.


## IDE workflow preview

`klab-ide`'s `WorkflowEditor` includes a workflow-diagram toolbar button immediately before the
optional Delete button. It opens an owner-modal window, requests the workflow's PNG from the
editor's ResourcesService using `info(workflow.getUrn(), WORKFLOW, BufferedImage.class, scope)`,
and fits the image to the resizable window. Loading runs off the JavaFX application thread;
closing the dialog cancels the request. Missing images and service errors are shown in the dialog.

`BaseServiceClient` negotiates `image/png` for `BufferedImage` information requests and decodes
the binary response with ImageIO. Authentication, service headers, scope headers and request
timeouts are retained. A missing image returns null; failed requests and invalid images raise
an error. The IDE uses its existing JavaFX Swing dependency for BufferedImage conversion.
