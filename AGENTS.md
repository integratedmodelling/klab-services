# Collaboration requirements

- Full Python A+B+C work uses `notes/python-roadmap-execution/roadmap-execution-queue.json`
  alongside the existing ledgers. Preserve all42original IDs/history. At a completed
  task or verified merge, checkpoint remote state and immediately begin the next
  independently runnable requirement; a PR/test/milestone is not a stopping gate.
  `roadmap_queue.py check/next` validates/selects work; `checkpoint --pr N` verifies
  remote merged state and local ancestry without promoting acceptance. Saved resume
  instructions are not an automatic-restart guarantee. No final sleep until the full
  implementable roadmap criteria and owned cleanup are verified.

- Before Python-client A+B work, consult
  `notes/python-roadmap-execution/MODELER_IDE_STATE.md` and its timestamps. Run its
  bounded read-only check only when the task needs live Modeler gates. Python-only
  work must not depend on IDE readiness; unavailable gates do not authorize repair.
- Maintain `notes/python-roadmap-execution/AB_COMPLETION_LEDGER.md` as the single
  ongoing A+B campaign ledger. Simulator verification, real Java compatibility and
  scientific Java execution are distinct. Never silently fall back from real
  services to simulation, and never ship simulator code in the production SDK.

- Give development-pass updates directly in chat. Never require the user to open
  a separate document to understand what was done or the current status.
- Each substantive status update must explain: the current PR state, what changed
  in the latest pass, what remains, why work is blocked (if applicable), and the
  concrete path to unblock it, including required evidence or authorization.
- Supporting documentation may preserve evidence but must not replace a
  self-contained chat update. Distinguish verified results from hypotheses and
  implementation completion from acceptance completion.
- Launch background services without creating visible terminal windows. Include
  agent-created console windows in cleanup checks; do not close unrelated user
  terminals or terminate restored services merely to remove window clutter.
