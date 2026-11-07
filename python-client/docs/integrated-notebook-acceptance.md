# Installed notebook acceptance

`tools/verify_notebook_matrix.py` keeps three evidence groups distinct:

1. SDK notebook output/credential/budget/cell-order and demo guard controls.
2. Real local exact-interpreter top-level await and HTML/kernel-shutdown smoke.
3. Actual SDK-only owned notebooks using independent HTTP reference services.

```text
python tools/verify_notebook_matrix.py --artifact-receipt RECEIPT.json --output-directory NEW_DIRECTORY
```

The shared matrix runner pins exact installed SDK/worker/reference module bytes and
test/support/helper/runner source before and after execution. No failure, error or
skip is allowed; existing evidence directories are never overwritten. CI can use
`--capture-current-source` for installed-module/source-byte comparison, explicitly
not wheel/sdist or fresh dependency isolation. Optional kernel/analysis dependencies
must be present; no Java IDE gate or native repair is involved.

The ordinary async notebook submits two independent Jobs, cancels only its own local
wait, and verifies both a surviving wait on that same Job and the independent Job
complete with exact finite reference endpoints. Closing a same-principal reader
preserves owner work. A separate Bob client is actually denied access inside the
kernel, after which owner data remains readable. Partial graph loading, invalidation
and refresh preserve explicit unknown/partial consistency rather than native lineage
claims. A separate raster notebook verifies declared dictionaries/worldview and raw
integer codes/masks/float32 band/time labels, not native semantic or time authority.
The existing principal-race notebook is also included: two same-principal clients
race an explicit private-draft CAS, one wins, historical draft content remains exact,
and foreign reads/writes/queries refuse. These drafts remain unvalidated private
reference state, not accepted source publication or native semantic admission.

All three workflows enforce unchanged credential/JWT/output-byte/three-cell-order guards,
exact interpreter and SDK import paths, owner session cleanup and owned kernel PID/
creation-time shutdown. They close no unrelated terminals/services. Kernel execution
remains explicitly unmeasured by the owning-thread operation tracer; notebook assertions
are separate workflow evidence, never fabricated API invocation attribution.

Local kernel smoke is not an HTTP/scientific result, and HTTP reference workflows
are not native scheduler, provider accuracy, permission/issuer/deployment or scientific
acceptance. No full graph/history, native agent disposal or final original-row/release
approval is inferred.
