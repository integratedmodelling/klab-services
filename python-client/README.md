# k.LAB Python client

**[Python-client branch review: capabilities, next steps and seven notebook demos](docs/release-preview.md).**

For development planning and CLI review, see the
[contributor guides](../docs/contributor-guides/README.md) and the
[current reconciled planning guide](../docs/PYTHON_CLIENT_PLANNING_GUIDE.md).

Optional [scientific analysis and recorded provenance](docs/scientific-analysis.md)
open native rasters as labeled xarray values plus validity and inspect bounded,
read-only partial neighborhoods without claiming revision-consistent snapshots.

Native observation exports can be streamed to files through existing advertised
exporters, with bounded download memory and atomic publication. See
[streamed export usage, file policy and live acceptance](docs/stream-exports.md).

`klab-python-client` imports as `klab_client`, requires Python 3.11+, and provides
a synchronous API for scripts/notebooks. The verified local workflow authenticates
a signed user, creates a context, runs a model, reads actual scientific values,
reattaches, and explicitly releases test-owned state. The local authority and
datasets are test fixtures; production onboarding and real-provider accuracy
remain deployment-specific.

## Install and check

```sh
python -m pip install /path/to/python-client
# Developers, from python-client/:
python -m pip install '.[dev,local,analysis]'
python -m pytest -q
python -m build
```

PowerShell uses the same commands through a virtual environment:

```powershell
py -3.11 -m venv .venv
& .\.venv\Scripts\python.exe -m pip install ".[dev,local,analysis]"
& .\.venv\Scripts\python.exe -m pytest -q
```

In a notebook, `%pip install /path/to/python-client` installs into the kernel's
environment. No import-time networking, background event loop, NumPy or pandas
is required. Cryptography/psutil are optional local-test tooling dependencies.
The developer commands above include the optional analysis readers so the full
offline suite runs instead of skipping its scientific-reader tests.

## Connect through a supported credential path

Supply already-issued k.LAB network JWTs only to explicitly configured service
origins that trust the issuing hub. Services must load its verification key through
their existing certificate/hub workflow. Obtain/renew credentials using the
deployment's existing authenticated Engine/account flow. A generic OAuth token,
anonymous token, local Python role set or server administration key is not a
scientist credential. Browser `webui_` credentials currently reject scoped
requests and are not supported for this workflow.

`Client.from_env()` reads only documented variables:

| Variable | Meaning |
|---|---|
| `KLAB_RUNTIME_URL`, `KLAB_RUNTIME_TOKEN` | Runtime base URL (include `/runtime` or proxy prefix, omit `/api/v1`) and issued credential |
| `KLAB_REASONER_URL`, `KLAB_REASONER_TOKEN` | Reasoner endpoint and credential |
| `KLAB_RESOURCES_URL/TOKEN`, `KLAB_RESOLVER_URL/TOKEN` | Optional explicit peer endpoints/credentials |
| `KLAB_AGENT_NAME` | Authorized user's provenance name |
| `KLAB_TIMEOUT`, `KLAB_POLL_INTERVAL` | Positive seconds, default 30 and 0.5 |
| `KLAB_CA_BUNDLE` | Optional CA path; TLS verification is otherwise enabled |
| `KLAB_SERVICE_IDS`, `KLAB_RUNTIME_SERVICE_ID` | Existing peer IDs/home runtime ID, or initialize them explicitly below |
| `KLAB_CONTEXT_ID` | Example's optional accessible existing context |

Explicit configuration is also supported with `Client(runtime_url, credential,
reasoner=Endpoint(reasoner_url, reasoner_credential), ...)`. Credentials are
origin-bound, excluded from reprs and redacted from diagnostics; redirects and
environment proxy/credential discovery are disabled.

## Scientific workflow

Typed requests and context options are documented in
[typed observation requests](docs/typed-observe.md); `Context.observe` accepts a
resolved observable or definition text without manually authored Java DTOs.

Configure endpoints, credentials and matching scientific assets first:

```python
from klab_client import Client
from klab_client.experiment import run_elevation

with Client.from_env() as client:
    client.initialize_user_scope()  # explicit peer advertisement, not an authority grant
    observation, metres, millimetres = run_elevation(client)
    print(observation.id, observation.units, metres.values)
```

`examples/elevation.py` is the runnable terrain/reference example. It requires
`earth:Region`, `geography:Elevation`, a compatible model and a covering dataset.
Its 5×4 EPSG:3857 grid checks actual storage cells, geometry, units and m→mm
conversion. A deterministic local fixture is available separately; see
[acceptance and throughput](docs/throughput.md).

For your own inputs, resolve an observable, construct `ObservationImpl`, and use
`context.submit(...)` to obtain a Job. `job.result(timeout=120)` retrieves the
actual server result. `observation.fetch_data([0, 1], curve='D2_YX')` returns
explicit indexed values, raw cell text, geometry, units and source/target semantics.
Numbers use integers/Decimal, missing cells use None, and zero/False remain valid.
Reads are bounded to 1–256 cells per call; this is not a bulk export interface.

## Ownership, jobs and errors

Versioned, secret-free context/observation/job references are described in
[saved handles](docs/saved-handles.md). Save/load are offline; `client.restore_handle`
explicitly verifies configured service identities and current authorized work.

`client.attach_context('session.context')` attaches to authorized existing state;
`context.within(observation_id)` preserves a focus path. Save the job ID and scope
before waiting; `context.job(saved_id).result(...)` resumes it after reconnect.

Client close/context-manager exit closes **local HTTP resources only**. Remote
`context.release()` / `session.release()` are explicit disposal operations. Never
release shared/user-owned state unless you intend that operation. The example
releases only its newly created disposable fixtures after success; failed fixtures
remain available with printed IDs for inspection/manual cleanup.

FINISHED means result delivery, not scientific correctness. Error notifications,
empty outcomes and ABORTED/INTERRUPTED/EMPTY states are handled explicitly.
WaitTimeout retains `.job` and does not stop the server. `cancel()` reports request
acceptance; polling confirms interruption or a completion race. No mutations are
automatically retried after ambiguous network/server failure.

Errors are under `klab_client.errors` (configuration, transport/unknown submission,
authentication/authorization, invalid request, missing asset, protocol/server,
job failure/cancellation/unavailability, timeout and unsupported operation).
Remote `contextualization` is exposed exactly; legacy description getters map
only compatible activities and raise UnsupportedOperationError otherwise.

## Boundaries and further reading

Unsupported: binary Avro worker/job data, dataflow encoding, general chunk/window reads, automatic
query/consumer geometry conversion, contextual-unit/range/currency adapters, full
Java Modeler/API parity and mandatory array integrations. Unsupported methods
raise named errors rather than fake success. Generic assets/graph queries retain
raw DTOs; scientific values are a distinct storage route.

* [Contract matrix](docs/contracts.md) and [public API inventory](docs/public-api.md)
* [Analysis/provenance guide and limits](docs/scientific-analysis.md)
* [Review corrections and current validation](docs/review-corrections.md)
* [Live reference format](docs/live-acceptance.md)
* [Functional workflows and test organization](docs/functional-testing.md)
* [Current export/functional verification and review](docs/exports-verification.md)
* [Buildable GeoTIFF correction on this branch](component-patches/geospatial/README.md)
* [Reproducible local tests and throughput](docs/throughput.md)
* [Separate server/client/tooling review](docs/review.md)
* Resume note: `../notes/python-client/work-log.md` (not package documentation)

Compatibility was revalidated with develop 64754ea2b plus the bounded corrections
in this branch. This is not a package-publication or maintainer-approved roadmap
claim. CI runs offline tests; live tests require an explicit configured stack and
fail, rather than skip-as-success, when prerequisites are missing.

## Engine-like connections

Use `connect(DeploymentProfile.load("deployment.json"), credentials=provider)`
with `HubCertificateProvider` for native ENGINE certificates or
`IssuedTokenProvider` for existing origin-bound credentials. See
[connection-layer.md](docs/connection-layer.md) for the profile schema,
readiness/selection, renewal and local-only lifecycle, and
[examples/connect.py](examples/connect.py) for catalog/handle-export usage.
Acceptance levels and remaining actual-Hub evidence are recorded in
[connection-verification.md](docs/connection-verification.md).
