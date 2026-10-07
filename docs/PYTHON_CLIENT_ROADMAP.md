# Building the full k.LAB Python client

## Assessment and recommendation

PR 84 establishes a substantial and useful first client milestone. It connects a Python program to real k.LAB service contracts and implements a complete, deliberately bounded scientific workflow: initialize an explicitly configured user scope, create or attach a context, submit an observation, distinguish job outcomes, read actual storage values, reconnect, and release remote state explicitly. Its attention to authorization, scientific-value integrity, ambiguous failures and lifecycle ownership is valuable infrastructure to preserve.

The next step is to develop that foundation into an **Engine-grade Python user client with a scientific data interface**. Completing this means supporting coherent user workflows across identity, services, scopes, observations, live twin state and scientific data. Method-count parity with Java is an unreliable target: the Java client includes unfinished methods, and some shared Java interfaces deliberately include service-side operations that a remote client must never implement.

The recommended sequence is:

1. Agree on the public client boundary and a versioned interoperability contract
2. Complete authenticated connection, service selection and typed scope/observation workflows
3. Add efficient scientific exports and scale-aware Python data access
4. Add coherent twin browsing, provenance, events and reconnect recovery
5. Extend into semantic authoring, resources, models and behaviors where the server supports them
6. Treat Python-hosted contextualizers or an embedded runtime as separate products

This is an architecture and delivery assessment. The evidence was inspected on 7 October 2026 at PR head `1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e` and its original integration base `64754ea2b4d7207d51db454506f51b4b18741817`. The isolated upstream integration branch is based on updated `develop` at `142cb08e76da133a0396340b7293396bb2dc1e2c`, including the authority-codelist/proposal work. The earlier PR test evidence does not validate that new combined tree. Proposed work below is identified as such; existing functionality, reported test evidence and backend-dependent ambitions are kept separate.

Sources: [PR 84][pr], [repository architecture][architecture], [Python contract matrix][contracts], [Python implementation][client].

## What the PR already contributes

The contribution is more than interface scaffolding. The inspected implementation provides:

- A packaged, synchronous Python 3.11+ library using HTTPX, with no import-time network activity or required scientific-array dependency
- Explicitly configured, origin-bound credentials and endpoints; TLS verification by default, disabled redirects and credential redaction
- Remote semantic resolution, resource listing/retrieval/resolution, scope creation and attachment, Runtime submission and a bounded direct Resolver interface
- Real job handles with polling, result retrieval, timeouts that retain resumable handles, explicit cancellation acceptance and distinct failure/interruption/unavailability states
- Runtime/context binding checks and an important separation between closing local connections and deliberately releasing remote state
- Scientific point reads through `StorageScan.Point`, preserving traversal, temporal slice, geometry and source/target semantics, rather than presenting observation metadata as computed values
- Integer/Decimal and boolean decoding that preserves valid zero/false values and distinguishes missing data
- Checked Java DTO conversion, retained raw fields, and explicit errors for unsupported operations
- Shared server work around units, job outcomes, storage registration and warm/cold context authorization

The existing synchronous interface is a sound first product choice. Similarly, keeping NumPy and pandas optional is useful; richer scientific integrations can be extras rather than prerequisites for authentication, job management or metadata access.

There is also meaningful verification. The three hosted Python 3.11/3.12/3.13 checks for the inspected head are successful. The PR reports 97 offline tests per interpreter, 56 strictly selected Java tests, and a real local service-stack workflow with signed user credentials and synthetic data, including cancellation and restart/authorization cases. These are useful layers of evidence, especially the distinction between mocked transport tests and actual computation/storage. The report explicitly does not claim a green full Java suite or independent terrain-provider validation. The committed throughput summary describes a small synthetic, pre-final-commit integration run and is not a general capacity benchmark. No tests or service deployment were rerun for this assessment.

Sources: [client][client], [transport][transport], [DTO conversion][dto], [test cases][tests], [hosted workflow][ci], [PR verification record][pr], [local measurement record][measurement].

## Define three different completion targets

### A dependable scientific client

A scientist can connect to an authorized deployment, discover or choose appropriate services, express an observation in a spatial/temporal context, inspect progress and failure, retrieve usable scientific results, inspect their provenance, and return to the work after closing Python. The experience should work in a script and a notebook without requiring Java implementation names or manually assembled wire dictionaries for ordinary operations.

This is the first release target. It should include bounded, documented service and data capabilities rather than claim every k.LAB possibility. PR 84 already supplies the core execution path; authentication ergonomics, domain objects and substantial data access are the next high-value additions.

### A full remote user client

This adds the user-facing orchestration responsibilities currently distributed across Engine, Modeler and their clients: service catalogs and selection, coherent twin/graph navigation, semantic assistance, richer observation and observer workflows, resource/model inspection and authorized authoring, and supported behavior/agent interaction.

“Full” should mean coverage of the agreed, implemented remote user workflows. It need not reproduce desktop windows, IDE controllers or a JVM's internal class hierarchy. Python can provide headless editing and validation plus notebook representations while a separate application owns interactive UI.

### A provider SDK or embedded runtime

Writing a Python contextualizer, hosting an adapter, serving data to remote runtimes, running the Reasoner locally, or owning storage/scheduling/transactions is a different commitment. These roles require execution contracts, deployment and isolation, dependency management, resource budgets, cancellation, output validation and provenance attribution. They are not implied by a full remote client.

The appropriate first bridge for an existing Python model is an explicit service/resource contract with semantic inputs and outputs. A Python worker SDK may then share schemas and transport primitives with the client. Reimplementing the Java inference engine, graph database, scheduler or storage manager should not be a client milestone.

Sources: [access and extension model][klab], [Engine and scopes][scopes], [Modeler observation orchestration][modeler], [components][components], [client-only digital twin boundary][client_twin].

## Capability matrix

The last column identifies the main delivery responsibility: **Python** means chiefly client work against existing routes; **joint** means client work plus clarification, hardening or extension of the server contract; **server-led** means Python cannot establish the promised behavior alone.

| Capability | PR 84 foundation | Work needed for the agreed full client | Responsibility |
| --- | --- | --- | --- |
| Identity and onboarding | Accepts already-issued network JWTs for configured origins | Supported Engine/Hub login integration, identity/profile and credential-provider abstraction, renewal/expiry behavior, secure persistence when selected, multi-deployment profiles | Joint |
| Service catalog and selection | Capabilities/status inspection and explicit peer advertisement | Discover authenticated service references; distinguish reachable, available and operational; select compatible services/worldviews; refresh advertisements; explain unavailable dependencies | Python and joint |
| Federation | Explicit peer IDs and home Runtime binding | Multiple Resources providers, origin-qualified identities, safe routing and partial-failure reporting; later consume coordinated discovery when available | Joint; global coordination server-led |
| Scopes and lifecycle | Session/context creation, attach, focus and explicit release | Typed configuration, persistence/rights/observer/grid settings, complete supported contextual selection, safe saved handles and restoration diagnostics | Python and joint |
| Semantics | Resolve concepts/observables; preserve raw semantics | Meaningful Python semantic objects, supported semantic predicates/search/composition and validation; respect worldview identity and knowledge revisions | Python against supported Reasoner APIs |
| Observation workflows | Submit an `ObservationImpl` or checked mapping; retrieve a bound result | Convenient observe/register/query/focus workflows; typed constraints, selected models and lexical context; observer geometry and grid configuration; meaningful collections and relationships | Python and joint |
| Planning and explanation | Direct Resolver returns a raw Dataflow job | Typed resolution outcomes, coverage/diagnostics and selected dependencies; expose plans without implying execution or portable reproduction | Python; replay server-led |
| Jobs and cancellation | Poll/result/timeout/resume/cancel state handling | Serializable origin-qualified handles, progress/events, retention awareness, bounded parallelism, sync/async APIs and safe reconciliation after ambiguous submission | Python and joint |
| Scientific values | 1–256 explicit offsets per call, implemented as individual HTTP point requests | Streamed exports, efficient windows/chunks, typed values and validity, revision-pinned reads, cancellation and bounded memory | Python plus exporter/transport work |
| Space and time | Retains geometry DTOs, traversal and explicit slice | Typed scales, CRS/axis/world-coordinate mapping, temporal-state discovery/selection, observer extents, grid alignment and supported geometry conversion | Python and joint |
| Units and categories | Simple unit semantic snapshots and checked cell decoding | Ordinary range/currency mediation where supported; explicit precision policy; semantic dictionaries, worldview commitments and masks; reject unsupported contextual conversions | Python and joint |
| Twin graph and provenance | Raw graph query DTOs and retained observation fields | Typed assets/links/activities/commits, coherent partial cache, revisions, provenance navigation and execution explanation | Python against existing primitives; recovery joint |
| Events and reconnection | HTTP polling; manually resumable context/job handles | Scoped notifications and commit application, deduplication and race handling, reconnect catch-up or explicit resynchronization, stale-state indicators | Python and joint |
| Resources and models | List, retrieve, resolve | Typed query/info, dependency loading/routing, import/export schema discovery; later parse/validate/author/publish with supported locks and versioning | Python; incomplete mutations joint |
| Behaviors and agents | No dedicated user-facing workflow | Invoke supported server-side agent/behavior operations and inspect outcomes; integrate notifications and permissions | Python and joint; compiler remains server-side |
| Python analysis experience | Importable synchronous library and an elevation example | Optional NumPy, pandas, xarray, vector/raster adapters, notebook summaries and progress; documented metadata-preservation rules | Python after data contracts |
| Compatibility and operations | Pinned integration target, errors, tests and package builds | Public API/version policy, feature negotiation, cross-version contract tests, real-provider acceptance, sustained resource/failure testing and release documentation | Joint |

Sources: [Python public API inventory][public_api], [common service contracts][services], [Resources contract][resources], [Reasoner contract][reasoning], [Runtime client][runtime_client], [storage contract][storage], [coordination boundaries][coordination].

## Recommended Python architecture

### Separate protocol adapters from public domain objects

Keep a small transport and wire-schema layer that understands current endpoints, headers, error responses and Java `@CLASS` discriminators. Above it, provide stable Python objects for service references, scopes, observables, observations, jobs, scales, graph assets and scientific data. Retained raw data remains valuable for diagnostics and forward compatibility, but should not be the primary interface for ordinary work.

This separation allows existing `*Impl` classes to remain compatibility facades while the public API becomes idiomatic. Prefer properties, typed value objects, iterators and explicit operations over translating every Java getter and interface inheritance relationship. Generated DTOs can reduce repetitive serialization work once the contract is stable; they will not supply scope semantics, scientific correctness or a useful notebook API.

The protocol layer must preserve unfamiliar fields and distinguish harmless extensions from unknown states that affect correctness. A newer semantic activity, unsupported precision conversion or incompatible scan-description version should produce a specific diagnostic, not an invented default. Version and feature handling should be explicit per service, because a federation can contain mixed revisions.

### Add an Engine-like connection layer

Introduce a connection/session manager with replaceable credential providers, an authenticated service catalog, capability negotiation, health/readiness tracking and an explicit selection policy. Keep the configured-URL/issued-token route as a supported path for scripts, controlled deployments and testing.

Use the existing authentication and discovery contracts rather than assuming a generic OAuth token is sufficient. Java currently authenticates through a certificate/Hub path and receives identity, groups, federation and service references. Decide whether Python supports that directly or attaches through an already-authenticated Engine; either is a legitimate product choice if the supported experience is complete and documented.

Do not forward credentials merely because a service advertises another URL. Preserve the PR's origin-bound credential discipline and make trust checks explicit in the discovery layer. New peer readiness should refresh the authorized catalog; notification acknowledgement should not be interpreted as proof that every dependency is usable. The repository explicitly distinguishes its current bounded-network mechanism from proposed global discovery and ownership coordination.

Sources: [scope orchestration][scopes], [authentication implementation][authentication], [service coordination][coordination], [transport foundation][transport].

### Make remote identity and lifecycle first-class

A persisted handle should identify its service/runtime, user/deployment context, twin, focused scope and asset/job, with a serialization version and no embedded secret. Bare numeric IDs are useful wire values but inadequate cross-runtime identities. A URI or typed reference can retain identity while service location changes later, without promising that migration already exists.

Separate local detachment/connection closure, remote cancellation, context release and any future suspend/delete operation. Expose only the remote lifecycle semantics that the service actually guarantees. Preserve the PR's non-destructive client shutdown and explicit release behavior. Add ownership/access metadata for explanation, while leaving authorization decisions to the server.

The public observe workflow should construct the right semantic request, observer/focus and resolution constraints. Model selection, project/namespace context, geometry and grid installation belong in typed options. A user should not need to know every header, and the client should not expose service-internal transactions as if it could commit a Runtime's graph.

Sources: [scopes][scopes], [Modeler dispatch][modeler], [Runtime routes][runtime_controller], [persistent twin lifecycle][persistent].

### Build the scientific data interface around a declared view

The next scientific milestone is more than returning a NumPy array. A data view must identify:

- The source observation, Runtime/twin, committed revision and selected event/time interval
- Shape, dimension names, traversal, CRS, coordinates/cell support and source versus requested geometry
- Primitive dtype and precision policy, validity/missingness and spatial coverage
- Source and requested observable, units/range/currency mediation, and categorical dictionary/worldview when applicable
- Chunk/window selection, size estimate, stream lifetime and cancellation behavior
- The provenance needed to explain the view and any conversion

The server already has substantial machinery worth reusing: planned reads, immutable descriptions, source leases, primitive block reads, indexed point access, supported spatial mediation, categorical dictionaries, grid alignment and shape coverage. A storage description is not itself an executable plan or permission to access data, and its local source-revision fingerprint is not a durable cross-provider content hash.

Start by exposing **the existing generic export route and advertised exporter schemas**. `KlabServiceController` already streams `/export/{class}/{urn}`; `BaseService` locates exporters and carries supported temporal and semantic options. Python needs genuine stream/file handling rather than buffering every response as its current transport does. Confirm the installed exporter and requested representation instead of promising that every observation can be exported in every format.

Then determine whether those exports cover required scientific slicing and lazy access. If they do not, specify a bounded window/chunk protocol around the existing read planner. Agree on dtype, validity, coordinates, dictionaries, revision pinning, checksums where appropriate, error behavior after partial transfer and cancellation before choosing the binary encoding. A format such as Arrow or an array-oriented format is a design candidate, not an established k.LAB protocol. The worker/adapter Avro contract is separate, and binary job retrieval is documented as incomplete; neither should accidentally define the scientist-facing data API.

Do not scale point access by issuing millions of requests. Keep it for inspection, validation and sparse samples; build bulk functionality on an appropriately bounded transport.

Sources: [StorageScan][storage_scan], [storage read and export contract][storage], [generic export controller][service_controller], [exporter selection][base_service], [current Python transport][transport], [common binary-job limitation][services].

### Add scientific adapters without discarding semantics

Use optional integration packages or extras:

- NumPy for explicitly materialized numeric arrays, with a documented validity representation and precision checks
- pandas for observation catalogs, provenance tables, point samples and suitable time series
- xarray for labeled multidimensional fields whose dimensions and coordinates have been established
- GeoPandas for vector features with preserved CRS, stable observation identities and semantic attributes
- Rasterio-compatible exports for raster workflows, retaining georeferencing and masks
- Lazy/chunked execution only once server-side selection and revision stability support it

These should be views/adapters of a k.LAB result, not replacements for its identity and semantic metadata. In particular, xarray treats arbitrary attributes as user metadata rather than enforcing k.LAB semantics; preserving attributes alone cannot guarantee semantically valid arithmetic. Local post-processing should remain distinguishable from a server-resolved computation, and any derived result returned to k.LAB needs an explicit import/annotation/provenance path.

For categorical observations, numerical storage codes must not become ordered measurements. Preserve the dictionary and worldview commitment. Distinguish missing values from out-of-support cells, and do not quietly convert 64-bit integers through floating point. Treat contextual units involving cell area or duration according to the supported server contract rather than a generic string-based unit conversion.

Sources: [scientific storage semantics][storage], [xarray data model](https://docs.xarray.dev/en/stable/user-guide/data-structures.html), [GeoPandas CRS model](https://docs.geopandas.org/en/stable/docs/user_guide/projections.html), [Rasterio georeferencing](https://rasterio.readthedocs.io/en/stable/quickstart.html).

### Give a live twin a coherent local representation

A full client needs a typed, explicitly partial graph projection. Fetch assets, directional links, activities and commits; apply updates once; track what is loaded and what revision it reflects. Represent creation, classification/characterization and relationships correctly instead of assuming every operation creates a new observation.

Use scoped events to update progress and invalidate/refresh state. Commit visibility is the relevant persistence boundary: a child activity finishing is not proof that an enclosing transaction committed. Handle the race between a job response and its corresponding message. On a dropped connection, recover from supported history or perform an explicit resynchronization; mark stale information instead of silently displaying it as current.

Java already supplies useful precedents for a partial graph and temporal activity/transition recovery. However, current AMQP transient auto-ack queues are live notifications, not durable event replay. A complete reliable subscription contract requires backend support. Start with polling plus scoped notifications and honest resync behavior; do not wait for a hypothetical global event platform before exposing useful graph browsing.

Sources: [knowledge graph][graph], [client twin implementation][client_twin], [client graph implementation][client_graph], [provenance commit semantics][provenance], [messaging limits][coordination].

### Preserve one set of semantics across sync and async APIs

Keep the convenient synchronous API. Add an explicit asynchronous client and async iterators for services, concurrent jobs and streams when those workloads are in scope. Share DTO codecs and state machines; do not hide a background event loop inside the synchronous client or invoke a nested loop in a notebook.

Use separate concepts for a request timeout, waiting timeout and server cancellation. A local task interruption should not silently dispose a twin. Bounded concurrency, backpressure and explicit stream closure matter more than parallelizing every endpoint.

Retain the PR's safe default of not retrying ambiguous mutations. Retry read-only or expressly idempotent operations within a bounded policy. If the server does not support an operation key/status reconciliation mechanism, report an unknown submission outcome rather than risking duplicated computation. Offline mode can inspect an explicitly saved snapshot or prepared request; it should not imply offline reasoning, authoritative permissions or automatic replay of queued mutations.

## Server contracts that shape the critical path

Several next steps are ordinary Python work. Others need a backend owner or an agreed interoperability contract. The distinction prevents the roadmap from treating missing platform guarantees as unfinished wrappers.

| Contract area | Existing foundation | Decision or prerequisite for broader promises |
| --- | --- | --- |
| Authentication | Engine/Hub authentication and issued network credentials | A supported non-Java onboarding/renewal route and credential lifecycle |
| Protocol compatibility | Java contracts, controllers, DTOs and generated API descriptions | Maintained language-neutral DTO/schema and feature/version policy; consistent structured errors |
| Scientific export | Generic streamed export plus planned storage reads and installed exporters | Validated export coverage; generic chunk/window contract only where existing formats are insufficient |
| Long-running jobs | Scoped cached results, status and cancellation | Document retention; persistent operation lookup/idempotency if recovery after ambiguous submission is required |
| Twin lifecycle | Creation, connection, release and bounded restoration | Distinct detach/suspend/delete semantics, safe quiescence, complete restoration and scheduler checkpoints for execution continuation |
| Graph/event recovery | Commit fetch, partial client graphs, temporal recovery primitives | Clear snapshots/cursors/retention and resynchronization; durable delivery/replay for stronger guarantees |
| Federation | Authenticated configured catalogs and peer scope recovery | Global discovery, placement/ownership generations, migration and connected-twin composition remain platform work |
| Semantic authoring | Reasoner validation/search and Resources typed operations | Enumerate supported mutations and projections; do not advertise pending operations as available |
| Reproducibility | Recorded activities, plans and provenance | Complete provenance-to-dataflow extraction, dependencies and serialization before claiming portable replay |

Important distinctions:

- A persistent twin reopening does not establish that an interrupted simulation can resume safely
- A Resolver Dataflow describes work in an existing context; it is not automatically a portable reproduction package
- Generic export/adaptation routes should be used where implemented; named visualization/explorer routes currently include placeholders
- Global discovery and connected-twin composition are explicitly described as proposed or incomplete in repository documentation
- Ordinary unit/range/pinned-currency mediation has implemented support; contextual cell-area/duration conversions and some geometric cases remain bounded or incomplete

Sources: [common services][services], [persistent twins][persistent], [dataflow contracts][dataflow], [discovery][coordination], [connected twins][distributed], [storage][storage], [Resources remaining work][resources].

## Delivery phases and acceptance scenarios

The effort labels below are relative planning bands, not calendar estimates: **bounded** means a focused client/contract slice; **substantial** means several interacting client subsystems; **platform-dependent** means a completion date depends materially on server work. Team size, supported deployments and reuse of Java components have not been specified, so precise week estimates would be misleading.

### Phase 0  Establish the client contract

**Deliverable:** an agreed scope for the scientific release and full user release, a client/server capability inventory, versioned fixtures and a reference deployment. Preserve PR 84's existing regression suite and positive workflow.

**Acceptance:** every public operation is classified as implemented and tested, implemented but not yet accepted end to end, or unsupported. A compatibility test distinguishes an unavailable service, unsupported feature and invalid request. DTO/enum changes do not silently reinterpret scientific meaning.

**Dependencies:** no new backend architecture. Requires agreement with maintainers on supported versions and public responsibilities.

**Effort:** bounded but essential; it prevents later API churn.

### Phase 1  Complete the everyday observation workflow

**Deliverable:** authenticated connection profiles, service catalog and selection, typed configuration/scopes, an idiomatic observe path, observer/grid options and saved handles. Build first on the existing bounded-network deployment model.

**Acceptance scenarios:**

1. A new user follows one supported credential flow, sees usable services and runs an observation without hand-authoring Java DTO dictionaries
2. A notebook kernel closes and a new process attaches to the same permitted twin, restores an observation/job handle and reads its state without disposing it
3. A read-only collaborator can inspect shared work while prohibited operations fail explicitly; a stale token is distinguished from an unauthorized context
4. An unavailable Reasoner or changed service capability produces an actionable explanation; credentials are not forwarded to an untrusted origin
5. A requested model, observer geometry, focus and resolution constraint can be inspected in the resulting plan/provenance

**Dependencies:** Phase 0; the chosen identity integration. Broader catalogs must preserve service-selection policy rather than wait for global discovery.

**Effort:** substantial.

### Phase 2  Make results useful for scientific work

**Deliverable:** stream/file exports using supported schemas, typed scale/time/unit views, robust data descriptors and optional array/tabular/geospatial adapters. Add chunked selection where required by demonstrated workloads.

**Acceptance scenarios:**

1. Retrieve a nonuniform multi-shard field through a supported exporter with an independently verified coordinate mapping, valid zero/false and missing cells
2. Select explicit temporal states and a supported spatial window; confirm dtype, precision, CRS, units, source revision and masks
3. Compare a real-provider result against an independent reference with declared dataset revision, sampling and datum/tolerance policy
4. Transfer a representative large result with bounded memory; interrupt a stream and verify clean resource closure and an honest partial-result/error state
5. Convert to xarray or a raster without guessing axis orientation; categorical data retain their semantic dictionary and worldview

**Dependencies:** Phase 0 plus stable scope/result objects from Phase 1. Export wrapping and metadata modeling can proceed in parallel with identity work. Lazy arrays depend on selection/revision contracts, not merely an async HTTP client.

**Effort:** substantial; new generalized bulk protocols are platform-dependent.

### Phase 3  Support live and persistent scientific sessions

**Deliverable:** typed graph/provenance navigation, progress and subscriptions, committed-state reconciliation, async concurrency, reconnect/resync and explicit offline snapshots.

**Acceptance scenarios:**

1. Two clients observe the same twin and converge on committed changes without duplicate graph entries when events are duplicated or arrive around the HTTP result
2. A client disconnects while work runs, reconnects and catches up or explicitly refreshes its partial view; it does not claim an unverified cache is current
3. Cancellation races with completion, a job expires, and a process restarts; each outcome remains distinguishable and no operation is silently resubmitted
4. A provenance query explains the resources/models/components and accepted execution plan for a selected observation, including semantic-update effects
5. A notebook can run several bounded concurrent jobs and cancel its own waits without disrupting other work or deleting a shared context

**Dependencies:** Phase 1 and graph/job primitives; authoritative scientific readback integrates Phase 2. Stronger durable replay and crash-safe execution continuation remain gated by server contracts.

**Effort:** substantial; durable guarantees are platform-dependent.

### Phase 4  Complete the broader remote user experience

**Deliverable:** semantic assistance and document validation; richer resource/model discovery and dependency inspection; supported import/authoring/publishing/repository workflows; behavior/agent invocation and monitoring. Expose available export/adaptation for notebook representations rather than copying desktop UI code.

**Acceptance scenarios:**

1. Find candidate assets by meaning and inspect applicability, dependencies, versions and access before selecting them
2. Parse/validate a model or ontology against an identified knowledge revision; stale validation cannot appear as current success
3. Import a supported data asset, supply its semantic annotation and use it in an observation; provenance records the supplied input
4. Edit/publish an authorized supported asset with appropriate locking/version checks, and report conflict or unsupported mutation honestly
5. Start a supported server-side behavior/agent, receive scoped outcomes and reconnect without claiming the Python process owns its compiler or scheduler

**Dependencies:** Phases 1 and 3; Phase 2 for scientific inputs/outputs. Scope the first authoring release to operations the server actually implements.

**Effort:** substantial, with backend-specific slices that can be delivered independently.

### Phase 5  Extend the platform through Python where needed

**Deliverable:** a separately scoped provider/worker SDK or local service-launch integration, if users need to contribute Python algorithms or run a managed local stack. Share protocol types with the client while keeping runtime dependencies optional.

**Acceptance:** a Python computation receives declared scoped inputs, follows agreed geometry/units/validity contracts, supports cancellation/resource limits, and returns validated outputs with attributable provenance. A failed worker does not publish partial scientific state.

**Dependencies:** explicit execution/deployment contracts and server integration. A genuinely embedded Python runtime would need its own feasibility and compatibility plan.

**Effort:** platform-dependent and potentially much larger than the remote client.

### Continuous release gate

Every phase should retain fast offline tests, exact-revision Python/Java contract fixtures, installation-from-wheel checks and a small reproducible live deployment. Add cross-version/mixed-service tests, expired/forbidden credentials, timeouts, retry ambiguity, stream cancellation and restart cases as the corresponding features appear. Scientific acceptance must check independently known values and coordinate semantics; a successful HTTP response or finished job is insufficient.

For a public release, include a version/support policy, capability limitations, tested deployment recipe and migration guidance. The offline suite, live fixture suite, real-provider validation and scale/soak results should remain separately reported.

## Decisions that unblock the next milestone

1. Is the first supported authentication experience direct Hub/certificate integration, attachment through an authenticated Engine, or both?
2. Which scientific workflows and observation types define the first release beyond the existing small elevation example?
3. Which installed export representations can satisfy those workflows, and which require a new chunk/window contract?
4. What client-visible guarantees apply to retained jobs, committed reads, reconnect and twin persistence?
5. Which authoring and behavior operations belong in the full remote user client, and which should be optional modules?

The immediate recommendation is to retain the work in PR 84 as the tested execution foundation, settle these decisions, and make the next contribution a **typed observe-and-export scientific workflow**. That delivers practical value while the more ambitious federation, durable-event and replay contracts mature.

## Integration and Java compatibility gate

PR 84 changes shared Java behavior as well as adding Python code. The integration condition for eventual promotion to `develop` is **no unacceptable Java regression at the exact combined candidate revision**. Integration into the isolated upstream `feature/python-client` branch preserves the contribution for further validation; it does not establish that this condition has passed.

A concrete source-level concern remains in the Java federation-session path. The existing contract selects a common session ID for users in the same non-local federation. Runtime session creation reuses that session without rebinding its owner, while PR 84 makes managed non-context scopes owner-only. A second legitimate federation member can therefore receive the shared session ID and then be denied when creating a context through it. This is a strong source inference, not an executed reproduction.

The [federation compatibility note](PYTHON_CLIENT_FEDERATION_COMPATIBILITY.md) records the complete source chain, reproduction specification, security invariants, design options and exact-revision acceptance gate. Resolve the intended session-authority policy without weakening private-context ACLs or granting collaborators authority over another user's private session.

The acceptance gate includes the combined Java diff, the strict affected Java selection, native Java client and federation workflows, the new authority-codelist/proposal tests, a broader unchanged-baseline comparison, Python packaging/contracts, and real-stack scientific/lifecycle/cancellation/restart checks. Existing hosted Python checks and previously reported Java/live results are historical evidence; they do not certify the updated combined branch. No new runtime tests were executed in preparing these documents.

## Source notes

Repository links below are pinned to the inspected PR head unless marked as the integrated Java baseline. Existing documentation sometimes describes intended architecture beside implemented behavior; this assessment uses explicit implementation-boundary sections and inspected code to distinguish them. Test totals and local live results are attributed to the PR's records; only the hosted check conclusions were independently queried.

[pr]: https://github.com/integratedmodelling/klab-services/pull/84
[ci]: https://github.com/integratedmodelling/klab-services/actions/runs/37642924541
[architecture]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/ARCHITECTURE.md
[klab]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/KLAB.md#access-extension-and-integration
[contracts]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/python-client/docs/contracts.md
[public_api]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/python-client/docs/public-api.md
[client]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/python-client/src/klab_client/client.py
[transport]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/python-client/src/klab_client/transport.py
[dto]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/python-client/src/klab_client/dto.py
[tests]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/python-client/tests/test_client.py
[measurement]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/python-client/docs/results/local-throughput.json
[scopes]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/SCOPES.md
[services]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/SERVICES.md
[coordination]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/SERVICE_COORDINATION_AND_DISCOVERY.md
[resources]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/RESOURCES.md
[reasoning]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/REASONING.md
[components]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/COMPONENTS.md
[storage]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/STORAGE.md
[storage_scan]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.api/src/main/java/org/integratedmodelling/klab/api/data/StorageScan.java
[graph]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/KNOWLEDGE_GRAPH.md
[provenance]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/PROVENANCE.md
[persistent]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/PERSISTENT_TWINS.md
[distributed]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/DISTRIBUTED_TWINS.md
[dataflow]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/docs/DATAFLOW.md
[runtime_controller]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.services.runtime.server/src/main/java/org/integratedmodelling/klab/services/runtime/server/controllers/RuntimeServerController.java
[service_controller]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.services/src/main/java/org/integratedmodelling/klab/services/application/controllers/KlabServiceController.java
[base_service]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.services/src/main/java/org/integratedmodelling/klab/services/base/BaseService.java
[runtime_client]: https://github.com/integratedmodelling/klab-services/blob/64754ea2b4d7207d51db454506f51b4b18741817/klab.core.common/src/main/java/org/integratedmodelling/common/services/client/RuntimeClient.java#L397-L531
[client_twin]: https://github.com/integratedmodelling/klab-services/blob/64754ea2b4d7207d51db454506f51b4b18741817/klab.core.common/src/main/java/org/integratedmodelling/common/services/client/digitaltwin/ClientDigitalTwin.java
[client_graph]: https://github.com/integratedmodelling/klab-services/blob/64754ea2b4d7207d51db454506f51b4b18741817/klab.core.common/src/main/java/org/integratedmodelling/common/services/client/digitaltwin/ClientKnowledgeGraph.java#L129-L255
[modeler]: https://github.com/integratedmodelling/klab-services/blob/64754ea2b4d7207d51db454506f51b4b18741817/klab.modeler/src/main/java/org/integratedmodelling/klab/modeler/ModelerImpl.java#L179-L316
[authentication]: https://github.com/integratedmodelling/klab-services/blob/64754ea2b4d7207d51db454506f51b4b18741817/klab.core.common/src/main/java/org/integratedmodelling/common/authentication/Authentication.java#L118-L395
[federation_contract]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.api/src/main/java/org/integratedmodelling/klab/api/scope/UserScope.java#L71-L79
[federation_selection]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.common/src/main/java/org/integratedmodelling/common/services/client/scope/ClientUserScope.java#L101-L123
[session_reuse]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.services.runtime.server/src/main/java/org/integratedmodelling/klab/services/runtime/server/controllers/RuntimeServerController.java#L700-L713
[context_request]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.common/src/main/java/org/integratedmodelling/common/services/client/BaseServiceClient.java#L265-L281
[scope_authorization]: https://github.com/integratedmodelling/klab-services/blob/1a8bfc07bde272dd3aed7c2e2918960d5c8aea1e/klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/ScopeManager.java#L47-L63
