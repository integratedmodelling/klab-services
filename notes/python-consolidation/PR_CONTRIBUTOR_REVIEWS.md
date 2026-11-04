# Existing Python PR contributor reviews — resume pass

Final disposition: **all nine original PRs merged normally**. Refreshed actual-base
wheel source inventories, focused tests and hosted checks precede each recorded
merge in `PR_LEDGER.md` / `existing-pr-refresh-20261104`. #138 additionally passed
genuine managed Runtime acceptance (`MANAGED_AGENT_ACCEPTANCE.md`), retaining
UNCONFIRMED publication and UNKNOWN remote cleanup. Historical pending language
below records review-time boundaries, not a claim that those merge gates remain open.

Author-agent review against `docs/contributor-guides/KLAB_CLI_Contribution_Review.md`.
This is neither a submitted maintainer approval nor milestone acceptance. Exact
original base/head SHAs are in `pr-ledger.json`; original heads remain unchanged.
Python product source in combined candidate `53f925bd1` has not changed during the
receipt/CI closeout passes. Existing source-matched wheel results therefore remain
applicable to this code review; new Java/deployment acceptance is separately gated.

## Scope, architecture, reliability and recommendation

| PR | Read-only review and important retained boundary | Finding / merge recommendation |
|---|---|---|
| #133 | Documentation-only reproducible Java handoffs; bounded commands, recorded failing language compatibility and no invented live acceptance. The failure describes its historical head, not a claim that the later integration still cannot compile. | No demonstrated documentation defect in the narrow handoff. Current delivery-base build/CI still must be updated through #192; do not turn this into a backend implementation PR. |
| #134 | `agent_status.py`: bounded bytes-only JSON, duplicate/unknown fields rejected, exact Java Status DTO and integer ranges, immutable snapshot; no transport, authentication, ordering or cleanup claims. | No demonstrated code defect. The six-field identity/transport boundary is explicit; native Status wire checks supplement 61 focused installed-wheel tests. Pending actual integration-base/hosted acceptance, not a complete managed-agent feature. |
| #135 | `agent_control.py`: explicit caller assertions, process-local one-shot journal; UNKNOWN stored before sender invocation, no automatic replay, false publication not absence of mutation, terminal/conflicting reports latch, detach never stops remotely. | No demonstrated code defect. 116 focused tests plus combined suite. Current #134 differs from the historical parent by a CI-composability test; integrate the actual parent head, not just its API base SHA. Not durable cross-process idempotency or authenticated incarnation proof. |
| #136 | `graph_projection.py`: every load reuses authorized GraphBrowser reads, retained pages explicitly partial and stale-capable, local views not ACL proof; byte/page bounds, generation-aware concurrent invalidation and no implicit graph replay. | No demonstrated defect in the bounded projection. 29 focused tests and combined suite; server currentness/ACL tests remain separate. No coherent-snapshot, durable resync or topology-write claim. |
| #137 | Documentation-only roadmap and acceptance reconciliation stacked on #133; partial agent/status/control and authoring-preview capabilities remain distinguished from execution and future roadmap requirements. | No demonstrated narrow mapping defect. Five focused tests and combined suite. Merge after the actual #133 head and retain unresolved Java/live requirements, not false closure. |
| #138 | `agent_execution.py`, `agent_messaging.py`, `_agent_wire.py`: deferred BEHAVIOR-only registration, explicit opt-in, original scope/Runtime binding, single admission before ambiguous POST; scripts/private sessions excluded. Caller-driven thread-affine AMQP, passive existing exchange, owned exclusive queue, bounded I/O/report budgets, no mutation replay or silent publisher-confirm downgrade. Local queue cleanup is not remote-agent cleanup. | No demonstrated defect in reviewed bounded implementation. 155 focused tests, combined suite and native interoperability checks are distinct gates. Actual supported broker capability/lifecycle and managed-agent evidence on the final stack remain acceptance work; a wire-format test alone cannot accept remote cleanup. |
| #139 | `_job_binding.py` and sync/async call sites: capture actual client/transport/scope/endpoints/credentials, known service IDs, decoder and result kind; validate before poll/retrieve/cancel/save. Unknown IDs may be learned but already-known IDs cannot change. | No demonstrated anti-rebinding defect. 195 focused tests and combined suite, including cancellation/uncertain-outcome controls. This is local identity consistency, not server ownership/incarnation proof or write replay protection across new objects. Merge after #140 and current-base CI. |
| #140 | Existing `PR140_REVIEW.md`: sort only the already-bounded filename/body list before exclusive writes; approved pre/postimages and unchanged numerical oracle remain enforced; partial outputs stay as failure evidence. | No demonstrated code defect. 47 source-matched wheel-focused tests; no claim that copying admits the original frozen fixture against current ODO. Independent copy fix must not be held for an unrelated Java test once its own actual-base checks qualify. |
| #141 | `source_previews.py`: existing Resources parse endpoint only; fixed document-class allowlist, bounded UTF-8/response budgets, credential-material rejection, before/after endpoint/service binding, exact parser source or explicitly permitted edge trimming. Preview receipt is not ParsedSource authoring input. | No demonstrated read/write escalation defect. 57 focused tests plus native DTO/model transport checks and combined suite. No compile/execute/persist/write-authorization expansion. Merge after #140 and current-base CI. |

## Evidence and unresolved acceptance

All original-head focused checks used noneditable wheels with complete package
source-inventory/hash equality (earlier wrong editable-source attempts remain
invalidated and preserved). The integrated wheel passed **2,772 offline tests**;
its 42 modules match the pinned combined source. Counts overlap and are not added.
Commands and source identities are in `INTEGRATED_ACCEPTANCE.md`, `pr-ledger.json`
and the authoritative evidence index. The reviewed implementation is suitable for
normal integration; **none of these nine is yet labelled merged or live accepted**.

The remaining path is #191's qualifying normal merge, #192's delivery baseline
integration, actual-head refresh/tests/hosted checks and each focused merge in DAG
order. Preserve original frozen scientific failures and UNKNOWN outcomes. Do not
invent an approval requirement where none exists, waive an applicable failing
check, or use unrelated broad Runtime failures to hold an independently qualifying
documentation/read-only/copy correction.
