# Consequence engine audit and staged repair prompts

Audit date: **3 November 2026**. Scope: the current working tree, including the admission and
collective-input repairs that made `staging.vxii.basic.cotechino` resolve successfully. The base
commit is `f6ae0ab21`; auditing that commit alone omits the repairs. This audit changes no
production behavior and does not restart or submit work to the live stack.

Read [admission](ADMISSION.md), [consequence contracts](CONSEQUENCE_ENGINE.md), and
[existing implementation status](CONSEQUENCE_ENGINE_STATUS.md) alongside this plan. The existing
C1–C7 acceptance gaps and explicitly unsupported semantics remain separate work.

## Findings

### A1 — P1: CHANGE follow-up commits rescan and resubmit unchanged qualities

`ChangeProcessHandler.evaluate` only uses the incoming commit to check whether anything was
added or modified. It then enumerates all observations and considers every resolved concrete
quality whenever any occurrence exists. A process-only consequence commit therefore reconsiders
the original qualities. Only writer/reuse checks prevent another submission; no trigger relevance
check distinguishes an original quality change from the consequence's own result.

The live client log confirms this in context `anonymous.3h4iftxkug`: activities **2749** and
**3563** request Elevation and Slope change; **4542/4726** repeat both under 2749, and
**5277/5450** repeat both under 3563. The corresponding CHANGE decisions report the same quality
IDs **14181/14182** as applied on the parent pass and both child passes. A controlled probe also
reproduces both unchanged qualities being submitted again after a process-only follow-up commit
when no writer/reuse guard accepts them.

The observed request means `change in geography:Elevation in m optional`, rather than plain
Elevation. `optional` is the present best-effort request policy. The evidence establishes redundant
requests, but does not establish that optionality alone causes the defect or that duplicate native
quality observations were created. The selected observer's `ObserverResolution.deferred` branch,
writer coverage, and typed resolver results need tracing together before selecting the repair.

Source: `ChangeProcessHandler.java:27–86`; `ObserverResolution.java:39`.

### A2 — P2: CHANGE reports unproved results as APPLIED

The same handler counts any nonempty result with a positive ID as applied. It does not require
resolved coverage, verify that the result is the requested change process, or inspect a typed
terminal resolution outcome before choosing its decision. A controlled result with zero resolved
coverage is counted as `APPLIED`. This repeats the distinction exposed by the earlier admission
failure: successful submission/native existence is insufficient evidence of scientific resolution.

This is a separate confirmed defect from A1. The live logs also show initial CHANGE resolver
`NO_MODEL` outcomes followed by later reference-like `RESOLVED` graphs; correlate those results
before concluding which particular live submission was falsely counted. A1's repeated activity
IDs alone cannot prove A2 caused every redundant request.

Source: `ChangeProcessHandler.java:80–97`. Compare the typed outcome handling in
`DefaultRoleConsequenceRuntime.submit` and `RuntimeService.prepareConfigurationExplanation`.

### A3 — P2: recomputation receipts and activity provenance disappear on persistence

`ConfigurationRecompute.recordReceipt` stores `klab.scheduler.configurationRecomputations` as a
nested map on the observation and `klab.scheduler.configurationInputChange` as a map on the
Activity. The native encoder's `sanitizeMetadata` retains only scalar POD values. The Activity's
special audit allowlist does not include this provenance key. Both values are therefore absent
from the encoded `_metadata`, despite being present in memory.

An isolated probe calls the actual native encoder and confirms both omissions. Existing helper
tests retain the in-memory objects and do not exercise a storage/reload boundary. Idempotence
and audit evidence can consequently differ between a warm cached object and a restored one.
This does not establish a durable crash-replay contract; that remains explicitly unsupported.

Source: `ConfigurationRecompute.java:363,377`; `AbstractKnowledgeGraph.java:106,233–258,420–431`;
`Utils.Data.isPOD` in `klab.core.api`.

### A4 — P2: birth ID allocation breaks receipt keys and in-flight cleanup

Before a new configuration has a durable ID, `ConfigurationRecompute` builds its receipt key
and indexes `configurationsInFlight` with the negative ID. Its release callback later reads
`configuration.getId()` again. When commit assigns a positive ID, cleanup removes that new key
and leaves the old negative-key entry holding the transaction. The stored receipt key and
input-change provenance also retain the original negative identity.

The probe runs the real helper, changes the birth ID from `-7` to `700` before the commit hooks,
and confirms both the orphaned `-7` in-flight entry and the old receipt key. Repeating the same
initial trigger/revision then returns `DEFERRED`, rather than `ALREADY_EXECUTED`. This is the
same before/after ID-allocation boundary recently repaired for collective producer bindings.

Source: `ConfigurationRecompute.java:233,352–377`. The same problem is not present in the
constant `inputChangesInFlight` cleanup key; distinguish the two registries when repairing it.

### A5 — P2: nested consequence scope creation loses handler cancellation

`ConsequenceEngine.evaluate` attaches engine-lifetime cancellation to its handler scope.
`GuardedConfigurationPublisher.publishBatch` and the role-conferral path subsequently call
`forConsequence` again. That method clears the entire cancellation supplier, including the newly
attached handler token. A probe using real `ServiceContextScope` objects confirms that the handler
becomes interrupted while the nested scope remains live.

The normal twin shutdown also closes `ResolutionCycles`, which supplies another cancellation
layer. That mitigates some shutdown paths; the probe does not establish graph disposal during
active work. The confirmed defect is the lost lifetime signal, especially for direct engine close
or future nested work. Detaching a completed user request is legitimate; detaching the active
handler's lifetime requires a different contract.

Source: `ServiceContextScope.java:141–154,337–347`; `ConsequenceEngine.java:228`;
`GuardedConfigurationPublisher.java:158`; `DefaultRoleConsequenceRuntime.java:701`.

## Verification boundary

- Existing targeted Maven run: **97 tests, 89 passed, 8 failed, no errors or skips**. All eight
  failures are in `PreparedConfigurationRecomputeTest`. Keep that failure visible.
- That fixture supplies lifecycle evidence only for the configuration; prerequisite observations
  receive Mockito's default `false` from the new currentness gate. In an isolated shadow copy,
  explicitly recognizing the fixture's durable inputs and exact transaction-owned staged inputs
  makes **20/20** prepared-configuration controls pass. Repository test sources were not changed.
  This isolates a fixture defect; it is not a passing result for the original Maven run.
- Isolated assertion probes confirm A1's handler path, A2's acceptance of zero coverage, A3's
  encoder omissions, A4's ID/cleanup mismatch, and A5's lost cancellation token. They use test
  collaborators; they do not prove full service-side configuration or cessation acceptance.
- Live evidence is read-only existing-log corroboration of the CHANGE repetition. No new live
  consequence, process, configuration, or cessation was initiated for this audit.

Local audit artifacts are in `review-evidence/consequence-audit/`: `ChangeAuditProbe.java`,
`ConsequenceAuditProbe.java`, their result files, `change-live-evidence.txt`, and
`existing-suite-results.log`. The directory is ignored by Git. This document retains the findings
and reproduction requirements independently; promote desired reproductions into normal tests
during Stage 0 rather than relying on ignored artifacts in a future checkout.

## Repair sequence

Implement the following as separate reviewable changes. Do not fix unrelated C1–C7 semantics,
restart a general Reasoner optimization effort, or weaken admission to make tests pass. Each stage
ends with tests of the production path and an update to this document's evidence, not just helper
assertions. Preserve the working cotechino admission and dependency-only input-plan fixes.

### Stage 0 — establish a trustworthy baseline

**Prompt**

> Read docs/CONSEQUENCE_ENGINE_ADMISSION_AUDIT.md and preserve the existing working-tree repairs.
> Repair PreparedConfigurationRecomputeTest's lifecycle setup: explicit durable fixture inputs may
> be current; a negative input must be the exact staged object owned by the active transaction.
> Retain unowned, failed, foreign, reference-only and wrong-perspective rejection controls. Replace
> broad mock shortcuts with a real native-transaction control where ownership is being tested.
> Promote the audit reproductions into focused regression tests, using real encoding and commit
> callbacks for A3/A4. Do not change production currentness rules. Run the original 97-test group
> and the previous admission/collective-input suite; record exact outcomes and remaining failures.

**Exit gate:** original prepared-configuration controls pass with explicit evidence; each planned
production repair has an independently failing regression. Keep tests scoped to their contracts.

### Stage 1 — stop redundant CHANGE discovery without suppressing legitimate change

**Prompt**

> Reproduce A1 using one grid-time bearer, Elevation and Slope, and the actual retained semantic
> builder. Correlate source commit, decision, requested operator/URN, result ID, coverage, typed
> outcome and writer/reuse rejection reason for the six logged requests. Determine why the first
> successful or NO_MODEL result fails to suppress its own process-only follow-up. Preserve optional
> best-effort behavior and observer-specific discovery. Bound candidate selection by relevant
> quality/occurrence changes, while retaining the necessary first-occurrence scan of old qualities.
> Use existing resolution/cycle ownership and reuse evidence; do not add a permanent negative cache,
> blanket-disable CHANGE, or suppress every process-origin commit. Test unchanged follow-up commits,
> a newly introduced quality, changed support, a new relevant occurrence, a joint writer for two
> qualities, observer-specific versus absolute results, and cycle drain.

**Exit gate:** unchanged qualities produce no repeated work from their own follow-up commits;
new relevant support or semantics can still trigger valid discovery. The actual request remains
a CHANGE process rather than an optional copy of the original quality.

### Stage 2 — make CHANGE decisions reflect actual resolution

**Prompt**

> Fix A2 in ChangeProcessHandler after Stage 1's result trace identifies the real contract.
> Evaluate the typed terminal outcome, result meaning, lifecycle/access, and actual support before
> declaring a change obligation fulfilled. Distinguish a genuinely complete reusable change/writer
> from native existence, a partial result, zero coverage, typed NO_MODEL, DEFERRED and failure.
> Keep ordinary optional absence nonfatal. Do not treat an absent executor or an empty generic
> response as a NO_MODEL certificate, and do not infer successful scientific execution from a
> positive ID. Add controls for wrong-result semantics and reused complete results. Keep decision
> observationIds' meaning explicit and compatible with the client. Rerun Stage 1's loop tests.

**Exit gate:** zero-coverage or unrelated results never become APPLIED; unchanged successful
reuse and genuine optional NO_MODEL terminate without redundant semantic submissions.

### Stage 3 — make recomputation evidence survive persistence and ID allocation

**Prompt**

> Repair A3 and A4 together in ConfigurationRecompute. Choose an explicit versioned persistence
> representation for recomputation receipts and input-change provenance that the actual native
> encoder retains. Define bounded legacy decoding for the current in-memory map form. Rebind new
> configuration identities, receipt keys and provenance after durable allocation, before native
> updates are encoded. Capture stable registration keys for cleanup; do not remove a transient-key
> entry using a later ID. Verify commit and rollback leave both in-flight registries empty. Preserve
> source/observer/cycle/native read-set authority: receipt metadata alone must not authorize execution
> or skip a required admission. Test a real birth through allocation, encoding, reload, duplicate
> trigger/revision lookup, changed revision, rollback and warm-cache versus restored behavior.

**Exit gate:** durable receipts and provenance round-trip with the durable identity; no old
negative-key registry entry retains a transaction; duplicate detection has a documented authority
boundary. This stage does not introduce durable crash replay.

### Stage 4 — preserve cancellation through nested consequence work

**Prompt**

> Fix A5 by distinguishing detachment of the completed user's request from derivation inside an
> already admitted handler. Preserve handler/engine and cycle-lifetime cancellation in nested
> configuration publication, role attribution, and membership-derived work. Audit every
> forConsequence call site rather than changing its reset behavior globally. A cancelled completed
> user request must not retroactively cancel valid post-commit consequences. Test engine close
> before nested transaction admission, cancellation during preparation, and draining of an already
> executing atomic operation. Retain bounded shutdown and cycle lease ownership; do not dispose
> storage/graph before active work drains or use forced interruption of synchronous Bolt I/O as a
> completion certificate.

**Exit gate:** nested scopes observe their handler's cancellation, new work cannot start after
close, and valid already-admitted work drains without leaking its cycle or storage ownership.

### Stage 5 — bounded live acceptance and documentation

**Prompt**

> Validate Stages 0–4 in the existing anonymous single-service-per-type configuration, using the
> established owner/client contracts. Preserve the live projects, worldview and exact service
> identities; do not launch competing services or change authentication. First rerun cotechino and
> confirm model discovery, dependency initialization and successful coverage. Correlate CHANGE
> parent/child decisions until cycle quiescence and prove the next explicit root can proceed. Then
> use a separately identified owned fixture to verify one configuration birth, same-ID input-change
> recomputation, durable receipt reload and rollback. Record source hashes, deployed versions,
> actual model execution and immutable earlier history. Obtain any deployment authority still
> required before changing the live stack. Update ADMISSION.md and consequence status with precise
> evidence; passing controlled tests does not establish the untouched cessation/network paths.

**Exit gate:** finite relevant CHANGE activity, correct decisions, complete cycle drain, preserved
cotechino resolution, durable configuration evidence, and an explicit list of unverified branches.

## Invariants for every stage

- Separate planning ownership, operative lifecycle, input-plan association, and scientific execution.
- Keep exact transaction, context, user, observer, semantic-cycle and source/read-set checks.
- Preserve temporary planning identities until allocation, then bind durable evidence deliberately.
- Dependency-only consumers retain input plans without acquiring fictitious executable operations.
- Preserve immutable history and atomic commit/rollback. Do not turn a durable result into failure
  because later diagnostics or delivery fail.
- Make no broader termination, replay, named-event, multi-root explanation or negative-emergence
  promise than the existing consequence contract.
