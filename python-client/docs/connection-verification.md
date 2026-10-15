# Connection layer: contract inventory, assumptions and verification

## Candidate and authority

- New worktree/branch: `klab-python-connection`, `feature/python-client-connection`.
- Actual base: `9c7c371539ff31a05238cfc7355395ca05f21ace`, current upstream
  `feature/python-client`. No develop replacement was performed.
- User inputs: `KLAB_Connection_Layer_CLI_Spec.md` (14 October 2026), the updated
  planning guide persisted as `docs/PYTHON_CLIENT_PLANNING_GUIDE.md`, and Eric's
  contribution review guide. Actual contracts and maintainer authority take precedence.
- Local implementation/testing and normal commits are authorized. No publication,
  PR creation, merge, deployment or contributor assignment is inferred from the spec.

## Contract inventory before implementation

| Area | Actual contract / evidence |
|---|---|
| Certificate file | `KlabCertificateImpl.java:153–189` uses `Properties.load(InputStream)`: ISO-8859-1 bytes, Java escapes/continuations, last duplicate wins. ENGINE required by `Authentication.java:195–199`; level defaults USER. |
| Certificate properties | `KlabCertificate.java:109–122`: username, signature, certificate payload, type/level, partner Hub, email, agreement. Native file validation does not cryptographically validate PGP or parse `klab.validuntil`; expiry is also enforced by the Hub's encrypted license/agreements. |
| ENGINE exchange | `ServicesAPI.HUB` and `Authentication.java:230–247`: POST `/api/v2/engines/auth-cert`, JSON name/key/userType/certificate/level/idAgreement/email, no preexisting Authorization header. This is not TLS mutual auth. |
| Hub implementation | Legacy `integratedmodelling/klab`, develop `ac41ff2074246eb310de015da8b780e2a00f3490`: `EngineLicenseController.processCertificate` responds 200; `EngineAuthResponeFactory` validates/decrypts USER licenses, profile and agreement; TEST follows local-network behavior. Unsupported/invalid paths may produce null rather than a login; Python must fail explicitly. |
| Response | `EngineAuthenticationResponse`, `AuthenticatedIdentityImpl`, `IdentityReference`, `ServiceReference`, `HubReference`: userData identity/token/expiry/groups; Hub id; services with id/identityType/urls/permissions/online/primary. Legacy nodes are not scientist-service substitutes. |
| Identity/expiry | `Authentication.java:275–395` uses identity.id as username, userData.token as issued credential and timezone-less expiry as UTC. Identity-sharing group metadata is not a permission grant. Engine data and dashboard bridge are different contracts. |
| Credentials/audience | Services receive issued JWT in Authorization; expiry metadata is a scheduling hint. Python trusts an explicitly approved HTTPS Hub response and server-side JWT acceptance, not unverified token claims. No new JWT verifier or refresh route. |
| Service observation | GET `/public/capabilities` and `/public/status`. Capabilities use `type` (`RuntimeCapabilitiesImpl.getType`, similarly the other service capabilities); status uses `serviceType` (`ServiceStatusImpl.getServiceType`). Both report `serviceId`; availability/operational flags belong to status. Hub reference id is distinct from capability-observed UUID; profile pinning applies to both providers. Unknown worldview compatibility is not invented. |
| Peer initialization | POST `/notifyUserScope`: UserScopeNotification services{id,type,url,status}, emailAddress, localFederation; boolean acknowledgement. Engine excludes localhost peers from remote advertisements and retains latest per-target pending state. Acknowledgement is not readiness/global membership. |
| Existing scientist API | Client owns typed observe/Jobs/reads/handles/export. Connection owns auth/profile/catalog/selection/readiness and local close; no hidden twin cancellation/release or mutation replay. |
| Errors | Requested login has no anonymous fallback. Malformed/null/expired packages, denied auth, unavailable/ambiguous services and unknown identities have explicit outcomes. Error payloads/certificate/token secrets must not be printed or retained in public evidence. |

Hub implementation/source evidence is available; genuine production Hub/certificate
access has not been supplied. Real Hub onboarding is therefore not assumed accepted.

## Provisional assumption ledger

| Default | Replacement seam / invalidation / migration |
|---|---|
| Native certificate exchange plus issued tokens | CredentialProvider.acquire; new providers preserve grant/trust/identity semantics. Engine attachment only follows a verified interface. |
| Exact approved service/Hub bases; TEST HTTP only on explicitly enabled loopback | Profile/trust adapter. No credential forwarding to advertised new destinations. Changing identity/issuer requires explicit reconnect, not a parameter-only identity migration. |
| Explicit choice, otherwise one observed eligible candidate | Injectable selection policy; output must stay among trusted eligible entries. Runtime endpoint/observed ID stays pinned beneath existing contexts. |
| Synchronous explicit refresh/renewal and bounded wait | Small polling/provider seam, no background loop. Expiry may reacquire before sending a request; auth denial requires explicit renewal. No replay of ambiguous mutations. |
| Timezone-less expiry interpreted UTC like Java | Bootstrap date parser; scheduling only, not signature/permission verification. A future issuer policy change requires explicit compatibility work. |
| Safe retained DTO extensions | Wire decoder removes/redacts secrets; unknown correctness-affecting states reject or become unsupported/unknown. Raw login packages are not profile/cache authority. |
| Latest pending peer snapshot per target | Scoped initializer with explicit bounded attempts, following native Engine notification semantics. No global registration/removal/replay guarantee. |

## Acceptance status — original connection milestone

Implementation commit `440a146117c3adf2fa0c5ec52546d64897cc5714` was locally
validated against the available contract and
controlled-service evidence levels. Actual Hub onboarding is **Not run**.
The consolidated machine-readable record is
[results/connection-verification.json](results/connection-verification.json).
Its complete executable-file hash manifest binds these results to source digest
`e476d83410bf3918242fe02bc0721fec79200006950191d678ac6936d5c1ac81`.
These results and the manifest describe that original implementation, including
its then-current tests. The cleanup follow-up below changes the executable source;
its checks are recorded separately rather than reusing the original live or
installed-package results as acceptance of a changed revision. Publication/hosted
CI has not been performed.

| Check | Status | Exact scope and limits |
|---|---|---|
| Windows Python 3.12.7 offline | Passed | 757 unit/integration tests; includes actual loopback HTTP/subprocess forbidden-origin, redirect and ambiguous-mutation controls under optimization. |
| Installed Linux Python 3.11 / 3.12 / 3.13 | Passed | 757 tests per interpreter, independently installed SDK file hashes matched the source manifest; no editable/source-import substitution. |
| Existing coverage gate | Passed | All four new modules and existing requests/handles/exports: unexcluded 100% lines/branches; changed production-code gate retained. |
| Existing lint and package builds | Passed | `E9,F63,F7,F82`; sdist and wheel on all three installed Linux interpreters and Windows. Broader F401 lint was explored and includes preexisting unrelated unused imports; the established gate is the reported required check. |
| Controlled exchange, compute/export/recovery | Passed | Certificate and token providers each initialized actual peers, computed the known field (20 cells at 123.25 m and 20 at 123250 mm), exported published native GeoJSON, closed locally, and restored/exported the original observation in a fresh process. |
| Access/expiry/readiness | Passed | Owner reconnect, valid collaborator positive control, private-context AuthorizationError, known expired TEST JWT rejected as AuthorizationError; actual ready-service observations. Server denial is not guessed to be a generic expiry error. |
| Existing direct-Client regression | Passed | Native GeoJSON suite plus signed scientific lifecycle/cancellation/failure and four concurrent full-workflow samples using the shared value oracle. |
| Final owned live run | Passed | 6 functional tests plus 1 direct native GeoJSON test, zero skipped, with `PYTHONOPTIMIZE=1`; `connection-live5`. All five owned JVMs stopped, authority stopped, all owned ports closed. |
| Secret-free evidence | Passed | Actual private TEST tokens/enrollment material and JWT patterns absent from SDK logs/JUnit/JSON, profiles and saved handles inspected by the evidence validator. Credential files remain private outside the repository. |
| Actual Hub/production certificate | Not run | No actual ENGINE certificate and supported Hub deployment supplied. Controlled fixture does not decrypt/validate real licenses or production agreements. |
| New Java tests / broad reactor | Not applicable | No Java source changes. Actual compiled Java services exercised through HTTP; five deployed class hashes and explicit server revision recorded. No new Java-suite pass is claimed. |
| Provider-reference accuracy / numerical TIFF | Not run | This feature uses controlled known scientific output and the official published GeoJSON component. Existing TIFF correction/publication and measured-provider gates remain separate. |

## Reproduction and provenance

Commands run from `python-client/` through the Windows environment at
`C:\Users\lumsd\AppData\Local\Temp\opencode\klab-exports-312\Scripts\python.exe`:

```text
python -m pip install -e ".[dev,local]"
python -m pytest tests/unit tests/integration -q --cov=klab_client --cov-branch --cov-report=json
python tools/check_feature_coverage.py coverage.json --base ad2bc949c167e1c2e370973ce4427fe67cb1cc30 --new-module src/klab_client/requests.py --new-module src/klab_client/handles.py --new-module src/klab_client/exports.py --new-module src/klab_client/connection.py --new-module src/klab_client/connection_models.py --new-module src/klab_client/credentials.py --new-module src/klab_client/_bootstrap.py
python -m ruff check src tests examples tools --select E9,F63,F7,F82
python -m build
```

Installed-package matrix driver:

```text
wsl.exe --exec python3 /mnt/c/Users/lumsd/AppData/Local/Temp/opencode/run_connection_matrix.py
```

The three final records are `connection-3.11-e476d83410bf-20261014T174906.json`,
`connection-3.12-e476d83410bf-20261014T174906.json`, and
`connection-3.13-e476d83410bf-20261014T174906.json` under the external temporary
evidence root. Each includes the exact uv/installation/identity/test/coverage/
lint/build argument arrays and exit statuses. An earlier driver invocation hit
the terminal's 300-second timeout partway through its matrix; the final invocation
used a sufficient execution budget and completed every interpreter successfully.

Final live command, with `PYTHONOPTIMIZE=1` in the invocation environment:

```text
python tools/run_export_acceptance.py --server-repo C:\Users\lumsd\Downloads\k1\klab-python-federation-integration --java C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack\jdk\jdk-21.0.12.1+1\bin\java.exe --worldview C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack\assets\imod --component C:\Users\lumsd\AppData\Local\Temp\opencode\pr92-c0267e0-published-geojson\home\.klab\services\runtime\components\plugins\klab.component.geospatial.jar --state-dir C:\Users\lumsd\AppData\Local\Temp\opencode\connection-live5 --functional-suite connection-onboarding --functional-suite connection-access --functional-suite connection-readiness --functional-suite compute-and-analyze
```

- Explicit compiled server: `244883d7b04f1eae2cc22e799575d33a9bdb2a43`, clean
  separate server candidate. This does not merge or finish #89/#90.
- Worldview: `608bef150ced0a109db98a5aad64ba4461beaa54`; manifest and controlled
  model hashes are in the record. The constant-field oracle is not measured terrain.
- Published geospatial component: `1.0-20260926.083046-222`, SHA-256
  `e7d470f69943e93ce48e9669bad6a41b618dfbe0e7bc748076e010583daef644`.
- Controlled Hub contract fixture: this candidate's `tools/fixture_hub.py`,
  loopback-only generated enrollment/RSA material. Legacy actual Hub source
  inspection is pinned above; genuine Hub acceptance remains outstanding.
- External `connection-live5/{run-report.json,functional.xml,exports.xml}` and
  installed-matrix records retain detail. `verify_connection_evidence.py` checked
  source identity, counts, scientific/access properties, private-secret absence
  and closed ports before writing the consolidated record.

## Internal contribution review

**Recommendation: Ready for maintainer review at the documented acceptance
levels.** The complete local diff was inspected against the actual base, including
new providers/profile/catalog code, the transport seam, shared workflows, runners,
tests and documentation. No remaining demonstrated blocking defect was identified
within this bounded contribution. This is an internal review, not independent
reviewer approval or a guarantee of maintainer acceptance.

A subsequent read-only review reproduced an important cleanup defect: an
initialization `ProtocolError` was replaced by an `OSError` from transport closing.
That finding superseded the initial recommendation until the focused correction
below. The correction preserves the existing contract and is covered by explicit
failure-path regressions.

Two live-discovered contract defects were fixed before final validation:

1. Native scope notification status needs Jackson `@CLASS`; an initially reduced
   status payload prevented acknowledgement (`connection-live1`). Preserve the
   sanitized native status DTO; regression tests now require its discriminator.
2. Capabilities expose `type`, whereas status exposes `serviceType`; a strict
   guard initially used the wrong field (`connection-live4`). Source inspection
   corrected the adapter and independent wire fixtures. Unknown capability types
   still reject, and missing/ambiguous selection now includes safe diagnostics.

Other checked failure/lifecycle cases include incomplete refresh invalidating
readiness, expected Runtime pins with certificate discovery, identity/issuer
changes, concurrent expiry reacquisition, old acknowledgements versus newer
pending snapshots, endpoint mutation not expanding credential trust, certificate/
token redaction after renewal, bounded file/metadata reads, atomic profile failure
and cleanup, and no scientific mutation replay or hidden remote disposal.

Contributor coordination: this is Python connection/functional work; the existing
server identity-sharing candidate remains separate and no contributors were
contacted or assigned. GeoTIFF algorithms/source patch were not changed.
Remaining contract evidence is actual-Hub onboarding and the separate deployment/
native identity-sharing server gates; these are not represented as passed.

## Cleanup review follow-up

Reviewed parent: `440a146117c3adf2fa0c5ec52546d64897cc5714`.

`Connection` now uses a shared cleanup helper during failed initialization and
context-manager exit. Cleanup is still attempted. If an operation is already
failing, its original exception object and traceback propagate, with a note such
as `Connection cleanup failed: OSError`. The note excludes the cleanup exception's
message, credential values and other potentially sensitive diagnostics. With no
primary failure, the cleanup exception propagates; explicit `close()` retains
its existing error behavior. Local closing marks the connection closed even if
the transport's close operation raises.

Six additional unit cases in `tests/unit/test_connection.py` cover:

- Malformed peer acknowledgement plus transport-close failure during startup.
- Context exit with `SubmissionOutcomeUnknown`, `ProtocolError`, or
  `KeyboardInterrupt` plus transport-close failure: same primary object, original
  traceback origin, safe note, and no release/cancellation/retry requests.
- Successful context body followed by a failing close.
- Explicit failing `close()` and subsequent rejection of connection operations.

Follow-up validation on Windows Python 3.12.7, using the same interpreter and
commands listed above: **27 focused connection tests passed; 763 full offline
unit/integration tests passed; the existing unexcluded 100% new-module line/branch
and changed-code coverage gates passed; existing lint passed.** Actual services,
installed Linux interpreter matrices and package builds were not rerun for this
focused cleanup change. Their original milestone results remain identified above
at their original source revision. No Java or scientific-validator change was
needed for this correction.

The reproduced connection cleanup finding is addressed at the deterministic
fault-injection evidence level. Actual Hub onboarding and separate server
identity-sharing acceptance remain outstanding as previously recorded.

## Bootstrap cleanup and deterministic-deadline correction

Reviewed parent: `d71a8a02928a4318d51e71b4e5897866de42eef8`.
The next read-only review reproduced two remaining findings: bootstrap resource
closing could replace a primary authentication error and expose a cleanup
message, and the mandatory coverage gate depended on a one-millisecond
wall-clock timeout selecting a particular branch. Both are addressed here.

The bootstrap adapter explicitly owns its HTTP response and client and attempts
both closes independently. Primary authentication, authorization, protocol,
transport and interruption failures retain their semantics, with cleanup notes
containing only resource/error types. Standalone cleanup failures are reported
without the raw cleanup message. HTTPX iterator-EOF closing failures are also
sanitized; the certificate-provider regression checks that a known dummy
signature cannot appear in the rendered exception. JSON object validation occurs
before final resource cleanup, preserving malformed-package errors.

Fourteen new cases in `tests/unit/test_bootstrap_cleanup.py` exercise denied
authentication/authorization, certificate signature privacy, malformed/nonobject
JSON, metadata limits, interrupted reads, interruption identity/traceback,
iterator-EOF closing, standalone cleanup, both-resource failure and client
construction failure. Deadline controls in `test_connection_guards.py` now
advance module-local fake clocks during the exact probe/stream/notification
phase; they do not rely on scheduler sleep duration to obtain coverage. Production
timekeeping and the coverage gate remain intact.

Executable source digest for the `1bb607ea0` bootstrap cleanup milestone:
`b11a45bc8fc1225e0469e833349122d858bdb6c1c4b4fbfdad3de8ede3d8d687`.
Full manifest and consolidated evidence:
[results/bootstrap-cleanup-verification.json](results/bootstrap-cleanup-verification.json).

| Current correction check | Status | Evidence |
|---|---|---|
| Focused bootstrap cleanup/deadline checks | Passed | 15 cases, including the previously reproduced masking/signature-exposure fault. |
| Windows Python 3.12.7 full offline suite | Passed | 777 unit/integration tests; existing unexcluded new-module/changed-code coverage gate passed. |
| Installed Linux Python 3.11 / 3.12 / 3.13 | Passed | 777 tests per interpreter, installed SDK hashes matched; coverage, lint, sdist and wheel gates passed for each. |
| Existing lint | Passed | `E9,F63,F7,F82`. |
| Owned real-service acceptance under optimization | Passed | Seven checks: both providers' compute/export/fresh-process recovery, access/expired-TEST-token rejection, readiness, and existing scientific/direct-export regressions. |
| Cleanup and public-evidence privacy | Passed | Owned JVMs/authority stopped, ports closed, actual TEST secrets/JWT patterns absent from inspected public evidence. |
| Actual Hub onboarding | Not run | No actual ENGINE certificate/deployment supplied; controlled exchange remains explicitly labelled. |

The installed matrix uses the existing commands above. Final external records:
`connection-3.11-b11a45bc8fc1-20261014T192643.json`,
`connection-3.12-b11a45bc8fc1-20261014T192643.json`, and
`connection-3.13-b11a45bc8fc1-20261014T192643.json`.
The live invocation repeats the earlier explicit server/Java/worldview/published
component arguments and the same four functional suite flags, with state directory
`C:\Users\lumsd\AppData\Local\Temp\opencode\connection-bootstrap-correction-live1`
and `PYTHONOPTIMIZE=1`. Server, worldview and published component pins are unchanged
and explicitly recorded in the new evidence. No new Java-suite pass is claimed.

At these evidence levels, the two reproduced review findings are corrected and
the contribution is ready for maintainer review. Actual Hub and separate server
identity-sharing gates remain outstanding; maintainer acceptance is not implied.

## PR #93 JSON diagnostic refinement

Reviewed parent: `1bb607ea08e5acdc86f294afa634cc16dead6cb1`.
The optional review finding is now corrected: only errors raised by the JSON
decoding step are classified as invalid JSON. A `ValueError` or `TypeError` from
HTTPX response closing after a valid body instead follows the sanitized transport
failure path. Existing authentication, protocol and interruption preservation and
independent resource cleanup semantics remain covered.

The bootstrap cleanup tests now include closing `ValueError`/`TypeError` cases
with a valid `{}` body and a malformed-encoding negative control. Current checks
on Windows Python 3.12.7: **17 focused tests and 780 full offline tests passed;
required coverage and lint gates passed.** The same commands and coverage module
set listed above were used. Installed-interpreter matrices, builds and actual
services were not rerun for this narrow diagnostic-only change. The earlier
777-test matrix and seven controlled-service checks remain explicitly tied to
their earlier executable manifest; they are not new execution results for this
follow-up. PR #93 hosted checks will validate the updated branch independently.

## Suggested PR

**Title:** Add Engine-like Python connections with certificate/token providers
and verified functional recovery

**Description:** Scientists can load a versioned secret-free deployment profile,
authenticate using the source-grounded native ENGINE exchange or existing issued
tokens, inspect/select verified services, wait for scoped readiness, and use the
existing typed compute/export/handle APIs. Origin-specific credential resolution
preserves Runtime bindings, explicit renewal and mutation-uncertainty semantics.
Named functional suites cover two-provider compute/known values/native GeoJSON,
local close/fresh-process recovery, valid collaborator denial and expired TEST
credentials. Cleanup corrections preserve primary failures through Connection
and bootstrap resource closing, sanitize cleanup diagnostics, and use deterministic
deadline regression controls. Current validation: 777 offline tests on Windows
and installed Linux 3.11/3.12/3.13, existing coverage/lint/build gates, and seven
owned real-service checks under optimization with cleanup and secret-free evidence.
Exact manifests and earlier milestone results remain separately traceable above.
The subsequent PR #93 diagnostic refinement adds precise JSON-error scoping and
passes 780 offline tests plus coverage/lint on Windows; its earlier matrix/live
evidence is distinguished in the follow-up section above.
Actual Hub
onboarding is not yet exercised; fixture exchange does not certify license or
production-agreement validation. Server/global-discovery/worker and numerical
TIFF delivery remain separate. No publication/PR/merge is performed by this report.
