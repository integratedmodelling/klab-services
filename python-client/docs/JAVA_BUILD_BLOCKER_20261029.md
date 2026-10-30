# Shared Java build blocker across the Python milestone PRs

Date: 29 October 2026. Investigation: read-only source, workflow and existing
GitHub build-log inspection; no build reruns, dependency updates or Java changes.

**Update: local compiler reproduction now executed.** The unchanged full production
adapter fails at line1234 with pinned binary `1.0.0-20261029.085338-82` and compiles
with pinned binary `1.0.0-20261016.113257-72`. A portable real-method reproduction
needs no private distribution or GitHub log access: [exact commands, checksums,
observed output and runnable script](repro/java-adapter-switch/README.md).
These checks do not change production source, update Maven dependencies, rerun
GitHub CI or consume an observation attempt. The historical read-only audit below
remains distinct from this subsequent controlled compiler test.

## Executive summary

Five open Python PRs independently fail to compile the same shared Java module.
This is one shared source/dependency compatibility problem, not five demonstrated
Python defects. All five have passing reported Python checks.

The failure prevents a successful full Java package build. It also prevents Maven
from reaching multiple later service/distribution modules. Each PR therefore lacks
a green Java build, even when its Python feature tests and local live acceptance
pass. Stacking compounds the integration coordination, but is not the sole reason:
**each of the five PRs has its own build log showing this exact compiler error.**

PR98 does not block PR99. The dependency direction is
**#101 → #99 → #98 → #100 → #102**. All five share the Java build problem.

## 1. Exact failure

File:
`klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/lang/LanguageAdapter.java`

Compiler diagnostic:

```text
LanguageAdapter.java:[1234,9]
the switch expression does not cover all possible input values
```

Maven reports:

```text
org.apache.maven.plugins:maven-compiler-plugin:3.13.0:compile
(default-compile) on project klab.services.resources: Compilation failure
```

This is a **compile-time error**, not a Python test failure, service outage,
authentication problem, empty numerical result or a failed scientific algorithm.
The compiler cannot produce the Resources module from this source/dependency
combination.

## 2. What the code is doing

At `LanguageAdapter.java:1232`, `adaptSemanticType(SemanticSyntax.Type type)`
translates the language parser's semantic-type enum into internal k.LAB flags:

```java
private Set<SemanticType> adaptSemanticType(SemanticSyntax.Type type) {
  var ret =
      switch (type) {
        case VOID, NOTHING -> EnumSet.of(SemanticType.NOTHING);
        // Individual enum cases map to internal semantic flags.
        // The expression must cover every enum constant known to the compiler.
      };
  // ...
}
```

This is an explanatory excerpt, not the complete method or a proposed patch.
Unlike a switch statement that can simply perform no action, this switch
**expression must yield a value** for every possible enum constant. There is no
catch-all covering a newly introduced enum value. If the dependency adds a value
that this adapter does not handle, Java rejects the expression before execution.

The mapping is scientifically meaningful: flags distinguish qualities, predicates,
subjects and relationships. Choosing an arbitrary fallback merely to compile can
silently change meaning or defer failure until a user submits the affected concept.

## 3. Dependency mismatch evidence and its limits

### Confirmed

- The Resources POM declares
  `org.integratedmodelling:org.integratedmodelling.languages.observable:1.0.0-SNAPSHOT`
  at `klab.services.resources/pom.xml:62–66`.
- Other language artifacts—worldview, k.IM, observation and k.Actors—are also
  declared as snapshots in that POM. They must be treated as a coordinated
  language contract, not assumed interchangeable solely because versions match.
- The prior inspected published observable-language **sources** artifact
  `1.0.0-20261029.085338-82` has 55 `SemanticSyntax.Type` values, including
  `RELATIONSHIP`. This adapter omits that value.
- A previously inspected older local cached snapshot has 54 values, all covered
  by the adapter. See `PR99_DEPLOYMENT_HANDOFF.md:37–42` for that evidence and its
  original qualification.

### Strong explanation, not yet exact CI artifact attestation

A newer enum and an older exhaustive adapter are consistent with the compiler
failure. `RELATIONSHIP` is a concrete known discrepancy, not an invented guess.
However, the exact timestamped **binary JAR and checksum used by each failing CI
run** have not been established. Do not describe the observed sources artifact as
proven to be the binary compiled against in every run.

What is certain without that identity is narrower: each compiler saw a selector
enum whose possible values were not exhaustively covered by the source switch.
The precise resolved binary/enum inventory is needed before approving a repair.

Snapshots are mutable coordinates: two machines or dates can resolve different
content under the same `1.0.0-SNAPSHOT` version. An older cached successful build
does not prove a current fresh resolution is compatible.

## 4. Why Python-only PRs encounter it repeatedly

All five inspected heads have the same `.github/workflows/build.yml` build command:

```sh
./mvnw -B -U clean package -DskipTests
```

The workflow runs on **push**, uses Ubuntu 22.04 and Temurin Java 21, and has no
path filter excluding documentation/Python changes.

- It builds the **root multi-module Maven reactor**, not only changed files or the
  Python client. The Resources module is included in the root `pom.xml`.
- `-U` requests updated snapshot checks. This is why dependency identity matters;
  it does not guarantee a different or compatible artifact on the next run.
- `clean` removes project build output; it does not repair the dependency contract
  or empty Maven's dependency cache.
- `-DskipTests` skips test execution, **not production Java compilation**. The
  Resources compiler error remains fatal even with tests skipped.
- A documentation-only push still triggers this build and encounters the same
  problem while the source/dependency combination remains unchanged.

Separately, `.github/workflows/python-client.yml` runs the Python offline matrix
and core-only checks. Those passing jobs demonstrate their own Python/package
checks; they do not compile or validate this Java adapter.

## 5. Verified affected PRs

Each linked run's failed log was read successfully and contains the identical
`LanguageAdapter.java:[1234,9]` non-exhaustive-switch diagnostic.

| PR | Feature | Audited head | Java build evidence | Python checks |
| --- | --- | --- | --- | --- |
| #101 | F1 semantic explorer | `69c424a3b` | [40709124644](https://github.com/integratedmodelling/klab-services/actions/runs/40709124644) — failed | Passed |
| #99 | F2 resource/model inspector | `c872c1040` | [40968811793](https://github.com/integratedmodelling/klab-services/actions/runs/40968811793) — failed | Passed |
| #98 | F3 execution evidence | `002a0e3ff` | [40962844502](https://github.com/integratedmodelling/klab-services/actions/runs/40962844502) — failed | Passed |
| #100 | F4 experiment runner | `7fae2ea97` | [40709122757](https://github.com/integratedmodelling/klab-services/actions/runs/40709122757) — failed | Passed |
| #102 | Milestone delivery | `88c95c0cf` | [40710104266](https://github.com/integratedmodelling/klab-services/actions/runs/40710104266) — failed | Passed |

The entire `LanguageAdapter.java` Git blob is byte-identical across those five
audited heads; its SHA-256 is
`2832aa2739e926f07829c74c8b970bfe77cee1406cbbb72eeef0250c63036dc2`.
This was checked with `git show <head>:<path>`, not inferred from similar errors.

#90's latest reported Java build
[38164623374](https://github.com/integratedmodelling/klab-services/actions/runs/38164623374)
passed on 11 October. No fresh run was requested; this is not evidence that it
would still pass against today's snapshots. No current failure from this cause
was observed for #90.

## 6. Build blast radius versus feature acceptance

In #99 run `40968811793`, the reactor stops at Resources compilation and reports
later modules **SKIPPED**, including:

- Reasoner server and Resources server;
- Resolver server, Runtime and Runtime server;
- CLI and Modeler;
- GraphDB support, LSP and AMQP support;
- distribution packaging.

Those skipped modules have not each been proven defective. The reactor did not
reach them after the earlier failure. A green full build cannot be claimed from
that run, nor can it validate newly built service/distribution products for the PR.

The stack adds a second coordination layer: later PRs depend on earlier feature
bases. Fixing only one branch does not automatically repair published siblings or
descendants. An approved shared fix needs deliberate propagation and checks on
the intended integration candidates, with history and separate PRs preserved.

**This is a build/integration gate, not proof that GitHub mechanically prohibits
every merge.** Branch-protection/ruleset enforcement was not audited here. The
practical issue is that maintainers do not have the successful full-build evidence
expected for integration; bypassing a check would not restore that evidence.

For #99 specifically, local synthetic numerical/model-association acceptance has
passed. For #98, local persisted-diagram positive control has passed, while a valid
nonowner identity fixture remains a separate blocker. Repairing Java CI neither
invalidates those successes nor completes the outstanding permission/scientific/
delivery gates.

## 7. Smallest safe path to a lasting repair

The Java/language dependency maintainer should own this work. Current authorization
excludes Java, scientific/component, ontology and worldview source changes; this
report does not expand it.

1. **Attest the failure's input contract.** On an authorized failing environment,
   record the head, Java/Maven versions, effective dependency selection, actual
   timestamped language JARs and checksums. Enumerate the binary
   `org.integratedmodelling.languages.api.SemanticSyntax$Type`, and compare its
   constants with the switch cases. Inspect the binary, not only current sources.
2. **Choose a coordinated correction.** Either approve the intended semantic
   mapping for every newly introduced enum constant, with regression tests, or
   select a demonstrably compatible language dependency set if that is the proper
   release contract. Pinning an older artifact is not automatically safe: it may
   remove features relied on elsewhere. A speculative default branch, ignored
   enum value or arbitrary flag set is not an acceptable semantic repair.
3. **Verify compilation, mapping semantics and integration.** Run targeted adapter/
   language contract tests with the approved artifacts, then the complete CI build
   command. Because that command skips tests, run the relevant Java tests explicitly
   as a separate check; compile success alone does not prove the mapping is right.
4. **Propagate once, then verify each candidate.** Agree on the upstream/base fix
   and its integration order. Verify each affected PR's intended tested head with
   the approved dependency identities. Do not assume an upstream green badge
   retroactively validates unchanged failed runs or all downstream heads.
5. **Retain evidence and separate residual gates.** Publish the fix identity,
   dependency inventory, successful Java build/test results and remaining feature
   limitations. Leave nonowner isolation, scientific accuracy and final delivery
   acceptance as independent work where applicable.

Do not simply retry all five PRs repeatedly. With the same source and enum the
failure is deterministic; if a mutable snapshot changes the outcome, provenance
is still required to explain why it became green.

## 8. Evidence and investigation boundaries

- Audited latest reported checks for all six currently open PRs in this repository;
  all were authored by the same contributor (`Dobbes`).
- Read five failed GitHub run logs with `gh run view <run> --log-failed`; each
  command exited 0 and showed the same compiler diagnostic.
- Read the Resources POM, root module list, Java workflow and adapter source.
- Compared the adapter Git blobs and root build command at all five affected heads.
- Did not rerun builds, invoke computations, change dependencies/source, contact
  Jenkins with additional credentials, bypass controls, or merge/rebase branches.
- Jenkins error statuses were observed in some PRs, but their underlying causes
  were not inspected; do not assign them this same cause without separate evidence.
- Machine-readable audit retained at
  `C:/Users/lumsd/AppData/Local/Temp/opencode/pr-java-ci-impact-20261029.json`.

**Bottom line:** one incompatible shared Java language-adapter contract is
preventing five otherwise Python-green PRs from producing a successful full Java
build. Align and verify that contract once, propagate it deliberately, then assess
each PR's remaining independent acceptance gates.
