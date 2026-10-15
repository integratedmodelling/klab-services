# k.LAB contribution review guide for CLI agents

Prepared for Eric Lumsden — 2026-10-11.

## Purpose and authority

Review a proposed contribution for architectural fit, scientific fidelity, reliability, useful validation, and ease of maintainer review. Optimize for a contribution Ferdinando and the other maintainers can understand and trust.

This is Eric's review guide, inferred from inspected public work and Ferdinando's emails. It is **not an official project policy or a guarantee of acceptance**. Repository instructions, current contracts, explicit maintainer feedback, and the agreed issue take precedence. Do not turn inferred preferences into mandatory requirements.

Explicit direction from the supplied emails: full Python support is a priority; Ferdinando calls `feature/python-client` Eric's branch and asks him to incorporate `develop`; Arnab is a resource for Python conventions; Andrea brings scientific and spatial experience; Arnab and Enrico maintain NiFi. These invitations do not assign those people review duties.

## How Eric should invoke this guide

Give the CLI this instruction, filling in the review inputs:

> Read KLAB_CLI_Contribution_Review.md and the repository instructions. Review the specified contribution against its actual integration base. Remain read-only: do not edit code, create branches, merge, commit, reopen PRs, publish, or send messages. Begin with an evidence-based review. Run checks only when they can be isolated from my working changes; identify any live-service checks that would create or delete remote objects before running them. Produce the review report defined below. Separate demonstrated defects, contract uncertainty, and optional improvements. Do not claim that passing tests proves scientific correctness or maintainer acceptance.

Review inputs:

```text
Repository / checkout:
Feature and scientist or operator use case:
Issue / agreed scope:
Integration target:
Base SHA:
Candidate SHA or branch:
Include uncommitted changes? [yes/no; identify paths]
Relevant maintainer feedback:
Available local/live environment and authorized operations:
```

If an input is missing, infer it only from available evidence and label the inference. Ask when choosing the wrong base, feature scope, or authorized operation would materially change the review. Do useful independent inspection while a clarification is pending.

## 1. Establish what is being reviewed

- Read applicable `AGENTS.md`, contribution guidance, package instructions, the relevant roadmap, and issue/PR discussion.
- Record current branch, HEAD, working-tree status, base SHA, and candidate SHA. Preserve unrelated changes. Include staged, unstaged, and relevant untracked files when authorized.
- Compare the complete contribution to the intended base, including inherited integration changes. Identify the feature-specific diff separately when needed.
- Verify that the base contains required upstream fixes. Do not silently substitute the latest `develop` for the agreed integration target.
- Distinguish current observations from historical test reports. Identify revision drift and untested conflict resolutions.

Python starting context, to reverify rather than assume: development is under `klab-services/python-client`, targeting `feature/python-client`; #84 was merged, #85 contained typed requests, and #86 contained saved handles. The specific federation compatibility fix was `16e1be9083b6ea9e72743f871ccc920207f34710`; it did not complete the broader federation roadmap. Check current PR state and ancestry before recommending integration.

## 2. Review the capability end to end

| Dimension | Questions to answer | Evidence to seek |
| --- | --- | --- |
| Usefulness and scope | What can a scientist/operator do afterward? Is this an independently useful slice of agreed work? | A concrete before/after workflow and explicit exclusions. |
| Architectural fit | Does it use existing service contracts and established code patterns? Does it keep authoritative decisions in the correct service? | Trace input through client, transport, server, persistence, and output. |
| Contract fidelity | Are request fields, response shapes, advertised capabilities, and errors actually supported? | Current server implementation or versioned contract; tests against real shapes, not just client-generated mocks. |
| Scientific meaning | Are values interpreted faithfully? Are absent semantics disclosed? | Verified dimensions, axis order, coordinates, CRS, units, precision, categories, and missingness where relevant. Valid zero is not missing data. |
| Identity and permissions | Can one caller access, reconnect to, or dispose of another caller's work? Is identity origin-qualified? | Caller identity, Runtime binding, persisted ownership, private/public/group access, and release rules. |
| Lifecycle and reliability | What happens on timeout, cancellation, failure, restart, or lost connectivity? | Bounded waits, terminal-state handling, cleanup, recoverable IDs, and explicit unknown outcomes. |
| State and provenance | Does reconnection recover authoritative state? Can evidence be traced to the exact operation or proposal? | Persisted metadata, version/candidate references, checkpoints, and failure-safe updates. |
| Performance and concurrency | Is memory bounded? Can overlapping runs interfere? | Streaming behavior, request limits, unique workspaces, cache isolation, and relevant concurrency checks. |
| Contributor fit | Does this duplicate or cross another contributor's work? | Current issues, PRs, recent changes, and explicit ownership statements. Expertise alone does not establish ownership. |
| Maintainability | Is the implementation understandable and consistent with the project? | Existing patterns, limited dependencies, useful errors, focused documentation, and no unrelated refactor. |

Apply each dimension proportionately. Mark irrelevant items N/A with a reason; do not require every feature to implement every roadmap capability.

## 3. Focused checks for likely contributions

### Typed requests and saved handles

- Confirm serialization against the current supported request contract, including malformed and unsupported inputs.
- Reconnect against the updated server, preserving home Runtime identity and adopting authoritative configuration.
- Check unauthorized access, private-context exclusions, expired or missing scopes, and release/disposal behavior.
- Saved handles must not persist credentials accidentally or imply replay/recovery guarantees the server does not provide.
- Review authorization conflict resolutions directly; passing Python tests alone does not validate changed Java authorization.

### Exports, metadata, and scientific adapters

- Discover supported formats from the service; verify at least one chosen exporter in a compatible live environment.
- Publish a completed download only after applicable integrity checks. Test interrupted transfers and cleanup without overwriting an existing valid output.
- Preserve scientific metadata; report unsupported or missing fields instead of inventing coordinates, units, revisions, or precision.
- Use an independently specified fixture or known result to test orientation, coordinates, missing cells, valid zero, and representative values. A serialize/deserialize round trip is insufficient alone.
- Keep optional scientific dependencies optional where established packaging conventions allow it.

### NiFi contributions

- Confirm the intended workflow boundary with Arnab/Enrico when changes overlap their active processors; prepare a proposal, do not send it automatically.
- Check FlowFile/session lifecycle and success/failure routing against the supported NiFi version.
- Isolate intermediate files per run; make output ownership and cleanup explicit.
- Bound polling and handle unsuccessful terminal states. A timeout after submission may mean the outcome is unknown; retry only with evidence about deduplication or idempotency.
- Preserve scientific algorithms unless that change is explicitly in scope and has scientific review.

## 4. Validation rules

Select checks because they expose a plausible failure, meet an existing gate, or demonstrate the claimed capability. Avoid tests that merely mirror implementation, invented coverage thresholds, and broad unrelated test runs.

Use this evidence ladder where applicable:

1. Focused deterministic tests for success, invalid input, failure, and lifecycle transitions.
2. Contract checks using independently verified payloads or compatible server behavior.
3. Relevant Java/package/build checks when shared code, packaging, or server logic changed.
4. One end-to-end scientific/operator workflow in an authorized environment.
5. Independent scientific reference checks where interpretation is part of the claim.

Use existing coverage gates when the repository or agreed feature requires them. High coverage does not establish compatibility, scientific validity, or reliable deployment. Live checks are required only for claims that depend on a live service; otherwise state the limitation clearly.

Record each exact command, exit status, environment, tested revision, and concise result. Use **Passed / Failed / Blocked / Not run / Not applicable**. A dependency-resolution failure is Blocked, not Passed and not evidence of a feature defect. Never reuse an older live result as validation of a changed candidate. Report documentation-only or research-only work according to its actual claims.

## 5. Classify findings and make a recommendation

| Classification | Use when |
| --- | --- |
| Blocking defect | Evidence shows broken promised behavior, unauthorized access, data corruption, scientific misinterpretation, or a required failing check. |
| Important defect | A demonstrated issue materially harms reliability or usability within scope. Explain whether it should hold the PR. |
| Contract / coordination question | Evidence is insufficient or a maintainer decision is needed. Identify the missing fact and the smallest way to resolve it. |
| Optional improvement | A useful enhancement that does not invalidate the bounded contribution. Keep separate from acceptance criteria. |

For each finding include a file/line reference, trigger, observed or traceable behavior, consequence, evidence, and smallest plausible correction. Label source-inspection conclusions separately from reproduced failures. Do not manufacture findings to fill a checklist.

Recommend one of: **Ready for maintainer review**, **Needs fixes**, or **Needs contract/coordination evidence**. State what the evidence supports. Only maintainers decide acceptance; a reviewer recommendation is not authorization to publish or merge.

## 6. Required review report

```markdown
# Contribution review: <feature>

## Recommendation
<Recommendation and one-paragraph explanation; state material limitations.>

## Reviewed state
- Repository, integration target, base SHA, candidate SHA.
- Included local changes and required upstream fixes.
- Current roadmap/issue agreement and unresolved assumptions.

## Capability and boundaries
<Concrete before/after example; included behavior and exclusions.>

## Findings, ordered by consequence
<Classification, location, trigger, consequence, evidence, minimal correction.>
<If no defects were found, say so without implying exhaustive proof.>

## Validation actually performed
| Check / command | Revision and environment | Status | What it establishes / limits |
| --- | --- | --- | --- |

## Scientific and lifecycle evidence
<Relevant reference checks, identity/permission checks, recovery behavior;
mark N/A or unavailable evidence explicitly.>

## Contributor coordination
<Observed overlap, unresolved decisions, suggested reviewers; no assignments.>

## Smallest next actions
<Only actions necessary to resolve findings or missing claimed evidence.>

## Suggested PR description
<Problem, resulting behavior, useful example, validation, known limitations.>
```

Keep the executive recommendation brief. Put detailed evidence in the report so a maintainer can examine it without reconstructing the conversation.

## Evidence behind this guide

The inferred preferences come from public work inspected on 2026-10-11 and the emails Eric supplied:

- [Python client PR #84](https://github.com/integratedmodelling/klab-services/pull/84) and [roadmap](https://github.com/integratedmodelling/klab-services/blob/feature/python-client/docs/PYTHON_CLIENT_ROADMAP.md): scientist-facing workflows and an integration branch.
- [Federation authorization](https://github.com/integratedmodelling/klab-services/commit/16e1be9083b6ea9e72743f871ccc920207f34710): caller identity, access rights, context ownership, and lifecycle tests.
- [Context creation and Runtime handling](https://github.com/integratedmodelling/klab-services/commit/9e5d50066cd934fb89e0335456a5c9c92096834c): authoritative configuration, explicit failures, and origin-qualified identity.
- [Workflow behavior instrumentation](https://github.com/integratedmodelling/klab-services/commit/1f48b432a47aec3830ca91ae61a2993761ab9055): durable attachments, checkpoints, recovery, and bounded tests.
- [Ontology review PR #83](https://github.com/integratedmodelling/klab-services/pull/83): exact candidate binding and preserved review evidence.
- [Grid alignment](https://github.com/integratedmodelling/klab-services/commit/bc6f6cffb197cd1c3c9766fb1f5fbd9b073c8837): scientific spatial contracts and persistence.
- [Deferred authority hierarchy](https://github.com/integratedmodelling/klab-services/commit/3b01ceaa907000ffccafac97444029027c81d30b): useful partial capabilities with explicit semantics and later enrichment.
- [Worldview research boundary](https://github.com/integratedmodelling/imod/blob/worldview/strawman-2026/experiments/strawman-2026/bootstrap/LAND_AGRICULTURE_REVISION.md): distinguish parser/structural checks from semantic and scientific acceptance.

Acceptance of a commit is evidence of project direction, not proof that every implementation choice is ideal. Do not infer a universal rule from one change, copy defects, or assume AI authorship determines quality.
