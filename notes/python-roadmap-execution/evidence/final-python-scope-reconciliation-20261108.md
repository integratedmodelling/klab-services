# Exact merged-candidate Python scope reconciliation

**Candidate:** `161f06f7a01f9dd4b69df130b0044051794e8db5` (ordinary PR #317 merge).
**State:** all 42 bounded Python scopes reconciled; bookkeeping delivery and owned
cleanup pending. **Not full original/native acceptance, scientific approval or release.**

All 42 original IDs and embedded historical audit records are unchanged. All 17 stable
Java-gap IDs/documents remain, including failed evidence and missing historical
`NullScannerReproducer.java` custody. No issue closed or native gap resolved.

## Fresh verification

- Source/wheel/sdist/installed parity: SDK 71, worker 6, reference 37 modules and 13 assets.
- Installed suites: SDK **5305**, tooling **38**, worker **116**, reference **538**,
  governance **39** passed; zero failures, errors or skips.
- Both Windows/Linux-target mypy: **114 files**; fatal lint passed.
- Fresh installed owning-thread traces: **5305 SDK + 538 reference tests** passed.
  All **455 nonabstract callables** linked through **130 reviewed entries**;
  **51 abstract declarations** intentionally uncalled/unmapped; all 506 classifications retained.
- Complete SDK/helper coverage replay: 5305 passed; **40 unchanged checker commands
  under each of two preserved bases** passed. No thresholds, checker scope or exclusions changed.
- All required Python/reference test references match fresh executed-test bytes.
  Seven historical parameter references additionally match exact passing JUnit names;
  no parameter-specific operation attribution inferred.
- All 28 campaign PRs #290–317 independently verified remotely merged and ancestors
  of this candidate. #315/#316 reconciled heads each had 48 successful rollup checks;
  #317 had 25. Exact commit check runs independently SHA-matched. Earlier heads/runs
  are historical, not substitutes for updated-head checks.

Measured SDK coverage remains 10481/10481 statements, 3320/3322 branches and 165 existing
exclusions. This is **not** unexcluded/branch 100%, exhaustive input coverage or
whole-function correctness. Traces do not measure worker threads/processes/kernels
or invocation counts. Dependencies are borrowed from the existing interpreter,
not freshly isolated. The reference service/instrumentation are absent from SDK/worker wheels.

## Row-specific dispositions

`P` means `PYTHON_COMPLETE_SIMULATOR_VERIFIED`: only the bounded implementation and
local/mock/finite-reference verification stated in that row. It does not confer native
acceptance or remote authority. Three rows use explicit blocked-axis dispositions.

Queue receipt fragments identify rows in the following single table:
<a id="CAP-04"></a><a id="RC-P1.5"></a><a id="CAP-01"></a><a id="RC-P1.3"></a><a id="CAP-05"></a><a id="RC-P4.2"></a><a id="CAP-14"></a><a id="RC-P4.4"></a><a id="CAP-13"></a><a id="RC-P4.1"></a><a id="RC-P4.3"></a><a id="CAP-10"></a><a id="CAP-11"></a><a id="RC-P3.1"></a><a id="CAP-06"></a><a id="RC-P3.4"></a><a id="CAP-03"></a><a id="RC-P4.5"></a><a id="CAP-17"></a><a id="RC-P5"></a><a id="CAP-09"></a><a id="RC-P2.2"></a><a id="CAP-15"></a><a id="RC-P2.5"></a><a id="RC-P0"></a><a id="CAP-02"></a><a id="RC-P1.1"></a><a id="RC-P1.4"></a><a id="CAP-07"></a><a id="RC-P1.2"></a><a id="RC-P3.3"></a><a id="RC-P3.5"></a><a id="RC-P2.1"></a><a id="RC-P2.3"></a><a id="RC-P2.4"></a><a id="CAP-08"></a><a id="CAP-12"></a><a id="CAP-18"></a><a id="RC-P3.2"></a><a id="CAP-16"></a><a id="RC-R1"></a><a id="RC-R2"></a>

| Original ID | Disposition | Reviewed Python scope | Unestablished/external boundary |
| --- | --- | --- | --- |
| CAP-04 | P | Immutable endpoint selection, bounded collections and selection-preserving saved restoration/submit/resolve | General semantic grouping/native relationship applicability |
| RC-P1.5 | P | Explicit typed plans and independently calculated finite grid/namespace/project/scenario/observer effects | Native model ranking/admission/provider numerical science |
| CAP-01 | P | Installed certificate/token/profile/catalog/renewal/expiry/privacy and explicit routing controls | Production issuer/license/roles/deployment approval |
| RC-P1.3 | P | Current-principal reads and opted-in advertised READ grant/inspect/revoke/expiry | Native granular rights/sharing TTL/retention |
| CAP-05 | P | Source/hash/knowledge/worldview/revision-bound validation and explicit revalidation | Actual Java semantic admission/knowledge transactions |
| RC-P4.2 | P | Parser/source preview versus revision-aware finite validation evidence | Actual native accepted-source/admission authority |
| CAP-14 | P | ADD/locks/import/deferred registration plus explicit conditional source/validation/publication reference | Native CAS-safe publication/compiler/actor acceptance |
| RC-P4.4 | P | Opted-in source/knowledge CAS update/validate/publish/readback and failure controls | Native semantic publication/retention or public release |
| CAP-13 | P | Existing membership and explicit source-bound finite dependency graph inspection | Native transitive closure/access completeness |
| RC-P4.1 | P | Provider/version/source/hash/access pins, pagination, cycles/gaps/budgets | Actual scientific dependency selection/closure |
| RC-P4.3 | P | Public import/annotation/observation/provenance join, denial/failed-import owned cleanup | Real provider lineage or fabricated remote resource deletion |
| CAP-10 | P | Bounded graph/cache plus opted-in checkpoint/delta reconciliation | Native scientific commits/durable history |
| CAP-11 | P | Explicit notification hints/cursors, gap/epoch resync; Job finish is not commit | Native durable replay/broker order/permission leases |
| RC-P3.1 | P | Finite convergence with duplicate/out-of-order hints and explicit resync | Native full-graph snapshot/revision authority |
| CAP-06 | P | Bounded provenance/activity and selected execution explanation | Native complete scientific execution lineage |
| RC-P3.4 | P | Resource/model/component versions and planned-versus-recorded/failed effects | Durable root commit or proof of actual science |
| CAP-03 | P | Independent authorized A/B capture and typed C-owned copied result lifecycle | Live federation/upstream grant delegation/durability |
| RC-P4.5 | P | Source preview/deferred controls and finite reconnectable managed outcome/disposal workflow | Actual k.Actors/compiler/native private-session disposal |
| CAP-17 | P | Separate public worker protocol, exact receipts, aggregate output budget and deployment handoff | Hostile-code sandbox/hard-total-disk/container authority |
| RC-P5 | P | Installed worker failure/recovery and declared resource/output custody | OS/kernel isolation/deployment approval |
| CAP-09 | P | Explicit finite spatial windows/read grants and exact layout/masks/units/pins | Native provider resampling/time/scientific accuracy |
| RC-P2.2 | P | Explicit scan/export/temporal-state selections, assembly/cancel/expiry/release uncertainty | Native time-axis/state-retention authority |
| CAP-15 | P | Detached raster/vector/array/tabular/notebook raw codes/masks and declared labels | Native dictionary/worldview/time authority |
| RC-P2.5 | P | Exact categorical integers and explicit band instants, no inferred conversion | Provider categorical/temporal scientific acceptance |
| RC-P0 | P | 455 nonabstract reviewed test associations, 51 abstract intentionally uncalled, exact installed/test bytes | Exhaustive correctness or scientific Resolver encoding; encoder remains unsupported |
| CAP-02 | P | Public configured-origin onboarding/provider routing and failure/expiry matrix | Native issuer/roles/deployment acceptance |
| RC-P1.1 | P | Installed profile/catalog/certificate/token identity/readiness controls | Production issuer/license approval |
| RC-P1.4 | P | Explicit same-origin routing/renewal/no-replay/mixed-provider refusal | Native current rights/scientific readiness |
| CAP-07 | P | Sync/async Job result/cancel/wait and explicit restoration/lifecycle guards | Native retention/incarnation/interruption/disposal guarantee |
| RC-P1.2 | P | Offline saved identity and fresh current-credential restoration/refusal | Saved identity is not a credential/ownership grant |
| RC-P3.3 | P | Installed restart/expiry/identity fence/no-replay matrix | Native durable lifecycle/cleanup confirmation |
| RC-P3.5 | P | Installed independent notebook clients/concurrent Jobs/own-wait cancellation/graph reconciliation | Kernel operations unmeasured by owning-thread traces; native collaboration science |
| RC-P2.1 | P | Synthetic multishard and authentic recorded-data known-answer coordinate/mask/precision workflows | Recorded data is not fresh actual-provider execution |
| RC-P2.3 | PYTHON_PREPARATION_COMPLETE_NATIVE_ACCEPTANCE_BLOCKED | Executable actual-provider comparison runner and strict independent-reference validation | Approved pinned model/data/CRS/datum/tolerances/rights and native execution; scanner/reproducer custody |
| RC-P2.4 | PYTHON_BOUNDED_CONTROLS_COMPLETE_NATIVE_SCALE_BLOCKED | Bounded-memory/interrupted streaming, byte budgets and destination preservation | Representative approved native provider scale/soak/deployment load |
| CAP-08 | P | Installed scalar integer/Decimal precision/missingness and partial/stale refresh | Every input/branch or native scientific correctness |
| CAP-12 | P | Socket-forbidden exact offline snapshot roundtrip and optional-dependency safety | Offline payload is not fresh remote permission/state |
| CAP-18 | P | Origin/identity/privacy/no-replay/no-fallback regressions | General OS/network hostile-code security |
| RC-P3.2 | P | Explicit partial graph refresh/invalidation preserves uncertainty | Native complete graph/current permission or revision lease |
| CAP-16 | P | Fresh packages/assets/full suites/types/lint/coverage and explicit mixed-version refusal | Actual mixed native service tuple compatibility/fresh dependency isolation |
| RC-R1 | P | Exact candidate integration, 42-row/17-gap custody and tested support/migration limits | Native acceptance or broad OS/support/release certification |
| RC-R2 | PYTHON_READINESS_TOOLING_COMPLETE_RELEASE_BLOCKED | Candidate-bound checker retains all eight gates and explicit NOT_ELIGIBLE outcome | Publication/develop/support approval and human/scientific attestation |

## Readiness and outstanding work

All eight gates retained: offline, coverage, core-package, owned-lifecycle and
scientific-reference **PASS within the finite/recorded scope above**; security-failure,
scale and server-compatibility **BLOCKED**. Checker exit **3 / NOT_ELIGIBLE**;
`publication_authorized=false`. Scientific-reference PASS is not real provider science.
Hashes and operator-authored PASS states prove custody, not truth or human approval.

Implementation/finite-reference reconciliation is distinct from acceptance of the full
original requirements. Remaining owned work: ordinary CI/merge/checkpoint of this
bookkeeping and safe worktree/cache/kernel/service/window cleanup. Native/external gates
require approved evidence/authorization, not unsolicited repair or simulator fallback.

## Immutable evidence custody

Root: `C:/Users/lumsd/AppData/Local/Temp/opencode/full-roadmap-20261106/`.
Detailed row receipts retain each original requirement, exact test references/hashes,
JUnit parameter custody, campaign deliveries and native gap links.

| Artifact | SHA-256 |
| --- | --- |
| `exact-merged-roadmap-final-first/final-42-row-scoped-reconciliation.json` | `c3e17b151b173386cc34d58f79bfc055b70cf4c1f7d88291beb5281ad0196352` |
| `exact-merged-roadmap-final-first/final-verification-summary.json` | `0728da062ef7f9acd1f24ee8fae0ef1a1bc4d90b3a3737b030ec7f49cb06b12d` |
| `exact-merged-roadmap-final-first/receipt.json` | `25ed2ec6862df420fd76e5a245a9955b55bf276ab1aa757f31cdf8db0aceca7c` |
| `reconciled-final-campaign-deliveries.json` | `8b37ef2d8f4139b9e0ceda47f5750dccf17212bb429333a3afbe7f5802416809` |
| `../final-roadmap-scope-review.json` | `7600208a7af15aaf42b791c8d84133a4bbb5ab55e1ed843f13f47719c8f068b0` |

All earlier failed/premerge evidence retained; these receipts do not overwrite it.
