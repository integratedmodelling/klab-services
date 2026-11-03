# Authoring/import/publishing: partial server support and blocked full contract

**Full roadmap contract remains incomplete; bounded ADD-only implementation is in progress.**
Source base: `bb4f62ce01bb41d93f506ee34c19687b401d8640`. The user authorized moving
past blockers and circling back, then authorized returning to authoring. The
following audit preserves the broader missing contracts. `authoring.md` describes
the narrowly implemented source-parse/ADD workflow and its separate acceptance
gates; it must not be counted as complete editing/publishing support.

## Supported paths found in current source

- ResourcesProviderController exposes `POST /parseAsset/{assetClass}` for parsing
  source into a knowledge asset. Parsing is not publishing, remote validation of
  an entire dependency closure, or a conditional write.
- ResourceCRUDController exposes typed PUT submit, DELETE, retrieve/list/resolve,
  including query-parameter URN variants. Resource/namespace/ontology/behavior,
  project/workspace and specialized workflow paths have distinct bodies/modes.
- ResourceAdminController exposes project lock/unlock and repository management.
  Workspace/project creation and document ADD/UPDATE exist in ResourcesProvider;
  the audit does not claim that all authoring endpoints are absent or stubs.
- ResourcesProvider rejects PUBLISH for non-Resource assets. VALIDATE requires a
  workspace and invokes deferred validation/cache invalidation; it is not a
  general nonmutating single-document dry-run. UPDATE_DEFERRED persists source
  before workspace validation; it is not a validation-only transaction.

## Concrete incomplete contracts

`klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/ResourcesProvider.java`
explicitly states in the document UPDATE path that source files are replaced and
version history is pending storage support. The same method reports document
MERGE as not implemented, and existing-project submit updates requiring manifest
and history support as not implemented. Workspace/project submission modes also
differ; a generic mode enum is not evidence that each asset supports every mode.

Before a full round-trip Python authoring interface is accepted, establish:

1. Which asset classes and submission modes form the maintained product scope;
   parse/retrieve/save/deferred-validation/publish are different operations.
2. Source identity, expected revision/conflict behavior and history/recovery for
   file-replacing UPDATE. Do not invent optimistic concurrency from a parsed DTO
   or pretend that the current lock is a versioned conditional-write protocol.
3. Lock owner/lease/expiry/restart and release behavior, including failure after a
   potentially accepted update. Do not auto-unlock an ambiguous remote write.
4. Per-principal/project permissions and publication rights in a genuine owned
   fixture; semantic validation errors versus accepted file persistence must stay
   distinguishable. `List<ResourceSet>`/empty results are not generic success.
5. Publication authority/version checks, resource adapter import schema, content
   rights and notifications/dependency refresh for the chosen resource product.

Python can adapt implemented wire contracts, but cannot create missing server
history/merge/conditional persistence by replaying PUTs or porting parsers. A safe
narrow ADD-only or source-parse preview feature is possible **if explicitly scoped
and accepted separately**, not represented as the complete authoring milestone.

## Unblock and test requirements

Choose a bounded supported authoring scope with explicit overwrite/version limits,
or authorize separately specified server history/conflict behavior. Provide an
owned writable project fixture for positive/negative round trips; do not edit the
preserved worldview or scientific projects to manufacture a pass. Test parse
diagnostics, ADD collision, invalid dependency/source persistence, permission
denial, lock contention/loss, restart, publication denial and unknown write
outcomes, with verified cleanup only of newly owned assets. No automatic retry
after ambiguous mutation; original scientific-source preservation is mandatory.

The original source audit changed no Java/worldview/resource and launched no
services; it was not live authoring acceptance. The follow-up ADD-only feature
does not remove any history/conflict/publishing gate above. Require its genuine
owned-fixture acceptance and final CI before merging that bounded implementation.
