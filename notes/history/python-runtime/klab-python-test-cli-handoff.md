# k.LAB local setup and Java-to-Python test — CLI handoff

Date: 2026-09-24
Repository: https://github.com/integratedmodelling/klab-services
Inspected baseline: dcb5c609a2314b8fc5474d40109d19e3c5c1be76

## Instruction to the CLI agent

Carry out this plan: inspect the local environment, establish a reproducible storage-test baseline, then implement and run a small Java-to-Python subprocess integration test. Work autonomously on reversible setup and implementation. Report actual commands, results, and blockers. Do not stop after proposing a plan.

Read applicable repository instructions before editing. Preserve existing work. Do not reset, overwrite, or switch a dirty checkout. Reuse an appropriate checkout or create an isolated worktree/clone. Do not push, open a PR, or change upstream CI as part of this task.

This handoff specifies intended work; none of its acceptance criteria should be treated as already passed. The test names and configuration below were inspected at the stated baseline, but the tests were not executed in the originating conversation.

## Goal and scope

The eventual contribution is a Python scientific computation adapter for k.LAB. This task establishes its local development loop:

1. Run existing Java storage tests.
2. Test a real Python function independently.
3. Invoke that Python worker from a JUnit test using a real subprocess.
4. Verify success, diagnostic handling, malformed output, and timeout cleanup.
5. Document one-command reruns and the next k.LAB integration boundary.

The subprocess test is a prototype transport boundary, not a complete k.LAB contextualizer. Do not claim a Python result has become an observation or been committed to a digital twin.

Not in scope: starting the four-service stack, worldview provisioning, production authentication, CESM ingestion, xarray/NetCDF, Spark, HPC, HTTP workers, remote storage providers, or a generalized SDK. Do not modify core storage behavior to accommodate this prototype.

## Phase 1 — inspect and establish environment

Assume Windows/PowerShell only until the actual host is inspected. Adapt to Linux/macOS if appropriate. Record OS, repository path, branch, SHA, Java, Maven, and Python versions.

Required:
- Git.
- JDK 21. Both java and javac must be available.
- Repository Maven wrapper.
- Python 3.11 or 3.12.
- Optional IntelliJ for debugging; CLI execution must work without it.

Prefer existing installations. If Java must be provisioned, use an official distribution and the host's approved installation mechanism; prefer user-scoped setup. Do not silently replace global Java/PATH configuration. Explain any installation blocked by host policy.

PowerShell checks:

```powershell
git --version
java -version
javac -version
py --list
```

Fresh checkout example, only if no suitable checkout exists:

```powershell
New-Item -ItemType Directory -Force C:\dev | Out-Null
Set-Location C:\dev
git clone --branch develop https://github.com/integratedmodelling/klab-services.git
Set-Location klab-services
git switch -c python-bridge-baseline dcb5c609a2314b8fc5474d40109d19e3c5c1be76
.\mvnw.cmd -version
```

Use the inspected commit for the first baseline unless an existing user-selected revision takes precedence. Record any deviation. Avoid mixing unreviewed upstream changes into the baseline. Maven must use Java 21 too.

The source commit pins repository code, not mutable SNAPSHOT dependencies. Record resolved versions and any snapshot incompatibility; do not describe this as a fully hermetic build.

## Phase 2 — run the existing tests before editing

First run one class:

```powershell
.\mvnw.cmd -pl klab.core.services -am "-Dtest=StorageScanTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Then:

```powershell
.\mvnw.cmd -pl klab.core.services -am "-Dtest=StorageScanTest,SpatialScanTest,StorageConsumerIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test

.\mvnw.cmd -pl klab.services.runtime -am "-Dtest=StorageConsumerExecutionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

On Unix use ./mvnw with the same arguments. Inspect actual Surefire reports under each module's target/surefire-reports. A BUILD SUCCESS with no selected tests executed is not sufficient. The failIfNoSpecifiedTests setting allows prerequisite modules without matching tests to participate.

Save a concise baseline record with command, exit status, tests run/failures/errors/skips, and report locations. Avoid committing bulky logs or target directories.

### Failure handling

- Toolchain mismatch: fix local Java selection, then retry.
- Missing internal language/snapshot artifact: record exact Maven coordinates, repository URL, and error. Inspect documented repositories and available local sibling checkouts.
- Authentication failure: identify the required repository/access without printing credentials.
- Compile failure: preserve the first meaningful compiler error and assess snapshot compatibility.
- Assertion failure: preserve the actual test report; do not alter expected results to make it green.

Do not randomly upgrade dependencies, skip failing baseline tests, create fake internal artifacts, weaken assertions, or disable validation. Do not use -DskipTests as evidence of a test baseline.

If unavailable dependencies block Java, complete independent Python work and reviewable source changes where feasible, but report Java execution as BLOCKED. Do not claim end-to-end success. List exactly what the user needs from Ferdinando, such as a matching klab-languages checkout or published artifact access.

## Phase 3 — Python fixture and contract

Create a repo-local virtual environment. Windows example:

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install pytest
$env:KLAB_PYTHON_EXECUTABLE = (Resolve-Path .\.venv\Scripts\python.exe).Path
```

Use Python 3.11 if that is installed. Exclude .venv locally or through an appropriate existing ignore rule. Pin the pytest version actually used in a small test requirements file; no production Python dependency is needed.

KLAB_PYTHON_EXECUTABLE is a NEW variable for this prototype, not an existing k.LAB setting. It must point to an actual interpreter executable, not a shell command containing flags. Pass its arguments separately. Document setting it for IntelliJ too.

Implement a small Python function using the standard library:
- Input: a list of finite numbers or null values.
- Output: double each number; preserve null as missing data.
- Include zero, negative and fractional inputs.
- Reject booleans, strings, non-finite values, and invalid request shapes.
- Reject overflow to non-finite output.
- Use strict JSON: no NaN or Infinity.

One request per process, one JSON response on stdout, UTF-8 encoding. Close stdin after sending the request. Logs and errors go to stderr.

Protocol version 1 example:

Request:
```json
{"protocol_version":1,"operation":"double","values":[1,2,3,null]}
```

Successful response:
```json
{"protocol_version":1,"values":[2,4,6,null]}
```

Failure: nonzero exit with a useful stderr message; no successful JSON response. This is a test protocol, not an approved k.LAB API.

Write pytest checks for expected values, null preservation and invalid input. Keep error/timeout/malformed-response fixture workers separate from the scientific function rather than adding artificial failure operations to its public contract.

## Phase 4 — Java subprocess integration

Inspect the existing JUnit, JSON-library and Maven conventions. Prefer test sources in klab.core.services, alongside the existing storage tests, with a small test-scoped Python process helper. If that module is inappropriate, choose the nearest suitable existing module and document the reason. Avoid creating a new Maven module or exposing an unproven production API.

Suggested files (adapt package/path to repository conventions):
- PythonProcessIntegrationTest.java.
- A small test-scoped PythonProcessRunner helper if needed.
- Python worker and focused fixture scripts under test resources.
- A pytest test file and test requirements file.
- A short developer runbook.

Implementation requirements:
- Use ProcessBuilder with a list of arguments; never cmd /c, shell=True, or concatenated shell commands.
- Resolve interpreter from KLAB_PYTHON_EXECUTABLE; fail with actionable instructions when the explicitly selected integration test lacks it.
- Ordinary existing Java test runs must remain usable without Python. Use an explicit Maven profile or equivalent opt-in test mechanism. When opted in, missing Python must FAIL, not silently skip.
- Keep test selection compatible with existing Surefire conventions and avoid changing discovery for unrelated tests.
- Resolve worker resources without relying on an IDE working directory; copy into a temporary directory if necessary.
- Drain stdout and stderr concurrently so a full pipe cannot deadlock the process.
- Set a configurable bounded timeout. On expiry terminate the process, wait for termination, force termination if necessary, and clean up readers/executors and temporary files.
- Use separate stdout/stderr; validate exit status before parsing a response.
- Validate response version, shape, numeric/null values and expected result length.
- Include useful bounded stderr diagnostics for failures; do not dump environment variables.
- Bound diagnostic/output accumulation for this small fixture.
- No arbitrary sleeps in success tests. A timeout fixture may deliberately block; use a generous test deadline and assert the worker is no longer alive afterward.
- Handle paths containing spaces.

Required cases:

| Case | Assertion |
| --- | --- |
| Successful real worker | [1,2,3] becomes [2,4,6] |
| Mixed values | Zero, negative, fractional and null inputs retain expected meaning |
| Worker failure | Nonzero exit and recognizable stderr reach the Java failure |
| Timeout | Deadline enforced and subprocess terminated |
| Malformed response | Zero exit with invalid JSON or wrong schema is rejected clearly |

Use real Python for the invocation tests, not a mocked ProcessBuilder. This phase does not need access to storage or live services.

## Phase 5 — verification and handoff

1. Run pytest with the configured interpreter.
2. Run the new opted-in Java integration test.
3. Rerun the selected storage baseline tests if code/build changes could affect them.
4. Inspect Git diff and ensure no environments, credentials, build outputs or unrelated changes are included.
5. Write the runbook and a concise results summary.

Provide exact PowerShell commands and Unix equivalents for:
- Python environment creation and dependency installation.
- Interpreter configuration.
- Existing storage tests.
- pytest.
- The new Maven profile/test command, using the actual implemented names.
- Finding test reports.

Report:
- SHA and environment versions.
- Files changed and why.
- Tests actually run and counts.
- Any skipped or blocked checks.
- Whether the original baseline changed.
- The next smallest step: inspect how a real contextualizer receives scanners and writes results, then decide where this process adapter belongs. Do not implement storage integration in this task.

## Acceptance criteria

- [ ] Java 21 and Maven wrapper verified.
- [ ] Inspected baseline SHA or justified alternative recorded.
- [ ] Selected storage tests actually executed; results preserved.
- [ ] Real Python function and pytest checks pass.
- [ ] Java invokes the configured Python interpreter successfully.
- [ ] Failure, timeout cleanup and malformed-output cases pass.
- [ ] Existing default Java tests do not acquire an unconditional Python requirement.
- [ ] Explicit bridge-test invocation fails clearly if Python is unavailable.
- [ ] Runbook gives reproducible commands.
- [ ] No claim of a completed k.LAB observation integration.
- [ ] Any dependency/access blocker is clearly separated from test failures.

## Repository references

- README: https://github.com/integratedmodelling/klab-services/blob/dcb5c609a2314b8fc5474d40109d19e3c5c1be76/README.md
- Testing guide: https://github.com/integratedmodelling/klab-services/blob/dcb5c609a2314b8fc5474d40109d19e3c5c1be76/docs/TESTING.md
- Storage guide: https://github.com/integratedmodelling/klab-services/blob/dcb5c609a2314b8fc5474d40109d19e3c5c1be76/docs/STORAGE.md

At this baseline, the checked-in CI workflow uses -DskipTests. Local passing tests must be established independently; changing CI is outside this handoff.

