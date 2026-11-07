# Resume the full Python A+B+C campaign

This is a resumable instruction, **not a daemon or guaranteed automatic restart**.
The supported OpenCode CLI provides `run --session`; no documented unattended
restart guarantee was established. Do not inject into a concurrently running
session, add permission bypasses, replace global configuration or spawn duplicate
agents. If this process ends, an operator may resume the existing session with:

```powershell
opencode run --session <existing-session-id> --file notes/python-roadmap-execution/RESUME_FULL_ROADMAP.md
```

1. Read repository instructions and `MODELER_IDE_STATE.md`. Python-only work needs
   no live IDE readiness check or repair. Read the live sidebar tasks.
2. Inspect `git status --short --branch`, `git log -5 --oneline`, remote integration
   and the queue's active PR state. Preserve unfamiliar/uncommitted user work.
3. Run `python notes/python-roadmap-execution/roadmap_queue.py check` and `next`.
   Use `roadmap-execution-queue.json`, the full execution ledger and original
   acceptance audit; never reset the queue using `seed` after initialization.
4. Implement the selected remaining public SDK functionality and independent
   simulator scenario. The queue includes exact per-row missing work/tests and
   historical evidence. Java blockers do not stop independent Python work.
5. Require public sync/async tests, installed packages, source/artifact parity,
   data/auth/lifecycle/cleanup controls, relevant coverage/types/lint, synchronized
   gaps/handoff/reproductions and ordinary PR/final-head CI. Fix failures without
   weakened assertions/gates. Never claim native execution from simulation.
6. On merge, verify remote merge state; append receipt to both required ledgers;
   checkpoint queue active branch/PR/requirement/dependencies and row evidence.
   Immediately select and begin the next runnable requirement, not a final answer
   that treats the merge as campaign completion. Continue independent work if CI
   or review blocks another slice.
7. No Java repair, worldview/scientific-engine changes, historical study replication,
   develop promotion or public release. Final sleep remains forbidden until all
   implementable42row completion criteria pass and no independent work remains.

Most recent initial checkpoint: verified PR289 merge `48ef20741`, corrected head
`43acd7618`, all17reported final-head checks PASS; earlier provider coverage failures
remain in the receipt. Active branch initially `test/python-selection-workflow-audit`.
Next implementation: CAP-04 persisted relationship endpoints and collection queries.
Inspect current queue rather than assuming this initial checkpoint stays current.
