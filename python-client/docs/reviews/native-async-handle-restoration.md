# Native async saved-handle restoration review

Author/CLI review, not independent human approval. Base361b6920b (PR217);
branchfeature/python-native-async-handle-restoration. Implementation completion
is not acceptance completion; no PR yet.

## Contract and architecture

Await existing native async capability/attach/query/Job APIs. Reuse Handle schema,
pure credential/origin/scope validators and checked observation decoder; no sync
network/thread wrapper, event-loop facade or protocol invention. Schema also admits
mapping Jobs on both services; preserve existing decoder semantics. Runtime and
peer identity must agree before attachment, exact context/observation IDs and URNs
must agree, persisted observer must be AGENT. Wrong origin/credential/nonboolean
policy rejected before HTTP. Relocation remains an explicit same-service-ID policy,
not migration or Runtime-generation portability.

Restored scopes are unowned. Existing explicit authorized release remains possible;
restoration/local close/wait cancellation must never call it automatically. EMPTY
retention status, missing graph rows and access failures do not recreate work.
Do not treat malformed/empty protocol responses as absence. No implicit user-scope
initialization or scientific resubmission in restore itself.

## Development validation and retained failures

250 source-checkout tests across async client/restore and sync handles passed before
installed validation. Initial import ownership, invalid RUNNING mock enum and assumed
unowned-release ban were corrected to maintained contracts; validators not loosened.
Installed44aa5fd26 built/installed/passed offline tests, then failed changed-branch
coverage: valid persisted AGENT observer and mapping decoder branches untested.
Add positive observer and Runtime/Resolver mapping tests;26 restore tests PASS.
Full installed gates are rerunning with thresholds unchanged. No live result yet.

## Actual acceptance plan / limits

Fresh hidden owned Java deployment, one existing234.5scenario computation. Save
original context/Job/observation handles and restore in a fresh installed-interpreter
process; require original focus/Job/result20cells/unit and unowned attachment.
Reuse accepted own-wait cancellation check, ensure local close preserves creator
work, foreign/known-expired TEST credentials refused and missing observation returns
MissingAssetError after a positive lookup. Creator scope/session/process cleanup
and exact installed/live/tool identities must be independently confirmed.

Deleted contexts, wider observer/peer deployments, in-flight restart/history and
Runtime-incarnation guarantees remain unaccepted unless actual evidence is added.
Unit schema/wire tests are not deployed lifecycle proof. Native async export,
managed agents/events, production Hub and original-study science remain separate.
Quality warning retained. FullA/B and release eligibility not claimed.

Review in progress: final exact candidate, installed gates, actual Java outcome,
privacy/cleanup receipts and final-head CI/normal eligible merge remain required.

## Installed exact candidate

20d07091d on361b6920b: `run_gates.py --repo <checkout>
--label ab-native-async-handle-covered` PASS:3,117installed/17gates/lint/build/
sourceparity. Prior failedcoverage receipt retained, no exclusions added.
Wheel76974ad80f4d443654b9bae3050dc6c12bdfe2e23fb88e7f1df8506a09bbaa35;
sdistfedc1724b04f1b01c88b85b0cd1a633a9faaf5664bf8bf6b43bd11b020a7b227.
One actual command active, not predeclaredPASS:
`run_reliability_acceptance.py --server-repo <checkout> --java <Temurin21>
--components <corrected-components.json> --state-dir <native-async-handle-live>
--scenario async-handle-restoration`. Need actual original-state/value/access/
creator/worker/service cleanup and exact installed/raw/live identity before PR.

## First actual pass / review safeguard

Actualnative-async-handle-live at20d07091d PASS: fresh process restored original
Job2/observation15/focus11 and20cells in m; no scientific replay. Local wait
cancellation and close preserved owner work; foreign/expiredTEST credentials denied,
missing observation returned MissingAssetError with original lookup positive control.
Creator context/session, worker and owned service/listener cleanup PASS; unrelated
Java unchanged. Deleted-state and wider deployment/restart-history claims remain open.

Review identified that final harness report I/O could mask an existing primary
failure, as in the previously diagnosed model-plan harness. Add a small typed-note
save safeguard and tests, leaving SDK restoration and successful computation path
unchanged. Final installed gates and fresh exact-head live validation are required
before delivery; preserve the first passing receipt, do not relabel its candidate.
