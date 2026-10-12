# Existing public scaffold method inventory

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

Typed facade: `ObservationRequest`, `ObservationOptions`, `ResolutionConstraint`,
`ContextOptions`, `GridOptions`, `RectangularGeometry`, `Context.observe`, and
`Session.create_context(options=...)`. See [typed contracts and verification](typed-observe.md).

Remaining unsupported capabilities include Avro worker contextualization and job
data decoding, bulk file export, automatic arbitrary query/consumer geometry
conversion, pandas/xarray adapters, privileged server administration, full Modeler
UI emulation and Java API parity. These are explicit boundaries, not empty success.
