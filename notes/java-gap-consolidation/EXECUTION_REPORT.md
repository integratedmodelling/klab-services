# Java-gap consolidation — execution report and resume checkpoint

Date: 2026-11-08 UTC. **Not a native acceptance or completion certificate.**

## Delivery and evidence

- PR: https://github.com/integratedmodelling/klab-services/pull/320
- Base: `feature/python-experiment-runner`, verified integration
  `9db975b21146093a7962d29e28345a762519befd` (#319), not develop.
- Working branch: `docs/java-gap-canonical-consolidation`.
- Canonical handoff: [JAVA_GAPS_FOR_FERDINANDO.md](../../docs/java-service-contract-gaps/JAVA_GAPS_FOR_FERDINANDO.md).
- Executed 18-record installed-package suite at implementation candidate
  `ee8bf4e4dde8b893de36a2601b2b86aa88e264c6`: 416 simulator/source guards,
  1,319 SDK tests, 116 worker tests; zero skips/failures/errors. Later passes add
  receipt validation, stricter capability JSON checks and documentation/status
  synchronization; their exact-head CI must also pass before merge.
- Latest focused local governance pass: **72 tests**, fatal lint, canonical
  relative-link/selector validation, generated-report drift and source inventory.
- All 18 static native preflights returned the expected exit 3 / REAL_NOT_RUN.
- Full sanitized pass/failure logs and custody hashes:
  [receipt index](../../docs/java-service-contract-gaps/evidence/consolidation-20261108/receipt-index.json).
- Existing Python queue `check/next`: 42 scoped dispositions, zero runnable rows;
  no native/research/release/develop acceptance was promoted.

## Coverage

18 stable records: 2 historical confirmed defects, 4 source-scoped missing client/API
contracts, 7 provisional contracts, 5 untested complete native integrations.
17 simulator passes and one explicitly unimplemented encoder substitute (0018).
37 simulator modules, 33 CLI source scenarios, 54 indexed contract cases and 42
current roadmap rows are classified. All original 17 IDs/aliases and all original
42 row histories survive. Seventeen issues are cross-referenced, 11 open; no issue
write, reassignment or relabel was made. Staging source was accessible and inspected
at `0de15fbafab988455de87edd7d4581a7c22000ac`, not executed.

Native behavioral tests executed: **0**. Complete native reproducers missing:
**all 18**, with the exact individual reasons in the canonical records/backlog.
Repository custody/capability probes and historical TIFF inspection do not count
as native reproductions. Resolved setup history is not counted as a missing API.

## Remaining delivery gate and resume instructions

This checkpoint precedes final exact-head CI/review/ordinary merge confirmation.
Do not represent an open PR, initial-head green checks or this report as completion.

1. Inspect `git status --short --branch`; preserve any unfamiliar changes.
2. Fetch origin and verify PR #320 head/base, reviews, threads, check conclusions
   and ancestry. Do not use develop as the campaign base or bypass protections.
3. Run `govern.py validate`, `govern.py generate --check`, `source_coverage.py`,
   the governance tests and fatal lint from the canonical quick start. Evidence
   output paths must be unused; preserve every failed receipt.
4. Address substantive review findings with document/index/test updates in this
   same PR. Do not treat native unavailability as authorization for Java repair.
5. Only after review and exact-head checks pass, merge normally and independently
   verify remote merged state plus local ancestry. Then run queue `check/next`;
   do not create speculative Python work when it reports no runnable row.
6. Save remote exact-head/merge receipts and an updated operational checkpoint.
   Verify owned tests/services/watchers/Git operations/windows have ended; do not
   terminate unrelated processes or remove the original worktree's coverage/cache.

## Conditional Windows sleep authorization

The user authorizes **suspend only after the entire specification is verified
complete**, all required merges/checks/review are confirmed, reports/checkpoints
are saved and writes are safe. No shutdown, restart, hibernate or intermediate
sleep is authorized. If review/CI/merge or other consolidation work remains
unfinished, **leave the computer awake**. Native gaps documented as evidence
boundaries must never be fabricated as passes merely to reach the sleep gate.

The original worktree remains outside this owned consolidation worktree. Private
failed-environment receipts, public staging source clone and clean installed test
environment are retained for reproducible resumption; retention is not a running
operation or permission to delete unrelated user state.
