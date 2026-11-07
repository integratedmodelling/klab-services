# Next independently implementable worker resource boundary

This is an implementation queue, not completion evidence. The containment pass
retains trusted-code requirements and does not certify a production sandbox.

Next coherent slice for RC-P5/CAP-17 / JAVA-GAP-0017:

- Extend immutable operator-supplied Limits with explicit, opt-in memory controls
  and document the exact platform semantics instead of calling all limits RSS.
- Windows: enforce aggregate Job memory before gated script execution; reject
  unsupported Job assignment and never fall back to uncontained execution.
- POSIX: enforce child address-space limit before runpy, not a parent-process
  limit; explicitly distinguish per-process virtual address space from aggregate
  resident memory and deliberate descendant escape. No root/cgroup provisioning
  inferred from this campaign.
- Test actual over-allocation, invalid bounds, setup failure, child termination,
  no publication and cleanup over installed artifacts. Keep network credentials
  outside the child and preserve scientific envelope validation.
- Disk quotas/hostile-code isolation require an independently enforced operator
  boundary or honest separate limitations. Polling directory sizes is not a hard
  quota; do not relabel it as one.
- Maintain the same stable gap/handoff, CI and full ledger with exact evidence;
  native ABI/deployment/restart/scientific acceptance remains REAL_NOT_RUN.

If an actual deployment hard boundary requires operator privileges or container
policy, record the precise missing authority and continue A/B descriptors,
revision-safe authoring, managed agents, durable notifications or federation.
