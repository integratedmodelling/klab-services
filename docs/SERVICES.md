# Common service endpoint contracts

This document describes the HTTP operations shared by the Resources, Reasoner, Resolver, and
Runtime services. It is the entry point for the service API documentation: common transport,
inspection, adaptation, asset exchange, jobs, and administration belong here; the assets and
operations owned by each service belong in its service-specific reference.

This is an initial reference to the current implementation, not a declaration that every service
supports every asset or representation. A shared route delegates to the receiving provider;
its useful projections, import/export schemata, and permissions depend on that provider.

## Service-specific references

| Service | Responsibility | Documentation scope |
| --- | --- | --- |
| Resources | Manage and distribute resources, projects, workspaces, and language assets. | [Resources service contract](RESOURCES.md) is the currently scoped service reference. It also identifies implementation work remaining. |
| Reasoner | Interpret and reason over semantics. | Dedicated endpoint reference pending. |
| Resolver | Resolve observation requests into executable plans. | Dedicated endpoint reference pending; [Resolution](RESOLUTION.md) provides conceptual context. |
| Runtime | Execute dataflows and manage observations and digital twins. | Dedicated endpoint reference pending; [Digital twins](DIGITALTWINS.md) provides conceptual context. |

Future service references should extend this contract with their own endpoint tables, supported
knowledge classes and projections, authorization rules, examples, and implementation limitations.
The conceptual documents above are not substitutes for those endpoint contracts.

## Authority and URL conventions

The source of truth for paths is [ServicesAPI](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/ServicesAPI.java).
The common Java contract is [KlabService](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/KlabService.java).
HTTP bindings live in the shared [controllers](../klab.core.services/src/main/java/org/integratedmodelling/klab/services/application/controllers),
with provider behavior in [BaseService](../klab.core.services/src/main/java/org/integratedmodelling/klab/services/base/BaseService.java).
[BaseServiceClient](../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/BaseServiceClient.java)
implements the corresponding remote Java operations.

All paths below are relative to the service root URL. The API currently mixes `/public/...`,
`/api/v1/...`, and unversioned paths. Do not prepend `/api/v1` to every route. Path placeholders
such as `{urn}` must be URL-encoded as path values. Knowledge classes are the names of
`KlabAsset.KnowledgeClass` values, for example `WORKFLOW`, `CONCEPT`, and `OBSERVABLE`.

A Java method or a constant in `ServicesAPI` does not by itself establish an HTTP route. This
reference lists actual controller mappings. In particular, the session/context lifecycle
constants are not implemented by the shared scope controller described below.

## Authentication, scopes, and representations

Public discovery is permitted without authentication. The shared security filter requires
authentication for the other API routes, including `/api/v1/adapt`. Supply the user token in
`Authorization: Bearer <token>`; the token filter also accepts the raw token for existing clients.
A controller accepting a nullable principal or scope does not make its route public.

| Header | Contract |
| --- | --- |
| `Authorization` | Supplies the identity used to build the service-side authorization and scope. |
| `klab-scope` | Selects the session/context scope where required; use the existing scope serialization contract rather than inventing an identifier format. |
| `klab-service` | Identifies the scope's native service where relevant to distributed requests. |
| `Content-Type` | Describes the request body, typically `application/json`, `text/plain`, or `multipart/form-data`. |
| `Accept` | Selects the requested response representation where the endpoint supports negotiation. Set it explicitly for images and exports. |

See [Scopes](SCOPES.md) for scope semantics. Visibility and mutation authority remain provider
responsibilities; authentication alone does not grant access to every asset. Administrative
operations additionally carry role and permission requirements.

JSON is the normal bean representation. Binary representations are raw response bytes, not
base64 JSON strings. The Java `adapt` client returns `byte[]`; callers requesting JSON decode
those bytes as UTF-8 before deserializing the result.

## Public discovery

| Method | Path | Result |
| --- | --- | --- |
| GET | `/public/health` | Spring Actuator `HealthComponent`, containing aggregated health information. |
| GET | `/public/status` | `KlabService.ServiceStatus`, for operational status polling. |
| GET | `/public/capabilities` | `KlabService.ServiceCapabilities`, including common and service-specific capabilities. |

Capabilities may reflect the caller's privileges when authorization is present. Consult them
for advertised import/export schemata and service features. A service being reachable does not
mean every operation is ready or supported. The health controller returns the health component
directly; clients should inspect its health content rather than assume the status mapping of a
separate Actuator endpoint.

## Inspection and queries

| Method | Path | Input | Result |
| --- | --- | --- | --- |
| GET | `/api/v1/info/{knowledgeClass}/{urn}?infoClass=...` | Fully qualified Java projection class in `infoClass`; `Accept: application/json`. | One typed projection or provider-defined absent result. |
| GET | `/api/v1/info/{knowledgeClass}/{urn}` | `Accept: image/png`; omit `infoClass`. | PNG of an adaptable asset, or 404 when the source is absent. |
| POST | `/api/v1/query/{knowledgeClass}?infoClass=...` | JSON object of query parameters; fully qualified projection class. | JSON list of typed projections. |

These operations require an authorized `UserScope` (including its session/context subtypes).
`knowledgeClass` identifies the source kind; `infoClass` identifies the requested view of it.
It is not a request to convert every object into any Java class. The server must have the class
and the provider must support that projection.

The common implementation supports service status/capability and common information projections.
Its generic query accepts `urn` or `query` as a Java regular expression over information
identifiers, defaulting to `.*`; unsupported keys are rejected. Service-specific query
implementations may define additional contracts in their own references. There is no common
pagination or query language promised by this route.

For an adaptable process, requesting
`org.integratedmodelling.klab.api.documentation.FlowChart` as `infoClass` returns a laid-out chart.
The PNG variant resolves the same source through provider visibility checks and renders it.
Successful image responses carry `Cache-Control: private, no-store`. JSON absence is not
uniformly mapped to 404 by the controller; do not generalize the PNG status contract to all
projections.

## Content adaptation

`POST /api/v1/adapt` transforms content supplied in the request body. Unlike `info`, it needs no
stored asset URN. Unlike `import`, it does not submit the supplied content for storage.

| Input | Requested output | `assetClass` query parameter | Provider |
| --- | --- | --- | --- |
| Complete FlowChart JSON envelope | `image/png` | Omit | All providers inheriting the BaseService implementation. |
| Language source text | `application/json` | Required semantic `KnowledgeClass` | Resources specialization. |

The body is the source itself, not a JSON wrapper containing a `source` field. The output is
selected by `Accept`; the optional `assetClass` selects semantic parsing in Resources. It is an
enum name, not a Java class name, and is distinct from inspection's `infoClass` parameter.
No general registry of arbitrary source/media-type conversions is promised yet. Other providers
can override `adapt` while retaining the shared HTTP and client contract.

### FlowChart to PNG

Post the complete chart with `root` and optional chart-level `metadata`, not just the ELK root
node. Both plain JSON and the IDE serializer's `@CLASS` metadata are accepted; the base adapter
does not use these metadata fields to load arbitrary Java types. See [Flow charts](FLOWCHARTS.md)
for the graph model, ID rules, labels, ports, links, and layout options.

For example, save this as `chart.json`:

```json
{
  "root": {
    "id": "process",
    "children": [
      {"id": "input", "labels": [{"text": "Input"}]},
      {"id": "result", "labels": [{"text": "Result"}]}
    ],
    "edges": [
      {"id": "compute", "sources": ["input"], "targets": ["result"]}
    ]
  }
}
```

Using a POSIX shell, with `SERVICE_URL` set to the service root and `TOKEN` to a valid token:

```sh
curl --fail-with-body "$SERVICE_URL/api/v1/adapt" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: image/png' \
  --data-binary @chart.json --output chart.png
```

The service validates and lays out a detached chart using ELK, measures labels, and renders PNG
headlessly. Rendering preserves aspect ratio and limits the image to 4096 pixels on either axis.
This operation is synchronous and returns the PNG directly, not a job ID. It is the route used
by the IDE's detailed ActivityCard for resolution and dataflow graph metadata.

### Resources source parsing and compatibility

For Resources, send source text with a supported knowledge class:

```sh
curl --fail-with-body "$RESOURCES_URL/api/v1/adapt?assetClass=OBSERVABLE" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: text/plain; charset=utf-8' \
  -H 'Accept: application/json' \
  --data-binary @observable.txt
```

`observable.txt` must contain a valid observable declaration for the service's language context.
`OBSERVABLE` and `CONCEPT` use the declaration helpers; other supported asset classes use the
workspace parser. An arbitrary knowledge class is not necessarily parseable.

The existing `POST /parseAsset/{assetClass}` route remains available and returns semantic beans.
It shares the provider's parsing helper with `/adapt`; existing `ResourcesService.parseAsset`
callers and their interfaces remain valid. Parsing rules and supported document kinds belong
in [Resources](RESOURCES.md) and the language references.

### Negotiation and errors

Set `Accept` explicitly. If the header is absent, the controller defaults to `application/json`,
which does not select FlowChart rendering. Accept lists are considered in Spring's specificity
and quality order; entries with `q=0` are skipped. Compatible wildcards currently select PNG
when `assetClass` is absent, or JSON when it is present.

Successful adaptation returns 200 with the selected `Content-Type` and
`Cache-Control: private, no-store`. Unsupported conversions return 406. Invalid input reported
as `IllegalArgumentException`, including malformed FlowChart JSON, becomes 400. Authentication
failures and provider-specific exceptions follow their own handling; this is not a promise that
every parsing or rendering failure is normalized to the same error body.

## Asset import and export

| Method | Path | Input | Result |
| --- | --- | --- | --- |
| POST | `/import/{schema}/{urn}` | JSON properties with `Content-Type: application/json`. | Numeric asynchronous job ID. |
| POST | `/import/{schema}/{urn}` | Multipart upload with a part named `file`. | Numeric asynchronous job ID. |
| GET | `/export/{class}/{urn}` | Required `Accept` media type and optional query parameters. | Streamed representation of the identified asset. |

Import resolves a transport schema against the receiving service's capabilities and request
scope. Use an advertised schema compatible with the input; the schema defines the JSON property
shape or accepted file format. The API convention uses `X:X:X:X` as the suggested URN placeholder
when no URN is proposed. Import submits the provider operation to the user's job manager.

Export's `{class}` is a `KnowledgeClass`. Extra query parameters are passed to the provider as
transport parameters. Export formats and asset support are service-specific; the shared
controller does not imply that all assets can be exported. Its fallback handling for observation
exports is incomplete, so consult the provider contract rather than relying on automatic fallback.

## User scope initialization

`POST /notifyUserScope` accepts a `UserScopeNotification` JSON bean and returns a boolean.
The engine uses it after connecting a user to services. The notification includes `services`
(entries with `id`, `url`, `type`, and status), `emailAddress`, and `localFederation`.

The controller attaches clients for the supplied federation to the authenticated user's service
scope, reuses the local provider when its ID matches, and returns the result of service validation.
It returns false if the required service user scope is unavailable. This notification does not
create a session or digital twin; see [Scopes](SCOPES.md) and the future Runtime endpoint reference
for their lifecycle contracts.

## Asynchronous jobs

Job IDs are meaningful within the service and user scope that submitted the operation. Poll and
retrieve using the same identity and appropriate scope headers.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/jobs/status/{id}` | `JobStatus` with `status` and optional `stackTrace`. |
| GET | `/jobs/retrieve/{id}` | Completed result serialized as JSON text; unavailable results or task failures raise an error. |
| GET | `/jobs/cancel/{id}` | Boolean indicating whether a cancellation request was accepted. This GET currently changes state. |
| GET | `/jobs/retrieveData/{id}` | Intended binary `Data` retrieval; see implementation limitation below. |

The manager reports `WAITING` while a future remains pending, `FINISHED` for a completed result,
`ABORTED` for a failure, `INTERRUPTED` for cancellation, and `EMPTY` when no entry is available.
Results are cached rather than a permanent job archive. A cancellation request does not guarantee
that external side effects have been reversed.

The current binary retrieval controller falls through to an exception even after copying a
`BaseDataImpl` result. Treat this route as incomplete until that behavior is corrected; do not
use it as the contract for PNG adaptation, which returns bytes directly from `/adapt`.

## Administration

The shared administrative controller is annotated for `ADMINISTRATOR` or `SYSTEM` roles.
Reading or changing settings additionally requires `CRUDOperation.ADMINISTER` in a service user
scope. Deployment method-security configuration must enforce the role annotations.

| Method | Path | Input and result |
| --- | --- | --- |
| PUT | `/shutdown` | Requests shutdown of this instance; returns true when accepted. |
| GET | `/settings` | Returns the visible settings map. |
| POST | `/set/{setting}` | Body is parsed as that setting's declared value class; submits the change and returns a job ID. |
| GET | `/credentials` | Lists visible external credential metadata. |
| POST | `/credentials` | Accepts `CredentialsRequest` containing host and credentials; returns `CredentialInfo`. |
| DELETE | `/credentials?id=...` | Removes credentials by ID; returns a boolean. |
| GET | `/checkCredentials?scheme=...&host=...` | Currently a stub that always returns false. |

External credentials are for services and resources contacted by k.LAB; they are distinct from
the token authenticating the current API caller. The setting body must match the setting's value
class; do not assume a universal `{"value": ...}` envelope from older method commentary.

## Web UI and generated API descriptions

The shared web controller serves the dashboard at `/`, full-page extensions at
`/ui/{componentName}`, configuration at `/public/ui/config`, and declared component resources at
`/public/ui/components/{componentId}/{version}/{resourcePath}`. These are presentation endpoints;
see [Web UI architecture](WEBUI_ARCHITECTURE.md) and [Web UI extension](WEBUI_EXTENSION.md).

The security configuration permits `/swagger-ui/**`, `/v3/api-docs/**`, and `/v3/api-docs.yaml`.
Where enabled by the application, generated OpenAPI documents complement this reference with
request schemas. They do not establish that a provider implements every advertised specialization.

## Documentation work remaining

The next branches are dedicated Reasoner, Resolver, and Runtime endpoint contracts. Resources
already has its [scoped reference](RESOURCES.md). Each branch should identify supported
projections, adaptation formats, transport schemata, permissions, and asynchronous workflows.

Common follow-up work includes documenting a consistent error/status envelope, completing binary
job retrieval and credential checks, and reconciling lifecycle constants with their actual
service bindings. Keep unimplemented or partial behavior explicit rather than deriving guarantees
from endpoint names or Java signatures.
