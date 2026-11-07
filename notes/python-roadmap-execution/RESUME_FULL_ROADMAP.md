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

Most recent verified checkpoint: PR291 merge `ceeaf623a`, exact `df2e1f102`,
all37reported final-head checks PASS. PR290 selection merge `6307e93f4` also verified.
Active child `feat/python-explicit-collaborator-rights`, direct requirement RC-P1.3;
new advertised opt-in shared-read SDK/simulator source is implemented with82SDK/
6publicHTTP/10fixture controls passing. Fresh installed verification and ordinary
sharing PR delivery remain pending; source freeze is recorded in both ledgers.
CAP-01 remains the existing certificate/token/profile seam, not shared-grant code.
Inspect actual queue and running jobs rather than assuming this checkpoint stays
current. After this delivery, immediately begin revision-aware validation and the
remaining independently runnable work; never stop at the next merge.
