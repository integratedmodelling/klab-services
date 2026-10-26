# k.LAB Python milestone: discover, compare, and explain scientific experiments

Prepared for Eric Lumsden, 19 October 2026. Clean milestone specification, 26 October 2026.

Status: implementation specification. Features, tests, commands, and artifacts proposed here are not claims of implementation or passing validation. This document authorizes the implementing CLI to develop and locally validate the milestone; it does not itself request publication, remote deployment, or changes to Java.

## 1. Outcome and scope

Deliver a substantial Python-client increment, comparable in breadth to the recent analysis/provenance delivery plus its integration, packaging, and demonstration work.

A scientist can explore semantic identities, inspect candidate models/resources, submit a named collection of independent scientific experiments, inspect available execution evidence, export/analyze the results, and restore the original work in a fresh Python process.

Implement four features:

1. Semantic explorer.
2. Resource/model inspector.
3. Execution-evidence viewer.
4. Bounded experiment runner.

Integrate them through one reproducible, independently checked scientific workflow and one executed notebook. Reuse existing connection, typed observation, Job, saved-handle, export, raster/xarray, and provenance facilities.

### Hard boundaries

- Zero Java source changes. Do not modify Java tests, server API implementations, Maven files, ontology/worldview source, NiFi, authority plugins, exporters, or other component source to complete this milestone.
- Run existing Java services and use their existing authenticated HTTP interfaces. Verify the exact selected binaries and their contracts.
- Use an existing compatible component archive. A previously corrected archive may be used only with its provenance/hash and separate deployment status disclosed; do not create/apply a new component correction here.
- Missing Java functionality is a documented dependency, never a Python-side imitation of authoritative meaning. Do not invent units, coordinates, identities, model selection, successful results, or execution evidence.
- No portable plan replay, durable Job continuation, global federation, generalized chunk/snapshot protocol, or worker SDK.
- No arbitrary server administration or implicit restart/import on a shared deployment.
- Keep optional scientific/display dependencies optional. Core connection, discovery, Jobs, and reference inspection must remain usable without NumPy, Rasterio, or xarray.
- Persist no credentials in handles, reports, profiles, notebooks, command lines, or committed fixtures.

## 2. Read these references before implementation

Read applicable AGENTS.md and current repository/package instructions first. Reconcile this plan with current source and explicit user direction.

Required repository references:

- `docs/PYTHON_CLIENT_PLANNING_GUIDE.md` — primary current planning reference.
- `docs/contributor-guides/KLAB_Python_Client_Planning_Guide.md` — Eric's complete modified plan.
- `docs/PYTHON_CLIENT_ROADMAP.md` — retained historical requirements, interpreted through the current guide.
- `docs/contributor-guides/KLAB_CLI_Contribution_Review.md` — mandatory assessment method for every feature and final integration.
- `python-client/docs/contracts.md`, `public-api.md`, `functional-testing.md`, `scientific-analysis-contracts.md`, `develop-worldview-compatibility-issue.md`, and current connection/export/analysis evidence.
- Current Java `ReasonerController`, `ResolverController`, relevant Runtime/query controllers, ServicesAPI constants, DTOs, and client implementations.
- `docs/AUTHORITIES.md`, `REASONING.md`, `RESOLUTION.md`, `FLOWCHARTS.md`, and `KNOWLEDGE_GRAPH.md`.

Source context inspected during planning: upstream `feature/python-client` at `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`; primary guide last changed in `e7659b03193448c6bd1aacc76f257ba0af0842d2`. Refresh remote references when implementing; do not assume these remain latest.

Known unresolved deployment evidence includes missing requested m/mm units and an empty scientific dataflow. Recent upstream commits are reasons to retest, not proof of repair. Preserve historical records and distinguish the exact old and new configurations.

## 3. Branch and PR strategy

### Establish an isolated integration branch

1. Locate the actual checkout, remotes, current working changes, and user branch. Record their identities; do not infer that the user's fork and upstream branch are synchronized.
2. Preserve unrelated changes. Prefer a clean git worktree for this milestone. Do not reset, clean, or replace the user's active branch.
3. Fetch relevant remote references and verify the intended starting point is upstream/current agreed `feature/python-client` with the previously delivered features present. Record `MILESTONE_BASE_SHA`.
4. Create `feature/python-discover-compare-explain` from that exact base. Place this specification in `docs/contributor-guides/KLAB_Python_Discover_Compare_Explain_Milestone.md`.
5. Keep Java fixed as an external test dependency. Do not silently merge current `develop` into the Python branch. A separately selected, unchanged Java checkout/build may be used for testing and must be recorded.

### Feature branches

Create each from the milestone branch at its recorded integration checkpoint, preferably in separate worktrees:

| Branch | Purpose | Dependencies |
| --- | --- | --- |
| `feature/python-semantic-explorer` | Semantic exploration | Contract reconnaissance |
| `feature/python-resource-inspector` | Typed model/resource inspection | Existing Resources API; may reuse accepted semantic types |
| `feature/python-execution-evidence` | Dataflow/activity evidence | Verified retrieval path; existing provenance |
| `feature/python-experiment-runner` | Bounded experiment orchestration | Existing connection/Job APIs; integrated discovery/evidence interfaces |
| `feature/python-milestone-delivery` | Notebook, throughput evidence, final packaging and reports | All four features integrated |

Suggested sequence: reconnaissance → semantic explorer → inspector → evidence viewer → runner → delivery. Features can be independently reviewed before their final live integration; do not claim the combined workflow passed early.

After each feature's review/fixes, integrate it into the milestone branch and record the merge candidate and test evidence. Use ordinary additive integration; no history rewriting of shared branches. Update dependent branches deliberately, inspect conflict resolutions, and rerun affected checks.

Feature PRs may target the milestone branch when remote PR creation is separately requested. The final milestone PR targets `feature/python-client`, not `develop`. Do not require feature PRs to merge into `feature/python-client` first: keep the completed milestone independently reviewable as one final PR. A single local integration branch with clear feature commits is an acceptable fallback if tooling makes separate worktrees impractical; preserve the same review checkpoints.

## 4. Reconnaissance gate: confirm the existing Java paths

Before designing public signatures, inventory each operation: Java route/method, request/response DTO, required scope, authorization, limits, error states, deployed availability, and Python entry point. Retain independently verified payload examples with secrets removed. Documentation conflicts are contract uncertainty; current implementation and actual wire behavior require reconciliation.

Existing source indicates:

- Reasoner concept/observable resolution and hierarchy/semantic operations have HTTP controllers.
- Authority search uses authenticated `POST /api/v1/authority/search`; local configured authority names differ from provider URNs.
- Resources list/retrieve/resolve operations already have Python foundations.
- Resolver `resolve` yields a Job containing a raw Dataflow; it does not execute that Dataflow.
- Java creates resolution and execution FlowCharts under `im:resolution-graph` and `im:dataflow-graph`; Runtime activities can carry these diagnostics.
- Python `fetch_provenance` already queries bounded recorded neighborhoods and retains raw activity fields.

Verify activity metadata retrieval through existing scoped APIs before promising diagram availability. Use the recorded execution plan when possible. Do not issue an extra resolve and pretend it is the plan used by an earlier computation; resolution is contextual and need not be a pure dry-run.

If a required response is unavailable: document the exact missing field/path, provide an honest unsupported/partial result where appropriate, and continue independent features. Do not weaken a required milestone gate or change Java. A final PR can be prepared as draft with blocked evidence, but milestone completion requires the core advertised journey to pass.

## 5. Feature scope and sizing

Sizes are relative engineering scope, not elapsed-time commitments or a target number of tests. Risks describe uncertainty separately.

| Feature | Scope | Risk | Main uncertainty |
| --- | --- | --- | --- |
| Semantic explorer | Medium | Medium | Evolving semantic DTOs, authority/revision behavior |
| Resource/model inspector | Small–medium | Low–medium | Completeness and routing of returned metadata |
| Execution-evidence viewer | Medium | Medium–high | Real deployed retrieval and serialization of activity diagrams |
| Experiment runner | Medium–large | Medium–high | Independent ownership, concurrency, deadlines, partial failures |
| Integration/delivery | Large | Environment-dependent | Compatible executable Java/model/component tuple |

This package is approximately comparable to, potentially larger than, the recent analysis/provenance increment plus polish. Do not add authoring, events, a general async API, or new Java work to fill perceived spare scope.

### F1. Semantic explorer

Provide typed results for configured authority search and documentation; concept/observable resolution; direct hierarchy navigation; and a bounded set of implemented semantic checks useful for interpreting a request. Provide readable text/notebook summaries without a mandatory UI dependency.

Preserve canonical identities, local authority names, labels, notifications, supported classification/edition metadata, and raw compatible fields. Do not use labels as identity codes or silently choose an ambiguous match. Respect documented paging bounds/statuses; authority search `total` need not represent the entire external catalog.

Any cache must bind service identity, authorization scope, worldview and available knowledge revision. Treat unsupported/unknown revision guarantees explicitly; do not cache mutable semantics as permanently valid. Remote semantic resolution may declare/materialize knowledge; do not describe it as universally side-effect-free.

Acceptance:

- Search → explicit candidate selection → inspect → resolve succeeds against an existing configured authority and current Java contract.
- Parent/child navigation and semantic checks agree with independently specified existing fixture relationships.
- Empty successful search differs from unavailable/failed/unsupported search.
- Unknown correctness-affecting states fail explicitly; harmless fields remain accessible.
- Knowledge changes invalidate affected state or return an explicit stale/refresh diagnostic.
- No credential forwarding to newly advertised untrusted origins.

### F2. Resource/model inspector

Wrap existing list/retrieve/resolve interfaces with typed, bounded views of models/resources: identifiers, labels, advertised observables, listed dependencies, available versions/provider metadata, notifications, and source raw fields.

Use supported list/filter operations only; do not promise a new global semantic model search. Missing fields remain unavailable. Distinguish advertised applicability from actual Resolver selection. Namespace/ranking constraints do not force a model. Do not recursively expand dependencies without explicit depth/item/request limits, and detect cycles if traversal is provided.

Acceptance:

- Retrieve and summarize at least one existing executable model and its available resource/dependency information.
- Correct originating service/scope routing is retained.
- Missing, forbidden, unavailable, malformed, and unsupported responses stay distinct.
- Partial metadata never becomes an invented complete dependency closure.
- Inspector output connects to the selected experiment's identifiers; planned alternatives remain distinguishable from executed choices.

### F3. Execution-evidence viewer

Decode existing raw Dataflow and available activity FlowCharts into typed read-only summaries: resolution outcome, model/dependency/actuator identifiers, coverage when supplied, activity identity/type/outcome, plan-versus-execution distinction, and explicit missing/partial evidence.

Support `RESOLVED`, `NO_MODEL`, and `FAILED` with their actual Java meanings. Job completion, resolution success, execution success, and durable root commit are different facts. Preserve graph node/link identities and parallel/reference relationships. Bound graph size and response bytes; avoid interpreting arbitrary metadata as executable content.

Render plain-text summaries and optional notebook diagrams/tables from the same decoded model. Do not require graph reproduction, `encode_dataflow` (documented unsupported), exhaustive lineage, or explanations of every rejected alternative.

Acceptance:

- A real Java computation exposes interpretable evidence tied to its own observation/activity, with the expected fixture computation represented.
- Metadata absent by design is an explicit unavailable/partial result, not proof of no dependencies.
- Planned edges are not relabeled as execution; failed activities are retained.
- Recorded diagrams and separately requested plans are never conflated.
- Additional scoped activity fetches preserve authorization and current membership.

### F4. Bounded experiment runner

A synchronous public API accepts named typed experiments and produces structured individual outcomes plus a batch summary. Each experiment specifies its request, supported variation, and explicitly owned context policy. Reuse existing APIs rather than duplicate transport/Job semantics.

Sequential operation is default; an explicit worker limit enables bounded parallelism. Do not share a mutable Client/Connection across workers unless current implementation guarantees and tests establish safety. Prefer independently owned connections and contexts, with secrets acquired through existing providers rather than serialized into worker arguments.

Retain Job/result handles as soon as acquired. Distinguish submission unknown, Job failure, wait timeout with recoverable handle, cancellation requested/accepted, confirmed terminal outcome, and cleanup failure. No automatic retry of ambiguous submissions. Cancelling a wait does not imply remote cancellation. Preserve successful siblings on partial failure. Closing the runner is local; remote release is an explicit owned-work policy.

Acceptance:

- Run four named independent experiments sequentially and with bounded parallelism.
- All successful outputs retain the correct experiment/context/Runtime/Job/observation association.
- An induced single failure does not discard sibling results or trigger duplicate submissions.
- Deadline/cancellation paths retain known handles and truthful outcome states.
- One owner cannot release another experiment/caller's work.
- Stable summary ordering is by experiment identity/input order, independent of completion order.
- Result/report serialization is versioned, bounded, and secret-free; local batch manifests do not imply durable server recovery.

## 6. Scientific fixture and experiment matrix

Reuse an existing deterministic scientific fixture and existing executable Java models/components. Verify their availability and contracts before choosing the notebook story. The prior 5×4 nonuniform fixture with known coordinate/value/mask behavior is a candidate, not proof it works on the selected current Java build.

Required matrix: two genuinely supported scientific variations × two grid resolutions = four named experiments. Variation may be an existing scenario or observable option only when independent expected effects are known. Grid controls must be honored by actual computation. Do not use namespace changes as an assumed scientific variation.

If no suitable scenario pair exists, prefer an already-supported request variation such as units after its contract is verified. If neither exists, mark matrix selection blocked; do not manufacture a comparison, modify scientific source, or call four identical requests different scenarios. Independent unit expectations must cover the chosen option and not infer missing response semantics from request text.

Authority exploration must be exercised against Java. Use an authority identity inside computation only when an existing model supports it. Otherwise make it an honestly separate discovery segment of the same notebook and disclose that it does not influence the numerical field. Model inspection and execution evidence must still refer to the actual computation.

The oracle must be specified independently of SDK/Java output and retain fixture revision, expected values/function, units, CRS, coordinate mapping, valid zero, missing cells, precision/tolerance, dimensions and grid-dependent expectations. Compare only scientifically compatible results. This validates controlled behavior; real-provider accuracy requires its own independent reference and is not implied.

## 7. Test architecture: build the deliverable into acceptance

Extend the existing `tools/workflows`, functional suites, runner selection, installed-package checks, and coverage gates. Do not build a disconnected demo framework. The following names are proposed; reconcile with existing names and avoid duplicate scenarios.

| Proposed location | Responsibility |
| --- | --- |
| `tools/workflows/discover_compare_explain.py` | Shared orchestration and scientific/evidence validators |
| `tests/functional/test_discover_compare_explain.py` | Named real-Java acceptance cases |
| `tests/unit/test_semantic_explorer.py` | Semantic/authority contracts and invalidation |
| `tests/unit/test_resource_inspector.py` | Typed metadata, routing, partial responses |
| `tests/unit/test_execution_evidence.py` | Real-shape Dataflow/FlowChart decoding and limits |
| `tests/unit/test_experiment_runner.py` | Scheduling, outcomes, ownership and cleanup |
| `tests/integration/test_experiment_process.py` | Real local subprocess/fresh-process behavior |
| `examples/notebooks/08_discover_compare_explain.py` | Scientist notebook source, following current conventions |
| `tools/run_milestone_acceptance.py` | Thin adapter over existing suite/runner mechanisms |
| `tools/run_milestone_throughput.py` | Measurement wrapper using the accepted shared workflow |
| `tools/build_milestone_deliverable.py` | Execute notebook and assemble evidence/PR package |

Public module/API names remain implementation decisions grounded in current conventions. No duplicated scientific scenario inside benchmarks, notebook cells, or report generators. The notebook calls production APIs and shared workflow helpers with real outputs; fixture expectations are validation oracles, never result substitutes.

### Layer A: focused deterministic and wire-contract tests

Cover each promised behavior and plausible failure, including independently verified Java payload shapes, null/absent fields, unknown states, paging, identity ambiguity, graph reference/parallel edges, response budgets, stale knowledge, forbidden access, worker cleanup, interrupt/cancellation races, and ambiguous mutations.

Use deterministic scheduler/clock control for lifecycle races rather than flaky wall-clock sleeps. Test concurrency with real overlap in a bounded local environment. Preserve established coverage/lint gates; do not invent exclusions or use a numerical test-count target. Verify old public interfaces remain compatible.

### Layer B: installed package and core-only validation

Run the existing supported interpreter/platform matrix, package builds, installation-from-wheel checks, and core-only smoke. Verify installed SDK sources match the candidate used in live evidence. Test optional-dependency absence and actionable diagnostics. An unavailable matrix environment is Not run/Blocked, not a silent pass.

### Layer C: named Java-stack functional acceptance

Implement the following proposed test cases through current runner conventions:

| Proposed test ID | Required observation |
| --- | --- |
| `test_discovery_and_model_inspection_use_existing_java_contracts` | Real authority/concept operation and scoped model/resource inspection; identity/status/metadata checked |
| `test_recorded_execution_evidence_matches_computed_observation` | Existing Java computes; evidence belongs to that work; expected computation represented |
| `test_four_experiments_preserve_scientific_meaning` | Full four-cell experiment matrix; independent values, units, geometry, masks, compatible comparisons |
| `test_parallel_experiments_preserve_identity_and_results` | Bounded overlap; equality to sequential scientific expectations; no cross-context contamination |
| `test_partial_failure_retains_successes_and_recoverable_handles` | Controlled failure/timeout; successful siblings retained; no ambiguous replay |
| `test_fresh_process_restores_original_experiment_results` | Same original observations and values restored without resubmission |
| `test_private_experiment_evidence_and_exports_enforce_access` | Authorized positive control and valid nonowner denial, using an owned two-user fixture |
| `test_milestone_notebook_executes_and_matches_evidence` | Real executed notebook and report agree with accepted workflow outputs |

Where inducing a state needs unsupported Java controls, retain the lower-layer failure test and mark that live subcase unavailable; do not introduce Java test endpoints. Core successful computation/evidence/matrix acceptance may not be replaced with mocks.

### Java validation without Java changes

Record unchanged Java checkout SHA, binary/build provenance, JDK and component hashes. Exercise Reasoner, Resources, Resolver, Runtime, graph/storage and exporter through actual HTTP calls. Use relevant existing Java regression tests when needed to establish compatibility of the selected stack; no new Java tests and no unrelated full-reactor requirement. Existing test-control routes are permitted only on an independently owned TEST stack where already supported.

Hosted acceptance and controlled fixture acceptance are separate tracks. Never point restart/admin/fixture workflows at shared services. Failed hosted checks remain failures; do not convert current missing-unit/empty-dataflow regressions to xfails, skip away required gates, or silently switch fixtures after a failure.

## 8. Throughput protocol

Demonstrate verified complete experiments per minute, rather than HTTP request volume.

- First pass Layer C correctness at the exact candidate.
- Define a batch as the four experiment matrix. Discover and inspect once per batch; report setup time separately and include it in the complete-batch wall clock.
- Compare worker limits 1 and 4 using independent owned contexts. Start with 20 measured experiment completions per configuration (five four-experiment batches). Warm-up is additional and separately counted. Increase sample size if needed for stable conclusions; report the sample count and p95 method.
- Add a larger supported grid workload separate from the small correctness grid. Record actual cells, bytes, request options and scientific oracle for every case. Bound resources before choosing its size.
- Start timing before orchestration; stop complete-experiment timing after successful compute, validation, required evidence retrieval, native export and analysis. Record cleanup separately and disclose it; record fresh-process batch recovery separately and in the full journey total.
- Record cold-process/service conditions honestly. Do not restart shared services or describe a merely new Python client as cold Java. Distinguish cached/reused results from new computations.
- Capture batch wall time and successful experiments/minute, per-stage latency median/p95, attempted/completed/failed/unknown/timed-out counts, grid cells, exported bytes and achieved cells/bytes rates. Wall time governs parallel throughput; do not sum overlapping durations into an elapsed-time claim.
- Measure Python and owned Java process memory when accessible, identify process scope and sampling method, and report unavailable server memory explicitly. No process-wide memory guarantee from Python array buffer estimates.
- Keep live external authority-network timings separate from deterministic captured/offline-backed behavior. Do not attribute network improvements to Python or snapshot replay unless measured.
- Validate every measured result. Retain failed attempts in denominators and report success rate. No scientific throughput claim when computation fails.
- Establish a baseline; do not invent a required speedup or assert four workers must be four times faster.

## 9. Final milestone deliverable

Create the following after all four feature branches are integrated and reviewed:

1. Installed Python client with the four public capability slices.
2. One executed notebook: **Discover, compare, and explain four scientific experiments**.
3. Notebook outputs showing real semantic search, model inspection, experiment outcomes, field plots, explanation views, comparison table, restoration evidence, and sequential-versus-parallel measurements.
4. A standalone HTML export and, if existing tooling supports it, a short recording of actual notebook execution. A recording is supplementary; the runnable notebook and evidence are primary.
5. Machine-readable correctness, throughput, notebook-execution, environment and artifact-hash records, with compact human-readable reports.
6. Feature review reports, final integration review, updated current roadmap/status inventory, release notes and a prepared PR title/body.

### Deliverable gate built into tests

Implement the builder to consume an acceptance run manifest containing candidate SHA/dirty-source digest, installed-package digest, Java/build/worldview/component identities, environment, run ID and all test outcomes. Reject mismatched/stale evidence. Require an identified clean final candidate for PR-ready status, or explicitly record the exact dirty source manifest while still developing.

Execute notebook cells against the same selected stack and installed candidate. Validate experiment identifiers, measured outcomes, plots/statistics, explanation association, and restored result identities. Do not accept a pre-rendered notebook or a copied historical chart as evidence. Notebook execution is an additional actual run; it must have its own run IDs and results and be associated with its own evidence.

Generate reports/charts from recorded results. Validate report schema, referenced files and hashes, absence of secrets/private state, and links. Commit compact sanitized evidence and notebook source/output according to repository convention; store bulky raw logs externally with traceable references. Do not package tokens, private certificates, local state or unreproducible machine paths into the SDK.

The builder must exit nonzero or produce an explicit draft/blocked status if required acceptance fails, evidence is missing, candidate identities differ, or claimed notebook outputs were not executed. Performance timing variability alone is not a correctness failure. A failed/blocked package can be reviewable but must not be labeled complete/ready.

## 10. Required contribution review after every feature

The user explicitly requires `docs/contributor-guides/KLAB_CLI_Contribution_Review.md` to assess **each of F1–F4, the delivery increment, and final combined milestone**.

For every assessment:

1. Reread the applicable guide and repository instructions, reusing the already read version only if unchanged.
2. Record actual integration target/base SHA, candidate SHA, included working changes, use case, expected contracts, and exact permitted test environment.
3. Perform the review in a read-only phase against the actual feature diff and inherited dependencies. The guide's read-only instruction applies to review; it does not prohibit the preceding authorized implementation or subsequent fixes.
4. Assess usefulness, architecture, contract fidelity, scientific meaning, identity/permissions, lifecycle, provenance, concurrency, overlap and maintainability proportionately.
5. Produce the guide's complete report: Recommendation; Reviewed state; Capability and boundaries; Findings ordered by consequence; Validation actually performed; Scientific and lifecycle evidence; Contributor coordination; Smallest next actions; Suggested PR description.
6. Separate demonstrated blocking/important defects from contract uncertainty and optional improvements. Report Passed / Failed / Blocked / Not run / Not applicable with exact commands, revisions and environments.
7. Exit read-only review to fix demonstrated defects, then re-review affected code and rerun appropriate checks. Never treat earlier live evidence as proof of changed code.
8. Integrate only with the supported recommendation and explicitly tracked remaining dependencies. No automatic maintainer acceptance or reviewer assignments. Do not send messages to maintainers.

Proposed reports under `python-client/docs/reviews/discover-compare-explain/`:

- `semantic-explorer.md`
- `resource-inspector.md`
- `execution-evidence.md`
- `experiment-runner.md`
- `delivery.md`
- `milestone-integration.md`

A feature may be implemented and locally reviewed while final Java-stack acceptance is blocked; the recommendation must disclose that boundary. Do not misrepresent a partial feature as fully accepted. Resolve required gates before the final completion recommendation.

## 11. Final PR preparation and definition of done

After all features land on the milestone branch:

- Review the whole diff against `MILESTONE_BASE_SHA` and the current intended target; identify target drift without silently rebasing or importing unrelated Java work.
- Confirm zero changes to Java/component/worldview/ontology source and no unrelated feature removal.
- Pass existing Python compatibility, lint, coverage, build, installed-wheel and optional-dependency gates appropriate to the final diff.
- Pass the core real-Java milestone workflow, deterministic scientific oracle, identity/permission controls, fresh-process restoration, and executed notebook gates.
- Produce honest throughput results at the final candidate, with all failures/resource limitations disclosed.
- Complete the mandatory final contribution review and resolve demonstrated blocking/important defects.
- Update `PYTHON_CLIENT_PLANNING_GUIDE.md` and applicable public API/contract/testing docs with per-capability implementation and acceptance status. Keep the historical roadmap snapshot intact.
- Generate a PR package: title, body, change summary, exact base/candidate, feature review links, compatibility matrix, validation summary, notebook/HTML links, scientific/performance evidence and remaining limits.
- Suggested title: **Python: discover, compare, and explain scientific experiments**.
- Final target: `feature/python-client`. Prepare locally first; create a draft/final PR only when the user's session authorization includes remote PR creation. This specification does not imply permission to merge or publish a release.

The milestone is complete when a reviewer can reproduce the new scientist journey, verify scientific and lifecycle claims through real unchanged Java services, and inspect the evidence without reconstructing this chat.

## 12. Remaining roadmap after this milestone

Update actual statuses from evidence; completion of this milestone does not finish these areas:

| Area | Still remaining |
| --- | --- |
| Observation ergonomics | Remaining supported observer/focus/grid options; broader deployment and collaborator acceptance |
| Scientific data views | General scale/time descriptors, temporal selection, spatial windows, category dictionaries, additional multidimensional/tabular adapters |
| Large scientific access | Revision-pinned chunks, stable snapshots/read leases, lazy arrays, representative large-transfer failure testing |
| Scientific accuracy | Independent real-provider reference acceptance with dataset/datum/tolerance provenance |
| Live sessions | General async SDK, notifications/subscriptions, stale graph caches, reconnect/resync and multi-client convergence |
| Jobs/recovery | Server-supported retention, durable continuation, idempotency/reconciliation and stronger crash recovery |
| Graph/provenance | Broader navigation and full dependency/resource-version closure beyond bounded recorded evidence |
| Resources/authoring | Imports, semantic annotation, document validation, deferred validation, edit/publish locks and version conflicts |
| Behaviors/agents | Supported invocation, monitoring and reconnection against current contracts |
| Federation | Remaining #89/#90 acceptance, cross-Runtime origin isolation, supported deployment routing/migration |
| Release operations | Support/version policy, mixed-version compatibility, sustained reliability, migration/release readiness |
| Separate extension products | Python worker/provider SDK, budgets/cancellation/output publication; embedded runtime feasibility |

## Implementation checklist

- [ ] Read instructions/references and preserve user work.
- [ ] Record base; create isolated milestone/feature branches.
- [ ] Verify existing Java HTTP paths and scientific matrix feasibility.
- [ ] Implement/test/review F1; integrate.
- [ ] Implement/test/review F2; integrate.
- [ ] Implement/test/review F3; integrate.
- [ ] Implement/test/review F4; integrate.
- [ ] Pass shared real-Java scientific acceptance at the combined candidate.
- [ ] Run throughput protocol with independent correctness checks.
- [ ] Execute notebook; build and test milestone deliverable.
- [ ] Complete delivery and final contribution-guide reviews.
- [ ] Reconcile roadmap and prepare the final PR package.
