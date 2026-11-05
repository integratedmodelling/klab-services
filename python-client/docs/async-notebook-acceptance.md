# Actual native async notebook acceptance

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
