# Source-bound finite dependency graph development review

Parent PR295 verified merged641eaa161/all40finalheadchecksPASS. This review is
local implementation evidence, not maintainer/native/scientific acceptance.

- Native membership remains closureUNKNOWN/edgesUNAVAILABLE. Separate advertised
  opt-in proposed graph endpoint never masquerades as existing native Resources
  closure or falls back to simulation after real failures. No parser/compiler,
  model-selection engine, graphdb loading or ontology repair.
- Independent fixture supplies five literal model/namespace/resource nodes/four
  edges and a consistent resource-root transitive two-node view. Model and resource
  roots are both exercised by public sync/nativeasync HTTP, not only fixture code.
- Exact UTF8sourceSHA256, source revision, concrete bounded version triples,
  provider/class/URN and server-reported READ subject/root/revision are retained in
  immutable receipts. Node and wholegraph labels explicitly deny native/cryptographic
  authority; current credentials/service ownership, not a receipt, grant real access.
- Every page confirms same Resources/request/root/snapshot/revision/rootnode/totals/
  providers. Opaque cursor only, never nextURL. Source-generation change409discards
  all pages without retry; explicit fresh inspection produces a new snapshot.
- Final checks reject duplicate IDs/identities/edges, missing endpoints/providers/
  sources/access, subject divergence, orphan nodes, cycles and incorrect totals.
  Shared dependencies in a valid DAG are accepted; no invented edges from native
  array order. Full snapshot emitted only after all bounded pages validate.
- Client/root/transport/endpoint/credential fences bracket every page. Review caught
  implicit context.client replacement risk; explicit object/transport identity fence
  and offline replacement refusal added. Configured credential reflection refuses;
  providerURLs remain inert without embeddedcredentials/query/fragments.
- Budgets apply per response/source and across pages/nodes/edges. Service caps8
  snapshots/root,128global,64cursors/snapshot with explicit429/no silent eviction.
  Continuations bind root/asset/class/page size/snapshot/current generation. Unknown
  snapshot410vsinvalidcursor422vsstalesource409. Ephemeral only, owned-root cleanup.
- Native full source/edge/access/snapshot/retention remains JAVA-GAP-0013 external
  obligation. No fabricated native functional adapter or native mutation is used.

Source verification:84SDK controls100%unexcluded entiremodule/allchangedSDK;
8public sync/nativeasync HTTP workflows;17supplementaryfixturecases100%.
Finalbodymypy/fatal lint/49governance+queue+CIorderingcontrolsPASS after resource-root
tuple annotation fix. Initial73SDKcontrols98.81%expanded to84/100%
with DAG/aggregate-budget/privacy/client-fence/version controls, no exemptions.
Initial tuple/map inference errors corrected with explicit annotations, no policy
weakening. Fresh installed fullartifact/CI/ordinaryPR acceptance still pending.
