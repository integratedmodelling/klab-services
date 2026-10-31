# Contribution review: combined discover / compare / explain milestone

**Historical checkpoint.** See the [31October integrated simplicity/contributor
review](delivery-readability-post-integration-20261031.md) for the current base,
corrected code,1,594 offline passes and remaining live gates. The user explicitly
prohibits merging102 even if its checks pass.

## Recommendation

**Needs contract/coordination evidence.** All four feature implementations and the
shared delivery tooling are present, assessed and locally tested. The original
milestone definition of done is **not met**: unchanged-Java scientific matrix,
running provenance, actual scientific notebook, two-user acceptance and measured
throughput remain blocked. This is the implementing agent's final-checkpoint
assessment under the contribution guide, not independent review or approval.

## Reviewed state

- Candidate executable content: `bec1c1708551661ca7fca1b94ab964bb044dbdbf`.
- Base/target: `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`, `feature/python-client`.
  Remote refreshed on 28 October; target still equals base. No unreviewed merge.
- Combined branch: `feature/python-discover-compare-explain`; separate F1/F2/F3/F4
  PR checkpoints and delivery stack are preserved without rewriting any history.
- Included: milestone specification/reconnaissance, F1–F4, shared workflow,
  acceptance/notebook/throughput/builder tools, tests, optional dependencies, CI and
  status/release documentation. Review-only follow-up files are not executable changes.
- Explicitly authorized offline checks and bounded read-only hosted diagnostics.
  No arbitrary shared administration, new component build or scientific-source change.

## Capability and boundaries

The complete intended remote scientist journey is represented in Python: explore
identities, inspect candidates, run independently owned named experiments, validate
native numerical exports/analysis against supplied independent references, inspect
recorded effects/plans, restore original results and assemble validated evidence.
The code does not supply missing authoritative semantics, force model selection,
replay plans, continue durable Jobs or implement a worker SDK. Its existence is
not evidence the deployed scientific journey succeeds.

## Findings, ordered by consequence

1. **Required live evidence missing.** The compatible four-case independent fixture,
   exact running Java/component/worldview attestation, actual computation and
   recorded diagrams tied to it remain unverified. The acceptance adapter deliberately
   does not attest declared build identities. Final ready status stays blocked.
2. **Permissions/scientific execution still unavailable.** No second valid identity
   or owned two-user scientific fixture was supplied. Notebook 08 exists, but only
   generic local kernel/HTML smoke was executed. No real scientific throughput
   result exists. These required gates are not skipped, mocked or called complete.
3. **Platform matrix incomplete.** Python 3.11 and 3.12 pass on Windows, including an
   installed wheel/core-only check. Linux and Python 3.13 remain pending actual CI.
4. **Publication structure corrected.** The initial allowed single-branch fallback
   did not satisfy the user's later explicit separate-PR requirement. F1–F4 now
   have individually reviewed draft PRs with feature-specific stacked bases, and
   delivery has a separate PR branch. No history rewrite or automatic merge.

No additional demonstrated combined-source defect was found after the recorded
delivery fixes. Passing tests do not prove scientific correctness or maintainer fit.

## Validation actually performed

Exact commands/environments/revision limits are retained in the feature and
[delivery assessment](delivery.md), rather than replacing earlier evidence with
later test counts. Latest executable content: 1,392 tests pass in each Windows
Python 3.12 and installed-wheel Python 3.11 environment; all new modules satisfy
existing unexcluded 100% line/branch/changed-code coverage; established lint passes;
wheel/sdist build and separate core-only wheel smoke pass. Eight functional cases
collect but have not executed against Java.

`git diff --name-only 1e20fc478f645b5fb5c2fd00ffd223a040ca2878 HEAD -- '*.java'
'pom.xml' '*.kwv' '*.owl'` returned no changes. No Java test/build acceptance is
inferred from that check. A Java reactor build is N/A to this Python-only delta;
actual compatible running binaries remain required dependencies.

Authenticated hosted semantic-readiness and m/mm wire/SDK tests passed on 28 October.
This no longer reproduces the older transport/unit failures; it does not establish
their Java root cause or current scientific exports. F1 resolution/search and F2
model retrieval were observed. Two Austria authority codes remained explicitly
unselected. The historical empty-computation-dataflow issue was not declared fixed.

## Scientific and lifecycle evidence

See individual assessments: [F1](semantic-explorer.md), [F2](resource-inspector.md),
[F3](execution-evidence.md), [F4](bounded-experiments.md),
[shared workflow](delivery-workflow.md), [delivery](delivery.md).
Deterministic wire shapes, real thread overlap, loopback HTTP/fresh-process original
restoration, independent local rasters and real generic notebook kernels provide
useful bounded evidence. None substitutes for real Java numerical data, permissions,
recorded computation or scientific reference acceptance. Valid zero, missingness,
precision, coordinate orientation and unknown revisions remain explicit.

## Contributor coordination

No server/scientific-source scope was crossed and no maintainer was assigned or
contacted. Five feature/delivery PRs are draft by design. The final contribution
target is `feature/python-client`, not `develop`. Historical roadmap is untouched;
current planning/status documents distinguish implementation from acceptance.

## Smallest next actions

Supply/verify the unchanged stack and independent fixture with supported two
variations/two resolutions, resolve runtime provenance attestation, provide the
second identity, and run the eight actual cases/notebook/throughput at one clean
candidate. Run remaining CI matrix. Reassess failed/blocked live findings and build
the final evidence package only after those gates pass. Do not merge merely because
the offline stack is green.

## Suggested PR description

**Python: discover, compare, and explain scientific experiments.** Draft stack of
four scientist-facing features plus shared acceptance/delivery tooling. No Java or
scientific-source changes. Latest offline/package checks pass as documented, but
full real-Java scientific acceptance, runtime attestation and measured delivery are
still blocked. Review feature PRs individually; retain these explicit limits.
