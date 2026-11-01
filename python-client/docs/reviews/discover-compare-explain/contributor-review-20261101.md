# Contribution review: PR102 scientific integration and delivery

## Recommendation

**Scope update, 1 November 2026:** the user explicitly authorized reducing PR #102
to workflow/acceptance tooling integration while deferring scientific completion.
The recommendation and findings below describe the original full-milestone review
and are preserved as history. They are not a claim that the original gates passed.
See the revised review at `tooling-scope-review-20261101.md` and the scope decision
in `../../discover-compare-explain-delivery.md` for the current merge boundary.

**Needs fixes and live validation.** The accepted-base candidate has useful
workflow infrastructure and passing offline checks, but the deployment gate has
no verification implementation and the original combined scientific journey has
not passed. These are remaining development/acceptance tasks, not demonstrated
external blockers. The user's explicit instruction authorizes fixes, bounded live
testing, publication and conditional merge after acceptance. It supersedes the
review guide's example read-only invocation, but not its evidence standards.

## Reviewed state

- Repository: `integratedmodelling/klab-services`; isolated clean starting worktree
  `pr102-final-acceptance-20261101`.
- Integration target: unchanged `feature/python-experiment-runner`, base
  `b130b33ab97aabc1dded0d99a7103810159482c9`.
- Starting candidate: `cd09ab1da1580b7e3c39987b9d3b1e26d1ac60b3`, open draft.
- Accepted #98/#100 corrections are integrated by normal history-preserving merge.
- Unpublished planning/evidence changes in the separate closeout worktree are not
  included or overwritten. No retarget to develop or unrelated Java source changes.
- Method: `docs/contributor-guides/KLAB_CLI_Contribution_Review.md`; scope remains
  the discover/compare/explain milestone. The research-driven roadmap is primary;
  older plans and all49 catalog entries remain preserved. PaL is not added here.

## Capability and boundaries

A scientist should discover an identity/model, compute two supported variations
at two resolutions, verify real values and metadata against an independent
controlled reference, export/analyze/explain results, reopen original identities,
execute the notebook and measure correctness-first throughput. Existing services
remain authoritative. No invented semantics, Python replacement exports, plan
replay, private API shims or scientific algorithm edits.

## Findings, ordered by consequence

1. **Blocking delivery defect, source-inspected:**
   `tools/run_milestone_acceptance.py` always emits `deployment_verified=false`;
   `tools/build_milestone_deliverable.py::build` correctly rejects that result.
   Even a complete passing test run cannot produce the promised deliverable.
   Smallest correction: verify a reviewed explicitly local launch's actual
   process/executable/argument/classpath/input identities before and after the run,
   preserve a sanitized hash-bound report, and require it in the builder. Do not
   add a trust-me switch or weaken the builder. Remote attestation remains out of scope.
2. **Required validation not run:** the complete independent matrix, native export,
   restoration, permission, notebook and throughput journey. Missing references
   must first be treated as fixture-development work. The already-published
   controlled constant/scenario fixture is a candidate to inspect, not assumed
   scientific correctness or a reason to relabel fractal smoke outputs as an oracle.
3. **Fixed before this pass:** private metadata restoration could be hidden by
   later evidence/export denial. cd09 requires restoration denial itself; the
   regression and positive-denial/invalid-credential controls pass offline. Live
   milestone verification still must establish the strengthened behavior.

An older PR100 untraced processing ProtocolError remains preserved and unexplained.
Passing diagnostics do not establish its cause or universal reliability. Further
observations relevant to this candidate must be diagnosed before fresh attempts.

## Validation actually performed

| Check | Revision/environment | Status | Establishes/limits |
| --- | --- | --- | --- |
| Git status, HEAD, PR/ref inspection | cd09/base b130, Windows | Passed | Clean starting candidate; draft, original target |
| Unit/integration, workflow coverage, lint, packaging | cd09, recorded Python3.12 | Passed previously:1,643 tests | Engineering behavior, not live scientific accuracy |
| Hosted Python3.11/12/13, core-only, Java/Jenkins | cd09 | Passed previously | CI, not scientific acceptance |
| Current live full milestone | revised candidate | Not run | Pending fixture/verification development, not waived |

Commands, candidate bindings and new outcomes will be recorded as implementation
and acceptance proceed; older evidence is not reused for changed executable code.

## Scientific and lifecycle evidence

Select an existing unchanged controlled fixture with independently calculable
expectations. Validate its exact source, grid/units/mask/precision and supported
variation before remote computation. Use isolated homes/ports and signed ordinary
users. Bound mutations, retain handles/intents before dispatch, never resend
ambiguous writes, and release only known-owned scopes after results are confirmed.
Fresh-process recovery and the notebook must use original authoritative results,
not recomputation presented as restoration. Process disappearance is not object release.

## Contributor coordination

No assignment of maintainers/reviewers is inferred. Follow existing Java APIs and
fixed accepted artifacts; if a contract fails, retain exact reproduction and
classify source defect versus harness issue before proposing changes. Maintainer
acceptance is not implied by this agent's conditional merge authority.

## Smallest next actions

1. Implement and regression-test fail-closed owned-local provenance verification.
2. Inspect/pin the existing controlled fixture and derive independent references.
3. Run a bounded vertical slice, then the original full acceptance and throughput.
4. Re-review the exact final candidate and merge normally only when required gates pass.

## Development checkpoint (not live acceptance)

The local verifier and adapter/builder integration are implemented.106 focused
tests pass; the complete local workflow passes1,678 tests and the unchanged
coverage gates, including unexcluded100% lines/branches for the new module. Fatal
lint and wheel/source-distribution builds pass. Tests exercise changed byte inputs,
unlisted classes/plugins/projects, process-birth drift, JDK mismatch, service-ID
mismatch, malformed/oversized responses, before/after deployment drift and omission
of a verified report. No trust-me switch was added. Cross-platform tests exercise
both subprocess-option branches without launching synthetic executables.

The existing published fixture project's three model/scenario sources were
recovered verbatim from cd09's `tools/fixture_hub.py`. Independent exact arithmetic
for its constant `100 + 23.25` field supplies candidate metre/millimetre references
at two grids and larger grids. The accepted Java semantic pipeline passes26
ontologies,2 strategies and3 namespaces with those unchanged files. This is
adaptation evidence only. A constant field does not establish real-terrain accuracy
or the nonuniform/missing-cell behavior of the older separate fixture; those
limitations must not disappear from acceptance review.

The next finite vertical slice is limited to1 session,2 contexts,4 observation
Jobs, two native exports and20 cells per numerical result, with recorded model/
activity association and same-principal original-result restoration. Fresh-process
restoration, four-case execution, strengthened permissions, notebook and throughput
remain subsequent acceptance work. No new live Jobs have run at this checkpoint.

## Suggested PR description

Integrates scientific discovery, computation, evidence, native export/analysis,
original-result restoration, notebook execution and measured throughput using
existing Python/Java contracts. Current status: development and validation in
progress; no full milestone acceptance or merge claimed. Final description must
cite actual final-candidate results and all residual limitations.
