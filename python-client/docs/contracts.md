# Server contract and compatibility

Inspected baseline: branch `feature/python-client`, commit
`75bf1f7d2` (full revision recorded in verification.md). The assignment's
`e24b756f0815d2f51034d62282a175857f5be15c` is not in this local object database.
This client targets the checked-out controllers, not an assumed older API.

Source roots below are relative to the repository. `API` means
`klab.core.api/src/main/java/org/integratedmodelling/klab/api/`;
`COMMON` means `klab.core.common/src/main/java/org/integratedmodelling/common/`.
Runtime controller: `klab.services.runtime.server/src/main/java/org/integratedmodelling/klab/services/runtime/server/controllers/RuntimeServerController.java`.
Jobs controller: `klab.core.services/src/main/java/org/integratedmodelling/klab/services/application/controllers/KlabJobController.java`.

| Python operation | Verb and full route | Payload / result | Identity and scope | Async / support | Source |
|---|---|---|---|---|---|
| All service `capabilities` | GET `/public/capabilities` | service capabilities, retained raw | issued credential when needed | implemented | API ServicesAPI; common service controllers |
| Reasoner `resolve_concept`, `resolve_observable` | POST `/api/v1/resolve/concept`, `/api/v1/resolve/observable` | **plain text** definition / polymorphic Concept, Observable | reasoner-authorized credential | implemented | ReasonerController; COMMON services/client/ReasonerClient.java |
| Resources `retrieve`, `resolve`, `list` | GET `/api/v1/retrieve/{knowledgeClass}/{urn}`, `/api/v1/resolve/{knowledgeClass}/{urn}`, `/api/v1/list/{knowledgeClass}` | asset, ResourceSet, asset list | authorized user | implemented; list has no server pagination | ResourceCRUDController; API services/resources/ResourceSet.java |
| Resources `contextualize` | POST `/contextualize` | binary Avro DataRequest / data job | service context | explicitly unsupported: binary worker input/Avro outside this slice | ResourcesProviderController:567–623 |
| `create_session` | POST `/createSession` | ScopeRequest / **plain text** ID | runtime user | implemented | runtime controller:678; API services/runtime/objects/ScopeRequest.java |
| `Session.create_context` | POST `/createContext` | ScopeRequest / polymorphic Configuration | session `klab-scope` | implemented | runtime controller:763 |
| `attach_context`, Runtime `connect_context` | POST `/api/v1/connect` | ScopeRequest / Configuration | runtime user | implemented; no ownership transfer | runtime controller:342; COMMON services/client/RuntimeClient.java:264 |
| Runtime `submit`, Context `submit` | POST `/api/v1/submit` | ResolutionRequest with transported Observation, agentName, resolutionConstraints / integer job ID | context (+ optional observation path, observer), origin runtime service ID | implemented Job handle | runtime controller:131; COMMON services/client/RuntimeClient.java:76 |
| Job `status`, `result`, `cancel` | GET `/jobs/status/{id}`, `/jobs/retrieve/{id}`, `/jobs/cancel/{id}` | JobStatus, serialized result, boolean | **same context/user** that submitted | implemented; FINISHED, ABORTED, INTERRUPTED, EMPTY; cancel boolean is request acceptance | jobs controller; klab.core.services JobManager.java |
| Observation `fetch_data` | POST `/api/v1/observation/value` | StorageScan.Point / **text/plain** cell | authorized context | implemented indexed cell reads; explicit traversal and initialization slice | runtime controller:70; API data/StorageScan.java; klab.core.services runtime/storage/StorageReads.java |
| Job binary data | GET `/jobs/retrieveData/{id}` | Avro Data | authorized scope | unsupported decoder; observation jobs return Observation, not Data | jobs controller:68; JobManager.getDataResult |
| Runtime `get_context_info` | GET `/api/v1/contexts` | ContextInfo list with Configuration | user | implemented | runtime controller; API services/runtime/objects/ContextInfo.java |
| Runtime `query_knowledge_graph` | POST `/api/v1/query` | structured KnowledgeGraph.Query / asset list | context | implemented for explicit wire DTO mapping; not arbitrary query strings | runtime controller; COMMON services/client/runtime/KnowledgeGraphQuery.java |
| Runtime `release_session`, `release_context` | GET `/releaseSession`, `/releaseContext` | boolean | corresponding scope | implemented, explicit remote close | runtime controller:847,863; ServiceContextScope.close; Persistence enum |
| Resolver `resolve` | POST `/api/v1/resolve` | ResolutionRequest / dataflow job ID | peer context + originating runtime `klab-service` | implemented as Job yielding raw dataflow; does not execute a dataflow | ResolverController:45; COMMON services/client/ResolverClient.java |
| Resolver `encode_dataflow` | none | none | none | unsupported; Java method itself TODO | COMMON services/client/ResolverClient.java:77 |
| Resolver `submit_resource`, `get_submitted_resources` | POST `/api/v1/resource`, GET `/api/v1/resources` | transported Observation / Resource; list | peer context | implemented | ResolverController:82,97 |

## Identity, DTOs, completion

`TokenAuthorizationFilter` accepts the issued credential in `Authorization` (and
also strips `Bearer `). `ServiceAuthorizationManager.validateToken` validates the
legacy k.LAB JWT against registered hub keys, or a service-local web-session
credential. A generic OAuth/Keycloak access token is **not** interchangeable.
Supply per-service issued credentials explicitly; web-session credentials belong
only to their issuing origin. No role set in a Python DTO grants authority.
Service peers must already be configured/advertised in the deployment's user
scope. Supplying `serviceIds` does not install or authorize services.

Scope grammar is `session.context[.observationId...][#observerId]`, or just a
session ID (API scope/ContextScope.java:435–525). It is not two independent IDs
joined speculatively. The `klab-service` header identifies the originating runtime
when forwarding a context to peers.

The mapper in COMMON data/jackson/JacksonConfiguration.java uses **`@CLASS`**
discriminators and implementation fields, not only conventional bean properties.
Python retains complete remote concept/observable DTOs when transporting them.
Geometry is an explicit API GeometryImpl/DimensionImpl DTO, not `str(value)`.
Missing cell text is `null`; exact integers, booleans, floating special values and
semantic keys must remain distinct. Results retain metadata, geometry, units,
coverage and commit/provenance fields when supplied. FINISHED is result delivery;
error notifications, empty observations and incomplete coverage are not silently
treated as scientific success. EMPTY means unavailable/expired, not cancellation.

## Concrete supported experiment

Use the maintained storage testcase's 500 m × 400 m EPSG:3857 rectangle at
`[200000,200500] × [6000000,6000400]`, with a 100 m grid. Resolve
`earth:Region`, submit the named region `staging.storage:rectangle`, then
submit `geography:Elevation in m` within that region. Read initialization cells
in D2_YX traversal. Resolve `geography:Elevation in mm` and request the same cells
with explicit StorageScan.Semantics. Assert finite, nonmissing samples, plausible
elevation (-500 to 9000 m), unchanged missingness and `mm = m * 1000`.
This invariant is independent of the selected elevation provider's exact terrain
values and checks a scientifically meaningful unit conversion.

Dependencies: authenticated runtime with storage/knowledge graph and default
provenance agent; connected reasoner and resolver; worldview defining the two
geography concepts; a working elevation model/resource covering this rectangle.
The repository contains the maintained experiment, not a deployed elevation
dataset. Without these prerequisites, live acceptance must fail explicitly.
No scientific completion or numeric values have been inferred from local fakes.

Local investigation on 5 October 2026 confirmed the Region namespace against
`integratedmodelling/imod` revision `608bef150ced0a109db98a5aad64ba4461beaa54`.
The maintained older storage testcase's `geography:Region` returns `owl:Nothing`;
the corrected example uses `earth:Region`. The actual Reasoner also drops string
unit declarations because `internal/SemanticsBuilder.withUnit(String)` is a stub
at the inspected server baseline. The m/mm requests resolved the semantic concept
but returned no unit field. This is a demonstrated server blocker for the unit
invariant, not permission to synthesize units in Python. See local-stack.md.
