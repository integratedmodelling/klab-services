# Staged worldview ingestion: integration evidence and follow-up

Recorded 2026-10-15. This engineering note accompanies the staged declaration,
complete-hierarchy and description-bearer provenance implementation. It does not
change worldview content or certify a running service installation.

## Reviewed inputs

- Services baseline: `ccf284278f0991c46127ddecab5a46b82ceb9835`.
- Reviewed services patch SHA-256:
  `ec46c5513120230420d7560ff51e633a8e9f7448486fcddca46a0a0697cdf0e0`.
- Companion worldview syntax-adapter patch SHA-256:
  `1b0ad8d1eeab3ba6e73b9297eb9528d94f2501a29bd7a8495450bd2603e36298`.
  This adds a private local declaration scope, without a grammar or public API change.
  Integrated independently into `klab-languages` develop at
  `2f11e0f22dc7fc990af9e18a95c53dd7bca4ea34`, without a shared dependency install.
- IMOD corpus: 26 ontology documents at
  `253d1046a1e04b4b45bc67a32b93a65829faa2f5`.
- Private reviewed `odo.owl` SHA-256:
  `2e840f49147a07b971146c24111c56f6831ea87347ff86e45588c1233a58c9bc`.
  Tests use this explicit private core catalog; the packaged core is excluded from
  the integration and must not be inferred to contain these reviewed bytes.

The focused contract set covers source/bean gates, staged declaration ordering,
domain/about clauses, description-bearer closure and compact authored provenance,
private environment publication, accepted-state retention and document validation.
The retained evidence includes all source, class, dependency, corpus and captured
authority-provider hashes. The current corpus has valid bean and logical results,
with the same 33 warning messages as the baseline. This does not assert that all
repository tests pass.

## Accepted cost and performance follow-up

Separate baseline and candidate JVMs used the same pinned parser dependencies,
private core and captured authority providers, one warmup and three measurements.
Times below are medians in seconds. Incremental ingestion submits a complete
captured worldview after one real declaration addition; it is not an in-place
ontology patch.

| Corpus | Full baseline / candidate | Incremental baseline / candidate |
| --- | --- | --- |
| Current 26 documents, 446 declarations | 18.72 / 23.51 | 18.42 / 23.59 |
| Current corpus plus 100 independent declaration groups, 746 declarations | 54.56 / 77.42 | 54.36 / 74.81 |

The user accepted this worldview-authoring cost, conditional on ordinary k.IM
validation remaining responsive. Performance optimization remains engineering
follow-up: profile completed hierarchy/description closure and provenance work,
and reduce full and incremental private rebuild cost without reintroducing
partial-graph reasoning, source-order dependence or lost authored origins. Use
the same pinned paired datasets and warning comparisons when assessing changes.
Do not trade away the single private reasoner, accepted-state isolation or final
complete-graph checks to improve a timing number.

## k.IM responsiveness and evidence limits

The ordinary model route remains Resources namespace parsing/adaptation and
`KimNamespaceVisitor`, followed by `ReasonerService.validateDocument` and an
accepted-environment lease. It does not submit or await a worldview candidate.
The new parser declaration overlay is constructed for `.kwv` ontologies only.
The small validator cache object created per document request does not scan
ontology declarations on the namespace route.

Four grounded private namespaces exercised a simple quality model, a dependency,
a process assignment and an invalid target. Each had ten warmups and fifty
alternating source edits per variant. Statuses and complete diagnostic payloads,
including offsets and lengths, matched after excluding notification timestamps.
Candidate valid-model semantic medians were 1.61, 2.14 and 1.98 ms, versus 1.67,
2.26 and 2.02 ms baseline. Source parse/adapt/visitor medians were 0.56–0.74 ms
candidate and 0.53–0.72 ms baseline. These small differences are not a statistical
throughput claim.

A candidate request completed in 1.83 ms while its private worldview builder was
paused and the service ingestion monitor was held. During actual background
worldview compilation, all 100 model requests per variant completed against the
old accepted generation: candidate semantic median/p95 1.41/1.83 ms, baseline
1.58/2.46 ms. Steady model checks retained the same actor and generation, with
one reasoner and unchanged flush counts. The existing 17 IDE semantic-validation
coordinator tests also passed.

Pending or rejected worldview candidates leave the previous accepted generation
ready. New semantics become available only after publication; old revision
requests then return `STALE_KNOWLEDGE`, and the IDE refreshes its cached checks.
Cold start without accepted knowledge is a separate wait. The explicit
administrative `updateKnowledge` API can rebuild a namespace-only batch, but no
active IDE save caller was found; this pre-existing API is not the ordinary
namespace validation route.

These are focused offline, in-process checks, not live HTTP/IDE end-to-end
certification, a full Maven reactor run, or a guarantee for every model workload
and machine load. The harness bypasses service lifecycle construction, uses
captured real authority providers, suppresses parser warnings and invokes the
Resources source visitor without its optional resource-reference resolver.
Actual semantic references are resolved by the real private actor. A test-only
builder wrapper provides the pending-build barrier; production source and
bytecode are unchanged. Shared dependency installs, packaged core replacement,
service restarts and pushes are separate operations, excluded from this local
integration.
