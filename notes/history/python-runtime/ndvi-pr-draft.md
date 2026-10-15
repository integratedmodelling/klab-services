# Local PR preparation notes

This file is outside `klab-services` and is not part of the proposed contribution.

## Observed project norms

Reviewed recent PR metadata and the human review discussion on 2026-09-28:

- [#79](https://github.com/integratedmodelling/klab-services/pull/79): focused build cleanup,
  descriptive title and short explanation of motivation; targets `develop`.
- [#70](https://github.com/integratedmodelling/klab-services/pull/70) and
  [#71](https://github.com/integratedmodelling/klab-services/pull/71): dependency update and
  follow-up cleanup; human feedback favored established dependency-management patterns.
  #71 received human approval. Both target `develop`.
- Recent maintainer feature/hotfix PRs also target `develop`; naming and body detail vary.
- No CONTRIBUTING file or PR template was found in the local checkout. The following
  structure is a reviewability recommendation, not an asserted mandatory template.

## Proposed title

Tests: add bounded Python NDVI computation and storage lifecycle evidence

## Proposed PR body

### Motivation

Establish a small, reproducible Java-to-Python scientific-computation test loop and
exercise it with an environmental calculation using k.LAB storage scanners. Extend
the evidence toward runtime integration with bounded batches, cancellation and a
concrete investigation of output publication semantics.

### Changes

- Add a test-scoped, bounded subprocess helper using strict JSON and a configured Python interpreter.
- Cover successful computation, missing values, diagnostics, malformed output, pipe flooding
  and timeout cleanup with real subprocess tests.
- Add an NDVI worker and a synthetic six-cell storage scenario with verified readback,
  shifted-grid rejection and invalid-input checks.
- Add an isolated implementation of the existing ScalarComputation contract, using
  named red/NIR inputs, bounded 1–128-cell batches and a partition-wide deadline.
- Reuse the worker/runner for larger fixtures; test scope cancellation and worker cleanup,
  compare stored values with Java, and record preliminary elapsed-time/heap measurements.
- Reproduce the native initialization rollback gap: earlier batch writes remain visible
  when a later batch fails, even without finalizeRun. Document production integration
  decisions with source references rather than claiming atomic publication.
- Document setup, scientific assumptions, expected results and rerun commands; index the
  scenario in the existing testing guide.
- Update one existing storage-test mock for upstream's scope-aware function lookup;
  retain all assertions and production behavior.

### Design fit

Uses the existing JUnit/Mockito storage-fixture pattern in `klab.core.services/src/test`.
Python is required only when the three integration classes are explicitly selected. Scientific
function tests are independent pytest checks beside the test worker resources.

This demonstrates the storage/process boundary; it does not register a production function,
add a k.IM model, or create a durable observation. A k.Actors acceptance testcase follows
once a registered computation can be submitted through the normal context workflow.
The runtime currently selects the Groovy scalar builder. Its normal contextualizer
initialization path opens native writers; the transactional temporal write set excludes
initialization. A supported Python selection mechanism and observation-wide atomic output
lifecycle remain prerequisites for the proposed production expansion. This PR supplies
the isolated prototype and evidence, not completed Runtime integration.

### Verification

On September 30, Windows 11, JDK 21.0.12.1+1, Maven wrapper 3.9.5, Python 3.12.7 and pytest 8.3.5:

- 47 Python checks passed.
- 6 Java subprocess and 3 NDVI storage checks passed.
- 5 new bounded-scalar checks passed, including six size/batch measurement combinations.
- 34 selected existing storage regressions passed. Python-unset regression execution
  was verified in the original September 28 session.
- All three explicitly selected integration classes failed setup with actionable instructions
  when Python was unset (expected negative check; no silent skips).

95 unique positive checks passed. Exact current commands, source findings and measurements
are in `docs/PYTHON_RUNTIME_PROTOTYPE.md`; original results are preserved in
`docs/PYTHON_BRIDGE_RESULTS.md`.
Full repository tests, Unix execution and live service execution were not run.

For 4,097 cells, 64-cell batches launched 65 processes and took 2,822 ms; 128-cell batches
launched 33 and took 1,451 ms in one local run. Process timing combines startup, transfer,
computation and cleanup. Java heap snapshots are not peak memory; Python memory was not
measured. These measurements establish neither Java speedup nor country-scale readiness.

Verified against local HEAD `75bf1f7d29c96ec86789d8e1a0135b0b44d0c8ef`, fetched previously;
remote refs were not refreshed on September 30.
The eight upstream commits fast-forwarded cleanly and preserved all 37 local files.
The first clean build exposed a stale mock in `StorageConsumerIntegrationTest` after
upstream switched function lookup to a scope-aware overload. A one-line mock correction
restored the regression suite; production code and assertions are unchanged.

## Submission preparation still required

1. Upstream integration and verification are complete at `75bf1f7d2`. If `develop` moves
   again before submission, review the new changes and rerun affected checks.
2. Stage only `.gitignore`, `docs/TESTING.md`, the four `docs/PYTHON_*.md` files,
   the six new computation Java files, and the nine source/requirements files under
   `src/test/resources/python-bridge`, plus the one-line `StorageConsumerIntegrationTest.java`
   mock correction. Review the staged diff before committing; identify that compatibility
   fix explicitly in the PR (or a separate focused commit if desired).
3. Keep the existing 19 tracked IDE metadata modifications (`.classpath`/`.settings`) out
   of this contribution; they were present before this review and have been preserved.
4. Use a focused contribution branch (e.g. `feature/python-ndvi-tests`) targeting `develop`.
   The current local branch is `python-bridge-baseline`; renaming is optional, not a rule.
5. Request maintainer review of the test-only scope and prospective computation integration
   boundary. Existing PR examples do not establish a required approval count.
6. Resolve supported Python computation selection and atomic initialization output before
   describing the expanded contribution as a production Runtime integration. The existing
   plan's integrated milestones are not yet complete; see the new decision record.

No commit, push or PR creation was performed in this preparation session.
