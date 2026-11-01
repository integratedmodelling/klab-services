# Milestone acceptance, notebook and delivery tooling

This tooling is implemented, not scientifically accepted. All actual services,
scientific references and separately authenticated identities must be provided.
No tool imports projects, applies component patches or restarts shared services.

## PR #102 scope decision (1 November 2026)

At the user's explicit direction, PR #102 is narrowed to **workflow and acceptance
tooling integration**, not completion of the scientific milestone. Merging the
tooling does not certify a scientific run, produce an accepted deliverable, or
establish native exporter compatibility. The original requirements below are
preserved as the future scientific acceptance contract, not silently removed.

Merge criteria for this bounded contribution are the existing offline tests and
coverage gates, lint, packaging, hosted checks, contributor review, and disclosure
of the failed live evidence. No runtime validator, test assertion, coverage gate,
throughput prerequisite or deliverable-readiness requirement is relaxed. No Java,
ontology, component-source or scientific-algorithm change is included.

### Demonstrated limitation and retained evidence

[Geospatial issue #10](https://github.com/integratedmodelling/klab.component.geospatial/issues/10)
contains exact versions, reproduction steps, source/bytecode findings and the
unchanged native TIFF embedded with its SHA-256. With Java `a0a013a188fb752907f2d143a61b86f4a9cb0a0b`,
geospatial build `1.0-20261025.094856-223` and Python `ced45f176`, the third bounded
slice completed two observation Jobs in one context. Observation units were `m`;
the native GeoTIFF band units were absent. The strict unit assertion failed.
The 5 x 4 grid, CRS, transform and all 20 constant values matched independent
expectations, but that is not a passing scientific/export workflow. Object releases
were confirmed; all 13 recorded owned JVM identities were absent after cleanup,
with no private helpers or changes to original service listeners.

The candidate fixture also incorrectly declared `float64`, although the native
exporter specifies TYPE_FLOAT and produced `float32`. That is a separate reference
declaration error to correct from the exporter contract, not an exporter defect or
permission to infer missing units from the requested unit. Earlier attempts stopped
before scientific submissions on two harness declaration errors (exact JDK release
and complete launch classpath); both were diagnosed, corrected and preserved.

### Deferred scientific completion (not satisfied by merging #102)

- Obtain an unchanged compatible exporter artifact with native measurement units,
  or separately authorize/review an upstream component correction. Pin its identity.
- Correct reference precision from the exporter contract and complete independent
  fixture fidelity, including nonuniform orientation, missing cells and valid zero;
  constant data do not prove these properties or real-terrain accuracy.
- Pass the complete two-variation/two-resolution sequential and parallel matrices,
  recorded evidence, partial-failure and strengthened two-user permission checks.
- Restore original results in a fresh process and execute the full scientific
  notebook; neither is claimed complete by the failed slice.
- Only after correctness passes, run bounded throughput and validate the complete
  deliverable with the unchanged fail-closed builder.

Until those gates pass, the scientific milestone and its deliverable remain
incomplete even if this tooling PR is merged. Missing prerequisites must fail;
do not mark them passed, convert them to skips, fabricate metadata, or replay an
ambiguous write. The upstream issue fixes only one prerequisite, not all deferred
work. Further live attempts require a diagnosed prerequisite change and bounded
authorization; no fourth attempt was launched for this scope decision.

## Independent fixture contract

Pass a UTF-8 JSON file to `--milestone-fixture`. Duplicate fields, nonfinite JSON,
credential-like fields/JWTs, files above 8 MiB and unsupported request fields fail.
The exact input bytes are hashed into each run. Schema version 1 requires:

| Field | Required data |
| --- | --- |
| `version` | Integer 1 |
| `model_urn` | Existing candidate model identifier; never forced model selection |
| `expected_node_ids` | Fixture-specified node IDs expected in a recorded successful numerical activity's plan |
| `authority` | `name`, `query`, explicitly selected canonical `code`, independent `concept` and direct `parent_urn` |
| `deployment` | `java_checkout_sha`, `jdk`, `component_sha256`, `worldview_revision`, and `service_builds` mapping all four service names to exact build identities |
| `reference` | `source`, `revision`, `method` (`controlled-fixture` or `independent-provider`) |
| `cases` | Exactly four entries spanning two genuine request variations and two reference resolutions |

Each case has `name`, `variation`, `resolution`, `request`, `context_request`,
and `oracle`. Requests accept only `definition`, optional `geometry`, `scenarios`,
`namespace`, and `project`. A rectangular geometry uses `west`, `east`, `south`,
`north`, `columns`, `rows` (the existing square-cell EPSG:3857 facade).
Contexts are explicitly retained `EXPLICIT_ACTION` work so originals remain
available for restoration. Owner disposal remains a later explicit operation.

An oracle supplies `source_sha256`, `units`, `crs`, `width`, `height`, six-value
north-up `transform`, row-major north-to-south `values`, and `dtype`, with optional
exact decimal `absolute_tolerance` and `relative_tolerance`. Values are decimal
strings/integers or null for missing cells. See the shared workflow document.
No supported live fixture is bundled: unit-test fixture dictionaries are synthetic
validator inputs, not scientific Java results or deployment configuration.

**Declared deployment identities are not runtime attestation.** Without checked
evidence the adapter still writes `deployment_verified=false` and the builder
refuses readiness. An explicitly owned **local** deployment may supply
`--deployment-evidence <private-launch.json>` to the acceptance adapter. This is
not a trust-me flag: `workflows/deployment.py` rechecks the live process birth,
executable and private argument-file bindings; every classpath file/class; exact
JDK release; staged worldview bytes against the clean pinned source revision;
served project files/configuration; installed component bytes; ordered classpath
build identities; trusted loopback URLs and actual advertised service IDs.
Checks run before and after acceptance and must produce identical snapshots.
Private paths/arguments are excluded from the hash-bound public `deployment.json`.
The builder requires that report and its artifact hash, not just a true boolean.

The private launch manifest has version1, kind `owned-local-launch`, the exact
fixture `deployment`, Java `checkout`, `worldview` checkout/staged roots,
`resources_configuration`, pinned `files` (role/path/sha256), and four `services`
(id/url/pid/created/executable/arguments/classpath). It is emitted by the reviewed
isolated launch harness after imports/index readiness. Service-build identifiers
are `classpath-sha256:<digest>` and JDK is the exact release-file JAVA_VERSION.
Only `[local]` installations need psutil. This verifies operator-reviewed local
evidence, not remote/hostile-author cryptographic attestation or scientific accuracy.
No service is imported, restarted or modified by the verifier. Arbitrary declared
identities or a manually flipped boolean must not be used to pass delivery.

## Named functional acceptance

Use a fresh external output directory and JUnit destination, both under existing
parents. Paths are not credentials; certificate contents/tokens are never arguments.
From `python-client`, with its installed SDK and optional `[analysis,notebook,local]`:

```powershell
python tools/run_milestone_acceptance.py --milestone-fixture <verified-fixture.json> --profile <trusted-profile.json> --certificate <private-owner.cert> --other-certificate <private-second-identity.cert> --output-dir <fresh-run-directory> --report <fresh-junit.xml> --allow-owned-work
```

This is explicitly computational and must be authorized for the selected deployment.
It uses the existing `run_functional_acceptance.py` suite dispatch and safety gates.
The full adapter first selects `milestone-compatibility-units` (semantic readiness
and the exact hosted m/mm wire-contract tests) and `milestone-compatibility-compute`
(the original terrestrial-region request), followed by the eight integration cases.
The original test bodies and assertions are reused, not weakened or duplicated.
This adds one owned session/context and one 20-cell observation Job to the full
acceptance workload; no automatic retry. Each future execution needs explicit
authorization for its workload. The latest bounded compatibility recheck passed
all four cases; it is not a passing run of the full twelve-case milestone.

For individual integration cases, select a named suite there: `milestone-discovery`,
`milestone-evidence`, `milestone-science`, `milestone-parallel`, `milestone-failure`,
`milestone-restore`, `milestone-permissions`, `milestone-notebook`. Missing required
inputs fail, never skip a required live gate. Ambient milestone credential paths
are cleared before explicit paths are forwarded.

Discovery checks explicit authority search/selection/documentation/resolve, both
directions of a fixture parent relationship, subsumption/satisfiability and model
inspection. Authority exploration is disclosed as separate from the numerical field.
Scientific cases call the shared matrix workflow. Partial failure deliberately
fails one local worker factory before submission while validating three real
scientific siblings; remote Job-failure/timeout controls are not invented.
Permissions use a distinct authenticated identity with a successful session control
and direct denial of the owner's recorded evidence and exports.

`restore_milestone_results.py` is launched with isolated Python (`-I`); it reacquires
authentication, restores original handles and uses the same science validators.
The parent checks process, candidate, fixture and original-result identities.
Child failure text is not published. No mutation is replayed.

## Actual notebook execution

`examples/notebooks/08_discover_compare_explain.py` follows the existing percent-cell
source convention. The milestone executor reuses the existing parser, runs a kernel
with the current exact Python executable, and exports real `.ipynb` and standalone
HTML outputs. Every invocation computes its own sequential/parallel matrices,
plots its own exports, inspects recorded evidence and restores originals in another
process. A different run ID from the primary acceptance batch is required.

The original seven TEST-state notebook demonstrations retain their existing runner;
they must not accidentally execute notebook 08 under their incompatible setup.
Optional Jupyter/plotting libraries are in `[notebook]`, not core dependencies.
Local kernel/HTML smoke tests are real execution but not scientific notebook acceptance.

## Throughput, only after correctness

```powershell
python tools/run_milestone_throughput.py --acceptance <run/acceptance.json> --milestone-fixture <verified-fixture.json> --large-fixture <verified-larger-grid.json> --profile <trusted-profile.json> --certificate <private-owner.cert> --output <fresh-throughput-directory> --allow-owned-work --allow-throughput
```

This authorizes up to 80 new experiments: five four-case batches for workers 1/4,
on correctness/larger grids. Each actual result passes the shared oracle/evidence/
export/analysis path. Larger grids retain the same named scientific variations,
units, extent and precision, and have more cells in every case. No Java restart is
performed, and Java/cache coldness is unknown. No warmup is silently excluded.

Records include wall-clock completed experiments/minute, success/failure/unknown
counts, setup and authority/discovery time, stage median/nearest-rank p95, grid cells,
exported bytes, cleanup and sampled parent-Python RSS. Remote Java memory is explicitly
unavailable without verified owned process identities. The last measured batch per
configuration is restored in a new process; recovery time is separate and included
in the full journey total. Failed measurements retain attempts and withhold success
rates. An early failed batch stops further batches in that configuration and records
unrun batches separately. Retained remote contexts are disclosed, not called disposed.

## Fail-closed deliverable builder

```powershell
python tools/build_milestone_deliverable.py --acceptance <run/acceptance.json> --throughput <throughput/throughput.json> --output <fresh-package-directory>
```

The builder checks the current source/installed-package/candidate identity, all twelve
passing tests, fixture identity, original restoration handles, executed notebook
cells, separate notebook run, relative artifact paths/hashes, full throughput
configurations and wall-clock rates. Arbitrary state files/certificates are not in
the artifact whitelist. A clean identified candidate and independently verified
deployment are required for `ready-for-review`; maintainer acceptance is never implied.
Failure writes `delivery.json` with `status=blocked` and returns nonzero.

These are structured evidence consistency checks, not a cryptographic trust service
for untrusted reports. They cannot turn manually fabricated evidence into scientific
proof. Do not copy historical charts or alter reported outcomes to satisfy a gate.
