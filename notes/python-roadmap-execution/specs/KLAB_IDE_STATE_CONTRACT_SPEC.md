# k.LAB IDE State Contract — Build and Maintain a Reusable Development Reference

## Purpose

Build a **persistent, trustworthy record** of the local k.LAB Modeler/Java environment that all future Python-client A+B specifications must consult. The state record is **not** a declaration that all scientific workflows work. It must distinguish service health, installed capabilities, completed integration gates, outstanding Java defects, and test-environment boundaries.

**Primary output:** `notes/python-roadmap-execution/MODELER_IDE_STATE.md` (tracked Markdown).

**Supporting output:** A small, safe, repeatable, read-only state-collection/check command documented in the Markdown, plus machine-readable evidence receipts in the established evidence/custody convention. Prefer using existing project tooling; do not create another monitoring framework.

**Existing authoritative inputs (read before implementation):**
- `notes/python-roadmap-execution/MODELER_SERVICE_REPAIR_REPORT.md`
- `notes/python-roadmap-execution/MODELER_FIRST_SCIENTIFIC_OBSERVATION.md`
- `notes/python-roadmap-execution/MODELER_REAL_DEM_REMEDIATION_REPORT.md` — **read its latest on-disk revision**, including the final Neo4j Spatial fix, successful Resolver selection, and Java null-scanner failure, not just an earlier copied report.
- `docs/contributor-guides/KLAB_Python_Client_Planning_Guide.md`
- `docs/PYTHON_CLIENT_ACCEPTANCE_MAP.md`
- Installed service contracts and existing tests as needed for exact endpoint syntax.

## Authoritative interpretation and boundaries

The latest user-reported status is `REPAIR_PARTIAL`: Neo4j Spatial is repaired, Resolver selected the authentic DEM-backed elevation model, and numerical execution failed on a confirmed Java **null-scanner defect**. Cleanup passed, services remained healthy, and A+B development resumed. Verify and cite the exact local report/receipts before presenting this as fresh live observation.

Do not equate:
- HTTP 200/`available=true` with `operational=true`;
- Reasoner accepted `imod` with model discoverability;
- model discoverability with context-specific Resolver selection;
- successful Resolver selection with Runtime numerical output;
- isolated 400/400 Java DEM coverage with a full Resources→Resolver→Runtime observation;
- Modeler IDE services (normally 8091–8094) with separate isolated Python/Java testing services (historically 38091–38094);
- a historical pass with a current pass.

This is a **local development environment**, not production. Avoid copying credentials, tokens, full private user paths in public issues, or secrets into tracked state files. Sanitize evidence. All status claims must have collection timestamp, source, and scope.

## Deliverable A — Stable state document format

Create the exact file `notes/python-roadmap-execution/MODELER_IDE_STATE.md`. Suggested sections:

1. **Purpose / how every spec uses this file**: a reference and gate, not an unconditional prerequisite for unrelated Python unit tests. Include exact update command and interpretation of stale/unavailable checks.
2. **State identity**: `last_checked_utc`, `checker_version`/commit, machine/environment identity (nonsecret), selected distribution/version/build (`202610301844` is *historical*, not hardcoded expected current), Java version, host-owned Modeler process and service IDs/endpoints. Record divergence, not assume PID permanence.
3. **Health matrix** for Reasoner, Resources, Resolver, Runtime, graphdb: reachable, authenticated status where appropriate, operational/connected/consistent values, exact source/evidence, last check time, and classification `PASS | FAIL | UNKNOWN | NOT_APPLICABLE`.
4. **Semantic/model inventory**: adopted worldview (`imod`), accepted generation, logical/consistency status, six prior nonblocking warnings if still present; number/identity of models, namespaces, resources and adapters, project/worldview relationships; exact DEM resource/model URNs from current results.
5. **Backend components**: geospatial KAR installed/registered on Resources and Runtime; GeoTIFF/raster adapter on correct service; Neo4j Spatial plugin, exact binary/version/hash where confirmed, `spatial.layers` plus required procedure result. Flag unknown provenance rather than extrapolate.
6. **Capability/acceptance ladder** with separate gates:
   - G0 services healthy;
   - G1 semantics recognized/accepted;
   - G2 authentic DEM and adapter/model/resource live;
   - G3 graphdb spatial procedures and context creation;
   - G4 Resolver selects actual DEM model;
   - G5 Runtime numerical output produced;
   - G6 400-cell scientific oracle comparison;
   - G7 Modeler IDE retrieval/display;
   - G8 Python-client end-to-end on the relevant *specified* environment.
   Historical baseline expected: G0–G4 have progressively been reached, G5 blocked by confirmed Java null-scanner, G6/G7 not passed, G8 needs separate evidence. **Revalidate** rather than simply mark G3/G4 freshly passed.
7. **Known blockers**: Java null-scanner defect with reproducible signature, source location/stack and maintainer handoff links from latest report; no speculative claims that the Python client caused it. Distinguish `ACTIVE`, `FIXED_VERIFIED`, `UNVERIFIED`, `OUT_OF_SCOPE`.
8. **Environment separation**: IDE ports/processes vs isolated CLI fixtures, local installed binaries vs repository HEAD, runtime paths vs test harness, and which tests depend on which. Explicit dependency map from A+B features to Java gates.
9. **Change history**: append-only succinct transitions and evidence refs, no rewriting yesterday's FAIL into today's PASS without a rerun.
10. **Runbook**: read-only refresh command, safe check order, how to interpret staleness, and when a deep scientific smoke test is justified.

Every claim should reference local evidence paths (and where possible exact report section/command/result). No stale session/PID hardcoding as a pass criterion. A historical build or UUID may be recorded as an observation, not a timeless requirement.

## Deliverable B — Read-only, fast, repeatable checker

Implement the smallest reproducible local checker, ideally a script in the established `notes/python-roadmap-execution/` or existing scripts tree. Choose PowerShell for Windows where it minimizes dependencies, or Python if the repository already has a suitable CLI pattern. Do not install packages.

Required checks:
- Presence/health of expected Modeler-managed services and graphdb; bounded network timeouts and no loops.
- Operational, consistency, accepted worldview generation with scoped authentication only if necessary; never print secrets.
- Minimal authenticated catalog queries for available model/resource/adapter/project identities, not broad data downloads.
- Neo4j `spatial.*` procedure discovery using the existing read-only verified method; no Bolt auth configuration changes.
- Optional `--deep` mode to perform **only nonmutating** checks of existing resource/model retrieval and archived scientific evidence; do not submit a context, Job, or mutate session state unless explicitly authorized in a separate spec.
- Compute an evidence/source timestamp; on failure output `UNKNOWN` or `FAIL` clearly and exit with useful status. Use an evidence receipt with timestamp, command/check version, selected service scope, results and error summaries. No credentials in output.

The checker should **not** launch or stop Modeler, modify config, install plugins, alter databases or submit Jobs. Include an offline/document-only mode when services are unavailable: preserve historical findings but mark live state `UNVERIFIED`.

Avoid a brittle exact-output parser. Detect discrepancies (different build, different service, missing adapter, different worldview) and report them as drift.

## Contract for *every future CLI spec*

Add a reusable small preamble (`MODELER_IDE_STATE.md` must include a copy-pasteable paragraph):

> Before work, read `notes/python-roadmap-execution/MODELER_IDE_STATE.md` and its evidence timestamps. Determine whether this task actually depends on live Java Modeler/graphdb services. If yes, run the bounded read-only state checker and record the gate(s) required. If no, continue Python-only work without requiring IDE readiness. Never infer a currently working science pipeline from service health. If an environment blocker is detected, classify it and proceed with independent A+B tasks rather than starting unsolicited IDE repair. At the end, update the state file only when new verified evidence changes a gate or a known issue.

Document freshness policy: the checker should run **at the start of any spec that depends on live IDE services**, not for every purely Python unit/documentation task. Keep last-check timestamps, and treat absent/stale checks as `UNKNOWN`, not `FAIL` or `PASS`. Do not create an hourly watchdog or background service.

## Validation and acceptance

Test the checker against: healthy current services, a simulated unreachable service (without actually stopping it), malformed/stale evidence, different build or service IDs, missing optional catalog entries, and historical failure receipts. Validate JSON and Markdown generation if applicable. Confirm it cannot change live state and never leaks credentials.

Success means:
- One human-readable `MODELER_IDE_STATE.md` accurately reports a healthy semantic/model/Resolver stack **without** claiming numerical science is working.
- A documented single command refreshes the bounded live facts and writes an attributable evidence receipt.
- All future A+B specs have a precise, nonblocking reference mechanism.
- No Java source, IDE configuration, ontologies, plugin installs, service restarts, scientific Jobs, or unrelated repositories changed.

## Stop rule

Once this durable state contract, checker, and bounded tests are complete, **stop IDE work and proceed to the outstanding-issues/A+B development audit**. Do not attempt to correct the null-scanner defect as part of this task.
