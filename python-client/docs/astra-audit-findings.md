# Audit findings — current working tree

**Historical audit snapshot:** subsequent remediation and real integration results
are recorded in [work-log.md](work-log.md) and [throughput.md](throughput.md).
Do not treat every finding below as still open. Browser scoped access remains an
explicit unsupported boundary; the verified scientific credential path is signed
network JWT. Geometry, metadata/binding, streamed wait checks, grid validation,
server units/null outcomes and private context authorization have been corrected
and tested subsequently.

Date: 5 October 2026. Reviewed checkpoint `490af42d4` plus the uncommitted
client/example/launcher/documentation changes identified in astra-audit-handoff.md.
This is a source/targeted-reproduction review, not a fresh full-stack acceptance
run or exhaustive security audit. Production code was not changed by this audit.

## Verdict

**Not ready to certify the scientist workflow or throughput.** The handoff correctly
separates previous infrastructure startup from scientific completion, but additional
contract and metadata defects exist beyond the recorded missing identity/assets
and server unit stub. The passing offline suite does not cover these defects.

## Findings

### 1. P1 — documented browser credentials cannot authorize scoped operations

Python README.md:58–63 presents service-local browser-issued credentials as an
authentication option. Session.create_context sends klab-scope
(src/klab_client/client.py:152–156), as do observation submission and job/data
requests. However:

* `klab.core.services/.../application/security/HubWebAuthentication.java:189–201`
  returns null whenever a klab-scope header is supplied, even for a valid session.
* `.../TokenAuthorizationFilter.java:68–75` turns this into HTTP 401 for web-session
  credentials.

Therefore this credential path cannot complete the advertised scoped workflow
at the inspected revision. Acquiring fresh browser credentials alone does not
solve it. This is a source-confirmed contract conflict; the audit did not possess
a legitimate browser session to reproduce it live.

**Remediation:** restrict the documented scientific workflow to a verified
credential/scope path, or implement and independently test authorized child-scope
selection for browser sessions at the server. Do not simply remove the guard:
it explicitly prevents reuse of privileged IDE scopes.

### 2. P1 — Geometry.encode returns a hash as if it were serialized geometry

`src/klab_client/api/runtime.py:70–78` returns raw["key"] for non-scalar geometry.
The server's `klab.core.api/.../geometry/impl/GeometryImpl.java:763–768` defines
key as `Utils.Strings.hash(encode())`. It is not a parseable geometry encoding.

The public inventory also incorrectly describes the supplied server key as an
encoding option. A caller passing encode() into a geometry-string contract gets
a hash instead of spatial/temporal support. The main example uses to_wire(), so
its request path does not exercise this defect.

**Remediation:** implement a checked encoding, or explicitly reject non-scalar
encoding when only a key is available. Add round-trip compatibility tests against
Java Geometry.create, including scalar/universal/empty cases.

### 3. P2 — mediated data carries contradictory semantic identity and units

`src/klab_client/client.py:224–232` sends target semantics to the server and uses
the requested unit in ScientificData, but retains the source observation's URN
as ScientificData.observable. An m-to-mm read can therefore report:

```
observable = geography:Elevation in m
units = mm
```

Reproduced with MockTransport through ObservationImpl.fetch_data. This is not a
live unit-conversion result; it demonstrates the client metadata construction
independently of whether the server computes the right numeric conversion.

**Remediation:** represent source and target semantics explicitly or use the
verified target identity for returned values, preserving the source identity in
a distinct field. Check identity, dimensions, units and missingness together.

### 4. P2 — wait timeout is not an overall deadline

`src/klab_client/client.py:98–124` checks the remaining budget before requests but
returns a decoded result without checking the elapsed time after retrieval.
`transport.py:73–76` passes the remaining value as an httpx timeout; httpx's read
timeout is an inactivity bound, not the total duration of receiving a response.

Reproduced using a real loopback ThreadingHTTPServer: the status endpoint returned
FINISHED, while the result endpoint sent small chunks every 15 ms. With
job.result(timeout=0.1), the call returned success after approximately **0.218 s**.
Each chunk arrived before the read timeout, so no TransportError was raised.
The synthetic delayed MockTransport reproduction showed the same late-success
behavior, but the loopback reproduction establishes that it is not only a mock
artifact. This is a transport test, not a k.LAB scientific run.

**Remediation:** define/enforce an end-to-end wait deadline across response reads
and decoding, or document the weaker semantics accurately. At minimum, late
result retrieval must not bypass expiry silently. Test slow/trickled responses
and verify the handle remains usable after timeout.

### 5. P2 — acceptance does not compare actual georeferencing to the reference

`tests/test_live.py:63–71` asserts only spatial shape [5,4]. It records returned
proj/bbox/shape/sgrid/gridurn without validating them. `tests/live_checks.py:48–56`
validates the reference file's own CRS/bounds, while verify_reference at lines
76–93 compares only numeric values and missingness.

A shifted or differently projected 5x4 result is not rejected on geometry, and
can pass if its cell values happen to match (for example a uniform field).
Traversal consistency proves index-order agreement, not geographical location.
Reference dataset/provenance fields are also recorded assertions rather than
proof of the runtime-selected source, as the existing documentation acknowledges.

**Remediation:** validate returned spatial support against the expected CRS,
bounds/resolution and orientation, using a supported geometry representation.
Add deliberately wrong-location/wrong-CRS equal-value cases to the checker tests.
Audit model/provider provenance when interpreting agreement with a reference.

### 6. P2 — direct Context.fetch_data accepts a foreign observation binding

`src/klab_client/client.py:195–232` uses the supplied observation's numeric ID and
metadata but sends the receiving Context's scope, without checking the observation
binding. In contrast, Context.within checks context identity at lines 179–180.

Reproduced: an observation bound to a.c was supplied to b.c.fetch_data; the
request used klab-scope b.c and returned values labelled with the a.c observation's
semantics. `klab.core.services/.../runtime/storage/StorageReads.java:168–181`
looks the ID up in the supplied server context. This is not evidence of a server
authorization bypass: a real server may reject the foreign ID. It is a client
context/metadata integrity defect and produces an avoidable wrong-context request.

**Remediation:** reject mismatched context/runtime bindings before making a
request. Verify behavior for two runtimes as well as two context IDs. Prefer
Observation.fetch_data for bound observations and make low-level context reads
explicit about their source identity.

## Existing blockers confirmed at source level

* `SemanticsBuilder.withUnit(String)` at server lines 291–293 is a no-op. A
  correct fix must also audit the URN construction and Unit serialization, not
  just assign a string field.
* `JobManager.status` at lines 84–94 returns a JobStatus without setting status
  when both stored result and failure are null. Python correctly rejects null.
* No new evidence establishes authorized context creation, observation execution,
  scientific values, or throughput. Prior logs/captures in the handoff remain
  historical evidence; the full local stack was not relaunched in this audit.

## Checks run

From python-client/:

```powershell
& "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311\Scripts\python.exe" -m pytest -q
```

Outcome: **53 passed, 2 deselected**. The deselections are live tests, not passes.

Targeted audit reproductions:

```powershell
& "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311\Scripts\python.exe" "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-audit-repro.py"
```

Outcome: reproduced cross-context request, mediated-identity mismatch and late
successful result retrieval. The script uses explicit synthetic fixtures and a
temporary loopback HTTP server, shuts that server down, and stores no credentials.
It is a host-local audit artifact, not committed product/test code.

## Remediation order and acceptance gates

1. Correct credential-path documentation; establish and test a legitimate
   authenticated user and peer scopes. Browser-session scope support is a separate
   server design issue, not just missing credentials.
2. Fix geometry encoding and scientific binding/metadata integrity; add targeted
   regression tests that fail on the audited implementation.
3. Fix the essential server unit behavior and null-job outcome reporting in
   separate, reviewable changes with relevant Java tests.
4. Provision one reproducible executable model/data fixture with known result,
   then pass complete submission/completion/storage readback, actual geometry and
   independent reference checks under normal authorized credentials.
5. Add deterministic active-cancel/failure/coalescing/isolation fixtures. Enforce
   or accurately bound wait deadlines under realistic network behavior.
6. Only then benchmark fresh and cached/coalesced execution separately at increasing
   concurrency, measuring completed computations/s, p50/p95/p99 latency, failures,
   retrieval bandwidth and CPU/memory/IO over a sustained interval. The current
   sequential point-read interface is not a demonstrated bulk throughput path.

Do not certify release readiness from test count, UP/operational flags, privileged
diagnostic requests or unit-conversion self-consistency alone.
