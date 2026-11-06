# Existing public scaffold method inventory

Resource file imports: `ResourceImporter` / `AsyncResourceImporter` discover
advertised RESOURCE/STREAM schemas; explicitly consented uploads return an
`ImportSubmission` with a Resources Job and actual uploaded-byte hash/count.
See [resource imports](resource-imports.md) for examples and the separate native
admission gate. No component installation or implicit scientific execution.

Local dependent candidate: `await async_fetch_execution_evidence(async_observation)`
awaits bounded recorded provenance and scoped activity reads, returning the existing
immutable `ExecutionEvidence`. Plans are not execution/commit proof and absent
diagrams remain explicit. It depends on unpublished native async provenance; neither
feature is yet accepted integration delivery. See
[dependent async evidence contract](native-async-execution-evidence.md).

`await async_fetch_provenance(async_observation, limit=64, offset=0)` explicitly
refreshes one bounded recorded neighborhood using native async membership/query
requests. Synchronous observations retain `fetch_provenance`. Both return immutable
partial views; empty pages do not prove absent lineage, planned links are not
execution evidence, and no shared export/provenance revision is implied. See
[native async recorded provenance](native-async-recorded-provenance.md).

`AsyncSemanticExplorer(AsyncClient, scope=None)` provides awaited concept/observable
resolution, parents/children, is_a/satisfiable, authority search, selected-candidate
resolution and documentation descriptors. It shares checked immutable views with
`SemanticExplorer` on sync clients; no thread wrapper/local inference. Candidate
selection is explicit, unknown revision remains unknown and URLs are never followed.
See [native async semantics](native-async-semantic-exploration.md).

Indexed reads require observation source IDs to be exact positive integers in the
Java signed-long domain (1..2**63-1). Out-of-domain observation identities raise
ProtocolError before read HTTP; they are not coerced or clamped. Source-zero and
detached-consumer bindings keep their explicit unsupported behavior. A domain-valid
integer does not establish source existence or authorization. See
[source identity bounds](indexed-source-id-long-bounds.md).

`Client.resources.retrieve(urn, "CODELIST")` and its native awaited async equivalent
retrieve the existing Java codebook asset as `KlabAsset` with full retained wire DTO.
CODELIST list/resolve remain unsupported. Numeric/string codes, order/preferences
are not interpreted or flattened; an empty codebook is metadata, not a scientific
mapping. See [retrieval-only contract](native-codelist-retrieval.md).

**Bounded native async observation:** `AsyncClient`, `AsyncSession`, `AsyncContext`
and `AsyncJob` expose the bounded observation workflow in
[`native-async.md`](native-async.md). The historical APIs below remain synchronous;
async connection orchestration is not claimed. Native async advertised file export
is implemented with bounded streaming/atomic publication; see
[candidate acceptance and limits](native-async-export.md). Native async
saved-handle restoration is implemented with existing origin/identity/selection
checks; [candidate acceptance and limits](native-async-handle-restoration.md)
are recorded separately. Controlled real-Java acceptance and its limits are recorded with the
feature; this is not full-roadmap or published-experiment acceptance.

The four ABCs and their `*Impl` classes have the same operation status. Service
implementations use a configured Client; no local fake success remains. Full
routes, identity and DTO sources are in [contracts.md](contracts.md).

| Interface / existing methods | Status / compatibility |
|---|---|
| Reasoner: capabilities, resolve_concept, resolve_observable | Remote; concept/observable preserve checked wire DTOs |
| ResourcesService: capabilities, retrieve, resolve | Remote; retrieve accepts explicit KnowledgeClass string (KlabAsset maps to RESOURCE); full asset raw retained |
| ResourcesService: contextualize | Explicit UnsupportedOperationError: binary Avro worker-input contract |
| RuntimeService: capabilities, get_context_info, connect_context, release_session, release_context | Remote; full IDs and configurations preserved; release explicit |
| RuntimeService: submit | Remote; returns Job, not echoed Observation |
| RuntimeService: query_knowledge_graph | Remote for structured wire DTO mapping; strings/local arbitrary objects explicitly unsupported; raw graph assets are not scientific numeric data |
| Resolver: capabilities, submit_resource, get_submitted_resources | Remote on configured peer context; requires originating runtime service ID and authorized scope |
| Resolver: resolve | Remote Job yielding raw Dataflow DTO; does not execute it |
| Resolver: encode_dataflow | Explicit UnsupportedOperationError; inspected Java method is TODO |
| Geometry / GeometryImpl: dimension, size, is_scalar | Local DTO access; unknown/universal finite size is explicitly unsupported |
| Geometry / GeometryImpl: encode | Scalar `1`, universal `*`, empty `X`; other encodings unsupported. Server hashes/keys are not geometry encodings; arbitrary values rejected |
| Observation / ObservationImpl: get_urn, get_name, get_observable, get_value, get_notifications | Local DTO access; get_value does not fetch or infer storage data |
| Concept / ConceptImpl: get_type, is_collective, get_qualifier, get_notifications, get_description_type | Exact raw contextualization retained. Compatible legacy mapping only; missing/future/unrepresentable remote activity raises UnsupportedOperationError instead of INSTANTIATION default |
| Concept / ConceptImpl: singular, collective | Local detached copies with changed collective flag; no remote normalization or new semantic authority implied |
| Observable / ObservableImpl: get_semantics, get_observer_semantics, get_description_type, is_optional, get_default_value, get_resolution_directives | Exact contextualization exposed; MEASURE/QUANTIFICATION, INSTANTIATION, DETECTION, CATEGORIZATION map explicitly. Other legacy conversions unsupported; local-only construction remains compatible |
| UserScope / UserScopeImpl: get_user_id, get_roles | Local descriptive identity/roles; no authorization grant |
| SessionScope / SessionScopeImpl: get_session_id | Local DTO access |
| ContextScope / ContextScopeImpl: get_context_id, get_observer | Local DTO access; remote Context adds explicit workflow helpers |
| Modeler / ModelerImpl: open_user, open_session, open_context, get_open_context | Local tracking only; never calls remote release or authorizes work |

Constructors, dataclass fields and enum members remain available. New DTO `.raw`
fields retain server IDs, metadata/provenance and optional future fields. Local
ObservableImpl without a remotely resolved wire DTO cannot be submitted as if it
were reasoner-authorized. Explicit input mappings must use the inspected Java
`@CLASS` discriminator.

New API: Client.from_env/initialize_user_scope/create_session/attach_context/close/context manager;
Session.create_context/release; Context.within/submit/job/release; Job.status/
result/wait/cancel; ObservationImpl.fetch_data; ScientificData; Endpoint;
ResourcesServiceImpl.list; dto.storage_semantics for simple units;
experiment.run_elevation. Error hierarchy is documented in README.

Saved references: `Handle`, `to_handle`, `save_handle`, `load_handle`,
`restore_handle`, source-object `to_handle()` and `Client.restore_handle`.
See [versioned schema and verification](saved-handles.md).

Native async typed metadata: `AsyncResourceInspector(AsyncClient, scope=None)`
provides awaited `list`, `retrieve` and `resolve` for MODEL/RESOURCE, with the same
immutable views, budgets and redaction as `ResourceInspector` on synchronous Client.
It uses existing native async service calls, not threads or a nested event loop.
Missing versions/dependencies remain unavailable; provider URLs remain inert data.
Neither facade proves model applicability, Resolver selection or computation.
See [native async inspection](native-async-resource-inspection.md).

Native Resources GET asset retrieval can encode Java `null` as HTTP200 with zero
bytes. Only that exact JSON-mode retrieve route is recognized as MissingAssetError
(status_code200); malformed nonempty bodies, unrelatedroutes/writes and otherempty
success statuses remain protocolerrors. This does not turn arbitrary failures into
absence or change authorization/ambiguous-write behavior.

Read-only parser receipts: `SourcePreviews(Client).parse` and awaited
`AsyncSourcePreviews(AsyncClient).parse` admit the existing namespace/ontology/
strategy-document/behavior families. Both return immutable `SourcePreview`, never
authoring `ParsedSource`, and preserve input/parser hashes under explicit
`allow_edge_trimming` policy. Shared byte/secret/DTO/binding checks do not establish
semantic revision validity, persistence, compilation or execution. See
[native async previews](native-async-source-previews.md).

Shared readers use the same saved-handle API with independently configured clients:
the server must admit the second principal through its existing context ACL. A handle
does not grant access, change credentials or confer creator ownership. Closing a
client is local transport cleanup, not context release. The bounded
[shared-context reader workflow](shared-context-readers.md) distinguishes client
read overlap from simultaneous server computation or revision leases; ACL admission
alone must not be described as a read-only per-operation permission role.

Native export: `Client.export_formats`, `Client.export_to_file`,
`ObservationImpl.export_to_file`, frozen `ExportFormat`/`ExportResult` and
`ExportIOError`. See [streamed files and exporter limits](stream-exports.md).

Typed facade: `ObservationRequest`, `ObservationOptions`, `ResolutionConstraint`,
`ContextOptions`, `GridOptions`, `RectangularGeometry`, `Context.observe`, and
`Session.create_context(options=...)`. See [typed contracts and verification](typed-observe.md).

Scientific analysis: `export_for_analysis`, `async_export_for_analysis`, `open_raster`, `trace_analysis`,
`AnalysisArtifact`, `RasterDescriptor`, `RasterAnalysis`, `TracedAnalysis`;
recorded neighborhoods: `fetch_provenance`, `ProvenanceNode`, `ProvenanceLink`,
`ProvenanceView`. See [supported representations and consistency limits](scientific-analysis.md).
The explicit awaited export helper preserves the same origin/units/hash association;
mixing a sync helper with an async observation fails before I/O, never via a hidden loop.

Optional native vector analysis: `open_vector`, `VectorAnalysis` with the `vector`
extra. Local bounded RFC7946 FeatureCollections only; preserves immutable source
records and a detached GeoDataFrame, without reprojection or topology repair.
See [vector budgets, identities and limits](vector-analysis.md).

Remaining unsupported capabilities include Avro worker contextualization and job
data decoding, general chunk/window access, automatic arbitrary query/consumer geometry
conversion, general pandas/multidimensional adapters, privileged server administration, full Modeler
UI emulation and Java API parity. These are explicit boundaries, not empty success.

Sync/native-async exports and analysis helpers accept explicit temporal `slice=`;
`ExportResult.requested_slice` records the request, not a committed revision. Only
source-confirmed TEMPORAL_TRANSITION query fields are supported; no unsliced retry.

## Connection orchestration

Typed recorded graph pages: `GraphBrowser` and `GraphPage`, reusing immutable
provenance node/link DTOs. Explicit synchronous one-hop pages with membership
checks, no automatic crawling or revision consistency. See
[graph-browsing.md](graph-browsing.md) for supported classes and acceptance limits.

Indexed tabular views: `ScientificDataOrigin`, `to_dataframe` and
`ScientificData.to_dataframe`, with optional `[tabular]` dependencies. New indexed
reads retain declared primitive/contextualization and origin metadata; unknown
fields remain unknown. See [precision, masks, metadata and categorical limits](scientific-tables.md).

Native read slices: `ScanSlice(type, key, start, end)`, `ScanSlice.initialization()`
and `to_wire()` mirror existing Java `StorageScan.Slice` metadata. Sync and async
`fetch_data(..., slice=...)` accept either the typed slice or the existing dict.
All four maintained event types are supported, with nonblank keys, Java-long
timestamps and native interval validation before HTTP. Keys/timestamps are caller
supplied; no time/revision discovery, lease, scheduling or temporal computation is
implied. See [native scan slices](native-scan-slices.md).

Indexed point offsets must be exact nonnegative integers in Java's signed-long
domain (0..2**63-1), with 1..256 offsets per fetch. The shared sync/async preflight
rejects out-of-domain values before any HTTP; it does not establish that a valid
integer names a cell present in the selected observation. Order and duplicates
remain explicit. See [offset bounds](indexed-offset-long-bounds.md).

Within a single sync/async indexed read, validated explicit native `semantics`
fields are copied before the first HTTP request and retained for every point and
the returned requested observable/unit metadata. Caller mutations remain caller
owned and cannot change later point requests or relabel earlier values. This is
not a remote revision lease, immutable observation snapshot or deep snapshot of
arbitrary extension objects. See [semantics isolation](indexed-read-semantics-isolation.md).

Offline indexed-read snapshots: `ScientificData.dumps` and `ScientificData.loads`
use bounded versioned JSON and existing scalar decoders, without scientific extras,
network access, remote restoration or operation replay. See
[snapshot scope and reproducible example](scientific-tables.md#offline-indexed-read-snapshots).

Bounded experiments: `Experiment`, `OwnedContextPolicy`, `ExperimentRunner`,
`ExperimentOutcome`, `ExperimentBatch`, `BatchInterrupted`, `WaitCancelled`.
See [ownership and cancellation semantics](bounded-experiments.md).
The [milestone delivery tools](discover-compare-explain-delivery.md) are shared
repository acceptance/notebook helpers, not extra APIs shipped in the SDK wheel.

Semantic exploration: `SemanticExplorer`, `SemanticResult`, `KnowledgeState`,
`AuthorityCandidate`, `AuthoritySearch`, `StaleKnowledgeError`. See
[semantic exploration and its acceptance boundary](semantic-explorer.md).

Model/resource inspection: `ResourceInspector`, `AssetInspection`,
`InspectionOrigin`, `AdvertisedObservable`, `ResourceReference`,
`ResourceResolution`. See [bounded advertisements and routing limits](resource-inspector.md).

Execution evidence: `decode_dataflow`, `decode_flowchart`, `fetch_execution_evidence`,
`DataflowEvidence`, `ActuatorEvidence`, `FlowChartEvidence`, `ChartNode`, `ChartPort`,
`ChartLink`, `ActivityEvidence`, `ExecutionEvidence`. See
[plan-versus-execution and partial-evidence contracts](execution-evidence.md).

`DeploymentProfile`, `ConnectionIdentity`, `ServiceReference`, `CatalogEntry`,
`ServiceCatalog`, `CredentialGrant`, `CredentialProvider`, `IssuedTokenProvider`,
`HubCertificateProvider`, `SelectionPolicy`, `Connection` and `connect` are public.
Connection exposes `identity/profile/catalog/selected/client`, synchronous
`refresh/initialize_peers/wait_ready/renew`, and local `close`/context management.
`CredentialExpiredError`, `ConnectionIdentityError`, `ServiceSelectionError`,
`ReadinessError`, `ProfileIOError` and `CertificateError` supplement existing
errors. See [profiles, providers, lifecycle and examples](connection-layer.md).
## Native async source authoring

Graph browsing/partial refresh have native awaited `AsyncGraphBrowser.neighbors`
and `AsyncGraphProjection.load`, returning the existing immutable page/view types.
Local view/invalidate/clear remain synchronous, never authorization or revision
authority. See [graph browsing](graph-browsing.md) and [projection](graph-projection.md).

Agent source preview also has an explicit awaited `AsyncAgentPreviews.preview`
counterpart to `AgentPreviews`, with the same nonexecuting flags and immutable
receipt. See [source-only preview](agent-source-preview.md); neither returns a
running agent or introduces thread-backed broker operations.
Deferred behavior registration has native awaited `AsyncAgentExecutions.create`
with matching AsyncContext and explicit consent/assertions; same-object attempts
serialize and cannot resend an admitted registration. Broker methods remain
explicitly synchronous. See [registration limits](agent-execution.md).

`AsyncAuthoring` is the awaited counterpart of `Authoring`: parse, create_workspace,
create_project and create_document. It shares ADD-only consent, exact-source,
origin/identity, confirmation and no-replay guards. See [authoring](authoring.md).
