# Actual native async notebook acceptance

## Current isolated HTTP notebook regression (separate evidence)

`developer-tools/klab-service-simulator/tests/test_notebook_client.py` now runs the
ordinary current SDK in an actual owned exact-interpreter kernel with top-level
await. Two finite simulated Jobs/contexts complete despite cancelling one local
wait; another waiter on the same Job survives. A second same-principal client
restores an observation and closes without disposing it. Native awaited graph
refresh remains explicitly partial/UNKNOWN. The test verifies exact SDK import,
kernel shutdown, credential-free outputs, two submissions only, zero remote Job
cancellations and explicit owned context/session cleanup. It adds no endpoint.

Dedicated `notebook-client` CI requires notebook/local dependencies and runs this
case without optional skips. It is **SIMULATED service acceptance with a real
kernel**, not a new Java computation, arbitrary notebook/browser GUI support,
durable recovery guarantee or positive collaborator-access test. Original served
Q8b scientific and principal evidence below remains historical and distinct.

## Q8b contract — before implementation

Basea83d9643dd776a3df3cba87d026800feb390565b; branch
feature/python-async-notebook-acceptance. Execute accepted async ownwait scenario
once in a real exact-interpreter IPython kernel, using top-level
`await scenario(Path(private_state))`. Reuse accepted scientific/lifecycle checks,
not notebook-generated surrogate data. No nest_asyncio/threadfacade/newSDK/Java/
model/worldview or reset of the deliberately single-use fixture.

Use the established privateKernelSpecManager/NotebookClient/finallyshutdown pattern.
Kernel must use exact sys.executable and noneditable installed klab_client from
outside checkout; verify installed source hashes and current harness identity.
Prespecified20cells234.5m/originalJob+focus/localcancel/otherwait/foreignattach/
unownedreaderclose/creatorcleanup assertions remain intact. Execute once only,
never retry notebook cells or scientific writes after ambiguous failure.

Record executed notebook and bounded report privately only after scanning all
outputs for actual configured credential material and JWT-shaped tokens. Verify
expected executed cell counts, no errors and genuine new kernel PID. Confirm
kernel shutdown and no kernel/agent-created console remains; unrelated kernels/
user terminals must not be touched. Native owned services/listeners also close.
Optional notebook dependencies remain absent from core import/runtime requirements.

This is one real native async notebook journey, not a browser GUI/profile/version
support promise, arbitrary notebook execution, Java simulation cancellation,
asyncsaved-handle restoration, agents/events or committedstate convergence.
Q7unresolvedhierarchy/branch830a141cd remains separate; A/B/studies incomplete.
Installed/live/review/normalCI/eligiblemerge acceptance pending.

## Verified bounded outcome

Atf04fe0a0f:3,013installedtests/17gates/lint/build/parity+8focusedPASS. Actual
ownedJava notebookPASS:threeorderedcodecells executed once withtop-levelawait,
exactnoneditableinterpreter/SDKsourceidentity innewkernel,originalJob/focus/20cells
234.5m/ownwaitcancel+otherwait/foreignattach/readerclose. CredentialoutputchecksPASS;
kernelshutdown/PIDgone/privatekernelspecremoved andcreatorcontext-session/service/
listenercleanupPASS;unrelatedJavaunchanged.
Wheel `148d5c8dfeb824bb4bef945b3dd0445615c0890502dc46f3e6f2c61f8a9983bf`;
executednotebook `2fbac8279872710ada7a0b898fa8752d41fc3b31320bd0fe1a1f0124baa5b81c`.
Notebook retained privately, not rendered/published as a public artifact. Optional
dependencies unchanged. Reproduction command and full boundaries in
[author/CLI review](reviews/async-notebook-acceptance.md). Normalhosted/merge/release
remain separate; fullQ8/A/B/studies remainincomplete.
