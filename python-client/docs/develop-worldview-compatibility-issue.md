# Develop interoperability blocker: Reasoner cannot accept the pinned worldview

**Observed:** 16 October 2026. **Status:** unresolved; live compatibility is not certified.
**Change scope:** this contribution changes only `python-client/`. It does not
merge develop, modify Java/API code, edit the worldview, or alter ODO semantics.

## Summary

The Python client can reach the locally deployed Java services, but the Reasoner
cannot publish accepted semantic knowledge for the tested imod worldview. The
same failure occurs on **unchanged develop**, using the same ontology source
hashes and exact ODO dependency. This is an existing server/worldview compatibility
blocker under these inputs, not a demonstrated Python-client regression.

There were two distinct failures:

1. **Dependency provisioning, addressed in the Python test tooling:** the Reasoner
   initially rejected an uncaptured numbered ODO import. Capturing and staging the
   exact declared release removes that diagnostic.
2. **Semantic candidate construction, still unresolved:** after that provisioning,
   the Java compiler rejects the `land` ontology's domain. No accepted generation
   becomes available, so scientific and restart acceptance cannot proceed.

The evidence does **not** establish whether the remaining root cause is a Java
compiler defect, unsupported legacy domain syntax, an invalid worldview, or an
unsupported server/worldview version pairing. Fixing it by changing scientific
meaning or relaxing Java validation is outside this Python-only contribution.

## Exact inputs

| Input | Revision or identity |
|---|---|
| Python-client parent | `188caeb47b86f2eb114cd6f557cf839df0eb10ee` |
| Unchanged develop baseline | `671a8181551a51b8a6b8080c3a13a2bf1cd0f048` |
| imod worldview | `608bef150ced0a109db98a5aad64ba4461beaa54` |
| Declared ontology | `https://raw.githubusercontent.com/integratedmodelling/odo-im/master/releases/0.1.0/odo.owl` |
| Immutable ODO source commit | `aa2cc5611799ceed2aad52ae2c2a349da29e3c4b` |
| ODO SHA-256 | `eb7318d725a16bd6e68a4cc12955c7b94b885a48d9c3c4f7837d4cd24a8ec7e4` |
| Environment | Windows, Temurin 21.0.12.1+1, Python 3.12.7 |

The first combined-server run used the earlier **uncommitted integration tree**,
not this PR branch's Java tree. That tree's source manifest and evidence are
identified in the linked JSON records. The independent baseline reproduction used
compiled unchanged develop. This PR intentionally carries neither that Java merge
nor an alleged Java fix.

## What was observed

### Before dependency provisioning

All five actual Java services exposed their public HTTP endpoints. The original
scientific acceptance then received Reasoner HTTP 500:

```text
Accepted semantic knowledge is unavailable
```

The Reasoner status explained the missing dependency:

```text
acceptedGeneration: 0
beanStatus: VALID
logicalStatus: UNRESOLVED
CANDIDATE_ISOLATION_UNSUPPORTED: uncaptured OWL import
{odo,https://raw.githubusercontent.com/integratedmodelling/odo-im/master/releases/0.1.0/odo.owl}
```

The new `tools/semantic_dependencies.py` captures the exact immutable source,
checks its hash, and writes the declared locator and hash into
`source-catalog.properties`. `fixture_hub.py prepare --semantic-catalog ...`
stages those bytes into disposable TEST state. It does not relabel the bundled
ontology as a numbered release, follow a remote fallback, or disable validation.

### After dependency provisioning

The missing-import diagnostic disappears. The Reasoner instead reports:

```json
{
  "acceptedGeneration": 0,
  "beanStatus": "VALID",
  "logicalStatus": "FAILED",
  "diagnostics": [
    "Candidate build failed: ONTOLOGY_DOMAIN_INVALID: land requires a resolved domain"
  ]
}
```

The baseline and combined candidate have identical **28 worldview source hashes**,
the same exact ODO bytes, and the same resulting diagnostic. The baseline
comparison exercised actual graphdb, Resources and Reasoner processes. It was a
readiness/candidate-build reproduction, not a successful full baseline workflow.

## Where the failure occurs

The links below are pinned to develop `671a81815`; they are references to existing
Java code, **not files modified by this contribution**.

1. [ReasonerServer.createPrimaryService, lines 64–75](https://github.com/integratedmodelling/klab-services/blob/671a8181551a51b8a6b8080c3a13a2bf1cd0f048/klab.services.reasoner.server/src/main/java/org/integratedmodelling/klab/services/reasoner/ReasonerServer.java#L64-L75)
   enables candidate publication using an explicitly captured local catalog.
2. [SemanticEnvironmentFactory.fromLocalCatalog, lines 99–129](https://github.com/integratedmodelling/klab-services/blob/671a8181551a51b8a6b8080c3a13a2bf1cd0f048/klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/SemanticEnvironmentFactory.java#L99-L129)
   reads captured ontology bytes and document locators. Its
   [build guard, lines 233–240](https://github.com/integratedmodelling/klab-services/blob/671a8181551a51b8a6b8080c3a13a2bf1cd0f048/klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/SemanticEnvironmentFactory.java#L233-L240)
   explains the initial uncaptured-import failure.
3. [ReasonerService.checkWorldview, lines 909–918](https://github.com/integratedmodelling/klab-services/blob/671a8181551a51b8a6b8080c3a13a2bf1cd0f048/klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/ReasonerService.java#L909-L918)
   retrieves the authoritative worldview from Resources and calls `loadKnowledge`.
4. [ReasonerService.loadKnowledge, lines 2043–2067](https://github.com/integratedmodelling/klab-services/blob/671a8181551a51b8a6b8080c3a13a2bf1cd0f048/klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/ReasonerService.java#L2043-L2067)
   performs bean preflight and submits the complete worldview for isolated candidate
   construction. Bean validity is not proof of successful semantic compilation.
5. [SemanticEnvironmentFactory.build, lines 277–295](https://github.com/integratedmodelling/klab-services/blob/671a8181551a51b8a6b8080c3a13a2bf1cd0f048/klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/SemanticEnvironmentFactory.java#L277-L295)
   constructs a private compiler actor and loads the snapshot there.
6. [ReasonerService.compileStagedOntologies, lines 3092–3097](https://github.com/integratedmodelling/klab-services/blob/671a8181551a51b8a6b8080c3a13a2bf1cd0f048/klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/ReasonerService.java#L3092-L3097)
   validates each ontology preamble through
   `WorldviewDomainSupport.validate(source, this::declareConcept)`.
7. **[WorldviewDomainSupport.validate, lines 15–21](https://github.com/integratedmodelling/klab-services/blob/671a8181551a51b8a6b8080c3a13a2bf1cd0f048/klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/WorldviewDomainSupport.java#L15-L21)**
   is the exact throw site. It rejects a resolved domain that is null, `NOTHING`,
   or not of semantic type `DOMAIN`. The recorded diagnostic does not distinguish
   which of those conditions occurred; further Java-side diagnosis is required.
8. [CandidatePublicationCoordinator, lines 271–276](https://github.com/integratedmodelling/klab-services/blob/671a8181551a51b8a6b8080c3a13a2bf1cd0f048/klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/CandidatePublicationCoordinator.java#L271-L276)
   records the exception as `FAILED`, prefixed with `Candidate build failed:`.
   [AcceptedSemanticRuntime.lease, lines 53–56](https://github.com/integratedmodelling/klab-services/blob/671a8181551a51b8a6b8080c3a13a2bf1cd0f048/klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/AcceptedSemanticRuntime.java#L53-L56)
   then cannot serve scientific operations because no accepted generation exists.

The relevant unchanged source is
[`imod/src/land.kwv`, lines 5–9](https://github.com/integratedmodelling/imod/blob/608bef150ced0a109db98a5aad64ba4461beaa54/src/land.kwv#L5-L9):

```text
ontology land
    ""
    version 1.0
    using imod, society, earth, life
    in domain imod:Knowledge of (imod:Geosphere or imod:Biosphere);
```

That expression is an investigation target, not a proposed Python-side rewrite.

## How the Python functional test exposes it

`tests/functional/test_develop_compatibility.py` selects the maintained
`develop-compatibility` suite. In `tools/workflows/develop_compatibility.py`,
`require_semantic_readiness` reads `GET /reasoner/public/status`, including the
Java metadata `delegate` wrapper, and checks `semantic.candidate`.

An HTTP 200 or local-authoring operational flag does not override accepted
generation zero. Transient publication states receive a bounded wait; a failed or
unresolved candidate fails the test with its diagnostic. Full redacted status is
retained as `develop-readiness.json`.

Once this gate can pass, the suite is designed to verify the known field and unit
conversion before/after a Runtime-only restart, plus private/shared/excluded
context permissions and collaborator-first restoration. **Those assertions have
not been reached in these runs.** No test was skipped or weakened to get a pass.

## Reproduction and evidence

### Prerequisites and safety

Use Windows/PowerShell, Git, a JDK 21 (including `javac`), Python 3.12 and Maven
wrapper download access. These are development-only Windows lifecycle tools, not
production deployment instructions. Run Python commands from this PR checkout's
`python-client/` directory, with its dependencies installed:

```powershell
python -m pip install -e ".[dev,local,analysis]"
```

Ensure ports **8382, 8091, 8092 and 18090** are unused for readiness-only; full
acceptance also needs **8093 and 8094**. Do not stop unrelated listeners, reuse
shared state, or supply production credentials. Each run needs a new state
directory whose parent exists. The runner creates local TEST credentials and
attempts owned-process cleanup even on failure; inspect cleanup status before
another run. State contains private keys/tokens: do not attach it wholesale.

### 1. Obtain exact, clean baseline inputs

Choose a new parent directory, replacing the example path below. Commands assume
Git is available and must stop on nonzero native-command exits. Do not replace an
existing checkout, reset changes, or update these pins to floating HEAD for this
reproduction.

```powershell
$root = "C:\klab-compat-repro"
New-Item -ItemType Directory -Path $root -ErrorAction Stop | Out-Null
git clone https://github.com/integratedmodelling/klab-services.git "$root\server"
if ($LASTEXITCODE) { throw "Server clone failed" }
git -C "$root\server" checkout --detach 671a8181551a51b8a6b8080c3a13a2bf1cd0f048
if ($LASTEXITCODE) { throw "Server pin failed" }
git clone https://github.com/integratedmodelling/imod.git "$root\imod"
if ($LASTEXITCODE) { throw "Worldview clone failed" }
git -C "$root\imod" checkout --detach 608bef150ced0a109db98a5aad64ba4461beaa54
if ($LASTEXITCODE) { throw "Worldview pin failed" }
git -C "$root\server" status --short
git -C "$root\imod" status --short
```

Both status listings must be empty before building. Use a fresh clone to avoid
stale compiled outputs. The fixture verifies worldview HEAD but does not prove
all working files are clean; that remains an explicit reproduction prerequisite.

### 2. Capture ODO and run the minimal semantic reproduction

Still from the PR's `python-client/` directory, set your actual JDK path:

```powershell
$java = "C:\jdk21\bin\java.exe"
python tools/semantic_dependencies.py --output "$root\catalog"
if ($LASTEXITCODE) { throw "Exact ODO capture failed" }
python tools/run_local_acceptance.py --server-repo "$root\server" --java $java --worldview "$root\imod" --semantic-catalog "$root\catalog" --state-dir "$root\readiness-state" --build --readiness-only
$readinessExit = $LASTEXITCODE
Get-Content "$root\readiness-state\run-report.json"
Get-Content "$root\readiness-state\develop-readiness.json"
```

`--build` builds the selected server modules, Web UI resources and runtime
dependency classpaths; it uses `-DskipTests`, **not a Java test certification**.
The selected checkout and HEAD are printed and recorded in `run-report.json`.
Readiness-only prepares no Runtime test-control classpath and launches no
Resolver/Runtime. Its expected historical failure is exit **1**, with the domain
diagnostic shown above in `develop-readiness.json`/`semantic-readiness.xml` and
`cleanup_exit_code: 0`. A build/startup failure is a different outcome: inspect
the corresponding step log and do not call it a reproduced semantic failure.
The new runner was exercised against the previously compiled clean baseline and
reproduced that failure with confirmed cleanup; the **fresh-clone `--build`
sequence was not rerun** in this pass. Missing Maven artifacts/build failures are
environment blockers, not a reproduced domain failure. See the new verification
record below. No live scientific acceptance pass is claimed.

After completion, check `cleanup_exit_code` and verify there are no remaining
listeners on the test ports. If cleanup fails, inspect PID/argument-file ownership
before retrying only this state:

```powershell
python tools/local_stack.py stop --repo "$root\server" --state-dir "$root\readiness-state"
```

That command stops only services whose PID files match the explicit state. The
runner separately owns/stops its TEST authority process. Do not kill arbitrary
port owners or reuse a state with uncertain live processes.

To isolate the **earlier missing-import trigger**, rerun with a fresh state path
and omit only `--semantic-catalog`; reuse the already built pinned server (omit
`--build` if unchanged). Expected diagnostic is the uncaptured ODO import, not the
post-provisioning domain error. Do not omit the import in the proposed final fix.

### 3. Run full Python acceptance after the semantic contract is repaired

Use the agreed server revision with compiled Runtime `FixtureControls`; baseline
develop alone does not supply those scientific test controls. Do not copy classes
from a different server build into the baseline to force acceptance. In a new
state directory, run:

```powershell
python tools/run_local_acceptance.py --server-repo C:\supported-server-with-test-controls --java $java --worldview "$root\imod" --semantic-catalog "$root\catalog" --state-dir "$root\full-state" --build --duration 1 --samples 1 --saved-handles --typed-observe
```

If maintainers require a newer worldview/ODO pairing, update the Python fixture's
explicit compatibility pins and dependency hashes in a reviewed follow-up rather
than silently substituting inputs. For an already running explicitly owned full
TEST stack, the named workflow alone is:

```powershell
python tools/run_functional_acceptance.py --state-dir C:\owned-test-state --suite develop-compatibility --report C:\owned-test-state\develop-compatibility.xml
```

`semantic-readiness` can likewise be selected alone on prepared running state.
It is a read-only HTTP gate, not permission/restart acceptance. The full
`develop-compatibility` workflow is owned-stack-only and mutates scientific state
and restarts Runtime. The selected server checkout is preserved across restart.

### Evidence boundary

- [Initial missing-dependency record](results/develop-compatibility-verification.json).
- [Exact-dependency rerun and baseline comparison](results/develop-semantic-dependencies-verification.json).
- [New explicit-server/readiness-only runner verification](results/develop-readiness-runner-verification.json):
  clean develop `671a81815`, previously compiled outputs, all three service starts
  successful; one readiness test failed with the same domain diagnostic and
  identical 28 candidate source hashes. Exit 1; cleanup exit 0; no test-port
  listeners remained. Fresh offline suite: **1,028 passed**, zero failures/errors/
  skips; existing Ruff selection passed. Tested working-code hashes and commands
  are recorded so the uncommitted review-fix run is not confused with its parent.
- The records contain revision boundaries, evidence filenames and SHA-256 hashes.
  Full local logs are not shipped and temporary evidence paths are not portable.
- Historical offline unit/local-integration validation: **1,018 passed**, no failures, errors
  or skips; existing Ruff selection passed. This is not a green live-suite claim.
- Owned service exits were confirmed and no test-port listeners remained.
  Production services and credentials were not used.

## Resolution boundary

This PR addresses Python test provisioning, readiness diagnostics, repeatable
functional routing and owned-process safety. It deliberately leaves Java,
worldview and ontology source unchanged and is not promotion to develop.

The platform maintainers need to identify a supported semantically valid
server/worldview/ODO combination, or diagnose and correct the compiler/domain
contract separately. Once available, rerun the exact pinned stack through this
functional suite before claiming scientific, restart or deployment compatibility.
Native Java-client interoperability, nonlocal shared-session acceptance and
production Hub/provider accuracy remain separate gates.

## Java-side investigation and acceptance checklist

This is a platform compatibility handoff, not a conclusion that Java must change.
Investigate on a separate Java/platform branch; this Python-only PR must not
implement a semantic workaround.

1. **Reproduce with the pinned inputs first.** Save the redacted readiness status,
   JUnit, step logs, selected server revision/dirty state, worldview source hashes
   and dependency hash. Compare Java changes against unchanged develop with the
   exact same inputs. Distinguish missing-import provisioning from domain failure.
2. **Inspect the domain contract at the throw site.** Break/log around
   `WorldviewDomainSupport.validate` and the `this::declareConcept` invocation in
   `compileStagedOntologies`. Record the parsed domain expression for `land`, its
   namespace/import context, the resolver return (null, NOTHING, or actual concept)
   and whether that result has `SemanticType.DOMAIN`. The existing diagnostic
   conflates those paths; it does not prove which one is wrong.
3. **Trace referenced semantics and compilation order.** Check how `imod:Knowledge`,
   `imod:Geosphere`, `imod:Biosphere` and the composite domain expression resolve in
   the isolated candidate actor, including prerequisites/imports and source/domain
   mapping. Confirm whether the pinned worldview syntax and numbered ODO 0.1.0 are
   supported by that server's contract. Bundled ODO has different classes; do not
   relabel it as release 0.1.0 or infer equivalence from a passing parse.
   In particular, the same Java helper's
   [interpretations method, lines 54–67](https://github.com/integratedmodelling/klab-services/blob/671a8181551a51b8a6b8080c3a13a2bf1cd0f048/klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/WorldviewDomainSupport.java#L54-L67)
   references `odo:KnowledgeDomain`/`odo:PhysicalDomain` and explicitly mentions ODO
   0.1.1 domain kinds. That is a concrete version-contract investigation clue,
   **not evidence that this later guard ran or caused the recorded land failure**.
4. **Choose the correct repair boundary.** If supported semantics are lost in
   parsing, resolution or candidate compilation, fix Java and add a minimal
   regression for the failing path. If the inputs are unsupported/invalid, obtain
   a maintainer-approved worldview/ODO version pairing or source correction in its
   owning project. Do not simply remove the DOMAIN guard, accept bean validity as
   logical validity, or publish a failed candidate. Preserve last-known-good
   generation isolation when a later candidate fails.
5. **Validate publication and rejection.** Run relevant Java domain/compiler and
   candidate-publication tests. Add a supported-domain success regression and an
   invalid/unresolved-domain rejection regression. Demonstrate a positive accepted
   generation, usable semantic resolution and clear diagnostics for bad inputs on
   actual services; HTTP health/beanStatus alone are insufficient.
6. **Return to the Python gates.** Rerun readiness-only, then full owned-stack
   acceptance on the agreed controls-capable server. Confirm field values/units,
   actual Runtime restart, collaborator-first ACL restoration, saved handles and
   typed workflows. Record any later failures separately; removing this blocker
   is not proof those unreached gates pass.

### Trigger, Python impact and why this branch cannot repair semantics

| Issue | Trigger and owner | Python-client impact | Python-only boundary |
|---|---|---|---|
| Uncaptured numbered ODO import | Startup/candidate construction with no exact local capture; Java candidate isolation refuses the external import | No accepted semantic generation; scientific calls cannot run | Addressed here by exact dependency provisioning, not by changing Java isolation or substituting scientific content |
| `land` domain invalid/unresolved | After exact ODO staging, authoritative worldview compilation calls Java domain validation on the unchanged `land` declaration | Generation stays zero; historical scientific calls return HTTP 500, while the new gate fails earlier with the actual reason; computation, conversions, restart/ACL/saved-handle suites are blocked before execution | Resolution/type checking and publication belong to Reasoner/worldview/ODO; the Python client only transports requests and cannot manufacture an accepted concept or generation |
| Wrong server selection in reproduction | Running tools from a feature checkout without specifying the Java checkout | Could diagnose the wrong server/version as develop | Addressed here through `--server-repo`, build/start/restart preservation and checkout identity reporting |
| Full fixture prerequisite absent on unchanged develop | Full preparation requires compiled Runtime `FixtureControls`, which that baseline lacks | Full scientific acceptance cannot be prepared; this alone is not a product compatibility defect | Readiness-only now isolates the semantic issue without those controls; full test-control support belongs in the chosen Java test environment, not copied or fabricated by Python |
| Cleanup masks scientific failure | Scientific operation fails and session release also raises | Previously the top-level error became cleanup, obscuring the triggering failure | Addressed here: retain the original exception and add the cleanup exception type as a redacted note; cleanup-only errors still fail |

Neither HTTP retries, client DTO changes, skipping readiness, coercing the domain
expression, nor using a bundled ontology under another release's name can correct
server-owned scientific meaning. Authentication succeeds far enough to reach the
semantic failure; no evidence here establishes an identity/ACL defect. Full native
Java-client interoperability and production deployment remain untested, not
implicitly certified by fixing this local Reasoner prerequisite.
