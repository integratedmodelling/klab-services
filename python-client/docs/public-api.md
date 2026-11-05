# Existing public scaffold method inventory

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

Native export: `Client.export_formats`, `Client.export_to_file`,
`ObservationImpl.export_to_file`, frozen `ExportFormat`/`ExportResult` and
`ExportIOError`. See [streamed files and exporter limits](stream-exports.md).

Typed facade: `ObservationRequest`, `ObservationOptions`, `ResolutionConstraint`,
`ContextOptions`, `GridOptions`, `RectangularGeometry`, `Context.observe`, and
`Session.create_context(options=...)`. See [typed contracts and verification](typed-observe.md).

Scientific analysis: `export_for_analysis`, `open_raster`, `trace_analysis`,
`AnalysisArtifact`, `RasterDescriptor`, `RasterAnalysis`, `TracedAnalysis`;
recorded neighborhoods: `fetch_provenance`, `ProvenanceNode`, `ProvenanceLink`,
`ProvenanceView`. See [supported representations and consistency limits](scientific-analysis.md).

Optional native vector analysis: `open_vector`, `VectorAnalysis` with the `vector`
extra. Local bounded RFC7946 FeatureCollections only; preserves immutable source
records and a detached GeoDataFrame, without reprojection or topology repair.
See [vector budgets, identities and limits](vector-analysis.md).

Remaining unsupported capabilities include Avro worker contextualization and job
data decoding, general chunk/window access, automatic arbitrary query/consumer geometry
conversion, general pandas/multidimensional adapters, privileged server administration, full Modeler
UI emulation and Java API parity. These are explicit boundaries, not empty success.

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
