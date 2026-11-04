# Integrated acceptance — INCOMPLETE checkpoint

## Targeted closeout readback — 19:55 UTC

Reviewed against milestone commit `d3801094548c4535efb1ffa6a1b83abb833ac379` using
the contributor guide. Product source, accepted delivery, component proposal and
flattened artifact are unchanged; all2,452 indexed evidence files still match.
No Java/scientific rerun is warranted. Thirteen services PRs remain merged and
services have no open PR; component11 remains ready/open with `push=false`.

Only documentation and ledger-output encoding changed: the acceptance map now
labels its historical PR/build claims and links accepted bounded workflows without
closing broader criteria; explicit UTF-8 output prevents the reproduced PR-ledger
decode failure on Windows. Prior raw ledger bytes were archived before regeneration.
Review recommendation: **Ready for maintainer review** for this narrow record
correction; no demonstrated blocking defect, no maintainer approval implied.
No scientific, authorization, mutation/replay or deployment code changed.

Validation on this documentation/tooling delta: **11 map/handoff tests pass**,
**6 evidence-tool tests pass**, Ruff passes, and all supporting top-level records
decode as UTF-8. Commands, dirty/source identities, current issue/PR census and
readback results are preserved under
`.consolidation-evidence/live-ledger-20261104T195503Z/`.
The milestone remains incomplete only for the external component merge action.

## Final execution reconciliation (supersedes status, not historical failures below)

All original PRs#133–#141 are merged, including #138 at
`87ef80ff589428f7c088ba60abcb7cf2b88276eb`. Prerequisites#191/#192/#194/#195
also merged normally after applicable checks. Current consolidated product source
at `4757e33d93a3d758df4d9392359d9f6d0b928c2a` matches accepted delivery for
the scientific Java/Python/host dependency scope. `final-source-reconciliation-20261104`
records exact compared paths and equivalence to the final test and successful
managed/science candidates. Later documentation commits are not retrospectively
claimed as tested code revisions.

- **225 selected Java tests**, zero failures/errors/skips, including H2GIS host
  initialization and explicit local broker configuration regressions.
- **2,772 offline Python tests**, one retained Rasterio warning, from the noneditable
  wheel whose complete42-module byte/hash inventory exactly matches this checkout.
  A first final-check attempt used the sequential PR wheel and rejected three
  byte mismatches before tests: Git product content was equal but Windows line
  endings differed. No result was accepted from that mismatched inventory. The
  immutable original combined environment matches exactly and passed the rerun.
- Real Java123.25m/20 valid cells, actual grid support, partial provenance,
  independent-client restoration, scope/listing/process/listener cleanup pass.
- Genuine deferred managed Runtime behavior reports READY/RUNNING/STOPPED using
  an owned private broker, not a fake peer. Publication remains UNCONFIRMED;
  remote-agent cleanup and durable-root proof remain UNKNOWN.
- Actual flattened HM0.12 component/host GT34.4/H2GIS probes and new-stack science
  pass. Component#11 is ready but upstream merge is blocked by account permission.

See `SCIENTIFIC_ACCEPTANCE.md`, `MANAGED_AGENT_ACCEPTANCE.md`, `DEPENDENCY_MANIFEST.md`
and `PR_LEDGER.md`. Original frozen ODO/large-worldview incompatibility remains
visible in issue#193; no original assertion or negative test was waived. The
whole milestone is **not COMPLETE** until component#11 is merged/approved superseded
and the final closeout reconciles that external action.

## Historical checkpoint evidence

## Exact identities

- Intended delivery base: `feature/python-experiment-runner`,
  `e09a7de7cac6b48526fd8b7ac72879abb36a77a3`.
- Backend compile/regression candidate: `52197c41b01213628e1fbf4e5290933eb9350f2b`.
  Parents include existing committed integration `5980cb54d` and fetched develop
  `196bb9dc142eafc92f4456c5073933fa98ad1c42` through recorded merge ancestry.
- Combined Python/backend candidate: `53f925bd1e886e887ecb65149f44e4b7f3c496c4`.
  All nine current PR heads are actual ancestors. Assembly retained earlier
  composition `805463205` and explicitly merged current #139/#136/#137 heads.
  No conflict resolution was required in this session.
- Consolidation notes were untracked/dirty during some runs; each run records
  the exact status, diff digest and untracked file hashes. No product source was
  dirty. Later documentation/tooling commits are not retrospectively tested SHAs.
- JDK Temurin 21.0.12.1; Maven 3.9.5; Python 3.12.7. Installed dependency freeze and
  complete 42-module source-hash comparison are retained with the combined wheel.

## Checks actually performed

| Gate | Candidate / scope | Status | Result and limitation |
|---|---|---|---|
| Initial dependency trees | Original services delivery and historical corrected component | PASSED inspection | 466 selected module/artifact rows hashed, none missing; not final compatibility |
| HM 0.12 resolver probe | Unchanged component SHA, explicit command-line override | PASSED inspection | GT34.4 and H2 1.1.119 still forced; no adopted POM change |
| Backend server dependency compilation | `52197c41b` | PASSED | Runtime/Resources/Reasoner server dependency reactor; tests skipped explicitly for compile only |
| Targeted Java regression reactor | `52197c41b` | FAILED | 219 tests, zero failures, one error, zero skips; Runtime-server skipped by normal reactor failure |
| Per-PR installed-wheel focused gates | Nine original heads | PASSED focused only | Counts below; not combined-base reviews or complete feature acceptance |
| Combined wheel build/install/source identities | `53f925bd1` | PASSED | All 42 package `.py` files match candidate SHA256 inventory; wheel retained, no editable installation |
| Combined Python unit/integration suite | `53f925bd1` | PASSED | 2772 passed, one expected Rasterio georeferencing warning; no live tests selected |
| Combined Java wire/reconnect selection | `53f925bd1` | PASSED | 22 tests, zero failures/errors/skips; common-module selection, not full Runtime acceptance |
| New audit-tool parser tests/lint | Local notes tooling | PASSED | Four parser regressions; Ruff check passes after fixes, no rule exclusions |
| Full combined Java/reactor/packaging gate | Final assembled candidate | NOT_RUN | Not inferred from compile, historical inventory or bounded selections |
| Managed Runtime agent/real broker lifecycle | Final assembled candidate | NOT_RUN | Native DTO tests do not establish managed-agent acceptance |
| Installed-client real-Java scientific demonstration | Final assembled candidate | BLOCKED | Current compatible fixture/ODO/language identity and separate owned stack not established |
| Remote PR merge closeout | All nine contributions | BLOCKED | Nine open, eight draft; no remote merge/retirement/approval bypass performed |

### Original-head wheel-focused results

| PR | Tests | Installed source modules verified |
|---|---:|---:|
| #133 | 6 | 34 |
| #134 | 61 | 35 |
| #135 | 116 | 36 |
| #136 | 29 | 35 |
| #137 | 5 | 34 |
| #138 | 155 | 39 |
| #139 | 195 | 35 |
| #140 | 47 | 34 |
| #141 | 57 | 35 |

Counts overlap and are **not** an additive suite total. First attempts reused an
editable package from a different worktree and are not source-matched acceptance;
all affected gates were rerun through verified noneditable wheels. The old main
venv also lacked httpx and failed before collection. Both failed attempts remain
preserved without being blamed on the product or silently relabelled passing.

## Reproduced blocker

`CollectiveSelectionAtomicExecutionTest.actualConsumerFailureReceivesSelectedAliasAndRootRollbackDiscardsClassification`
still throws `Generic multi-contributor query inputs require an issued query receipt association`
at `DigitalTwinImpl.java:1665` → `CompiledDataflow.java:2000`.
The exact 219-test reactor command is in
`klab-combined-targeted-java-20261104/run.json`; copied XML and per-module totals
were preserved under that run's `reports/` **before** later common-module tests.
No failure-ignore mode or assertion weakening was used.

The later Python composition changes no production Java source relative to
`52197c41b`; that source comparison does not turn its failed gate into a pass.
The closed issue #147 still has an unresolved acceptance requirement.

## Scientific and deployment boundaries

No real-Java scientific Job was created, resumed, cancelled or deleted this
session. No independent numerical result, provenance/recovery, service ownership
or remote cleanup acceptance is claimed. The original synthetic oracle remains
123.25 metres across 20 cells; frozen ODO/model assertions are unchanged.

The user's Modeler started during this session and now owns seven JVMs, including
services on 8091–8094. Those processes were identified read-only and left untouched.
Their command-line JAR names are not proof of loaded classes or matching candidate
source. Do not use the fixed-port fixture launcher against them or call them the
consolidation deployment. A separate controlled nonconflicting stack is required.

No artifacts, snapshots, packages or releases were published; no maintainer
messages, GitHub reviews, issue changes, PR updates or merges were sent.
