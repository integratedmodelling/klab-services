# k.LAB Python client

`klab-python-client` imports as `klab_client` and supports Python **3.11+**.
It connects Python scripts and notebooks to running k.LAB services: create or
attach to a scientific context, submit an observation through Runtime, wait for
its actual job result, and retrieve scientifically interpretable storage cells.
The public API is synchronous and uses `httpx`; no notebook event loop or
mandatory NumPy/pandas/xarray/AMQP dependency is needed.

Compatibility is based on server revision
`75bf1f7d29c96ec86789d8e1a0135b0b44d0c8ef`. **Offline verification is provided;
live acceptance remains unverified without a configured deployment.** See the
[contract matrix](docs/contracts.md), [public-method inventory](docs/public-api.md)
and [verification record](docs/verification.md). This contribution implements the
client specification; it is not an assertion of maintainer-approved roadmap or
complete Java API parity.

## Installation and offline checks

Run inside `python-client/`. Linux:

```sh
python3.11 -m venv .venv
. .venv/bin/activate
python -m pip install '.[dev]'
python -m pytest -q
python -m build
```

Windows PowerShell (no activation-policy changes needed):

```powershell
py -3.11 -m venv .venv
& .\.venv\Scripts\python.exe -m pip install ".[dev]"
& .\.venv\Scripts\python.exe -m pytest -q
& .\.venv\Scripts\python.exe -m build
```

For notebooks, install the package using `%pip install /path/to/python-client`
in the notebook kernel environment, then restart that kernel if needed. Run the
same synchronous Python workflow below in cells. There is no import-time network
access and no hidden credential discovery.

## Configuration and existing credentials

The first release accepts **already-issued authorized credentials**. Obtain them
through the deployment's existing k.LAB authentication workflow:

* A hub-authenticated Java engine receives a k.LAB network token in its
  authentication response/user identity. Use that issued token only with service
  origins trusting the issuing hub, and its authorized scientist username as the
  provenance `agent_name`.
* On deployments exposing the existing web authentication flow, sign in using
  that service's web UI. Its issued service-session credential is the value used
  in subsequent `Authorization` requests (inspect an authenticated request in
  your browser's Network panel). It is service-local: it cannot authorize a
  different reasoner/resources origin. Obtain separately issued credentials for
  those origins or use the supported hub-issued network token accepted by each.
* A Keycloak/OAuth browser access token is not the issued service credential. The
  current server exchanges it through its trusted-hub web authentication flow.
  This package does not invent another login protocol or use privileged server
  keys. Anonymous credentials do not grant scientific write authority.

The runtime must already know/authorize the user's reasoner, resolver, and resource
services. `service_ids` selects those registered service IDs in ScopeRequest; it
cannot grant permission or register unknown peers. Obtain these IDs from each
service's `/public/capabilities` or from your configured Java workflow. Supply
`runtime_service_id` when using a context against another service. It identifies
the context's home runtime, not your user or job.

Explicit configuration (variables below are read from your own secret/config
management, not hard-coded credentials):

```python
from klab_client import Client, Endpoint

client = Client(
    runtime_url, runtime_issued_credential,
    reasoner=Endpoint(reasoner_url, reasoner_issued_credential),
    resources=Endpoint(resources_url, resources_issued_credential),
    agent_name=authorized_username,
    runtime_service_id=runtime_service_id,
    service_ids=registered_service_ids,
    timeout=30, poll_interval=0.5, verify=True,
)
```

Alternatively `Client.from_env()` reads only these documented variables:

| Variable | Meaning |
|---|---|
| `KLAB_RUNTIME_URL` | Required service base URL; include reverse-proxy base path, omit `/api/v1` |
| `KLAB_RUNTIME_TOKEN` | Issued runtime credential; required for authorized workflow |
| `KLAB_REASONER_URL`, `KLAB_REASONER_TOKEN` | Reasoner endpoint and explicitly authorized credential |
| `KLAB_RESOURCES_URL`, `KLAB_RESOURCES_TOKEN` | Optional resource discovery endpoint/credential |
| `KLAB_RESOLVER_URL`, `KLAB_RESOLVER_TOKEN` | Optional direct resolver endpoint/credential |
| `KLAB_AGENT_NAME` | Authorized scientist's provenance name; required for Runtime submission |
| `KLAB_RUNTIME_SERVICE_ID` | Home runtime ID, needed for cross-service context requests |
| `KLAB_SERVICE_IDS` | Comma-separated IDs of the user's registered required services |
| `KLAB_TIMEOUT`, `KLAB_POLL_INTERVAL` | Positive seconds; defaults 30 and 0.5 |
| `KLAB_CA_BUNDLE` | Optional CA file; TLS verification otherwise enabled |
| `KLAB_CONTEXT_ID` | Used by the example/live test to attach to an existing context |

Configure endpoints explicitly; no service discovery URL is automatically trusted
with your credentials. Redirects are not followed. Proxy/environment credential
discovery is disabled. Each endpoint has its own credential, excluded from reprs
and sanitized diagnostics. TLS can be configured through the explicit `verify`
argument (boolean or CA path). The transport is reusable and closeable.

To prompt for a credential without putting it in shell history, Linux:

```sh
export KLAB_RUNTIME_URL='https://your-runtime.example'
export KLAB_REASONER_URL='https://your-reasoner.example'
read -rs -p 'Runtime issued credential: ' KLAB_RUNTIME_TOKEN; echo
export KLAB_RUNTIME_TOKEN
read -rs -p 'Reasoner issued credential: ' KLAB_REASONER_TOKEN; echo
export KLAB_REASONER_TOKEN
read -r -p 'Authorized username: ' KLAB_AGENT_NAME
export KLAB_AGENT_NAME
```

PowerShell:

```powershell
$env:KLAB_RUNTIME_URL = 'https://your-runtime.example'
$env:KLAB_REASONER_URL = 'https://your-reasoner.example'
$env:KLAB_RUNTIME_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Runtime issued credential' -AsSecureString)).Password
$env:KLAB_REASONER_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Reasoner issued credential' -AsSecureString)).Password
$env:KLAB_AGENT_NAME = Read-Host 'Authorized username'
```

Set the deployment's registered service IDs as described above. Endpoint example
hosts must be replaced with your real service URLs. Expired credentials produce
authentication errors; renew them through the same existing deployment workflow.

## Runnable scientific example

Prerequisites: the worldview defines `geography:Region` and `geography:Elevation`;
Runtime has working knowledge graph/storage and connected reasoner/resolver/resource
services; an elevation model/resource covers the checked-in small EPSG:3857 grid.
This is the maintained [storage testcase](../docs/testcases/klab/staging/vxii/storage.kactors)
experiment. It is not a locally fabricated elevation result.

Linux: `python examples/elevation.py`

PowerShell: `& .\.venv\Scripts\python.exe .\examples\elevation.py`

The script creates a new `ONE_OFF` context, submits the 5 × 4 rectangular Region,
submits `geography:Elevation in m` within it, prints resumable job IDs, waits for
completion and reads 20 actual cells. It also reads the same cells in mm and
asserts finite plausible terrain, preserved missingness, at least one real value,
and **mm = m × 1000** within declared numeric tolerance. No constant terrain value
is guessed independently of the selected deployment provider.

Notebook equivalent:

```python
from klab_client import Client
from klab_client.experiment import run_elevation

client = Client.from_env()
try:
    observation, metres, millimetres = run_elevation(client)
    print(observation.id, observation.observable.raw['urn'], observation.units)
    print(metres.values)
finally:
    client.close()
```

For ordinary scripts using your own already-resolved scientific inputs:

```python
from klab_client import ObservationImpl

session = client.create_session(name='python-science')
context = session.create_context(name='Disposable experiment')
observable = client.reasoner.resolve_observable('geography:Elevation in m')
# Supply the geometry/focus appropriate to your experiment, as in elevation.py.
job = context.submit(ObservationImpl(urn='', observable=observable))
print(job.id, job.scope.get_context_id())  # retain before waiting
observation = job.result(timeout=120)
data = observation.fetch_data([0], curve='D2_YX')
```

## Attachment, ownership, and interruption

```python
context = client.attach_context('session.context')  # actual accessible server ID
job = context.job(saved_job_id)  # use the original focused scope when applicable
status = job.status()
result = job.result(timeout=120)
client.close()  # local HTTP resources only
```

For the example, set `KLAB_CONTEXT_ID` to attach. It will submit the experiment
into that context but will **not release** it. A caller can focus with
`context.within(committed_observation_id)`. Scope tokens preserve the full
observation path and optional observer suffix.

`close()`, leaving `with Client(...)`, and restarting a notebook never release
remote scopes. Explicit `context.release()` / `session.release()` invoke the
server's remote close. Closing a context can dispose its digital twin according
to server persistence policy; `ONE_OFF` fixtures are disposable, while other
policies may retain durable state. Closing a session can affect its contexts and
jobs. Release only state you intend to dispose. Ownership flags are descriptive,
not local authorization grants.

The experiment releases its newly created disposable scopes **only after success**.
On timeout, failure, or Ctrl+C it leaves remote state available for inspection;
the printed context/job/focus IDs permit reconnecting and resuming. Manually
inspect/cancel/release these failed fixtures when appropriate. An ambiguous
submission failure may leave a computation without a returned handle; inspect
the context through the deployment's supported UI/Java workflow before retrying.

## Jobs, results, and errors

* `job.status()` returns the server's WAITING/STARTED/CHANGED/FINISHED/ABORTED/
  INTERRUPTED/EMPTY state. FINISHED means a result was delivered, not that an
  application invariant holds. Results retain coverage and error notifications.
* `job.result(timeout=...)` polls and retrieves the actual server result. Error
  notifications or empty results are failures. Partial coverage remains visible
  to callers; the experiment requires full coverage.
* `WaitTimeout` contains `.job`; it stops waiting, does not stop the server, and
  allows another `result(...)` call. Ctrl+C likewise does not discard the handle.
* `job.cancel()` returns cancellation-request acceptance; `.cancellation_requested`
  records that acceptance. Poll afterward: INTERRUPTED confirms the subscriber's
  interruption. A completion race can instead return FINISHED. For coalesced
  computations, interruption of one subscriber is not proof all shared work stopped.
* EMPTY is unavailable/expired, not successful cancellation. Server result caches
  are bounded (400 entries in the inspected JobManager); retain IDs and retrieve
  results promptly. Python's local close does not extend retention.

Errors are exported from `klab_client.errors`: ConfigurationError, TransportError,
SubmissionOutcomeUnknown, AuthenticationError, AuthorizationError,
InvalidRequestError, MissingAssetError, ProtocolError, ServerError, JobFailedError,
JobCancelledError, JobUnavailableError, WaitTimeout, UnsupportedOperationError.
Job exceptions retain the server job ID. No operations are automatically retried:
mutations without a verified idempotency contract report an unknown outcome after
ambiguous transport/5xx failure rather than duplicating submission.

Observation metadata and `.get_value()` are distinct from scientific storage
data. `fetch_data` returns `ScientificData` with explicit offsets, traversal,
slice, raw cell text, decoded values, observable identity, units and geometry.
Numbers use exact Python integers or Decimal; `null` is None, false/zero are valid,
category keys remain semantic URNs, and special numeric values are retained for
the scientist to assess. No dimensions/coordinates are guessed into a DataFrame.
Reads are deliberately small (1–256 explicitly requested cells per call); they
are not a bulk-export replacement. Multi-state temporal data require an explicit
StorageScan.Slice. Native committed observations and explicit simple-unit
mediation are supported; query geometry/consumer bindings, contextual-unit/range/
currency adapters and binary Avro job data are outside this release.

Resource discovery is available through `client.resources.list('RESOURCE')`,
`.resolve(urn, knowledge_class='RESOURCE')` and `.retrieve(urn, 'RESOURCE')`.
Results preserve full wire DTOs in `.raw`. The checked server's listing endpoint
does not offer pagination. Resource URLs returned in DTOs are informational and
are never followed with credentials automatically.

## Live acceptance

Ordinary tests exclude the live marker and require no credentials or network.
To explicitly invoke acceptance after configuring all prerequisites:

```sh
python -m pytest -o addopts='' -m live -s tests/test_live.py
```

```powershell
& .\.venv\Scripts\python.exe -m pytest -o "addopts=" -m live -s .\tests\test_live.py
```

This command **fails**, rather than skips, when configuration, authorization,
assets, actual computation, scientific data, or its invariant is missing. It
creates only the disposable scopes described above unless KLAB_CONTEXT_ID is set.
Fixture provenance is recorded in [tests/fixtures/README.md](tests/fixtures/README.md).

## Scaffold compatibility changes

Existing abstract interfaces, `*Impl` names, local DTOs and Modeler tracking are
retained. Service implementations now require a configured Client; constructing
one without transport no longer fabricates concepts, resource URNs or execution.
`Runtime.submit` and `Resolver.resolve` return Job handles instead of echoed
observations/dataflow URNs. Resource/graph responses retain raw DTO fields;
generic Java asset types are selected using explicit KnowledgeClass names.
Geometry.encode now encodes geometry, not arbitrary Python objects.
Unsupported worker contextualization/dataflow encoding raises a named exception.
See the full [inventory](docs/public-api.md) for every pre-existing public method.
