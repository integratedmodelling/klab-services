# Existing work: wrap-up and remaining decisions

## Latest user authorization

The user subsequently requested finishing **#98, then #100, then #102**, reviewing
and testing each and merging only if its required acceptance passes. This replaces
the earlier no-merge instruction for #102, retained below as history. The user then
explicitly approved ongoing live tests until these PRs are finished, superseding
per-attempt approval requests. Runs remain bounded and failures must be diagnosed;
ambiguous writes are not resent and no failed criterion is waived. This covers
required PR acceptance, not unrelated scientific/research expansion.
After the PR sequence, final Technical-page update/verification and owned-process
cleanup are complete, the user requests putting the computer to sleep. Do not sleep
while work is blocked, awaiting approval, or tests/services are active.

## Current status — 2026-11-01

### Superseding disposition: PR98 and PR100 merged; PR102 remains draft

PR100 merged normally at `4af05e33a52cb666c4f73443707144f355ed523f`,
accepting `b130b33ab…`. Exact tree `0a8f999a…` and both parents verified;
no bypass, retarget, force-push or source-branch deletion. Original three-case
acceptance passed together:3 tests,0 skips,10 contexts/20 Jobs;9 successes and
the expected isolated local processing failure. Every result/Job handle and scope
release verified.13 original JVM identities absent, helpers0, original listeners
unchanged. The older untraced processor ProtocolError remains an unresolved,
preserved residual, not claimed fixed by passing diagnostics/current acceptance.

PR102 is under review in a separate clean worktree
`pr102-final-acceptance-20261101`, preserving unpublished closeout changes here.
Normal merge `510b77050` incorporates accepted PR100. Published head `cd09ab1da…`
includes the reproduced permission-test correction requiring restoration denial
independently of evidence/export denial.1,643 offline tests, exact workflow coverage
gates, fatal lint and fresh wheel/sdist builds pass. Worktree clean after build.
Fresh hosted Python3.11/3.12/3.13, core-only, Java and Jenkins checks all pass
at cd09ab1da; this does not supply the missing scientific/live acceptance.
Live scientific/export/permissions/notebook/throughput acceptance is still
missing. Required independent four-case and larger-grid reference fixtures have
not been identified; synthetic tests and fractal smoke results are not oracles.
The adapter also always emits deployment_verified=false and requires a real
attestation integration, not manual relabeling. Ongoing test authority exists;
missing evidence is the blocker. No PR102 observation Jobs launched this pass.

Final Technical deployment and computer sleep remain pending. Do not merge PR102,
claim scientific acceptance, or sleep while required work is blocked.

**#98 merged normally** at `0ea89ded267fe047224366570edd231c6567ff5a`.
Accepted head `e5c4bb6fd…`; merge tree `2820ad1f…` exactly matches that candidate
and both parents are preserved. Fresh checks/rules/ref guards passed without bypass.
Acceptance9 passed both shipped two-user evidence tests, zero skips, four Jobs/two
contexts/20 cells per result; object releases and13-JVM/helper/listener cleanup
verified. Earlier failed/UNKNOWN evidence is preserved, not retrospectively passed.

**#100 is now active**, draft at `b130b33ab…` after normal #98 integration and
two regression-tested manifest-parser boundary corrections.1,515 offline tests,
coverage gates, lint and wheel/sdist builds pass. The first isolated attempt failed
before any contexts/observation Jobs on an optional, overconstrained Runtime
worldview profile setting;13-JVM cleanup verified. The corrected second attempt
was bounded to10 contexts/20 Jobs and the three shipped sequential,
parallel and sibling-processor-failure cases. That second attempt
passed sequential but failed parallel on one processing ProtocolError; all16 Jobs
returned and all eight created scopes have confirmed releases.13 original JVM
identities are absent; a reused unrelated Firefox PID was not touched. A separate
parallel-only diagnostic passed four contexts/eight Jobs with releases and cleanup,
without reproducing the failure. A warm-order diagnostic also passed eight contexts/
sixteen Jobs with releases and cleanup. A copy-only failed-store audit returned no
matching activity rows; original file hashes unchanged, zero observations, two
audit JVMs gone. It is inconclusive. The read-stress worker passed four contexts/
eight Jobs/32 evidence refreshes; its parent conservatively failed on PID reuse,
with subsequent birth-time cleanup verification. The controlled lifecycle diagnostic
also passed four contexts/eight Jobs/67 read-only refreshes with releases and cleanup.
The original full three-case suite now runs together (10contexts/20Jobs) with the
diagnostic waits/repeated reads disabled and unchanged assertions. No root-cause fix
or full acceptance is yet claimed. #102 follows
only after #100 passes.

The older pass narrative below is retained chronology where it differs from these
latest dispositions. Final Technical-page deployment and sleep remain pending.

**#90 merged** at `d86f9927d32aba983060760fdc9953feaf532631`, accepting
`a0a013a188fb752907f2d143a61b86f4a9cb0a0b`. Its merge tree matches the validated
candidate. Final evidence includes 321 selected Java tests, all eighteen deployment
packaging modules, fresh Java/Jenkins CI, thirty-three offline safety checks and
exact-head native metadata/identity/ACL/lifecycle plus credential controls.
Independent result-aware review gives bounded ACCEPT; cleanup is verified.

#98 is next, then #100, then #102. Each still needs its own reviewed, working
acceptance; their historical failures are not erased by #90. Required live tests
are now approved on an ongoing basis, with bounded workloads and no blind retry.
The final Technical-page update remains queued until the requested sequence is
finished. Broader scientific/PaL acceptance remains incomplete.

**#98 remains draft at `e5c4bb6fd…`.** The reviewed correction passes1,467
offline tests and fresh hosted checks. Isolated readiness-5 now passes both
ordinary-user semantic/model checks with semantic search ready and six confirmed
component imports. Zero contexts/observation Jobs ran; all13 recorded JVMs are
absent, private helpers are clear, original listener mappings unchanged.

This required correcting launch dependency resolution and selecting an unchanged
compatible published worldview revision, not changing ontology/scientific code.
Earlier readiness1–4 failures remain preserved, including unknown UN import outcome
and the readiness4 harness DTO failure. Acceptance-6 failed locally after one
confirmed region result; its elevation request was not dispatched and object release
was not confirmed. All13 JVMs are absent and original listeners unchanged.
Acceptance-7 computed four Jobs and both20-cell results, then failed during pytest
plugin loading before assertions; confirmed objects were released and13 JVMs absent.
Acceptance-8 computed four Jobs then failed an incorrect fixture contextualizer
expectation; the pinned source establishes the correct call, with no model edit.
Known objects were released and13 JVMs absent. Acceptance-9 is running with the
same two-context/four-Job/20-cell limits and shipped two-user tests. Fifty-five
offline safety tests plus exact two-test collection under
the patched worker pass; this
does not establish scientific acceptance or authorize merging before live results.

See [final #90 disposition](pr-90-finalization-pass.md) and
[preserved attempt history](pr-90-native-attempts.md) for exact scope and evidence.
The following sections are retained historical snapshots, not current PR states.

## Historical #90 correction snapshot

Latest work continues in the [finalization pass](pr-90-finalization-pass.md):
the user conditionally authorizes merging #90, then #98, then #100 only after
passing review/acceptance. The previously failing negative fixture is corrected
without dropping validation assertions; full build/test revalidation is pending.
The figures below describe the earlier completed origin-correction pass.

The user redirected work to #90 and authorized local Java fixes. The cached
wrong-Runtime return is now corrected in a separate local worktree. All **73
focused origin/identity/permissions tests pass**, including ten new regressions;
the unchanged target's corresponding 40 tests pass. Independent review confirmed
the bounded correction with no demonstrated new blocking defect.

The broader comparison still fails the same worldview test on both revisions
(candidate: 251 passed/1 failed; target: 191 passed/1 failed). Real two-user/native
acceptance remains unrun. Nothing was published or merged; the other PR work
remains paused. See [the local fix report](pr-90-fix-pass.md) for exact source,
dependency and result evidence. The original handoff below is historical context,
not a declaration that #90 or the outstanding stack is accepted.

## Historical bottom line — initial audit

**The roadmap handover and local review/checking pass are complete. The PRs are
not accepted or closed.** This is the conditional handoff allowed by the closeout
spec, not `CLOSEOUT_ACCEPTED`. All four PRs remain open drafts, with disposition
`BLOCKED_EXTERNAL_DEPENDENCY`. No merge, supersession or independent approval is
inferred from passing CI.

## What was finished in this pass

- Installed Eric's new research-driven roadmap beside the CLI contribution review;
  source bytes and all 49 research/capability/project entries are preserved.
  Installed the companion closeout spec; older plans/history remain available.
- Read both in full, plus the full PaL spec and the required existing plans,
  contracts and applicable reviews. Updated planning links/precedence.
- Rechecked every open PR, exact source/target identities, reported checks,
  review/comments, dependencies and upstream drift. Prepared
  [the ledger](pr-ledger.json), [baseline](closeout-baseline.md), five read-only
  assessments and [individual statuses for all twelve required cases](gate-matrix.json).
- Classified all 34 existing functional selectors by evidence level, without
  deleting tests, counting aliases as independent experiments or claiming a
  published scientific replication.
- Re-ran the existing offline suite: **1,599 passed**. Existing coverage/lint gates,
  wheel/sdist builds and fresh installed-wheel/core-only checks passed. This is
  engineering evidence, not a new live scientific run.
- Public GET-only inspection confirmed existing local/hosted services report
  operational. No service or scientific state was changed.

## Historical initial PR gaps

| PR | Plain-language gap | Next necessary evidence |
| --- | --- | --- |
| #90 | We have not accepted the complete Java identity/security combination | Agree the exact combination or maintainer-approved supersession; strict/native and real two-user tests; independent review |
| #98 | A second valid user could not open shared results in the prior test | Correct and verify both users' authenticated service setup; shared success and private denial on the exact candidate |
| #100 | The live multi-run test timed out three times and one context request failed | Bounded diagnosis, independently known expected values, successful sequential/parallel and sibling-failure checks, recovery/cleanup |
| #102 | The complete scientific notebook journey has not been demonstrated | Resolve #98/#100, verify the deployment, supply independent 2x2 expectations and native numerical exports, run all 12 cases/notebook/recovery/permissions/throughput |

The latest source still includes the recorded #100 failure unchanged; it has not
been repaired by extending a timeout or pretending later FINISHED states verify
numbers. Earlier successful small-area/unit checks remain valid historical
results, not a passing full milestone on a new candidate.

## What scientists can rely on—and what they cannot

The client has useful, separately tested connection, discovery, model-inspection,
small-request, saved-reference and recorded-explanation tools. Historical controlled
Java checks produced a finite 20-cell model-associated field. That is not proof
of measured environmental accuracy, full collaborator access, reliable multi-run
science or a completed release. The public evidence videos replay reports rather
than run new scientific experiments.

No production SDK fix is justified by this audit alone. Remaining fixture/authority,
waiting-delay and numerical-reference questions need evidence first, not a speculative
Python workaround or an unreviewed Java/scientific change. The five assessment files
are the implementing agent's read-only reviews; **the required independent review
has not been completed or represented as completed**.

## Decisions and authorization needed to continue

1. Select the exact Java/Python integration candidate. Four newer develop commits
   exist; neither calculated merge tree has been accepted or tested as a combined
   live deployment. Decide #90's remaining role without silently superseding it.
2. Provide/approve an isolated two-user fixture: both users' compatible service
   advertisements and authenticated peers, accepted semantic knowledge, model and
   numerical reference. Specify any allowed local setup/import/restart/restore.
3. Approve separate bounded live batches after readiness. The old 20-Job/200-cell
   runner ceiling is not renewed by the spec. No more submissions or deletions
   were performed in this pass. Do not rerun unknown mutations or silently use a
   different fixture after failure.
4. Establish actual running source/binary/worldview/Hub/component attribution and
   native numerical export availability. Both Runtime snapshots advertise only
   `component.jar`; a downloaded component elsewhere is not proof of deployment.
5. Obtain an independent reviewer, resolve findings outside the read-only phase,
   retest and prepare PR notes. Publishing/changing/merging/closing PRs still needs
   explicit authorization. The separately requested portfolio wording update is
   permitted; it must not declare these engineering/scientific gates complete.

## Next research step: pasture and livestock in Spain

Read the supplied `03_PAL_RESEARCH_MILESTONE_SPEC.md` in full before any work.
First recover and check the original Puerto R and PaL research files, versions,
data rights and dependencies. Next try a small authentic sunlight/Radiation
calculation through unchanged authorized Java services and check actual values.
Only if compatible, compare pasture growth with the original R results using
matching location, dates, units and aggregation. Do not rewrite the model in Python
to make an incompatible archive appear to work. None of these stages has run.

PaL may reuse the client interfaces and these documented limitations; it cannot
rely on a completed shared-access, runner or scientific-delivery acceptance gate.

## Requested public-page update

The separately authorized [progress page](https://eric-resume.up.railway.app/python-client-progress/)
is deployed at portfolio commit `8fb204490aaab227571aeca7037a9e9c4f5f4ac3` and
verified as an unauthenticated visitor. It now leads with what works, what still
needs work and three brief PaL steps; technical language, identifiers and original
reports are behind expandable sections. All 19 preserved videos play; artifact
hashes, all 12 workflow/34 suite pages, links, accessibility and mobile-width checks
pass. No new scientific result, independent approval or PR acceptance is claimed.
The roadmap and closeout files in this worktree remain local; this website update
did not publish or alter the outstanding PRs. The owned hidden preview was stopped;
original Java/GraphDB listeners retain their recorded PIDs.
