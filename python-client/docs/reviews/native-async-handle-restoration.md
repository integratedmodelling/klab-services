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

Final executableb658cc388 installed `ab-native-async-handle-final`:3,119tests/
17gates/lint/build/sourceparityPASS;255focused testsPASS. Exact noneditableWindows
Python3.12.7; inheritedRasterio warning retained. Wheela5b1ca64e646bfa7d5789090ecb277e3c7ac1cdb4c8d26fc6e8dce6cbbbd9e48;
sdist37db227c619c374beb3709da8edd203ae07cdae0182f762048bb71dcd21537ff.
Fresh exactcandidate live state `native-async-handle-final-live` ACTIVE; final
recovery/cleanup/identity not predeclared. No PR yet.

## Final exact-candidate recommendation

**Ready for bounded maintainer review**, author/CLI only, not human scientific
approval. Testedb658cc388c505b044a4f6653544bfd5483af7dba on361b6920b; subsequent
delta Markdown-only, actual SDK/harness/installed/live hashes verified. Full diff
reviewed for usefulness, architecture, identity/security, wire contracts, scientific
meaning, lifecycle, primary errors, state, performance and maintainability. No
remaining demonstrated important/blocking defect in this bounded restore slice.

| Check | Verified outcome and boundary |
|---|---|
| Full installed command above | PASS:3,119tests/all17gates/lint/build/sourceparity;255focused tests |
| Final owned Java command above | PASS:fresh installed process restores originalcontext/Job2/observation15/focus11/20cells234.5m without scientific replay |
| Access/ownership/lifecycle | PASS:foreign/expiredTESTdenials, positive-controlledmissingobservation, localwaitcancel/localclosepreservecreator, allcreator/worker/process/listenercleanup;unrelatedJavaunchanged |
| Identity/privacy | PASS:rawSDK/workflow/functional hashes, installed sourceparity/artifactpins/livehead; privatehandles/reports byte-hashed and actualcredentials absent |
| Final-head hostedCI/protections/normaleligiblemerge | Pending; required before merge |

Private `native-async-handle-final-live` and `native-async-handle-final-validation.json`
bind exact executable/artifacts/results/cleanup. First20d07091dpass and original
coverage failure retained, not relabelled. Actual persistedobserver/Resolver/mapping
deployments, deletedcontext and broader restart/history/Runtime-incarnation proof
remain separate; unit tests do not close them. No protocolerror=absence inference.
No SDK networkthreads, fake Runtime/provider implementation, scientificmodel/oracle/
Java/worldview/dependency change. ProductionHub/export/managedagents/fullA-B/studies
remain incomplete; Qualitywarning retained. No release/support/contributor/reviewer
assignment or publication authority inferred. Next normalPR/CI/exactheadmerge,
ancestry/postmerge and ownedbranch retirement.
