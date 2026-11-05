# Native async saved-handle restoration

## Contract before implementation

Accepted base `361b6920b706839a01856dacde80d1416da8d175` (PR217), branch
`feature/python-native-async-handle-restoration`. PR217 passed all final checks,
normal merge/ancestry/source equality and25postmerge tests; owned branch retired.
Its initial Decimal failure and bounded model-plan acceptance limits remain intact.

Implement `await AsyncClient.restore_handle(reference, *, allow_relocation=False)`
using existing validated Handle schema, credential/origin/selection validators and
observation decoder. Await existing async capability/attach/query/Job methods;
never call synchronous network helpers or wrap sync restoration in a thread.
Support context, observation and retained observation/dataflow Jobs only through
existing wire contracts. Verify Runtime and executing peer IDs, root context,
persisted focus/agent observer, exact observation identity/context URN. Attachments
are unowned; missing/expired/EMPTY Jobs fail without submission or release/replay.
Nonboolean relocation, credentials and wrong origins fail before remote I/O.

One fresh owned Java journey must save original handles, restore via native async
in a fresh process and verify original Job/observation/focus/20cells. Require
foreign/expired/deleted-state controls from maintained wire behavior, local close/
cancel ownership and creator cleanup; protocol errors are not absence evidence.
Source inspection confirms unowned attachment does not prohibit an explicit
authorized `release()` call in the existing sync/async API. Preserve that contract;
test no automatic release on restoration/local close, not an invented release ban.
Offline/source-derived negative tests, installed gates and exact-candidate review,
normal CI/eligible merge remain separate from actual acceptance.

No in-flight restart/durable history/Runtime generation portability guarantee,
native async export, async Hub facade, semantic migration or worker SDK. Full A/B,
managed lifecycle, broader Q7 matrices, unresolved Quality warning and authentic
study requirements remain separate. No release or scientific publication authority.

## Development checkpoint

Native restoration and a fresh-process acceptance workflow are implemented locally.
250 focused source-checkout tests PASS across async restore, existing async client
and sync handles; lint PASS. This is not installed/live acceptance. Tests cover
pre-I/O schema/credential/origin policy, Runtime/peer/root/observation identity,
selection refusal, EMPTY Jobs, original decoders, relocation identity, authentication,
local cancellation and no automatic release/recreation. Existing sync contracts
are unchanged. Initial test collection import and unsupported RUNNING mock status
were corrected to the actual Handle module and STARTED wire enum, not by loosening
production validators. The explicit-release test assumption was corrected as above.

The fresh Java workflow saves original context/Job/observation handles, restores
in an installed-interpreter child, verifies focus/original20cells, local cancellation/
close preservation, foreign/expired credentials, missing observation and creator
cleanup. Deleted-state and additional observer/peer deployment coverage remain
separate evidence requirements; no restart/history guarantee is predeclared.
Full installed gates, actual Java outcome and review/normal PR/CI are pending.

First installed attempt44aa5fd26 passed build/install/offline tests but correctly
failed changed-branch coverage for valid AGENT observer and `mapping` Job decoder
paths. Existing Handle schema admits mapping results on Runtime/Resolver; retain
that contract. Add positive persisted-agent and both-service mapping tests;26async
restore tests now PASS. No threshold exclusions or validator changes. A fresh full
installed attempt is required; previous run is not labelled a gate pass.
