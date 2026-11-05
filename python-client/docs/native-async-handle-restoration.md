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

## Installed checkpoint / live run pending

Executable20d07091d: fresh `ab-native-async-handle-covered` PASS with3,117installed
tests, all17coveragegates, lint/build/sourceparity. Exact noneditableWindows
Python3.12.7; inherited Rasterio warning retained. Wheel
`76974ad80f4d443654b9bae3050dc6c12bdfe2e23fb88e7f1df8506a09bbaa35`;
sdist `fedc1724b04f1b01c88b85b0cd1a633a9faaf5664bf8bf6b43bd11b020a7b227`.
Fresh owned Java `native-async-handle-live` is active. Actual recovery/access/
cleanup remains pending; installed success does not establish live acceptance.

## Final-candidate installed checkpoint

Review added type-only secondary report-save failure notes without masking a
primary recovery/cleanup error; SDK restoration is unchanged. Candidateb658cc388
passed255focused tests and fresh full installed validation:3,119tests/all17gates/
lint/build/sourceparity. Wheel
`a5b1ca64e646bfa7d5789090ecb277e3c7ac1cdb4c8d26fc6e8dce6cbbbd9e48`;
sdist `37db227c619c374beb3709da8edd203ae07cdae0182f762048bb71dcd21537ff`.
One exact-head `native-async-handle-final-live` run is active; no final live outcome
or PR/merge acceptance is predeclared. Earlier live PASS stays bound to20d07091d.

## Verified bounded result

Final executable `b658cc388c505b044a4f6653544bfd5483af7dba`:3,119installedtests/
17gates/lint/build/parity and255focused tests PASS. Exact-head fresh Java run PASS:
one original scenario computation, saved context/Job/observation handles restored
in a distinct installed-interpreter async process, original Job2/observation15/
focus11, all20cells234.5m. The worker submitted no scientific work and acquired no
creator ownership. Local wait cancellation and local close preserved creator work.
Foreign and known-expired TEST credentials were denied; a missing observation
returned MissingAssetError after positive original lookup, not protocol-error
absence inference. Creator context/session, worker and owned services/listeners
closed; unrelated Java unchanged.

Raw SDK/harness, installed artifacts and live candidate identity verified; private
handles/reports checked for actual configured credentials and byte-hashed. Earlier
coverage failure and first passing candidate remain preserved. Type-only report-I/O
notes preserve a primary failure; successful evidence saves remain required.

Normal final-head PR/CI/eligible merge remain pending. Actual deployed Resolver/
mapping/observer, deleted-context, restart/in-flight/history guarantees remain
unaccepted beyond source-derived unit contracts. No async export/Hub orchestration,
managed lifecycle, whole A/B or original-study acceptance. The Quality warning and
broader Q7 matrices remain separate. Explicit authorized release is unchanged;
unowned/local-close does not imply a client-side release prohibition.

Later deleted-state candidate9807a97d5 separately passed positive-controlled actual
Java listing/release/absence/restore-refusal/survivor-integrity/cleanup checks;
normalPR/CI deliverypending. See [deleted-state acceptance](deleted-state-recovery-acceptance.md).
This adds bounded evidence, not a reinterpretation of originalPR218 receipts or
in-flight/restart/history guarantees.
