 # k.LAB Python contribution: analysis and next PR plan

Date: September 30, 2026
Status: Proposed direction; extension point requires local verification.

## Purpose

Extend our existing Java-to-Python connection into one bounded,
measurable computation participating in actual k.LAB execution.

This document preserves the preceding analysis and turns it into a
CLI implementation handoff. It does not claim a new repository review
or successful test execution.

The preceding review used klab-services revision:
be5811ffce740ec1fb713d83aaf6fb51bb7ba8fb

Inspect the actual local checkout and existing Python work before
implementing. Do not assume this revision is still current or reset
the user's branch to it.

## Proposed conclusion

Continue the Python work, but make the next deliverable an integrated
computation with bounded data transfer, correct storage behavior,
failure handling, and repeatable performance measurements.

Then demonstrate a regional indicator derived from buildings or roads.

This connects the existing bridge work to Ferdinando's stated interest
in larger-scale computation and pipelines. It also addresses gaps in
external computation, performance evidence, and workflow verification.

Do not expand the project into a general Python framework, Spark backend,
or distributed scheduler before proving this small execution path.

## What Ferdinando indicated he wanted help with

The preceding review of the email chain in aries.pdf, pages 1–3,
identified these priorities:

| Email theme | Contribution implication |
|---|---|
| HPC was described as probably the least developed area. | External and larger-scale computation is a relevant opening. |
| Existing computational logic is local and can be inefficient. | Improve execution capacity and measure the result. |
| Spark and data pipelines are becoming important. | Data-engineering experience directly fits an emerging need. |
| IMF/UN work needs country-level indicators derived from building footprints or road networks. | A building or road aggregation is a concrete demonstration workload. |

These are directional requests, not a specification for an assigned
feature. Python, CESM, and the particular bridge design were our proposed
solutions, not explicit assignments from Ferdinando.

The reported call outcome was to understand the project and propose a
useful contribution.

Do not present the proposed architecture as maintainer-approved.

## Relatively underserved areas

These findings come from the preceding review and must be checked
against current code before implementation.

| Area | Finding at reviewed revision | Relevance |
|---|---|---|
| External computation | ScalarComputation anticipates distributed engines; the inspected Runtime builder selected Groovy, and no implemented Python backend was found. | Strongest overlap with our bridge and Ferdinando's interests. |
| Performance evidence | Representative throughput, contention, disk locality, and remote-provider benchmarks remained follow-up work. | Measure Python startup, transfer, computation, storage, and memory. |
| Complete workflow verification | The main build workflow skipped tests; documented full-stack acceptance remained manual. | Include a reproducible example and accurately label what was executed. |
| Resolver correctness and replay | Documented gaps included APPLY emission, semantic-plan reuse, whole-context reconstruction, and explicit runDataflow execution. | Investigate only if they block our chosen execution path. |
| Context-dependent conversions | Area- and duration-dependent conversions remained incomplete. | Relevant scientifically, but not part of this PR. |

Authority support and adapter discovery were receiving substantial
recent attention. They should not be treated as the most neglected
areas merely because they contain incomplete features.

## Architectural correction

Recent adapter-discovery work does not establish that a resource
adapter is automatically the correct home for Python computation.

Use the role of the Python code to select the boundary:

| Python's responsibility | Candidate extension point |
|---|---|
| Read an external dataset and supply contextualized data | Resource adapter |
| Transform existing k.LAB observations into a result | Runtime function/contextualizer |
| Participate in shard-oriented computation | Inspect ScalarComputation and its callers before deciding |

For this PR, prefer a supported computation extension point for
transforming existing observations.

Do not replace the default computation engine. Keep the process runner
independent enough to be called from the selected extension point
without introducing an unrelated execution framework.

## First CLI task: reconcile this plan with the checkout

1. Read applicable repository instructions and contribution guidance.
2. Inspect branch, HEAD, working-tree changes, and existing bridge code.
3. Read the existing Python-test handoff if available locally.
4. Identify which setup and subprocess milestones already pass.
5. Inspect current Runtime extension examples and storage lifecycle.
6. Trace input reads, output writes, completion, failure, and cancellation.
7. Check whether the selected path is affected by Resolver limitations.
8. Record the chosen integration point with source-file references.

Preserve unrelated changes. Reuse working bridge code rather than
creating a competing implementation.

Do not change dependency versions merely to bypass setup failures.
Record missing artifacts and distinguish environment failures from
behavioral failures.

Do not assume event transaction support applies to every computation
path. Verify the actual commit/abort boundary used by this integration.

If architectural uncertainty remains, complete the inspection and a
small isolated prototype, then report the specific decision needed.
Do not silently invent a new public extension API.

## Proposed next PR

Working title:

"Execute a Python-backed computation over k.LAB storage with bounded
transfer and failure handling"

### Commit 1: integrate one computation

Connect the existing Python runner to one supported Runtime extension.

Requirements:

- Read a named input through the existing k.LAB storage contract.
- Invoke a real local Python interpreter.
- Return results through the normal output lifecycle.
- Preserve input order, cardinality, and missing-value semantics.
- Use a simple deterministic calculation with a known answer.
- Keep dependency requirements minimal.

Acceptance:

A Runtime integration test invokes real Python and verifies the result
read back from k.LAB storage.

A subprocess test by itself does not satisfy this milestone.

### Commit 2: bounded batches and failures

Requirements:

- Transfer bounded batches; do not launch Python once per value.
- Do not load the entire observation into memory for convenience.
- Define batch ordering and validate result cardinality and types.
- Specify the representation of missing values explicitly.
- Apply deadlines and respond to cancellation.
- Drain process output without deadlock and bound diagnostic capture.
- Surface nonzero exits, malformed output, and timeouts usefully.
- Clean up worker processes and temporary resources.
- Use the existing output commit/abort lifecycle.

Acceptance:

- Results agree across uneven batch boundaries.
- Missing values retain their intended meaning.
- A failure after an earlier successful batch does not publish a
  partially completed observation.
- Existing committed data remains intact after failure.
- Timeout and cancellation leave no running worker.

If the selected Runtime path cannot provide the required publication
or rollback semantics, document that limitation and identify the
smallest compatible solution. Do not claim transaction safety based
only on a mocked storage test.

### Commit 3: demonstrate and measure

Requirements:

- Supply a minimal model/example and repeatable execution commands.
- Include a Java reference calculation for correctness comparison.
- Record environment, versions, input sizes, and batch settings.
- Separate startup, transfer, computation, and storage costs where
  practical; explain any timing overlap.
- Measure memory with the method and scope clearly stated.
- Include Python memory as well as Java memory, or explicitly report
  that one was not measured.
- Record enough execution details to identify the worker and settings,
  using existing provenance facilities where available.

Acceptance:

- Correct results for several input sizes.
- Reproducible commands and documented prerequisites.
- Measured elapsed time and memory, with limitations.
- Clear distinction between in-process integration and live-stack
  acceptance.

Python being faster than Java is not an acceptance requirement.
The capability is access to scientific Python; measurement determines
when its overhead is economical.

## Focused validation

Reuse existing tests and conventions where possible.

| Case | Required evidence |
|---|---|
| Normal calculation | Actual stored output matches known expected values. |
| Missing values | Missingness is preserved according to the documented contract. |
| Uneven batches | No dropped, duplicated, or reordered values. |
| Worker error after an earlier batch | No partially published result. |
| Malformed or wrong-length output | Useful failure with no successful publication. |
| Timeout or cancellation | Worker cleanup and correct failed/cancelled state. |
| Larger fixture | Bounded transfer behavior and measured memory. |

Use numerical tolerances appropriate to the operation.
Keep performance measurements separate from brittle timing assertions.

If Python tests are optional in the default build, provide an explicit
command/profile that requires Python and fails when it is unavailable.
A skipped bridge test is not evidence that integration passed.

## Logical follow-on: regional building indicator

Proposed first useful workload:

Total building-footprint area by administrative region.

This is a candidate indicator, subject to Ferdinando identifying a
useful dataset and agreeing on the scientific definition.

Start with a small known-answer fixture containing preassigned region
IDs and verified area values. This isolates aggregation from spatial
assignment.

Before introducing real geometries, specify:

- Treatment of buildings crossing administrative boundaries.
- Duplicate-feature handling.
- Invalid-geometry handling.
- Area units and measurement method.
- Missing or unmatched region assignments.
- Dataset version and provenance.

Spatial assignment can follow separately.

This aggregation may require a reduction across batches or shards;
do not assume that a scalar transformation interface already supports
it. Inspect reduction semantics before selecting its implementation.

Country-scale processing is a later acceptance target, not a claim
established by the small fixture.

## Contribution sequence

1. Existing milestone: Java invokes Python reliably.
2. Next PR: Python participates correctly in k.LAB execution and storage.
3. Follow-on PR: a useful building or road indicator.
4. Measurements guide investment in batching, spatial indexing,
   parallel workers, or Spark.

Do not assume Spark is the next step until workload measurements
justify it.

## Out of scope for the next PR

- A general Python plugin framework.
- Replacing the default computation engine.
- Spark or remote-worker orchestration.
- Running CESM or a broad NetCDF ingestion system.
- AWS emulation without an actual AWS dependency.
- General reprojection or calendar-aware conversion.
- Broad Resolver repair.
- A repository-wide testing or CI overhaul.

Address a related defect only when it directly blocks the chosen path,
and isolate that change for review.

## Questions for Ferdinando

Prepare these questions with concrete code findings:

1. Which supported extension point should host Python transformations
   of existing observations?
2. Which building- or road-based indicator and dataset would be most
   useful as the first demonstration?
3. Is there existing or parallel work we should reuse?
4. What is the smallest supported environment for live acceptance?

These questions do not prevent local inspection, baseline testing,
or an isolated prototype. Do not send messages automatically.

## CLI completion report

Return:

- Current branch and revision.
- Existing bridge work reused.
- Selected extension point and rationale, with file references.
- Implementation changes grouped by the proposed commits.
- Exact validation commands and actual outcomes.
- Test skips and environment blockers.
- Performance measurements and their limits.
- Whether live observation execution was performed.
- Remaining maintainer decisions.
- A draft PR description explaining the problem, behavior, and evidence.

Do not describe the feature as complete if only subprocess invocation
has been verified.

## Source pointers from the preceding review

Repository:
https://github.com/integratedmodelling/klab-services

Reviewed revision:
be5811ffce740ec1fb713d83aaf6fb51bb7ba8fb

Inspect these paths at that revision and compare with the current