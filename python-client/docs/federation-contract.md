# Broader federation: blocked composition and recovery semantics

**BLOCKED, not a completed federation feature.** Source audit at normal PR #107
merge `bb4f62ce01bb41d93f506ee34c19687b401d8640`; later work proceeds only under
the user's explicit instruction to defer blockers and circle back.

Existing Python Connection already discovers/selects explicitly trusted services,
binds credentials to approved URLs, verifies principal/issuer and service IDs,
checks configured worldview compatibility, advertises scope/service membership and
fails on identity changes. Configuring separate Runtime/Reasoner/Resources/Resolver
endpoints is real supported routing. This audit does not erase or reimplement it.

Broader federation must not be claimed from those existing capabilities:

- `ClientContextScope.connect(ContextScope)` returns null in the inspected Java
  client implementation.
- `ServiceContextScope.connect(ContextScope)` is an explicit TODO returning null.
- `docs/DISTRIBUTED_TWINS.md` describes composition as distinct from attaching,
  focusing or copying a context, and specifies proposed identity mapping,
  snapshot/watermark/replay and source-data ownership contracts.
- Runtime `/api/v1/connect` is an existing context attachment/reconstruction path,
  not proof that a composed independent twin, cross-runtime migration or failover
  with matching scientific data/storage revisions is implemented.
- Connection credential renewal and a service advertisement acknowledgement do
  not prove remote data authorization, replica freshness or safe mutation replay.

## Required product/platform decision

Select the actual federation operation: explicit cross-service routing (already
supported), source-twin composition, multi-runtime selection, migration, failover,
or replicated updates. Each has different identity, ownership and consistency
requirements. Do not create a generic Python `federate()` wrapper around a stub.

For composition/migration/recovery, obtain maintained server contracts for source
namespace/observation identity, unit/worldview/dictionary compatibility, read
authorization, immutable data versions and geometry/time support; active source
change handling and declared gaps/expiry; idempotency/unknown mutation outcomes;
disconnection, cleanup and independent ownership. Composition must never release
or delete a source merely because the composed client detaches.

## Acceptance required before a broader Python API

Use at least two separately owned Runtime fixtures with overlapping numeric asset
IDs and explicit, independently checked sources. Test correct/incorrect principal
and origin credentials, source revocation, semantic/unit mismatch, nonuniform
values/missingness/time support, dropped connection, restart/incarnation change,
expired replay, safe disposal and ambiguous mutations without resubmission.
Recorded source identifiers are not verified immutable storage versions by default.

No production Java/worldview/foreign service change, service launch or new live
federation experiment occurred in this audit. Source-level stubs and design text
are evidence of a missing accepted implementation, not a measured failure of all
remote deployments. Keep the PR draft/unmerged, retain the blocked roadmap item,
and proceed to release/support readiness without calling the roadmap complete.
