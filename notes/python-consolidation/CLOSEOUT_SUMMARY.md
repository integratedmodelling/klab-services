# Dependency closeout / repository consolidation

**MILESTONE_INCOMPLETE — safe execution checkpoint, not milestone completion.**

## What changed

An isolated local branch `milestone/dependency-closeout-20261104` now preserves and
assembles the committed backend integration, freshly fetched develop and all nine
existing Python/documentation PR heads. Product candidate:
`53f925bd1e886e887ecb65149f44e4b7f3c496c4`. Supporting ledger/tooling is under
`notes/python-consolidation/`; the authoritative record is `progress-ledger.json`.
This is **local integration**, not nine GitHub merges or promotion to develop.

| Measure | Before | Verified checkpoint |
|---|---|---|
| In-scope open PRs | 9 | 9; none merged/retired this session |
| Candidate ancestry | Backend fixes absent from delivery | Local candidate includes merged backend #143/#146/#148–#190 and current nine PR heads |
| Python candidate verification | Historical/scattered | 2772 installed-wheel offline tests pass; exact 42-module source identity verified |
| Java | Historical inventory | Backend compilation passes; targeted regression 219 tests / one error; separate combined wire selection 22 pass |
| Dependency evidence | POM declarations not runtime proof | Effective POMs, per-module trees, 466 selected artifact hashes, component archive inspection and explicit HM0.12 probe |
| Services worktrees | 61 pre-existing | 62 retained, including this purposeful integration checkout; zero removed |
| Unpublished work | Ten worktree tips not on origin | Durable archive refs plus verified bundle and independent mirror-recovery verification of all ten tips |
| Dirty files / raw reports | Distributed | Binary patches/untracked copies and four historical report sets archived and indexed |

## Dependency decision

**TARGETED_UPDATE_RECOMMENDED; not adopted or merged.** Both component main and
the local corrected-exporter branch still declare HM 0.11.3-SNAPSHOT. HM0.12.0 is
available and resolves in an explicit probe, but its native GT34.6 baseline is
overridden to34.4 and H2GIS's H2 request is overridden to1.1.119 in the component.
Graphdb separately resolves mixed Jackson core/databind lines. Numerical/geometric,
packaging, duplicate-class and actually-loaded compatibility checks remain open.
The account cannot push to upstream geospatial; closed component PR#9 remains
preserved, not automatically reopened or treated as merged.

## Exact remaining open PR census

Fresh live readback remains nine open contributions, eight drafts:

- [#133](https://github.com/integratedmodelling/klab-services/pull/133)
- [#134](https://github.com/integratedmodelling/klab-services/pull/134)
- [#135](https://github.com/integratedmodelling/klab-services/pull/135)
- [#136](https://github.com/integratedmodelling/klab-services/pull/136)
- [#137](https://github.com/integratedmodelling/klab-services/pull/137)
- [#138](https://github.com/integratedmodelling/klab-services/pull/138)
- [#139](https://github.com/integratedmodelling/klab-services/pull/139)
- [#140](https://github.com/integratedmodelling/klab-services/pull/140)
- [#141](https://github.com/integratedmodelling/klab-services/pull/141)

No in-scope PR was retired or superseded. No unrelated open service PR was found.
Open issues remain #89/#123/#124/#125/#126/#127/#129/#130/#144. Closed #147 retains
unfulfilled Runtime acceptance; see `ISSUE_ACCEPTANCE_MAP.md`. Future roadmap,
PaL, worker SDK, global federation and durable replay remain separately uncertified.

## Missing gates and next owner/action

1. **Implementing CLI/backend reviewer:** resolve or explicitly classify the
   reproduced issued-query-receipt regression, preserving alias/rollback/security
   assertions; run relevant and combined gates. Preserved dirty repairs are not
   accepted fixes and must be reviewed before reuse.
2. **Implementing CLI/scientific reviewer:** establish current ODO/language source
   compatibility without overwriting the frozen oracle. Run one independently
   checked real-Java workflow through the exact installed combined package.
3. **Implementing CLI/dependency maintainer:** choose and verify the minimal stable
   HM/GT34.x baseline, including H2/Jackson and package/load evidence; review and
   merge the needed focused dependency proposal through permitted repository workflow.
4. **Implementing CLI/repository maintainers:** finish each focused contributor
   review/current-base acceptance, fix findings, obtain required CI/Jenkins/review
   decisions, merge in verified dependency order and re-query GitHub. Passing
   original-head focused tests and local composition are not substitutes.
5. **Implementing CLI/operator:** use an explicitly owned nonconflicting controlled
   stack. Do not restart, stop, mutate or appropriate the user's Modeler services.
6. **Implementing CLI:** finish worktree purpose/ignored-artifact/lock inspection,
   preserve remaining unique raw evidence, produce a retirement dry run and only
   remove individually proven safe paths; verify final reachability/status/census.

All original worktrees remain; retaining UNKNOWN/unique/active paths is deliberate.
Local raw archives are access-controlled to the current user, SYSTEM and local
Administrators. Shared publication requires redaction review; no raw private logs
or Maven settings were committed. See `EVIDENCE_INDEX.md` and `evidence-index.json`
for file-level checksums and navigation. Milestone completion must remain false.
