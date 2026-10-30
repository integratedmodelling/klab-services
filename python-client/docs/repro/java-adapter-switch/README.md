# Reproduce the LanguageAdapter compilation failure—without GitHub Actions access

This is an executed reproducer, not a suggested command that has never been run.
The controlled run on Windows/Temurin `javac 21.0.12.1` produced:

```text
old: 1.0.0-20261016.113257-72; enum values=54; unhandled=[]; javac exit=0
new: 1.0.0-20261029.085338-82; enum values=55; unhandled=['RELATIONSHIP']; javac exit=1
AdapterSwitchReproduction.java:8: error: the switch expression does not cover all possible input values
        switch (type) {
        ^
1 error
Expected old-pass/new-fail reproduced: True
```

The wrapper's line 8 is the original `LanguageAdapter.java:1234` switch. This copies
the **entire real method verbatim**, not a replacement switch or a fabricated enum.
It compiles with the real internal `SemanticType.java` source and the real binary
`SemanticSyntax.Type` from each pinned published observable-language JAR.

## 1. Prerequisites

- Git and Python 3 (standard library only; no pip install).
- A **JDK 21**, including `javac` and `javap`, on PATH or selected with `--java-home`.
- HTTPS access to GitHub for the public checkout and Sonatype for the two public
  timestamped JARs. No GitHub login/token or service credentials are needed.
- About 2 MB for downloaded JARs and generated source/classes/logs.

No Maven installation/cache, Neo4j, k.LAB services, private distribution, observation
Jobs, scientific source changes or access-control operations are used.

## 2. Exact portable commands

From a directory where a new checkout and output directory may be created:

```sh
git clone --branch feature/python-resource-inspector https://github.com/integratedmodelling/klab-services.git klab-adapter-repro
cd klab-adapter-repro
python python-client/docs/repro/java-adapter-switch/reproduce.py --output ../adapter-switch-output
```

Use `python3` instead of `python` on systems where that is the Python 3 command.
If Java 21 is not selected on PATH, provide the JDK directory explicitly:

```sh
python python-client/docs/repro/java-adapter-switch/reproduce.py --java-home "/path/to/jdk-21" --output ../adapter-switch-output
```

PowerShell uses the same Python arguments; quote Windows paths containing spaces.
`--output` must name a **new directory**. The script refuses an existing path rather
than deleting or overwriting previous results. Use another new path for a rerun.

The script reads the two production source files from immutable repository commit
**`c872c1040c354375b7c72b180a649c7508bcebc6`** using `git show`, regardless of the
working checkout's current head. It verifies their LF-normalized SHA-256 before
extracting anything. Local production files are not edited or checked out/reset.

## 3. What changes between the two compiler runs

Only the selected binary language JAR changes; the generated method, internal enum
source, JDK and compiler flags are the same.

| Binary observable-language version | Enum constants | Cases absent from real switch | Compiler result |
| --- | --- | --- | --- |
| `1.0.0-20261016.113257-72` | 54 | none | exit 0 |
| `1.0.0-20261029.085338-82` | 55 | `RELATIONSHIP` | exit 1 |

The adapter has cases such as `BOND`, `FUNCTIONAL_RELATIONSHIP` and
`STRUCTURAL_RELATIONSHIP`; those do not cover the distinct enum constant
`RELATIONSHIP`. Java does not infer coverage from related names or meanings.

`reproduce.py` prints the actual `javac` diagnostics and writes:

- `old-javac.txt`, `new-javac.txt`: compiler stdout/stderr;
- `old-enum.txt`, `new-enum.txt`: actual `javap` binary enum inventories;
- `result.json`: commands, source revision/hashes, artifact URLs/hashes, enum values,
  missing cases, each compiler's exit code and the overall outcome;
- `source/`: the verbatim method wrapper and unchanged internal enum source;
- both downloaded pinned JARs and separate compiler output directories.

**Exit codes:** the newer-dependency **javac must fail**. The Python reproducer
returns **0** when it observes both the expected old-pass/new-fail outcomes; that
means the failure was successfully reproduced, not that Java CI is fixed. It
returns nonzero if the observed outcomes differ or a prerequisite/checksum fails.

## 4. Exact artifact identities

Public base URL:

```text
https://central.sonatype.com/repository/maven-snapshots/org/integratedmodelling/org.integratedmodelling.languages.observable/1.0.0-SNAPSHOT/
```

Append the filename for each exact artifact:

```text
org.integratedmodelling.languages.observable-1.0.0-20261016.113257-72.jar
SHA-256: 0a0703361aaeb2af7b2c6660f675434e7d4554af447f57c930a294ff25ad7051

org.integratedmodelling.languages.observable-1.0.0-20261029.085338-82.jar
SHA-256: 4b89ef600e5d620eaf18553170d11b49f5dd78f93914402d3901e4832f5edcf1
```

The script never resolves the moving `SNAPSHOT` alias. A download with unexpected
bytes is refused. Snapshot repositories may eventually remove timestamped files;
retain the downloaded files and their hashes with the result for long-term replay.
If public artifacts become unavailable, use a trusted preserved copy with the
**same hashes**; do not silently substitute the current snapshot.

For a repeat run without network access, provide the prior output directory:

```sh
python python-client/docs/repro/java-adapter-switch/reproduce.py --artifact-dir ../adapter-switch-output --output ../adapter-switch-offline-output
```

Git and the pinned source commit must still be present locally.

## 5. Full original-source reproduction was also executed

Separately from the portable method isolation, the investigation compiled both
unchanged production files:

```text
LanguageAdapter.java
ObservationPlanAdapter.java
```

With JDK21, the pinned newer observable JAR placed first on the classpath and the
existing Resources distribution dependencies, the **full original adapter** failed:

```text
LanguageAdapter.java:1234: error: the switch expression does not cover all possible input values
        switch (type) {
        ^
1 error
```

Placing the pinned 54-value older JAR first instead compiled those same source
files with exit **0** (only existing unchecked-operation notes). No Java source
was patched between those runs. See [observed output](observed-output.txt).

That full-file check uses a local distribution classpath and is supplementary;
the portable reproducer above deliberately removes that installation requirement.
The October27 distribution's own observable JAR also has 55 values and failed the
full-source compile; the passing control specifically used the timestamped
October16 `...113257-72.jar`, not an assumed-compatible directory date.

## 6. Relationship to the five failed PR builds

The GitHub workflow builds the root reactor with:

```sh
./mvnw -B -U clean package -DskipTests
```

It runs for pushes even if only Python/docs changed. Each of #101, #99, #98, #100
and #102 has a failed Java build showing the same production-file diagnostic;
their adapter Git blobs are identical. Resources compilation stops the reactor,
so later service/distribution modules are skipped. Python checks run separately
and pass. This is why the shared Java mismatch has such a wide build impact.

The local controlled result **proves this real source fails with the specified
55-value binary and succeeds with the specified 54-value binary**. It does not
attest which timestamped binary every historical CI run resolved, nor prove that
pinning the older language stack would be a correct full-product repair. That
still requires dependency provenance and semantic compatibility review.

The Node.js/action deprecation warnings shown in the Actions summary are separate
warnings, not the Resources Java compiler diagnostic reproduced here.

## 7. What a maintainer should fix—not just suppress

Confirm the intended language/API contract, then implement and test an approved
mapping for the new enum value or select a coordinated compatible dependency set.
Do not add an arbitrary default merely to turn the build green. Test semantic
behavior as well as compilation, then verify the full Maven build and affected
PR heads. Java/application source repair remains outside this Python task's
authorization; the reproducer modifies no production source.

For the wider impact/audited runs, see
[shared blocker report](../../JAVA_BUILD_BLOCKER_20261029.md).
