# Native async client: implementation specification and current pass

## Scope and state

Roadmap feature branch: `feature/python-native-async`, based on the normal PR #104
merge `e01908d718fc0beb20192f74cb6dfcd57dcff682` into
`feature/python-experiment-runner`.

**IN PROGRESS — not a completed public AsyncClient or accepted async scientific
workflow.** The first pass adds the private `_async_transport.AsyncTransport`
foundation and shares existing transport wire policies. It is not exported as a
finished user API. Do not merge the feature merely because transport tests pass.

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
- Explicit concurrency bounds and no replay of ambiguous submissions. Cancellation
  while a mutation request is in flight must preserve an unknown-outcome warning.

The ordinary sync client stays synchronous. Events/durable replay, live graph
caching, new chunk protocols, authoring and worker execution are later feature PRs.
Transport streaming alone is not a completed native file-export or async scientific
adapter workflow. Async connection orchestration/credential persistence and saved
handle restoration need explicit scope and acceptance before being advertised.

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

Reproduce the transport tests from `python-client`:

```text
python -m pytest tests/unit/test_async_transport.py tests/integration/test_async_transport_http.py -q
```

Public async scope/job/typed-request APIs, shared Job state-machine factoring,
scientific point readback, user examples, final coverage and real-service acceptance
are not implemented/complete in this transport foundation pass. Keep the PR draft
until its agreed feature scope and evidence satisfy the gates above. Start the
next roadmap feature only after this feature is reviewed and merged, or explicitly
record it blocked and obtain direction on any proposed change of sequence.
