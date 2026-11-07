# Conditional source reference development review

Parent PR293 verified merged0376f97c4. Local implementation review, not native
contract approval, scientific admission or final PR/full-row acceptance.

- Existing unconditional native CRUD and resource-info MARK_PUBLISHED are not
  misrepresented as CAS-safe namespace publication. Separate proposed protocol must
  be explicitly advertised and opted into; no reference route on ordinary services.
  Existing ADD-only Authoring/private RevisionDrafts are unchanged.
  Additional source inspection confirms ResourcesProvider rejects PUBLISH for
  non-Resource assets; deferred source save/workspace validation is not silently
  translated into this namespace conditional-publication contract.
- Server owns source revisions, finite diagnostics, knowledge and atomic conditions.
  Client DTOs or claimed successful validations cannot authorize publication. Source
  edits invalidate validation; old publication snapshots remain separately readable.
- A current knowledge integer alone cannot bypass stale stored validation: whole
  service/worldview/revision/session/content identity must still match at publication.
  The independent state lock serializes complete service operations; two independent
  public same-owner editors racing revision1 produce one revision2 and one409, never
  two successes or a silent last-writer overwrite.
- Current credentials/root/Resources identity are bound; no borrowed ownership from
  `context.owned`. Incoming privilege/identity/source/revision mismatch refuses.
  Contradictory VALID/Error diagnostics refuse; updates must confirm exact input
  source and validation invalidation. Literal bool revisions are rejected.
- Resource endpoint changes before source transmission refuse; after mutations they
  mark unknown outcome. Actual lost publication replies are reconciled by separate
  public readback without replay. Readback is not lease/CAS/idempotency attribution.
- Configured credentials in source, request route/body or returned receipts refuse.
  No credential-bearing source or protocol route is emitted before that check.
  Immutable receipts remain server claims, not cryptographic/native authority.
- Independent fixture has eight literal namespace sources, bounded16KiB/source,
  eight retained revisions/name and256root/name pairs. History eviction410,
  conflicts409, budgets429. Ephemeral only; owner cleanup removes reference state.
  No compiler/ontology, real Resources publication or native source deletion.
- No fake native management test for unestablished CAS contract. Source authority
  pinned and remaining maintainer/approved disposable-fixture obligations retained
  under JAVA-GAP-0009; no native source mutation or worldview repair performed.

Verified source gates:63SDK controls, full newmodule/allchangedSDK100%unexcluded;
6actual public sync/nativeasync HTTP workflows;13supplementaryfixture cases100%;
bodymypy/fatal lint and48governance/queue controlsPASS. Initial56controls96.38%
coverage expanded with binding/privacy races to63controls/100%, no exclusions;
three initial mypy optional-index errors corrected by explicit post-capability casts,
not runtime guard removal. Installed artifact/CI/ordinary PR delivery gates pending.
