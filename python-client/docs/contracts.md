# Supported transport contracts

Current integration target: klab-services develop **64754ea2b**, plus the bounded
corrections in this branch. The initial route review used **75bf1f7d2** when the
specification's e24b756f revision was unavailable locally. Routes/DTOs were checked
against actual Java clients/controllers, with current integrated regression/live
checks; they were not guessed from constants.

| Python operation | Verb / route | DTO / identity |
|---|---|---|
| capabilities | GET /public/capabilities | Retained capabilities; serviceId required |
| initialize_user_scope | POST /notifyUserScope | UserScopeNotification, authorized user; explicit configured peers |
| resolve_concept/observable | POST /api/v1/resolve/concept or observable | Plain definition text; polymorphic Concept/Observable |
| resources list/retrieve/resolve | GET /api/v1/list/{class}, /retrieve/{class}/{urn}, /resolve/{class}/{urn} | Explicit KnowledgeClass; raw asset or ResourceSet |
| create_session | POST /createSession | ScopeRequest with unique ID/name; user credential; text ID |
| create_context | POST /createContext | ScopeRequest; session header; Configuration |
| attach_context | POST /api/v1/connect | ScopeRequest; actual owner/ACL checked, persisted ACL restored |
| submit | POST /api/v1/submit | ResolutionRequest: transported Observation, agentName, constraints; context; job ID |
| status/result/cancel | GET /jobs/status/{id}, /retrieve/{id}, /cancel/{id} | Original scope/user; status/result or cancellation acceptance |
| fetch_data | POST /api/v1/observation/value | StorageScan.Point; committed native source/context; text cell |
| get_context_info | GET /api/v1/contexts | Actual ACL-filtered descriptors, not federation authorization |
| query_knowledge_graph | POST /api/v1/query | Explicit wire query; raw graph assets, not scientific numeric values |
| remote release | GET /releaseSession or /releaseContext | Explicit corresponding scope, Boolean |
| direct resolver operations | POST /api/v1/resolve or /resource; GET /api/v1/resources | Peer context plus home Runtime ID; dataflow Job/raw resources |

Authoritative sources: ServicesAPI, ReasonerController, ResourceCRUDController,
RuntimeServerController, KlabScopeController/KlabJobController, Java service
clients, JacksonConfiguration, ScopeManager, JobManager and StorageScan/StorageReads.

Wire objects use **@CLASS** and implementation fields. Geometry is a checked DTO
or explicit encoding, never str(value) or its hash. Scope grammar:
`session.context[.observationId...][#observerId]` or session ID; `klab-service`
identifies the originating Runtime. Credentials are explicit and origin-bound.
No local role assignment grants authority. Finishing HTTP/job delivery is distinct
from scientific correctness; missing cell text is null, not zero/False.

String units/null job outcomes and local semantic indexing/storage registration
have bounded server corrections here. Context reconstruction uses exact persisted
owner/ACL, defaults missing legacy ACL to owner-only, and never derives rights
from requester or federation. Public user/group exclusions apply to warm and
cold access. Unresolved explicit scopes return 404 before controllers. Domain
errors are mapped at REST/filter boundaries. Destructive release rejects foreign
bound Runtime handles; explicit bare IDs carry no origin information.
Unsupported methods and compatible legacy descriptor mappings are listed in
[public-api.md](public-api.md). See [tests/reproduction](throughput.md).
