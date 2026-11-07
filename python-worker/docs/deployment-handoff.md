# Worker deployment handoff — evidence required, not approval

Status: **experimental Python implementation; native compatibility REAL_NOT_RUN**.
RC-P5/CAP-17 and JAVA-GAP-0017 stay distinct from native/deployment acceptance.
This document authorizes no deployment, issuer changes, public release or OS repair.

## Exact proposed contract

Use the separately installed `klab-python-worker`, explicit `allow_experimental=True`,
HTTPS or operator-chosen loopback HTTP, an existing owned root scope and explicitly
provided user/worker-role credentials. No ambient credentials, proxy discovery,
redirect following, native failure fallback, downloaded code or server-selected shell.
Relative `experimental/worker/v1/` routes are a proposal, not assumed native endpoints.
Registration/claim/task/publication/recovery receipts must match exact protocol fields,
state and requested identifiers. A protocol-only200 is not success.

The worker executes only an operator-selected trusted local `.py` snapshot. Its SHA256
identifies the bytes copied into the owned working directory, not a mutable source path.
Declared finite numeric field layout/axes/coordinates/CRS/unit/mask cannot change during
execution. Input/output/implementation hashes serve custody, not proof of scientific
correctness. PUBLISHED status validates output/provenance/digest but cannot independently
prove the backend's input claim or scientific engine execution.

## Enforced Python/process controls and precise limits

| Control | Current implementation | Not established |
|---|---|---|
| Source/input | Explicit trusted local source <=1MiB; bounded finite JSON/4096cells | Hostile-code safety, resource-provider science |
| Child admission | Isolated `python -I`, minimal environment; trusted bootstrap waits for containment before code | Network/filesystem deny policy |
| Windows tree | Non-breakaway Job, kill-on-close; terminate/query active-process cleanup | Container/host administrator approval |
| POSIX tree | New session/group; signal owned group | Preventing malicious setsid/namespace escape or universal descendant reaping |
| Wall time | Explicit <=300seconds, cooperative parent monitor | Instantaneous distributed deadline |
| Memory | Optional Windows aggregate Job committed memory; POSIX inherited per-process RLIMIT_AS | Uniform aggregate RSS across descendants/platforms |
| Output | Monitored aggregate stdout+stderr acceptance budget, final recheck after stopping owned descendants; bounded stdout read | No-write overshoot, hard total-disk quota, arbitrary cwd-file bound |
| Cancellation | Explicit local event and scoped remote-state checks; publication fenced by backend lease | Instant global cancellation, rollback of arbitrary script side effects |
| Recovery | Restart loses lease; RECOVERY_REQUIRED then explicit owner recomputation consent | Durable native incarnation/retention or idempotent side effects |

The output monitor is sampled; a trusted process may write past the threshold between
checks. Do not describe it as a kernel-enforced disk quota. Nonzero/oversize/malformed/
layout-changing output is unpublishable; owned descendants are stopped before final
output acceptance, with platform-specific cleanup semantics above.

## Operator/native acceptance packet still needed

An authorized operator must supply and review these before any real deployment claim:

1. Exact wheel/sdist hashes and source-module parity receipt at the tested candidate,
   interpreter/OS versions, full unit/public HTTP/restart/failure reports and unchanged
   coverage gate results. Borrowed test dependencies are not fresh dependency isolation.
2. Maintained native worker ABI/endpoint advertisement, separate user/worker credential
   issuance/rights/revocation, root/Runtime/incarnation binding and native reference
   lifecycle. Run approved real positive/negative tests; never silently use simulation.
3. Approved container/OS execution policy for untrusted code: identity, network egress,
   mount allowlist, secrets, host access, process escape, aggregate CPU/RSS/PID and
   hard total-disk quotas. Evidence must identify the actual enforcement mechanism,
   tested rejection/cleanup and scope of approval. A checklist or Python flag is not
   enforcement. This package does not create/repair that boundary automatically.
4. Real lease/publication atomicity, cancellation, backend loss, restart/retention,
   stale publication and explicit recovery evidence. Record ambiguous side effects
   and operator reconciliation procedures; no blind claim/start/publish retry.
5. Independently pinned scientific inputs/provider/datum/layout/masks/expected output/
   tolerances and actual maintained-engine execution if science acceptance is desired.
   A trusted copy/double script or synthetic backend cannot supply that authority.
6. Owned-resource inventory and cleanup evidence, preserving unrelated user terminals,
   services and original session state. No deployment or release without explicit
   authorization from the responsible owner.

## Failure/reconciliation procedure

Transport or malformed mutation acknowledgements do not establish rollback. Inspect a
known task explicitly with current credentials. Never assume a lost claim is available
to execute again. Non-PUBLISHED status must contain no output/provenance; retain failed
tasks as evidence. Recovery requires explicit recomputation consent after considering
script side effects. If task identity itself was lost, use approved backend diagnostics,
not repeated submission. Reporting FAILED must not forward child stderr or credentials.
