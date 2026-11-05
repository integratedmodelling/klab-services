# ADD-only observation-strategy source drafts

## Q6a contract — before implementation

Base `554b5e30b9433300610ff66c4f79f6d564018827`; branch
`feature/python-strategy-source-authoring`. Extend existing `Authoring` by **one**
exact-source family, `OBSERVATION_STRATEGY_DOCUMENT`, reusing Q5's actual parser
class/source fidelity, existing ADD/consent/identity/owned-new-project guards and
ResourceSet acknowledgements. No general editor, compiler, Java, ontology/worldview
migration, dependency, publication or worker SDK.

Required: parse a uniquely named controlled empty strategy draft, preserve exact
source/comments/whitespace and service/class/URN identity; explicitallow_write=True;
new same-session confirmed workspace/project only; actual matching CREATE and exact
re-retrieved source/project; invalid source rejected; ordinary READ-only principal
denied project/document creation (independently verify no object); no ambiguous-write replay;
confirmed creator-owned project then empty-workspace cleanup and listings. Use a
distinct explicitly provisioned signed TEST operator with ROLE_ADMINISTRATOR at
its **first** Resources scope creation. Do not elevate/reuse cached scientist
scope or expose role provisioning in the public SDK.

If a write outcome is unknown, stop dependent mutations/deletes, retain the private
fixture for read-only inspection and report failure, not rollback. Mocked negatives
cover duplicate/empty/error acknowledgements, class/source drift, stale identity,
foreign project and missing consent. Do not pretend an uncertain ADD response is
an authoritative conflict/version-history protocol.

## Actual mutation inventory / remaining boundaries

| Surface | Current authority | Boundaries |
|---|---|---|
| NAMESPACE ADD | Existing Authoring and prior controlled Java acceptance | Preserve, do not rebuild |
| Strategy draft ADD | ResourceCRUDController272–274; WorkspaceManager866–883 adaptation; existing generic source creation/retrieval | This focused increment; physical source round-trip, not semantic/model applicability or execution |
| ONTOLOGY | WorkspaceManager835–858 gates supportsProjectOntologies and rejected source handling | Ordinary new-project helper does not provision worldview projects; stays unsupported here |
| BEHAVIOR/SCRIPT/app source | Q5 explicit edge trimming; prior Authoring exact-source refusal | No reconstructed/trimmed source silently written |
| Resource import/PUBLISH | ResourcesProvider1637ff: PUBLISH Resource-only, adapter/ingest-specific | Not a generic source publish method; separate contracts/rights/producer tests required |
| UPDATE/MERGE/versioned edit | Document UPDATE is source replacement; MERGE unimplemented; history pending | No overwrite/version/lock/rollback guarantee invented |
| Workspace/project creation/deletion | Existing owned ADD; confirmed project delete then empty workspace only | No cascading deletes or arbitrary retained-project attachment |
| Workflow/flow/agent actions | Distinct supported server routes and managed lifecycle boundaries | Not silently counted as completed by strategy draft storage |

ScopeManager307–337 assigns ordinary remote principals READ only; actual local/
administrator/system authorizations receive broader CRUD. Signed controlled TEST
operator success is **not ordinary-user or production-deployment write acceptance**.
No known knowledge revision, semantic dependency closure, strategy execution,
immutable publication, authoring concurrency or original-study result is claimed.
Implementation and installed/live/review/CI acceptance remain pending.

Permission refinement: workspace creation is authenticated-only, not universally
CREATE-mask gated. The negative controls must target actual project/document
guards, not assume ordinary users cannot create workspace descriptors. Prerequisite
PR207 merged at `cfb7bf481` fixes only those explicit grant denials to typed403;
PR208 supplies accepted routing CI bootstrap. No access grants changed.
