# Historical draft PR: scientific Python observation workflow

**Current completion evidence:** the complete deterministic scientific workflow,
signed-JWT lifecycle/security tests and reproducible concurrency 1/2/4 throughput
runs now pass on the isolated TEST deployment. Final offline suite: 64 passed;
2 actual local live tests passed explicitly. Read throughput.md and work-log.md
for current results; earlier milestone verification paragraphs below are history.
Production hub onboarding and real terrain/reference validation remain unverified.

## Summary

Replace misleading local scaffold successes with a transport-backed Python client
that lets scientists create or attach to a k.LAB context, submit an observation
through Runtime, inspect/resume/cancel its server job, retrieve its actual result
and read scientifically meaningful native storage cells from scripts/notebooks.

* Preserve `klab-python-client` / `klab_client`, the useful interfaces and local
  scope tracking. Add Client, Session, Context, Job and ScientificData.
* Implement reusable synchronous httpx transport, explicit endpoint credentials,
  scope/origin propagation, TLS/timeouts, redacted errors and unknown-submission
  outcome reporting without unsafe retries.
* Convert checked Java `@CLASS` DTOs explicitly and preserve optional metadata,
  geometry, semantic identity, units and server diagnostics.
* Distinguish job completion from scientific success, requested cancellation from
  observed interruption, wait timeout from server stop, and local connection
  close from explicit remote scope release.
* Add reasoner resolution and resource discovery/retrieval, plus honest explicit
  exceptions for the remaining unsupported worker/dataflow operations.
* Demonstrate the maintained small-grid elevation experiment with actual cell
  retrieval and an independent metres-to-millimetres scientific invariant.

## Contract evidence and compatibility

Baseline inspected: `75bf1f7d29c96ec86789d8e1a0135b0b44d0c8ef`.
The assignment's earlier reference revision was unavailable locally; current
controllers, Java clients, mapper, authentication, job manager and storage DTOs
were checked. [contracts.md](contracts.md) maps operations to routes/DTOs/source
references. [public-api.md](public-api.md) inventories every scaffold method.

Compatibility changes: `*Impl` service construction requires a configured Client;
Runtime.submit and Resolver.resolve return Job handles; assets/graph results
retain full raw transport fields; Geometry.encode rejects arbitrary-value
serialization. Local DTO construction and Modeler tracking do not grant remote
authorization. This is a supported vertical slice, not complete Java API parity.

## Verification

* Python 3.11.9 offline: **53 passed**, two live items deselected by default. Offline
  tests prohibit real HTTP and cover checked envelopes/headers, authentication,
  malformed responses, job progression/failure/cancellation races, timeout/resume,
  ambiguous submissions, local close versus release, and scientific decoding.
* Isolated sdist/wheel builds passed.
* Wheel installed/imported in a second clean Python 3.11 environment outside the
  checkout; import, concrete geometry, local lifecycle and pip check passed.
* `git diff --check` passed. No Java changes.
* Explicit expanded live invocation **failed twice for missing deployment configuration
  and independent reference**. Live acceptance
  is **unverified**, not passed/skipped-as-success. There is no measured live
  elevation result in this handoff. See [verification.md](verification.md).
* Subsequently built and launched the actual local four-service stack plus
  Neo4j and loaded the maintained public imod worldview. Public SDK HTTP calls
  succeeded; scientific session creation failed with HTTP 403. Live probes
  exposed the server string-unit builder stub and an unresolved Region namespace.
  Corrected the example to earth:Region and added a live-captured regression
  fixture/explicit error for HTTP-200 unresolved observables. See local-stack.md.

## Remaining acceptance prerequisite

Configure authorized runtime/reasoner endpoints and issued credentials, agent
name, registered peer service IDs, runtime graph/storage, geography worldview
and an elevation model/resource covering the checked-in 5 × 4 rectangle. Run:

```sh
python -m pytest -o addopts='' -m live -s tests/test_live.py
python examples/elevation.py
```

The test must return real observations, actual storage values and the scientific
invariant. The deeper suite additionally checks every cell in three traversal
orders, closes/reopens local clients and resumes focused jobs without changing
data, checks completed-job cancellation, and compares all cells with a separately
obtained `KLAB_ELEVATION_REFERENCE` file. Evidence/provenance and honest limits
are detailed in [live-acceptance.md](live-acceptance.md).
Newly created ONE_OFF fixtures are released explicitly after success;
attached contexts are never disposed. Interrupted/failed fixtures are retained
with reported IDs for investigation/resume/explicit cleanup.

No server fix, deployment, publication or PR submission is included. Unsupported
capabilities and live prerequisites are documented rather than hidden behind
placeholder results. Maintainer agreement to this exact scope is not asserted.
