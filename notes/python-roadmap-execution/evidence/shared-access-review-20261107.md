# Shared-read reference development review

Baseline: verified PR291 merge `ceeaf623a`. Local source review, not maintainer
approval, native security certification, final PR acceptance or full-row disposition.

- Native source is generic owner/user/group ACL. It does not demonstrate granular
  READ/TTL/disposal denial. New management is separately proposed, explicit opt-in
  and capability-gated; no experiment endpoint is tried on unadvertised/foreign/
  failed Runtimes, no native ACL translation or real-service fallback.
- Root URL/identity/selection is bound through ordinary secret-free handles and
  checked around requests. Current credentials are used; context.owned, DTOs and
  another client's original credentials never authorize server operations.
- READ grants are server-owned, current-principal and Runtime-root scoped. Shared
  callers cannot submit/cancel/dispose/re-share/revoke or cross roots. Native
  default-private scenarios and production issuer/license policies are unchanged.
- SDK receipt validation is exact/immutable/bounded, rejects privilege expansion,
  invalid identity/expiry and reflected configured credential material. It exposes
  server claims, not local permission booleans or cryptographic proofs.
- Mutation disconnects preserve unknown outcome/no replay; owner grant lookup
  permits explicit reconciliation. Post-response binding changes mark mutations
  unknown and discard reads. Lookup is not CAS or proof of a particular request.
- Mid-point revocation checks occur on each real HTTP request and abort multi-point
  output rather than returning partial ScientificData. Previously returned local
  data is not retrospectively erased; this is not a revocable cache mechanism.
- Expired grants410 and denied/revoked403 remain distinct. Existing expired Job
  status EMPTY semantics are retained, not relabeled cancellation. Root cleanup
  removes bounded retained grant/tombstone records; durable mode refuses explicitly.
- Independent simulator has no SDK import and two declared principals, bounded128
  records, synthetic native-unit point reads only. Supplemental intake controls do
  not replace six actual public sync/nativeasync HTTP workflows.
- Read-only native fixture test is prepared/NOT_RUN and uses distinct reader-issued
  credentials. It neither creates grants nor attempts mutation/disposal of real
  shared roots. Granular native management needs maintainer contract confirmation
  and separate approved disposable negative-test fixtures.
- Queue mapping corrected without history edits: RC-P1.3 is the direct collaborator
  requirement. CAP-01 remains the already-delivered certificate/token/profile seam
  with integrated acceptance pending, not a claim new grants implement certificates.

Latest verified source gates:82SDK controls,6public HTTP workflows,10supplementary
fixture cases with multiple hostile inputs, SDK/simulator new modules100%unexcluded;
body types/fatal lint pass. Fresh installed full regression/parity, synchronized
governance, exact-head hosted CI and ordinary PR merge remain delivery gates.

Final pre-package hardening rejects integer1 as a revocation confirmation (literal
JSON true is required), invalid context/client model rebinding and non-text server
root IDs. No permission inference from Python bool/int equality or malformed input.
91SDK+queue controls and all changed SDK decisions pass; final fixture remains100%.
