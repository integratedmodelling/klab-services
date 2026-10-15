# k.LAB connection layer: CLI implementation specification

Prepared 14 October 2026. Companion: `KLAB_Python_Client_Planning_Guide.md`.

## 1. Objective and authorization

Implement an Engine-like connection layer for the scientific Python client, and expand the existing functional testing around it. A scientist should supply a deployment profile and supported credential source, inspect usable services, compute an observation, export its verified result, close Python, and reconnect to the same authorized work in another process.

Proceed with reversible client design decisions instead of waiting for agreement on every preference. Initial authentication assumption: direct Hub/ENGINE-certificate authentication matching the native Java contract, plus the existing issued-token route. This is Eric's implementation assumption, not confirmed maintainer policy. Preserve replacement seams for later authentication providers and selection policies.

Implement and test in a separate feature branch/worktree based on current upstream `feature/python-client`. Preserve unrelated local work. Source inspection checkpoint for this specification: `a11462ddcf7aa0e2643c1f44faad95e8dd9fed36`; develop `3b01ceaa907000ffccafac97444029027c81d30b`. Refresh refs and record the actual base before coding. This document specifies work; it does not claim it has been implemented or tested.

Local implementation, normal commits, documentation and test execution are within scope. Publication, PR creation, merge and deployment require the applicable session authorization; this document alone does not instruct the CLI to publish. Do not contact or assign other contributors.

## 2. Required repository inspection

Read applicable repository instructions before editing. Read the companion planning guide and `KLAB_CLI_Contribution_Review.md` if available. Its inferred preferences do not override actual contracts.

Inspect current versions of:

- `docs/PYTHON_CLIENT_ROADMAP.md`, especially Engine-like connection architecture, Phase 0/1 and decisions.
- `docs/SCOPES.md`, `docs/SERVICE_COORDINATION_AND_DISCOVERY.md`, `docs/SERVICES.md`, `docs/ARCHITECTURE.md`.
- `docs/HUB_IMPROVEMENT.md`: distinguish dashboard credential exchange from native scientist authentication.
- `docs/PYTHON_CLIENT_FEDERATION_COMPATIBILITY.md`, `docs/SCOPE_PROPAGATION_TODO.md` and the chosen server candidate.
- Native `Authentication.java`, `KlabCertificateImpl.java`, `KlabCertificate` constants, `EngineAuthenticationRequest/Response`, `AuthenticatedIdentityImpl`, `ServiceReference`, `HubReference`, Hub route constants.
- `EngineImpl`, `ServiceMonitor`, `ServiceClientCatalog`, `ScopeAdvertisements`, authorization filters and their relevant tests.
- Python `transport.py`, `client.py`, `handles.py`, `requests.py`, `exports.py`, `api/services.py`, public API/contracts docs and all existing feature coverage gates.
- `python-client/docs/functional-testing.md`, `exports-verification.md`, `tools/run_functional_acceptance.py`, owned acceptance runners, shared `tools/workflows/` and `tests/functional/`.

Produce a compact contract inventory before implementation: actual method/path; request fields; certificate encoding; response status/body; credentials/audience; identity/groups/federation; trusted service references; expiry evidence; error states; implemented versus proposed behavior. Trace evidence to file/line or immutable commit.

Do not assume the Hub API is fully implemented inside klab-services. Locate its authoritative implementation/documentation where available, or report the missing source. Never invent endpoints, refresh flows or Engine attachment interfaces to fill gaps.

## 3. Scope

### Required

1. Secret-free versioned deployment profiles and explicit credential references/providers.
2. Existing issued-token connection through the new orchestration layer without breaking direct `Client(...)` usage.
3. Native Hub/ENGINE-certificate provider consuming the actual certificate and authentication contract.
4. Typed bootstrap identity/service references, authorized catalog, explicit selection and deterministic ambiguity errors.
5. Capability/status inspection and readiness diagnostics; bounded explicit refresh/wait.
6. Scoped peer initialization through existing contracts, including refresh of incomplete startup advertisements where supported.
7. Expiry/reacquisition behavior limited to implemented provider capabilities; explicit error distinctions.
8. Replacement seams demonstrated with two concrete providers and injectable selection policy.
9. Named functional connection suites integrated into existing scenario/runner organization.
10. Reproducible example and exact-revision verification record with honest acceptance levels.

### Deferred boundaries

General browser login, authenticated Engine attachment, persistent credential stores, automatic service launches, global discovery, automatic cross-runtime migration/failover, durable message replay, new server authorization or idempotency mechanisms. Provide extension interfaces only where needed by the two implemented providers; avoid speculative plugin frameworks and empty public feature methods.

Do not modify geospatial algorithms or solve the unpublished TIFF correction inside this feature. Prefer the existing verified GeoJSON workflow for the connection acceptance path.

## 4. Architecture and replaceability

Names below are illustrative; adapt to repository conventions without weakening behavior.

| Component | Responsibility | Must not own |
|---|---|---|
| `DeploymentProfile` | Version, hub/configured services, credential references, trusted destinations, selection preferences, budgets | Secrets, authoritative permissions or cached login as permanent authority |
| `CredentialProvider` | Supply credential for approved origin, report known expiry, explicitly reacquire/invalidate where supported | Scientific job submission, arbitrary routing, permission decisions |
| Bootstrap adapter | Authenticate via chosen contract and decode identity/catalog, preserve unfamiliar fields | Invented group grants or readiness |
| `ServiceCatalog` | References with identity, origin, type, capabilities and separately observed health/readiness | Global registry/ownership/migration |
| `SelectionPolicy` | Choose verified candidates or explain missing/ambiguous choices | Credential trust expansion |
| `Connection` | Wire provider/catalog/transport/client, refresh and initialize scoped peers, close local resources | Implicit cancellation or disposal of twins |
| Existing `Client` | Typed observation, job, graph, handle, export workflows | Certificate parsing, Hub-specific UI/login |

Keep wire adapters separate from stable Python domain objects. Retain raw fields, use specific errors for unknown correctness-affecting states, and do not copy JVM inheritance trees. Keep optional scientific readers separate from basic connection imports.

A certificate provider and token provider must support the same existing scientific workflow. Tests should show replacing a provider or selection policy does not require modifications to observe/export/restore code. Authentication changes that alter principal or issuer must still invalidate incompatible session assumptions; do not promise access continuity across different identities.

## 5. Authentication behavior

### 5.1 Issued-token provider

Preserve current explicit endpoints/credentials, origin binding, TLS and redaction. Accept trusted credential sources without putting secrets in profiles, repr, error text, reports, subprocess command lines, or saved handles. Environment/callback/private file integration may follow current project conventions. Never parse an unverified token and treat its claims as permission grants.

### 5.2 Certificate/Hub provider

- Follow actual Java ENGINE certificate validation, properties parsing and request construction. A k.LAB certificate is not generic TLS mutual authentication.
- Handle required fields, Java-properties escaping/continuations/encoding relevant to real certificate files, type/level, validity and agreement according to inspected source. Use independent fixtures for valid and malformed files; do not implement a naive line-split parser.
- Use an explicitly trusted HTTPS Hub origin. A Hub URL found in a certificate must satisfy the selected profile's trust rules; explain mismatches rather than silently send certificate material elsewhere.
- Exchange using the implemented route/DTO/status contract; decode identity, groups/roles/federation and service references without inventing authority.
- Reject malformed, expired, missing-profile or denied authentication with meaningful errors. No silent anonymous fallback in a requested authenticated connection.
- Do not copy Java token-bearing `toString()` behavior. Redact certificate body, signature/key, token, sensitive payloads and credential references where appropriate.
- No automatic redirects with secrets. Preserve certificate/credential privacy in errors and report files.
- Expiry metadata is a scheduling hint, not signature verification. Actual acceptance and authorization remain server-owned. Document trust/verification precisely.
- Reauthentication means repeating a supported exchange with the caller's provider; no made-up refresh endpoint.

### 5.3 Retry and renewal semantics

Separate connection timeout, request timeout, wait timeout, expiry, permission denial and job cancellation. Bounded retry can cover explicitly read-only/idempotent requests and authentication only where its actual semantics permit it. Never replay an ambiguous scientific mutation after token renewal. Preserve existing unknown-outcome behavior and resumable handles.

On identity/issuer change, mark the connection incompatible/stale and require explicit reconnection; never silently swap principals beneath existing context operations. Concurrent callers must not receive torn credential state. Start with explicit/synchronous renewal; avoid a hidden background thread/event loop. Only add coordination needed by the supported API.

## 6. Profile and catalog behavior

Profiles use a serialization version, validated URLs and credential references. Local paths are chosen explicitly. Save atomically where persisted, with overwrite semantics consistent with existing files. Unknown schema versions fail clearly. Old direct-client construction remains supported.

Catalog entries distinguish: advertised; authorized/trusted for use; reachable; available; operational; selected; unsupported/incompatible; stale/unknown. Some states may be represented as separate fields rather than an enum. A bootstrap reference is not proof of readiness or permission to access every asset.

Selection default:

1. Use an explicit compatible service choice when configured.
2. Otherwise choose only one eligible verified candidate of the required type.
3. If none or several remain, produce actionable diagnostics and require an explicit choice.

Compatibility/worldview fields must be used only where actually supplied and meaningful. Unknown compatibility is labelled unknown; do not claim verified equality. Do not switch Runtime underneath a bound context or change its saved identity. Minimal workflows may specify required service types; do not require every possible service for metadata-only use.

Scope advertisement follows existing `notifyUserScope` semantics. Do not advertise localhost peers to a remote service as if they referred to the same machine. Distinguish acknowledgement from dependency readiness. Keep pending latest per-target state so an older acknowledgement cannot discard a newer advertisement. If adding retries, they are bounded and explicit; do not interpret accepted notifications as global membership/removal/replay guarantees.

## 7. Public experience

Provide one readable example along these lines, with final names selected during implementation:

```python
profile = DeploymentProfile.load("deployment.json")
provider = HubCertificateProvider(certificate_path="/private/klab.cert")
with connect(profile, credentials=provider) as connection:
    connection.wait_ready(timeout=30)
    client = connection.client
    # Existing typed observe, job, saved-handle and export APIs remain in use.
```

This is proposed API pseudocode, not a current package interface. It must also work with an issued-token provider. Provide a catalog summary without tokens and a clear failure example. Closing the connection closes local resources, not remote scientific work. Explicit owner cleanup remains explicit.

## 8. Functional-testing expansion

Extend current architecture rather than add a parallel acceptance framework:

- Shared scenarios in `tools/workflows/connection.py` or similarly named focused module.
- Unit tests under `tests/unit/`; local HTTP/subprocess checks under `tests/integration/`.
- Genuine k.LAB workflows under `tests/functional/`, with existing `functional` and `live` markers.
- Register named suites with `run_functional_acceptance.py`; extend owned runners deliberately.
- Run shared scenario functions from wrappers; do not copy workflow code into benchmarks.

Proposed suite names: `connection-onboarding`, `connection-recovery`, `connection-access`, `connection-readiness`. A runner must recognize and dispatch them; do not document commands as current until implemented.

### Acceptance matrix

| Scenario | Required evidence | Level |
|---|---|---|
| Two providers, same workflow | Certificate and token providers supply the same stable connection interface; scientific calls unchanged | Unit/integration plus functional where available |
| Native certificate contract | Actual property/request/response fixtures, malformed/expired/type errors, trusted-origin enforcement | Offline contract and local HTTP |
| Real Hub onboarding | Real valid ENGINE certificate accepted by actual supported Hub; identity/catalog decoded; genuine services accept resulting credentials | Actual deployment acceptance; report unavailable if not run |
| Controlled exchange to real services | Clearly labelled controlled Hub contract fixture supplies valid test-issued credentials, then real Reasoner/Resources/Resolver/Runtime workflow executes | Controlled functional evidence, not genuine Hub validation |
| Connect → compute → export | New layer selects usable peers, initializes scope, executes typed controlled observation, checks independently known output and native GeoJSON identity/geometry | Real services, official published GeoJSON component |
| Fresh-process recovery | Save original IDs, close connection, new process reconnects and restores original work; no resubmission/recreation/disposal | Real services + subprocess |
| Access and expiry | Expired credentials distinct where observable; valid unauthorized collaborator denied; authorized positive controls; no guessed classification from ambiguous responses | Controlled auth integration and actual service access checks |
| Renewal | Provider reacquires supported credentials; read operations recover; mutation outcome is not silently replayed; principal changes require explicit reconnection | Integration; live if environment permits |
| Readiness changes | Known not-ready service becomes usable; refresh catalog/advertisement; unavailable Reasoner produces actionable error | Local controlled HTTP; optional safely owned real-service case |
| Catalog ambiguity/mixed capabilities | Explicit selection wins; multiple eligible candidates fail; unknown/changed capability produces precise diagnostic | Unit/integration |
| Untrusted advertised origin | Sensitive credentials/certificate never arrive at instrumented forbidden endpoint; redirects rejected | Local HTTP/integration |
| Resource cleanup | Streams/transports closed on error; independently attempt owned context/session release; preserve primary failure and report cleanup failures | Integration/functional |
| No hidden disposal | Closing failed/successful connection leaves caller-owned remote work available | Real services |
| Secret-free evidence | Tokens/certificate secrets absent from repr/logs/JUnit/JSON/profile/handle reports, including failure paths | Offline/integration |

A controlled Hub fixture should match inspected request/response semantics and disclose its limitations. Use valid test-scoped credentials for real services rather than pretending arbitrary mock tokens establish authentication. Do not modify production Hub configuration or fabricate production certificate authority. If actual Hub access is missing, implement the provider, run contract/controlled checks, and report real Hub acceptance as not run. Do not certify certificate login end to end until that gate is exercised.

### Integration with existing functional suites

Make shared scenario construction accept a connection factory/profile/provider so existing workflows can run via the new layer while retaining direct-client regression coverage. Do not force every existing fixture into a Hub-dependent path. At least the verified compute/GeoJSON/fresh-process scenarios must exercise the new connection, not merely construct it and then bypass it using manually configured clients.

The existing runner currently requires `scientist.json` for owned TEST suites. Extend its state schema only as needed: add secret-free profile and privately held certificate/credential references, retain compatibility, and report missing requested configuration as failure. Do not turn failures into skips. Live suites remain excluded from ordinary offline CI by existing markers; explicitly requested unconfigured suites must fail with instructions.

Readiness disruption/restart tests must touch only owned services, or use controlled local HTTP fixtures. Never stop shared/production services to satisfy acceptance.

Scientific validation uses explicit checks active under `PYTHONOPTIMIZE=1`, independent geometry/value references, and positive/negative controls. Numerical TIFF acceptance requires the separately corrected component; its absence must not block honest GeoJSON connection acceptance or be hidden as a successful raster test.

## 9. Execution milestones and review points

### A. Contract inventory and architecture

Record actual Hub/certificate behavior, implementation boundaries, public API proposal, trust rules and assumption ledger. Review internally against source. Proceed on the documented certificate default; no mandatory external approval pause is introduced by this spec.

### B. Providers, profiles and transport integration

Implement the token provider first to preserve behavior, then native certificate provider, normalized bootstrap and origin-specific credential integration. Add meaningful parsing/error/secret/redirection tests. Demonstrate provider substitution.

### C. Catalog, selection and scoped connection

Implement explicit catalog/refresh/readiness/selection and scoped peer setup. Test partial startup, ambiguity and trust. Reuse existing Client and preserve direct construction. No scientific domain method should import a Hub-specific provider.

### D. Functional workflow integration

Add named suites and shared scenarios, genuine service compute/export/recovery, access-negative controls, controlled Hub contract fixture, and actual Hub acceptance recipe. Expand runner/docs without duplicate scientific validators. Record unavailable real Hub validation honestly rather than halt unrelated implementation.

### E. Full contribution review and validation

Inspect the complete diff against the current base, not just last edits. Run the applicable full offline suite/coverage gates, installed-package supported Python checks and package build; relevant local HTTP/subprocess and selected real-service suites. Run affected Java checks only if Java changes or cross-service behavior warrants them; distinguish Maven packaging with skipped tests from test evidence.

Review scientific meaning, actual server contracts, failure/lifecycle behavior, contributor overlap and compatibility. Fix demonstrated defects; do not add endless redundant test rounds after the relevant checks pass. Prepare final report and reviewable diff. Publication status remains explicit.

## 10. Required deliverables

1. Implemented connection layer and certificate/token providers.
2. Backward-compatible public API, profile schema and example.
3. Unit/local-HTTP/subprocess regressions and named real-service connection suites.
4. Updated functional-testing, public API, contract and usage documentation.
5. One consolidated verification record: tested commits, server/Hub/component provenance, commands, results, exact acceptance level, unavailable environments, remaining dependencies.
6. Assumption ledger describing replacement seams and costly contracts.
7. Proposed PR title/description around delivered behavior; no claim of final maintainer agreement or global platform completion.

## 11. Definition of done

- Users can connect through both supported provider implementations to the stable connection interface; certificate contract is source-grounded and tested at its available evidence level.
- Profile/catalog/selection/readiness/scoped initialization work without manual Java DTO construction.
- Existing configured Client, typed observation, handle, export and lifecycle behavior remain intact.
- Genuine service compute/export/fresh-process restoration is exercised via the new connection.
- Trust, expiry, denied access, ambiguity, unknown capabilities and mutation uncertainty fail precisely without secret leakage or duplicate work.
- All applicable implemented checks pass; actual-Hub acceptance is either demonstrated or explicitly outstanding, never substituted with mock success.
- Replacement of authentication provider/selection policy is demonstrably contained; identity and scientific guarantees remain fixed to actual contracts.
- No pending server/exporter/global-discovery work is silently labelled complete.

## 12. Ready-to-use CLI instruction

Read this specification and `KLAB_Python_Client_Planning_Guide.md`, then implement the connection layer and functional-testing expansion in an isolated branch based on current upstream `feature/python-client`. Follow repository instructions, inspect actual native authentication and service contracts, and preserve existing work. Proceed using the documented certificate/Hub default plus issued-token provider, with replaceable policy seams. Do not invent server guarantees. Complete applicable tests and report exact evidence and remaining real-Hub/platform dependencies. Do not publish, merge, or deploy unless separately authorized in this session.
