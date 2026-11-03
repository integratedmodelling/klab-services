# Python roadmap acceptance map — 3 November 2026

**The roadmap is not complete, and remaining work is not external-only.** This
inventory covers all22 phase acceptance statements/scenarios and both continuous
release paragraphs in [the original roadmap](PYTHON_CLIENT_ROADMAP.md). It does
not certify every sentence of its broader capability proposals or every public
operation; a complete public-operation/feature negotiation inventory remains work.
Phase5 is an optional separate product, not automatically a scientific-client gate.

Baseline: integration `e09a7de7cac6b48526fd8b7ac72879abb36a77a3`. Bounded merged
features are not whole-scenario acceptance. Candidate PRs #134/#135/#136 remain
unmerged; their software tests do not establish authenticated live lifecycle,
durable graph recovery or scientific reproduction. No row below is a blanket
Java defect report. Historical failures and UNKNOWN outcomes remain unchanged.

Classes: **I** internal Python implementation; **T** testing/portable fixtures;
**B** backend contract/implementation; **D** maintainer/product/support decision;
**S** external scientific inputs/reference agreement. Multiple classes may apply.
**GAP** means issue coverage is incomplete, not permission to invent a backend
failure or claim all remaining blockers are uniformly agent-ready.

## Evidence groups

- **A — connection:** [connection](../python-client/src/klab_client/connection.py),
  [credentials](../python-client/src/klab_client/credentials.py),
  [catalog models](../python-client/src/klab_client/connection_models.py).
- **C — contexts/jobs:** [client](../python-client/src/klab_client/client.py),
  [typed requests](../python-client/src/klab_client/requests.py),
  [saved handles](../python-client/src/klab_client/handles.py),
  [async client](../python-client/src/klab_client/async_client.py),
  [current integration handoff](../notes/issue-89-agent-handoff.md).
- **F — scientific data:** [exports](../python-client/src/klab_client/exports.py),
  [analysis](../python-client/src/klab_client/analysis.py),
  [tables](../python-client/src/klab_client/tables.py),
  [scientific contract limits](../python-client/docs/scientific-analysis-contracts.md).
- **G — graph/evidence:** [graph](../python-client/src/klab_client/graph.py),
  [provenance](../python-client/src/klab_client/provenance.py),
  [execution evidence](../python-client/src/klab_client/evidence.py),
  [best-effort notifications](../python-client/src/klab_client/notifications.py).
- **H — semantics/authoring/agents:** [semantics](../python-client/src/klab_client/semantics.py),
  [inspection](../python-client/src/klab_client/inspection.py),
  [authoring](../python-client/src/klab_client/authoring.py),
  [authoring limits](../python-client/docs/authoring-contract.md),
  [agent handoff](../notes/issue-125-agent-handoff.md).
- **R — release:** [package metadata](../python-client/pyproject.toml),
  [release checker](../python-client/tools/check_release_readiness.py),
  [Python workflow](../.github/workflows/python-client.yml).

## Criterion-by-criterion ledger

IDs P1.1–P4.5 identify the corresponding numbered scenario in that phase. P0/P5
identify their single Acceptance paragraph; R1/R2 identify the two continuous-gate
paragraphs. "OPEN" means the **full stated criterion** is not certified, not that
all its constituent software is absent. Source/evidence is grouped above to keep
this table readable; the remaining work is deliberately explicit.

| ID | Criterion | Bounded implementation/evidence | Remaining work and class | Tracking |
| --- | --- | --- | --- | --- |
| P0 | Agreed boundary, public inventory, unavailable/unsupported/invalid distinctions | A/C/R and contract fixtures | OPEN: agreed supported versions/public scope, exhaustive operation inventory and cross-version negotiation [I,T,D] | #129; GAP: operation-by-operation ledger |
| P1.1 | New user credential flow and observation without Java wire dictionaries | A/C; typed requests and explicit credentials | OPEN: verify supported onboarding on the chosen deployment and end-user walkthrough, not a synthetic credential fixture alone [T,D] | #89/#129 |
| P1.2 | New process restores observation/job and reads without disposal | C; saved handles, bounded native/restart evidence in #89 | OPEN: full retained-job/history chain and Python notebook/process matrix; restoration is not execution continuation [I,T,B] | #89; GAP: retained-job contract/task |
| P1.3 | Read-only collaborator, denied operations and stale versus forbidden token | C; owner/outsider/shared-session tests and live bounded checks | OPEN: complete private-parent, negative credential and positive-control deployment matrix [T] | #89 |
| P1.4 | Actionable unavailable dependency/capability change; no untrusted credential forwarding | A; catalog/selection and origin guards | OPEN: provider temporary-unavailability/recovery and mixed-service capability fixture, no implicit trust expansion [I,T,D] | #89/#129; GAP: itemized provider-selection cases |
| P1.5 | Requested model, observer/focus/constraints visible in plan/provenance | C/G; typed options and plan projections | OPEN: test each selected-model/lexical-context/observer option against resulting served plan, not DTO round-trip only [I,T,B] | GAP: complete option-to-plan matrix |
| P2.1 | Nonuniform multi-shard export with independent coordinates and zero/false/missing | F; typed reads/exports and metadata guards | OPEN: portable nonuniform multi-shard oracle/served export fixture; uniform synthetic20-cell success is not this criterion [T,S] | #130; GAP: concrete multi-shard fixture task |
| P2.2 | Temporal states/window with dtype, CRS, units, revision and masks | F; bounded data descriptors/adapters | OPEN: maintained versioned selection/revision contract and Python adapter; verify actual advertised export coverage first [I,T,B] | #123/#130 |
| P2.3 | Real provider versus independent dataset/datum/tolerance reference | F; scientific failures remain preserved | OPEN: original Elevation remains unaccepted; obtain agreed operational artifacts/reference and run unchanged query [T,S] | #130 |
| P2.4 | Representative large stream, bounded memory and interrupted cleanup | F/R; stream limits/cancellation tooling | OPEN: choose representative workload, measure sustained memory/soak and partial-transfer closure on real deployment [T,D] | #129; GAP: workload-specific soak recipe |
| P2.5 | Correct raster/xarray axes and categorical dictionary/worldview | F; checked optional adapters and fail-closed limits | OPEN: extend only proven georeferencing/dictionary contracts; accepted numeric adapters do not imply arbitrary vector/categorical conversions [I,T,B,S] | #123/#130; GAP: remaining adapter scope |
| P3.1 | Two clients converge on committed changes despite duplicate/racing events | G; graph pages and best-effort hints | OPEN: coherent commit/version contract plus client dedup/reconciliation and two-client race fixture; #136 is not commit application [I,T,B] | #126 |
| P3.2 | Disconnect, reconnect/catch-up or explicit partial refresh; no false freshness | G; manual refetch; candidate #136 invalidation/retention | OPEN: genuine disconnect/refresh fixture and maintained recovery adapter; local-generation markers are not server revisions [I,T,B] | #126/#89 |
| P3.3 | Cancel/completion/expiry/restart remain distinct; no silent resubmission | C; bounded job states, async cancellation and ambiguity handling | OPEN: full retained-job/expiry/restart history and service retention/status contract; agent journal #135 does not solve Jobs [I,T,B] | #89; GAP: retained-job acceptance/contract |
| P3.4 | Explain resources/models/components, accepted plan and semantic updates | G/H; recorded plan/activity/provenance projections | OPEN: complete lineage/dependency extraction and served semantic-update evidence; recorded dataflow is not portable replay [I,T,B,S] | GAP: complete lineage/replay contract tasks |
| P3.5 | Bounded notebook jobs, own-wait cancellation, no shared deletion | C; async/runner limits and lifecycle separation | OPEN: served multi-principal notebook/job cancellation race fixture at supported deployment closure [T] | #89; GAP: explicit notebook scenario |
| P4.1 | Meaning-based candidates with applicability/dependency/version/access inspection | H; semantic search and resource inspection | OPEN: applicability/version/history/access matrix and capability-aware diagnostics for unsupported projections [I,T,B] | GAP: itemized discovery/version workflows |
| P4.2 | Parse/validate model/ontology at known revision; stale never current | H; supported parsing/validation and revision guards | OPEN: per-kind supported validation inventory and served stale-knowledge cases beyond bounded authoring operations [I,T,B] | GAP: broader authoring/validation criteria |
| P4.3 | Import data, annotate semantics, use in observation with input provenance | F/H; existing export/adaptation/inspection primitives | OPEN: enumerate actual supported import/annotation route/schema and build end-to-end client workflow; do not invent endpoint [I,T,B,D] | GAP: maintained import/annotation task |
| P4.4 | Authorized edit/publish with locks/versions and explicit conflicts | H; namespace-only ADD and honest unsupported boundaries | OPEN: edit/history/lock/conflict/publication contracts plus supported client mutations; namespace ADD is not general authoring [I,T,B,D] | #129 scope decision; GAP: full authoring tasks |
| P4.5 | Run agent, scoped outcomes, reconnect without owning compiler/scheduler | H; merged #111 source preview; candidates #134/#135 decoding/local policy | OPEN: actual maintained broker adapter/auth/incarnation, execution handle, genuine lifecycle, terminal confirmation and owned cleanup [I,T,B,D] | #125 |
| P5 | Optional provider SDK: scoped computation, budgets/cancel, valid attributed outputs | No delivered worker SDK; runtime/compiler not ported | OPEN: separate product/execution/isolation/output/provenance specification and implementation; not a client-only bug [I,T,B,D,S] | #127; optional scope decision |
| R1 | Offline/exact contracts/wheel/live fixture, mixed versions, failures and science | R/C/F; bounded accepted tests and retained historical evidence | OPEN: fresh-clone live fixture, broader unchanged-baseline Java matrix, mixed-version/credential/stream/restart tests, independent scientific oracles [I,T,B,D,S] | #89/#129/#130; current Java build failure in #89/#125 |
| R2 | Public support/version/limitations/deployment/migration; separate evidence layers | R; release tooling, no publication authorization | OPEN: maintainer support/version/publication decisions and deployment/scale/provider evidence; packaging success is not authorization [T,D,S] | #129/#130 |

## Reproducible next development, without fabricating Java bugs

1. **Internal:** turn GAP rows into bounded test/contract inventories, starting with
   P1.5 option-to-plan, P3.3 retained-job outcomes and P4.1–P4.4 authoring/import
   surface. Reuse C/G/H rather than invent routes. First reproduce the missing
   behavior with a sanitized fixture; distinguish a client gap, unsupported server
   operation, maintainer decision and demonstrated backend defect.
2. **Internal testing:** commit portable isolated fixture inputs/launch code for
   P1.2/P1.3/P3.2/P3.5. No dependency on an author's private temporary helpers.
   Generate test credentials; preserve owned mutation receipts and independent
   cleanup. Never restart retained failed graphs or replay ambiguous writes.
3. **Backend/build:** follow the exact source/JAR hashes, compile/javap commands and
   expected errors in the current #89/#125 bodies. Establish a maintained coherent
   language/source pair; do not delete scientific requirement cases to pass CI.
4. **Scientific/product:** obtain explicit artifact/reference and support decisions
   in #130/#129; do not substitute synthetic-v3 or silently broaden release scope.

No new GitHub issues are opened by this map. Existing issue groups are useful but
not a certified exhaustive actionable handoff; GAP entries remain visibly unowned
until a bounded, runnable task is documented. Design questions must not be styled
as reproduced Java bugs. A failed setup command is not scientific acceptance.

To verify this inventory from its PR checkout (Python3.11+):

```sh
python -m pip install -e './python-client[dev]'
python -m pytest -q python-client/tests/unit/test_roadmap_acceptance_map.py python-client/tests/unit/test_roadmap_handoffs.py
```

These checks ensure every structured source acceptance item has exactly one row
and local evidence links resolve. They do **not** execute the scenarios or certify
release acceptance. No service, credential or mutation is needed for this audit.

## Independent candidate composition check

A fresh **local-only** composition of #133–#136 passed2359 offline Python tests,
all16 Python workflow commands (including12 coverage gates, lint and packaging)
and6 selected common-module Java tests. See the
[pinned inputs/results](../notes/python-roadmap-combined-results-20261103.json).
This map and its three new audit tests were not part of that tree. No GitHub PR
was merged, no check bypassed and no live/scientific acceptance inferred. The
full Java reactor still has the separately documented downstream build failure.

Reproduce the same source tree in a **new** directory (requires Git, Python3.11+
and JDK21; test extras need normal package/dependency network access):

```sh
git clone --no-checkout https://github.com/integratedmodelling/klab-services.git roadmap-composition-check
cd roadmap-composition-check
git checkout --detach f5871f254d1246bf1bdbcb448d54383fb046e7e0
git -c user.name=LocalValidation -c user.email=local@example.invalid merge --no-edit 8928314d542320ef7a182e5985b07019e3610104
git -c user.name=LocalValidation -c user.email=local@example.invalid merge --no-edit bcf7a4406ea76f60454594a240c248ebd17d69fb
git -c user.name=LocalValidation -c user.email=local@example.invalid merge --no-edit 2d4d44e66747e09462b45bbf4599b2ee9bb6041a
git rev-parse 'HEAD^{tree}'
python -m pip install -e './python-client[dev,local,analysis,tabular,notebook,events]'
python -m pytest -q python-client/tests/unit python-client/tests/integration
./mvnw -B -pl klab.core.common -am -Dtest=AgentStatusWireTest,AgentInstantiationRequestSerializationTest,AgentEventBusTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected tree: `4e0428626a37eb27a9be46d086423bee4d740b36`. Merge commit IDs may
differ because author/time metadata differs. The per-command Git identity above
affects local test merges only; it does not configure a global identity or publish
anything. On PowerShell use `mvnw.cmd` and quote each complete `-D...` argument.
For all coverage/lint/package gates, use that tree's checked-in Python workflow,
respecting each step's `working-directory`. Dependencies may change at SNAPSHOT
coordinates: report actual hashes/errors, never mix historical closures or claim
an earlier selected Java PASS proves a current full reactor passes. Delete no old
fixtures or services; this recipe creates no persistent runtime graph or remote Job.
