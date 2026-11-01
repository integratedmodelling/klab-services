# Local Java-to-Python test loop

For the subsequent environmental-computation example with actual local storage reads
and writes, see [the NDVI storage demonstration](PYTHON_NDVI_DEMO.md).

The expanded [bounded scalar prototype](PYTHON_RUNTIME_PROTOTYPE.md) adds batching,
cancellation, preliminary measurements and a reproduced initialization rollback gap.
Its September 30 commands/results supersede the original contribution counts below.

This is a **test-only subprocess transport prototype**, not an approved k.LAB API or a
contextualizer. It does not create observations or commit results to a digital twin.
No running services, worldview, authentication, or production Python dependencies are needed.

## Setup (from the repository root)

Use JDK 21 (`java` and `javac`), the checked-in Maven wrapper, and Python 3.11 or 3.12.
Select Java for the current shell only. On Windows, `JAVA_HOME` is the JDK directory,
not its `bin` directory. Check that Maven reports Java 21 too.

PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
javac -version
.\mvnw.cmd -version
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r klab.core.services/src/test/resources/python-bridge/requirements-test.txt
$env:KLAB_PYTHON_EXECUTABLE = (Resolve-Path .\.venv\Scripts\python.exe).Path
```

Unix:

```sh
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version
./mvnw -version
python3.12 -m venv .venv
.venv/bin/python -m pip install -r klab.core.services/src/test/resources/python-bridge/requirements-test.txt
export KLAB_PYTHON_EXECUTABLE="$PWD/.venv/bin/python"
```

Substitute Python 3.11 if needed. Activation of the environment is unnecessary.
`KLAB_PYTHON_EXECUTABLE` is new to this prototype, not an existing k.LAB setting. Set it
to an actual executable path (spaces are supported), with no embedded quotes or flags.
For IntelliJ, set the project/Maven JDK to 21 and add the same absolute interpreter path
as an environment variable in the JUnit run configuration for `PythonProcessIT`.

## One-command reruns

PowerShell, after setup:

```powershell
# Independent Python checks
& $env:KLAB_PYTHON_EXECUTABLE -m pytest -q klab.core.services/src/test/resources/python-bridge

# Real Java-to-Python subprocess tests (explicit opt-in)
.\mvnw.cmd -pl klab.core.services -am "-Dtest=PythonProcessIT" "-Dsurefire.failIfNoSpecifiedTests=false" test

# Storage baseline: first one class, then both selected groups
.\mvnw.cmd -pl klab.core.services -am "-Dtest=StorageScanTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
.\mvnw.cmd -pl klab.core.services -am "-Dtest=StorageScanTest,SpatialScanTest,StorageConsumerIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
.\mvnw.cmd -pl klab.services.runtime -am "-Dtest=StorageConsumerExecutionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test

# Find actual reports; BUILD SUCCESS alone does not establish test execution
Get-ChildItem klab.core.services/target/surefire-reports,klab.services.runtime/target/surefire-reports -Filter '*.txt'
```

Unix equivalents:

```sh
"$KLAB_PYTHON_EXECUTABLE" -m pytest -q klab.core.services/src/test/resources/python-bridge
./mvnw -pl klab.core.services -am '-Dtest=PythonProcessIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
./mvnw -pl klab.core.services -am '-Dtest=StorageScanTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
./mvnw -pl klab.core.services -am '-Dtest=StorageScanTest,SpatialScanTest,StorageConsumerIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
./mvnw -pl klab.services.runtime -am '-Dtest=StorageConsumerExecutionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
find klab.core.services/target/surefire-reports klab.services.runtime/target/surefire-reports -name '*.txt'
```

The `IT` suffix deliberately falls outside Surefire's default discovery patterns. There
is no Maven profile or change to unrelated test discovery: selecting `PythonProcessIT`
is the opt-in mechanism. Its `@BeforeAll` fails with setup instructions if Python is
unset/unavailable, rather than silently skipping. `failIfNoSpecifiedTests=false` lets
prerequisite modules participate without matching this test class.

To verify the missing-interpreter failure in PowerShell, temporarily set
`$env:KLAB_PYTHON_EXECUTABLE = $null`, run the bridge command with
`"-Dsurefire.reportNameSuffix=missing-python"`, and then restore the interpreter path.
On Unix use `env -u KLAB_PYTHON_EXECUTABLE ./mvnw` followed by the same arguments.
An exit code of 1 and the interpreter setup error are expected for this negative check.

## Combined contribution checks

After setup, these commands exercise the Python tests, both Java integration classes,
and the selected existing core/runtime storage regressions. Run Maven commands sequentially
because their reactor modules share build output directories.

```powershell
& $env:KLAB_PYTHON_EXECUTABLE -m pytest -q klab.core.services/src/test/resources/python-bridge
.\mvnw.cmd -q -pl klab.core.services -am '-Dtest=PythonNdviDemoIT,PythonProcessIT,StorageScanTest,SpatialScanTest,StorageConsumerIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
# Confirm the storage regression tests work without Python configured.
$env:KLAB_PYTHON_EXECUTABLE = $null
.\mvnw.cmd -q -pl klab.services.runtime -am '-Dtest=StorageScanTest,SpatialScanTest,StorageConsumerIntegrationTest,StorageConsumerExecutionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
# Expected exit 1: both explicitly selected classes must fail setup, not skip.
.\mvnw.cmd -q -pl klab.core.services -am '-Dtest=PythonNdviDemoIT,PythonProcessIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dsurefire.reportNameSuffix=missing-python' test
$env:KLAB_PYTHON_EXECUTABLE = (Resolve-Path .\.venv\Scripts\python.exe).Path
```

Unix equivalents:

```sh
"$KLAB_PYTHON_EXECUTABLE" -m pytest -q klab.core.services/src/test/resources/python-bridge
./mvnw -q -pl klab.core.services -am '-Dtest=PythonNdviDemoIT,PythonProcessIT,StorageScanTest,SpatialScanTest,StorageConsumerIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
env -u KLAB_PYTHON_EXECUTABLE ./mvnw -q -pl klab.services.runtime -am '-Dtest=StorageScanTest,SpatialScanTest,StorageConsumerIntegrationTest,StorageConsumerExecutionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
# Expected exit 1; inspect the two missing-python reports for setup errors.
env -u KLAB_PYTHON_EXECUTABLE ./mvnw -q -pl klab.core.services -am '-Dtest=PythonNdviDemoIT,PythonProcessIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dsurefire.reportNameSuffix=missing-python' test
```

There are 90 unique positive checks: 47 pytest cases, 9 Java bridge/NDVI cases, and
34 storage regressions. The 28 core storage checks execute twice in this sequence.
The two expected missing-interpreter setup errors are separate negative checks.
Inspect Surefire reports as well as exit codes; the suffix preserves the positive reports.

## Fixture contract and bounds

Each process consumes exactly one UTF-8 JSON request through stdin until EOF:

```json
{"protocol_version":1,"operation":"double","values":[1,0,-2,0.25,null]}
```

It emits one stdout response on success:

```json
{"protocol_version":1,"values":[2,0,-4,0.5,null]}
```

Null denotes missing data. The worker rejects invalid shapes, extra/duplicate fields,
boolean/string values, unsupported operations/versions, non-finite inputs and overflow.
Failure means a nonzero exit and a useful stderr diagnostic, with no successful response.
Fault workers are separate fixtures; the scientific function has no artificial failure modes.

The test-scoped runner uses argument-list `ProcessBuilder`, closes stdin, and drains
stdout/stderr concurrently. Input and retained output are limited to 16 KiB per stream;
excess stdout fails, stderr is truncated with a marker, and both streams keep draining.
The bridge tests' default deadline is 15 seconds; override it with `-Dklab.python.timeoutSeconds=30`
(positive, maximum 120). The NDVI helper uses a fixed 15-second deadline.
On timeout the runner terminates, then forcibly terminates if
necessary, waits for the process, and closes I/O/executors. Fixtures must not spawn children.
JUnit copies classpath resources into a temporary directory with spaces and cleans it up.
This avoids reliance on an IDE working directory.

The six Java tests cover real computation, mixed/missing values, nonzero exit with UTF-8
diagnostics, timeout/reaping, malformed JSON/schema/value/length responses, and pipe flooding.
Python tests independently check the function and actual CLI protocol.

## Next integration boundary

Inspect how an existing contextualizer obtains scanners and writes results, starting with
`klab.core.services/src/main/java/org/integratedmodelling/klab/runtime/language/ArgumentMatcher.java`,
`runtime/computation/ScalarComputationGroovy.java`, and the storage-consumer tests.
Then decide with maintainers where an adapter should live and how missing data, geometry,
units, cancellation, and write ownership map to that boundary. This prototype leaves that
design decision open.

See [the local results record](PYTHON_BRIDGE_RESULTS.md). Contributions must be reviewed
through a PR and approved before merging.
