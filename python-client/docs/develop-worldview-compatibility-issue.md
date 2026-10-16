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

See [functional-testing.md](functional-testing.md#captured-semantic-dependencies)
for capture and owned-stack commands. An explicitly prepared running TEST stack
can run just the new gate and workflow:

```powershell
python tools/run_functional_acceptance.py --state-dir C:\owned-test-state --suite develop-compatibility --report C:\owned-test-state\develop-compatibility.xml
```

Use the **intended compiled server checkout**, not this Python branch's default
Java tree when investigating develop. `fixture_hub.py prepare --server-repo ...`
and `local_stack.py start --repo ...` explicitly select that checkout; the former
requires its compiled Runtime TEST controls. The recorded unchanged-develop
comparison started only graphdb/Resources/Reasoner to isolate semantic readiness.
The Python suite preserves the selected server checkout across Runtime restart.

- [Initial missing-dependency record](results/develop-compatibility-verification.json).
- [Exact-dependency rerun and baseline comparison](results/develop-semantic-dependencies-verification.json).
- Both records contain revision boundaries, evidence filenames and SHA-256 hashes.
  Full local logs are not shipped and temporary evidence paths are not portable.
- Offline unit/local-integration validation: **1,018 passed**, no failures, errors
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
