# Native async client: implementation specification and current pass

## Scope and state

Roadmap feature branch: `feature/python-native-async`, based on the normal PR #104
merge `e01908d718fc0beb20192f74cb6dfcd57dcff682` into
`feature/python-experiment-runner`.

**IN PROGRESS — public workflow implemented, real-service acceptance pending.**
`AsyncClient`, `AsyncSession`, `AsyncContext` and `AsyncJob` are exported from
`klab_client`. They support the bounded observation workflow described below;
transport tests alone are not sufficient to merge the feature.

The preceding PaL contribution was merged as pinned real-input prerequisites,
not a completed Java-backed Radiation experiment or published-study reproduction.

## Intended independently releasable feature

Native asynchronous observation workflows over existing endpoints:

- Explicit issued credentials and configured endpoints initially; synchronous
  Connection/Hub providers must not run inside the event loop by accident.
- `AsyncClient`, asynchronous scope create/attach/explicit release, typed observe,
  Reasoner resolution, real Job polling/result/cancel and sparse scientific reads.
- Shared DTO validation, typed request encoding, origin/runtime binding, scientific
  decoding, statuses and error meanings with the existing synchronous API.
- Native HTTPX async I/O and async iteration of unread response streams. No
  worker-thread facade, hidden loop or `asyncio.run` inside a library method.
- Local async context exit closes transport only. Cancelling a local await does
  not cancel Java work, dispose a twin or grant disposal authority.
- Explicit HTTP concurrency bounds (default eight, configurable positive integer)
  and no replay of ambiguous submissions. Cancellation
  while a mutation request is in flight must preserve an unknown-outcome warning.

The ordinary sync client stays synchronous. Events/durable replay, live graph
caching, new chunk protocols, authoring and worker execution are later feature PRs.
Transport streaming alone is not a completed native file-export or async scientific
adapter workflow. Async connection orchestration/credential persistence and saved
handle restoration are outside this first async observation release; their methods
fail explicitly where present. Use a separate synchronous Client for supported
sync export/restore operations; do not pass AsyncClient to synchronous inspection,
provenance or ExperimentRunner helpers. No synchronous network callback is invoked
implicitly on the event-loop thread. Existing offline Handle serialization can
record a known AsyncJob/AsyncContext after service identity has been established.

## Public use

```python
from klab_client import AsyncClient, ObservationRequest

async def observe_existing_context(context_id, definition):
    # Explicit configured origins and issued credentials from documented env vars.
    async with AsyncClient.from_env() as client:
        await client.runtime.capabilities()
        context = await client.attach_context(context_id)
        job = await context.observe(ObservationRequest(definition))
        result = await job.result(timeout=120)
        return result.id  # Async exit closes local HTTP only, not the user's twin.
```

In a notebook, `await observe_existing_context(...)` directly. A script can use
`asyncio.run` at its entry point, not inside a library method or nested notebook
loop. `examples/async_observe.py` requires explicit authorization before submitting
an observation into an existing permitted context.

`context.job(id)` and `context.within(observation)` are local operations, not
coroutines. Await `create_session`, `create_context`, `attach_context`, `observe`,
`submit`, `status`, `result`/`wait`, `cancel`, `release` and `aclose`. A bound result
uses the same ObservationImpl DTO; `await result.fetch_data(...)` delegates to its
async context. Point reads retain the 1–256-offset contract, explicit curves/time
slices and checked scalar decoding. They are not a bulk/lazy array implementation.

Use one client per event loop. The transport bounds total in-flight requests and
open streams per client, including permit waiting in the request's total deadline.
Cancelling an await while queued sends nothing and returns its permit correctly.
Callers own tasks created with `asyncio.create_task`/`gather`; cancelling one result
wait does not cancel a sibling or issue a server cancellation. Explicit `job.cancel`
reports acceptance only, while `job.status` distinguishes the resulting state.

## Transport decisions in this pass

- Endpoint validation and credential redaction reuse existing policies. Redirects
  and environment proxy trust remain disabled, TLS verification remains enabled
  by default. No new origin is trusted because it appears in a response.
- JSON/text responses use the same decoder and error mapping as sync requests.
  Optional byte budgets require identity encoding and are enforced during reads.
- Async total deadlines include async credential acquisition and response chunks;
  HTTPX request timeout remains an additional inactivity/connect boundary.
- An expired deadline before sending raises a transport error without a request.
  Transport failure or deadline expiry after a potentially mutating send reports
  `SubmissionOutcomeUnknown`; no automatic retries occur.
- Native task cancellation propagates `asyncio.CancelledError`. After an in-flight
  ambiguous request it carries a note requiring remote inspection before retry.
  Cancellation during Job waiting will retain its already-known Job handle.
- Stream consumers own the `async with` lifetime and bounded iteration. Binary
  failure responses are not buffered or echoed. Raw streaming is private for now;
  response representation validation/atomic file publication belong to their
  eventual higher-level implementation.
- Async transport accepts issued endpoint credentials or native async resolver
  callbacks. A synchronous resolver is rejected before invocation rather than
  possibly performing blocking authentication inside a notebook event loop.
- Async scope/Job/service constructors reject a synchronous client before any
  operation can accidentally block the event loop or send a mutation. Rotated
  callback credentials use the same validation as Endpoint credentials and join
  the redaction set before a response or error body can be interpreted.

## Acceptance before the feature merges

1. Existing synchronous regression, coverage, lint and packaging gates remain green.
2. Exact-contract sync/async parity for DTO encoding, scope identity, errors and
   decoded scientific values (including valid zero/false, missing and precision).
3. Async loop remains responsive while two independent HTTP requests overlap;
   bounded concurrency and cancellation/timeout isolation are demonstrated.
4. Request/stream bodies and local clients close on success, failure, deadline,
   cancellation and early consumer exit. No leaked background task owns requests.
5. Mutation ambiguity tests prove no resubmission. HTTP cancellation is not called
   confirmed server cancellation. Job expiry, interruption, failure and unknown
   statuses remain distinct; saved work is not recreated as a recovery shortcut.
6. A real HTTP integration test validates protocol use independently of mocked
   method calls. This is not a real k.LAB scientific computation.
7. A bounded authorized k.LAB async observation/readback/cancellation workflow uses
   actual compatible resources and verified cleanup. Missing fixture/backend
   evidence blocks that claim; mocks cannot substitute for it.
8. New/changed-code coverage, wheel/core-only checks, docs/example, separate
   read-only contribution review and normal hosted checks pass at the final head.

## Current remaining work

### Verified transport-foundation pass

- **Passed:** 60 focused async transport/actual loopback-HTTP cases, including
  overlapping requests without blocking the event loop, scoped credential headers,
  redirect refusal, HTTP error parity/redaction, byte limits, immediately-ready
  awaitables at deadline boundaries, cancellation/stream cleanup and primary-error
  preservation. The loopback server is test-owned and closed by the test.
- **Passed:** 1768 offline unit/integration tests, one existing Rasterio warning,
  57.33s on Python 3.12.7 with explicit current `src;tools` imports.
- **Passed:** existing coverage gate and the new private async transport's 100%
  unexcluded line/branch gate, lint, wheel and sdist build. Hosted checks have not
  been substituted for local results or vice versa.
- **Not run:** real k.LAB async observation/scientific readback, because the public
  async workflow is not implemented in this pass. No scientific acceptance claimed.

### Public-workflow follow-up (supersedes the first-pass implementation state)

The public observation workflow is now implemented. Focused parity/security tests
cover native scopes/jobs, known-Job resume, sibling wait isolation, synchronous
client rejection, rotated credential redaction and exact sync/async typed-request
wire equality. A deterministic zero-timeout case covers the already-expired wait
without relying on scheduler speed; earlier CI exposed that timing-dependent
coverage gap and the failed CI record is preserved.

The first isolated real-Java attempt passed two-user/service readiness but failed
before creating any scientific context/Job: its external allowlist omitted the
test's exact read-only MODEL retrieval prerequisite. All thirteen recorded owned
JVM identities were absent after cleanup. That attempt remains FAIL, not a scientific
pass. A separately bounded fresh attempt is prepared with precisely that one
prerequisite read added; no extra mutation budget or original fixture change.

The second attempt reached one private owned context but still submitted zero
scientific Jobs: the functional test requested `geography:Region`, which does not
resolve in the pinned worldview. The strict Reasoner check correctly rejected it.
Both owned scope releases were confirmed and all thirteen recorded JVMs were
absent after cleanup. The test is corrected to the fixture's established
`earth:Terrestrial earth:Region`, explicit private persistence and `python.fixture`
namespace ranking. No ontology edit, guessed semantic alias or relaxed validator
was used. This failure also remains FAIL in the record.

Reproduce the transport tests from `python-client`:

```text
python -m pytest tests/unit/test_async_transport.py tests/integration/test_async_transport_http.py -q
```

Public async scope/job/typed-request APIs, shared Job state decoding/validation,
scientific point readback and an example are implemented in the follow-up pass.
Final regression/coverage and real-service acceptance are still pending. Keep the PR draft
until its agreed feature scope and evidence satisfy the gates above. Start the
next roadmap feature only after this feature is reviewed and merged, or explicitly
record it blocked and obtain direction on any proposed change of sequence.
