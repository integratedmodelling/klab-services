# Python testing against the IDE's authenticated service environment

## Outcome observed on 17 October 2026

The installed local distribution and the hosted stack are **different deployments**.
Local services authenticated successfully after being restarted from the logged-in
IDE, but local Resources still had no workspaces/adopted worldview and local
Reasoner had accepted generation zero. Do not interpret successful login or HTTP
health as scientific readiness.

The real Hub advertised four HTTPS hosted services. The existing Python
`HubCertificateProvider` and `connect` successfully authenticated and selected all
four, without SDK authentication changes, TEST credentials or relaxed TLS checks.
Hosted Resources advertised workspace `im-main`, adopted worldview `imod`, and
returned **26 MODEL entries** from project `klab.staging.vxii` (entries are not
necessarily unique URNs). Examples include
`staging.vxii.basic.elevation-quantifier` and
`staging.vxii.devtests.elevation-quantifier`. These names are an inventory, not
evidence that either model is suitable for an independently verified elevation
benchmark. In particular, model names alone must not determine data meaning.
Generic WORKSPACE/PROJECT list calls returned empty lists despite the advertised
workspace and populated model inventory; no project-access conclusion is drawn
from those list results alone.

Hosted Reasoner reported accepted generation **1**, bean/logical **VALID**, with
**32 retained semantic diagnostics**. Its opaque worldview ID is not a Git revision.
No deployed binary commit or independently validated scientific dataset was
established. See the [verification record](results/ide-connected-semantic-verification.json).

## Safe, repeatable read-only diagnostic

From `python-client/`, use Python 3.11+ with this branch installed (`pip install
-e ".[dev]"`). Keep your genuine ENGINE certificate private; the default IDE path
on Windows is `$HOME\.klab\klab.cert`. The [example profile](../examples/hosted-deployment.json)
contains only explicitly trusted endpoints, never secrets. Check that these are
the intended destinations before using it; Hub advertisements alone do not grant
trust. Certificates are sent only to their matching HTTPS Hub; issued tokens are
forwarded only to the profile's approved services.

Choose a fresh report directory and run:

```powershell
$reports = "C:\existing-parent\fresh-connected-probe"
New-Item -ItemType Directory -Path $reports -ErrorAction Stop | Out-Null
python tools/run_connected_semantic_probe.py --profile examples/hosted-deployment.json --certificate "$HOME\.klab\klab.cert" --definition "geography:Elevation in m" --expected-urn geography:Elevation --samples 8 --warmup 1 --interval 1 --report "$reports\identity-only.json"
```

This uses `connect(..., initialize=False)`: it does **not** send peer-catalog
updates, create sessions/contexts/observations, import projects, publish anything,
or restart/stop services. Semantic resolution uses the normal Reasoner POST and
may warm server caches. It is application-level read-only, not zero server work.

It performs one warmup and eight sequential semantic requests with one second of
idle pacing between calls. The tool caps measurements at 20 requests and two
warmups, and requires at least 0.5 seconds of idle pacing. These are hard limits
for a modest diagnostic, **not permission for repeated load tests on shared
infrastructure**. Startup/authentication/metadata refresh is outside timing. The
SDK's service-ID binding and the accepted semantic generation/source-manifest
hash are checked across the measurement. An error or revision change invalidates
the measurement; no successful request rate is retained for a failed run.

The observed identity-only run passed eight checks of the expected
`geography:Elevation` identity and numeric artifact type. Median end-to-end
request latency was **130.84 ms**, range **126.48–135.56 ms**. Eight measured
requests took **8.05 s including pacing** (0.994 paced requests/s).
This small warm/cache-reusing measurement is **not server capacity, scientific
computation throughput, data-transfer throughput, unit verification or independent
scientific accuracy**. Every report explicitly sets `scientific_acceptance=false`.

## Separate strict unit gate: currently fails on hosted services

Run the same diagnostic with the independently expected unit:

```powershell
python tools/run_connected_semantic_probe.py --profile examples/hosted-deployment.json --certificate "$HOME\.klab\klab.cert" --definition "geography:Elevation in m" --expected-urn geography:Elevation --expected-unit m --samples 1 --warmup 0 --interval 1 --report "$reports\strict-unit.json"
```

Observed exit **1**, `passed=false`, with:

```text
Requested unit is absent or differs in the resolved observable; do not invent mediation
```

Independent raw-response checks, before Python DTO conversion, sent these bodies
to the hosted Reasoner's `POST /api/v1/resolve/observable`:

```text
geography:Elevation in m
geography:Elevation in mm
```

Both responses had `semantics.urn == "geography:Elevation"`, observable
`urn == "geography:Elevation"`, and **no `unit` field**. Their entire decoded wire
payloads had the same canonical JSON SHA-256 (recorded in the verification JSON).
The Python DTO did not remove the unit. This is a demonstrated deployed-wire
contract gap for the existing unit-aware workflow, not proof of its Java root
cause or evidence that scientific computation failed: no computation was
submitted. A server may use a different documented mediation contract, but one
has not been established here. Do not reconstruct a successful unit contract by
copying the requested string into the response.

The exact offline regressions for the tool are in
[`tests/unit/test_connected_semantic_probe.py`](../tests/unit/test_connected_semantic_probe.py):

- `test_missing_units_or_wrong_semantics_cannot_pass_unit_gate`
- `test_identity_only_diagnostic_keeps_unknown_units_unknown`
- `test_probe_never_initializes_peers_or_reports_science_and_redacts_errors`
- `test_measure_checks_identity_and_units_and_includes_pacing_in_rate`
- `test_operational_flag_is_not_accepted_generation`

The live commands above are explicit opt-in CLI checks, not mocked unit tests.
Offline validation of this Python-only branch passed **1,060 unit/local-integration
tests** (zero failures/errors/skips; one existing Rasterio warning) and the
existing Ruff E9/F63/F7/F82 selection. The same original readiness helper was also
rerun with the real hosted connection; see the
[current compatibility matrix and exact live test IDs](develop-worldview-compatibility-issue.md).

The new read-only connected functional suites can be selected directly:

```powershell
python tools/run_functional_acceptance.py --suite connected-semantic-readiness --suite connected-unit-contract --profile examples/hosted-deployment.json --certificate "$HOME\.klab\klab.cert" --report "$reports\connected.xml"
```

Actual hosted run: **one readiness test passed and two unit tests failed**
(`test_connected_resolved_observable_preserves_requested_unit[m]` and `[mm]`).
These are explicit live failures, not skips or offline regressions. These suites
require profile/certificate paths instead of synthetic `--state-dir` credentials.

## Local worldview investigation: traceable inputs, not a deployed fix

The current Java [staged worldview validation record](https://github.com/integratedmodelling/klab-services/blob/d8e16b277a616b63165b857018722bba658c0007/docs/STAGED_WORLDVIEW_VALIDATION.md)
names IMOD commit `253d1046a1e04b4b45bc67a32b93a65829faa2f5` and a reviewed ODO
core. That corpus uses ODO **0.1.1**, unlike the older inputs in the
[original compatibility issue](develop-worldview-compatibility-issue.md).
It also changes ontology sources, including `land`; changing only the imported
ODO file is not a verified migration.

The public ODO document at immutable commit
`f49f894ad788ba26656941069518c7eb7b904998`, path `releases/0.1.1/odo.owl`, has
SHA-256 `f21d59b13afcb5c5629a2823d04c2a6560b09c374ca4c85f8d55edfce5431b8c`
(80,740 LF bytes). Converting **only line endings to CRLF** produces the Java
record's exact private-core hash
`2e840f49147a07b971146c24111c56f6831ea87347ff86e45588c1233a58c9bc`.
This identifies matching text without disguising different byte hashes. Neither
version was staged into the user's IDE state or relabeled as ODO 0.1.0.

A fresh detached copy of that IMOD revision was captured outside the user's
workspace for inspection. **It was not imported or live-tested.** The corpus also
declares authority providers `klab.authority.taxa` and `klab.authority.pubchem`;
their required versions/availability must be established before any local
deployment experiment. Passing isolated Java corpus checks does not certify a
complete local environment. The documented local Reasoner startup restriction to
local worldview providers is another reason not to assume login imports remote
projects automatically.

## Next scientific-throughput gate

1. Agree on a real test model, exact resolution constraints, tiny geometry and
   reference dataset/output with Ferdinando; do not select by model name alone.
2. Establish preservation of requested units (or the supported replacement
   mediation contract) and record the actual deployed service versions.
3. Choose either an explicitly configured owned local stack, or obtain agreement
   for a **small hosted computation pilot**. That pilot would create/release its
   own session/context and consume compute/data resources; it was not run here.
4. Validate completion, actual returned cells, geometry/units/missingness and
   reconnect/readback before recording computation rates. An independent provider
   reference remains required for scientific accuracy.

Do not run `run_local_acceptance.py`, fixture preparation, or owned-process
restart/ACL suites against this IDE-managed state or shared hosted deployment.
