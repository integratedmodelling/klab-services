# Python roadmap acceptance map — 3 November 2026

## Consolidation update — 4 November 2026

The dated inventory below is retained as historical roadmap evidence, not the
current PR/build census. All nine original PRs **#133–#141 are now merged**, as
are backend/dependency prerequisites #191/#192/#194/#195. Accepted delivery is
`87ef80ff589428f7c088ba60abcb7cf2b88276eb`; exact reviewed heads, bases, checks and
merge commits are in [the consolidation PR ledger](../notes/python-consolidation/PR_LEDGER.md).
The old EXTENT/ATTRIBUTE build failure and receipt-association error are not
current blockers on that integrated source.

The installed source-matched client passed **2,772 offline tests**, **225 selected
Java tests**, and a genuine Java synthetic workflow: independently checked
**123.25 m in 20 valid cells**, actual 5×4 EPSG:3857 bounds, partial execution/
provenance inspection, separate-client authorized restoration and verified owned
scope/process/listener cleanup. Genuine managed Runtime registration and
**READY → RUNNING → STOPPED** also pass using a dedicated isolated broker.
Publication remains **UNCONFIRMED**, remote-agent cleanup and durable-root proof
remain **UNKNOWN**. These bounded results do not complete P1–P5 or release support;
the remaining broader criteria and historical failures below are not waived.

See [scientific acceptance](../notes/python-consolidation/SCIENTIFIC_ACCEPTANCE.md),
[managed-agent acceptance](../notes/python-consolidation/MANAGED_AGENT_ACCEPTANCE.md)
and [the closeout summary](../notes/python-consolidation/CLOSEOUT_SUMMARY.md).
Component [PR #11](https://github.com/integratedmodelling/klab.component.geospatial/pull/11)
is the sole unresolved consolidation PR: its HM0.12/host compatibility gates pass,
but an upstream writer must merge it or grant permission. The milestone remains
**MILESTONE_INCOMPLETE**, not an unmerged Python backlog or missing scientific run.

## Historical roadmap inventory and remaining broader criteria

**The roadmap is not complete, and remaining work is not external-only.** This
inventory covers all22 phase acceptance statements/scenarios and both continuous
release paragraphs in [the original roadmap](PYTHON_CLIENT_ROADMAP.md). It does
not certify every sentence of its broader capability proposals or every public
operation; a complete public-operation/feature negotiation inventory remains work.
Phase5 is an optional separate product, not automatically a scientific-client gate.

Baseline: integration `e09a7de7cac6b48526fd8b7ac72879abb36a77a3`. Bounded merged
features are not whole-scenario acceptance. Candidate PRs #133–#141 remain
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
| P1.2 | New process restores observation/job and reads without disposal | C; saved handles, bounded native/restart evidence in #89; #139 local anti-rebinding guard | OPEN: full retained-job/history chain and Python notebook/process matrix; local binding is not manager generation or execution continuation [I,T,B] | #89; GAP: retained-job contract/task |
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
| P3.3 | Cancel/completion/expiry/restart remain distinct; no silent resubmission | C; bounded states, async cancellation/ambiguity; #139 rejects live Job binding drift | OPEN: full retained-job/expiry/restart history and service retention/generation contract; source-level manager counter reset is not a deployed alias repro [I,T,B] | #89; GAP: retained-job acceptance/contract |
| P3.4 | Explain resources/models/components, accepted plan and semantic updates | G/H; recorded plan/activity/provenance projections | OPEN: complete lineage/dependency extraction and served semantic-update evidence; recorded dataflow is not portable replay [I,T,B,S] | GAP: complete lineage/replay contract tasks |
| P3.5 | Bounded notebook jobs, own-wait cancellation, no shared deletion | C; async/runner limits and lifecycle separation | OPEN: served multi-principal notebook/job cancellation race fixture at supported deployment closure [T] | #89; GAP: explicit notebook scenario |
| P4.1 | Meaning-based candidates with applicability/dependency/version/access inspection | H; semantic search and resource inspection | OPEN: applicability/version/history/access matrix and capability-aware diagnostics for unsupported projections [I,T,B] | GAP: itemized discovery/version workflows |
| P4.2 | Parse/validate model/ontology at known revision; stale never current | H; supported parsing/validation and revision guards; #141 read-only four-family previews/DTO wire test | OPEN: actual maintained-parser/source/revision/invalid-document acceptance; DTO compatibility is not parser or semantic acceptance [I,T,B] | GAP: broader authoring/validation criteria |
| P4.3 | Import data, annotate semantics, use in observation with input provenance | F/H; existing export/adaptation/inspection primitives | OPEN: enumerate actual supported import/annotation route/schema and build end-to-end client workflow; do not invent endpoint [I,T,B,D] | GAP: maintained import/annotation task |
| P4.4 | Authorized edit/publish with locks/versions and explicit conflicts | H; namespace-only ADD and honest unsupported boundaries | OPEN: edit/history/lock/conflict/publication contracts plus supported client mutations; namespace ADD is not general authoring [I,T,B,D] | #129 scope decision; GAP: full authoring tasks |
| P4.5 | Run agent, scoped outcomes, reconnect without owning compiler/scheduler | H; #111 preview; #134/#135 status/policy; #138 deferred registration/handle and actual native AMQP adapter | OPEN: maintained production broker/principal/incarnation provisioning, actual managed execution/terminal confirmation, private-session lifecycle and independent owned cleanup; native-wire peer is not managed Runtime acceptance [I,T,B,D] | #125 |
| P5 | Optional provider SDK: scoped computation, budgets/cancel, valid attributed outputs | No delivered worker SDK; runtime/compiler not ported | OPEN: separate product/execution/isolation/output/provenance specification and implementation; not a client-only bug [I,T,B,D,S] | #127; optional scope decision |
| R1 | Offline/exact contracts/wheel/live fixture, mixed versions, failures and science | R/C/F; bounded accepted tests and retained historical evidence | OPEN: fresh-clone live fixture, broader unchanged-baseline Java matrix, mixed-version/credential/stream/restart tests, independent scientific oracles [I,T,B,D,S] | #89/#129/#130; current Java build failure in #89/#125 |
| R2 | Public support/version/limitations/deployment/migration; separate evidence layers | R; release tooling, no publication authorization | OPEN: maintainer support/version/publication decisions and deployment/scale/provider evidence; packaging success is not authorization [T,D,S] | #129/#130 |

## Reproducible next development, without fabricating Java bugs

### Further development pass: implementation is not acceptance

- [#138](https://github.com/integratedmodelling/klab-services/pull/138) at
  `d04fb4e6c`: actual deferred BEHAVIOR HTTP registration/typed handle and native
  AMQP lifecycle adapter;155new/2479offline tests,16local gates and46selected
  Java/broker tests pass. Python and native-wire Linux CI pass. Qpid9.2.0 lacks
  Pika's confirm/nack capability pair: strict mode rejects, explicit opt-in stays
  UNCONFIRMED. Generated-credential native wire fixture verifies negative auth/
  ACL controls and zero owned queues, not managed execution/remote graph cleanup.
- [#139](https://github.com/integratedmodelling/klab-services/pull/139) at
  `7a33c2e60`, stacked on #140:183new/2392offline tests and14local gates pass.
  Pins existing Job/AsyncJob operations against mutable scope/ID/origin/credential/
  peer/decoder drift; actual loopback HTTP provides positive controls. Final
  push+PR Python CI passes. This is not an authenticated manager-generation token,
  retained history, two Java deployments or cross-restart continuity.
- [#140](https://github.com/integratedmodelling/klab-services/pull/140) at
  `28fb28bb4`: separate deterministic-copy prerequisite,2209tests/13gates and
  final Python CI pass. #139's first push CI **failed** the filesystem-order-
  sensitive partial-output test. Forced normal/reverse traversal reproduced it;
  sorting copy paths fixed it without changing bytes, scientific oracle, failure
  evidence or receipt meaning. That internal failure was not waived as external.
- [#141](https://github.com/integratedmodelling/klab-services/pull/141) at
  `568e19632`, stacked on #140:57new/2266offline tests,14local gates and a
  production-Jackson/KnowledgeClass DTO check pass. Read-only namespace/ontology/
  strategy-document/behavior previews use a separate DTO; authoring write
  admission stays namespace-only. Final Python/native-model/DTO-wire CI passes.
  DTO-only fixture text is **not parser acceptance**.

Every item remains draft/unmerged. See those PRs for current remote CI; local
counts identify exact feature inputs, not a supported release or original-study
acceptance. Full Java logs for #138/#139/#140/#141 still reproduce EXTENT/ATTRIBUTE;
Jenkins is separately red and its cause unverified. No scientific-meaning change,
dependency substitution, historical mutation replay or check bypass occurred.

An additional **local-only** composition of #133–#141 at the above feature heads
and #137's earlier `af8ddbb8f6c01a429f1b5003e895045efa416995` map snapshot
passed2758offline tests,19Python gates (15coverage), lint/package and47selected
Java/broker tests. Tested source tree `6cc3b1fd71ec7b2d02ec9b44b0a056f1c577679f`;
local commit `80546320525bfd93d06cda495c4bc58fd6dda982` was not pushed or merged
as a PR. README's adjacent feature-link conflict was resolved by retaining both
paragraphs; no production-code conflict needed resolution. This later map update
is not part of that recorded tree, and native broker PASS is not full Runtime
acceptance. Historical earlier composition below remains separately identified.
Exact public input SHAs, conflict resolution and evidence hashes are recorded in
[the additional composition receipt](../notes/python-roadmap-combined-next-results-20261103.json).

To reproduce that additional tree, first reconstruct the earlier composition
below (through its tree check), then in the same new disposable checkout:

```sh
git -c user.name=LocalValidation -c user.email=local@example.invalid merge --no-edit d04fb4e6cc96f51603cca06de03af24a4ed6bf17
git -c user.name=LocalValidation -c user.email=local@example.invalid merge --no-edit 7a33c2e605b9912929773c405c292880270c4595
git -c user.name=LocalValidation -c user.email=local@example.invalid merge --no-edit 568e19632efebc97f203a8a6c71eda0140f6165b
# The last command is expected to stop at one README-only content conflict.
python - <<'PY'
from pathlib import Path
path = Path("python-client/README.md")
lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
assert sum(line.startswith("<<<<<<< ") for line in lines) == 1
assert sum(line.rstrip("\r\n") == "=======" for line in lines) == 1
assert sum(line.startswith(">>>>>>> ") for line in lines) == 1
# Keep both complete link paragraphs, with one separating blank line.
path.write_bytes("".join("\n" if line.rstrip("\r\n") == "=======" else line
    for line in lines if not line.startswith(("<<<<<<< ", ">>>>>>> "))).encode("utf-8"))
PY
git add python-client/README.md
git -c user.name=LocalValidation -c user.email=local@example.invalid commit --no-edit
git -c user.name=LocalValidation -c user.email=local@example.invalid merge --no-edit af8ddbb8f6c01a429f1b5003e895045efa416995
git rev-parse 'HEAD^{tree}'
```

Expected tree is the later `6cc3b1fd71ec7b2d02ec9b44b0a056f1c577679f`, not the
earlier tree below. On PowerShell pipe the same Python resolution body in a
here-string to `python -`; quote whole Maven `-D...` arguments. Run that tree's
Python workflow commands with their declared working directories, then the
committed `agent-native-amqp.yml` selection plus `SourcePreviewWireTest` for the
native/DTO tests. Set `agent.python` to an events-extra interpreter: skipping
interop is not acceptance. No private live Runtime helpers are needed for these
bounded checks; they do not run the unaccepted full-stack/scientific scenarios.

### Still-independent next work

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
