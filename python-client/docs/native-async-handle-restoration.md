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
Offline/source-derived negative tests, installed gates and exact-candidate review,
normal CI/eligible merge remain separate from actual acceptance.

No in-flight restart/durable history/Runtime generation portability guarantee,
native async export, async Hub facade, semantic migration or worker SDK. Full A/B,
managed lifecycle, broader Q7 matrices, unresolved Quality warning and authentic
study requirements remain separate. No release or scientific publication authority.
