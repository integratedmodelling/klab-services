# Whole-tree type correction and fresh installed regression

## First final-head CI failure and gate-preserving correction

PR312 first finalhead6f4b22ae9070ef85d6a07c277c7fee3f7824133d reports47checks,
21FAIL. Candidate-source run42425570607 shows global changed-SDK line/branch checks
fed a narrow feature-only coverage report; offline run42425570586 separately finds
one truly uncovered new missing-delivery guard atagent_messaging.py321. Rollup and
failed logs retained by exact runIDs; local installed/type passes are not CI acceptance.

Corrective pass adds explicit mocked missing-delivery/noack/DISCONNECTED/unconfirmed
cleanup control. All21SDK feature workflows extend existing coverage with full SDK
unit contracts before the unchanged repository-wide diff gate. Selection workflow
append precedes simulator coverage database reset. No exclusion, checker scope
reduction, threshold relaxation or silent merge bypass. New workflow-input and
existing ordering controls plus delivery negative control3PASS. First local correction
run5269PASS2FAIL retained (test cleanup expectation and append ordering), both corrected;
fresh final local full SDK/coverage gate reproduction running. No production bytes
changed from the already rebuilt candidate; added tests require separate evidence.

Fresh original5270SDK/538reference tracesPASS/455observed51abstractuncalled; reviewed
private supplement links219mapped287missing with exact executed test hashes. Addition
of new test files does not fabricate their invocation evidence or invalidate unchanged
executed old-test bytes. Broader mapping delivery and finalheadCI remain pending.

PR312 draft, code1e740c34b on inherited packaging57c9b3dd7 and verified309–311 merges.
Final-head CI/merge and exact merged-candidate acceptance remain pending.42original
requirementIDs/history, three runnable/zero final dispositions preserved.

Whole-tree mypy checks114SDK/worker/separate-reference files under Windows and Linux
targets with no issues and no added type ignores; fatal lint passes. Retained diagnostic
history144→105→79→44→12→1→PASS prevents earlier focused types from being misreported
as a whole-tree pass. External missing-stub tolerance remains explicit; not strict
typing of all third-party libraries or a scientific/runtime acceptance claim.

Changes describe heterogeneous validated wire state and result collections, separate
reused callable names, narrow optional tokens/resources and declare transport error
metadata without creating fields on unrelated errors. Generic Job results distinguish
sync values from native async coroutines. Shared duck-receiver pure validation and
async release dispatch remain runtime-compatible; tested existing release routes.
New guards reject missing broker connection/channel, notification iterator/channel,
import binding and closed Store; anonymous async credentials keep text redaction data.
Context rejects user-only token; async Job rejects sync callback before I/O.

Focused243SDK/source538referencePASS. First broad source SDK run5264PASS/6FAIL retained:
new test incorrectly used nonexistent private field, corrected to public cancellation
state; three reliability tests require installed noneditable SDK, two notebook exports
needed existing Jupyter template path. Initial callback test also omitted required scope.
These were not product passes or waived guards. Fresh installed run below establishes
the requested installed/setup boundary independently.

Fresh source/wheel/sdist/installed parity71SDK/6worker/37reference modules and13reference
assetsPASS; no simulator/instrumentation in production SDK/worker. Full installed
5270SDK/38tooling/116worker/538reference/39governancePASS, zero failures/errors/skips.
Package candidate1e740c34b and raw/test/helper/runner/example bytes pinned, dependencies
borrowed from existing interpreter rather than fresh dependency isolation. Coverage
totals retained verbatim in JSON receipt, not claimed100percent or native coverage.

Fresh current-test-byte SDK/reference traces running; prior60mapped446missing is
historical, not automatically valid after changed source/test bytes. Prepared reviewed
supplement is not counted until exact trace linkage; other threads/processes/kernels
remain unmeasured. Body presence and hashes do not prove wholefunction correctness.
Real Java science, current rights/issuer/deployment/scale, OS/container hostile-code
enforcement and public release approval remain separate. No fallback/IDEOSrepair,
develop promotion, issue closure or final original-row acceptance inferred.
