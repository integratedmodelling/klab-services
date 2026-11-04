# Agent control journal — local policy, not a broker adapter

`AgentControlJournal` supplies independently testable client policy for future
agent-control integration. It does not instantiate an agent, implement a live
agent handle, select a transport, or introduce HTTP endpoints.

## Explicit binding and single admission

Create an immutable `AgentControlBinding` from independently established agent
URN, Runtime URL/ID, scope, principal key and Runtime incarnation. These are caller
assertions, **not authentication or a remote lease**. The Runtime URL uses existing
credential-free origin validation; identity fields are bounded and scope uses the
existing scope grammar. Do not put credentials in binding fields. Principal and
incarnation are excluded from repr. Missing incarnation must not be fabricated
from a URN, local clock, numeric observation ID or Runtime ID.

`journal.attempt("start" or "stop", binding=binding, sender=sender)` validates the
entire binding and consumes that action's admission **before** calling the supplied
`sender(binding, action)`. The callback must implement **one bounded transmission**
under that binding. A duplicate, concurrent or reentrant admission cannot call it
again. Sender failures are sanitized; cancellation still consumes the admission.

| Local outcome | Meaning |
| --- | --- |
| NOT_ATTEMPTED | No callback admitted for this action on this journal |
| UNKNOWN | Callback admitted; no valid publication result, possibly still in flight |
| UNCONFIRMED | Callback returned false; **not** proof no mutation occurred |
| PUBLISHED | Callback returned true; **not** observed execution or termination |

None permits replay of that action on this journal. An explicit stop may still be
requested after an unknown start. A prior stop prevents a later start. Reported
RUNNING/nonviability also prevents start; terminal/conflicting reports prevent
further commands. No command is sent implicitly during observation/loss/detach.

**Limit:** this is process-local, per-object admission protection. It cannot prevent
retries hidden inside an arbitrary callback, sends outside this object, or replay
through a new object/process. It provides no durable mutation-intent store, crash
recovery, callback timeout enforcement or exactly-once delivery. The future adapter
must enforce transport deadlines/one transmission and persist any required intent
*before* I/O. Never reconstruct a fresh journal to retry an ambiguous mutation.

## Status evidence and loss

`observe(body, binding=...)` uses the source-matched bounded Status payload decoder.
The future adapter must first authenticate the envelope, origin, principal and
incarnation. A matching payload URN is not such proof. Admission budgets cap report
count/bytes, including admitted malformed JSON; overflow closes admission as LOST.
Invalid oversized/nonbyte input is rejected before admission.

`view` is an immutable point-in-time local snapshot. Its last sender report is
delivery evidence, never an authoritative current-state query. Sender timestamps
are not sorted, treated as a total order or used to reopen a terminal agent.
Terminal reports latch. A later nonterminal report, or RUNNING followed by READY,
latches a conflict, even if its timestamp is greater. Duplicate terminal reports
are harmless evidence and consume admission budget.

`transport_lost()` and `detach()` permanently disable further admissions; there is
no reconnect/reset method. ACTIVE means only local permission to admit work, not a
verified live transport. An already admitted callback can finish after concurrent
detach/loss, but its result cannot reactivate the journal. Detachment never means
remote stop. `remote_cleanup` is always UNKNOWN: no status or publication result
proves deletion of an agent registry entry, queue, twin or private session.

## Safe reproduction

From this PR checkout, Python3.11+ with the client dev extra:

```sh
python -m pip install -e './python-client[dev]'
python -m pytest -q python-client/tests/unit/test_agent_control.py python-client/tests/unit/test_agent_status.py
```

All callbacks are controlled local test functions; no services, broker, credentials,
agents, sessions or scientific Jobs are created. Tests cover every binding field,
single admission, cancellation/unknown outcomes, both publication booleans, invalid
responses, status conflict/reordering, exact budgets, transport loss and a bounded
concurrent admission/detach case. Unit tests cannot establish transport security
or real lifecycle acceptance. #125 remains open for maintained transport, genuine
served execution/control, incarnation provenance, terminal confirmation and owned
cleanup. Durable replay/resync is not supplied by this slice.
