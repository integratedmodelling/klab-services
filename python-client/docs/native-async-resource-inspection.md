# Native async resource/model inspection

Basec178e390ee7ec5b9f301b92edbe5debfdf1b88f2 (PR225). Independent read-only
RC-P4.1/CAP-06 notebook prerequisite while offsetPR226CI runs. Same owned worktree,
separate feature branch from accepted integration; preserve reviewed226 candidate.

Existing contracts: ResourceInspector explicitly requires sync Client. AsyncResources
already implements native async MODEL/RESOURCE list/retrieve/resolve and service
capabilities. Add AsyncResourceInspector exposing awaited list/retrieve/resolve with
the same immutable advertisement/result types, item/response budgets, origin/scope
checks, notification errors, redaction and unsupported422 behavior as sync inspection.
Reuse pure _asset/_advertisements/_rows conversion and factor pure resolution decode
only as necessary; no network call through sync Client, thread wrapper or asyncio.run.

No semantic applicability inference, authoritative revisions, dependency closure,
model forcing, provider routing/trust expansion, discovery pagination, import/edit/
publish, worker API or Java production change. Descriptors and URLs stay inert data;
null version/dependency metadata stays unavailable, never reconstructed. Scope/endpoint/
service drift must fail the result; cancellation remains native/local, no writes.

Tests: nativeasync parity with sync checked immutable views, scope/capability calls,
missing/mismatched assets, malformed metadata/item/byte budgets, unsupported422,
origin drift, redaction, cross-client type refusal and cancellation. Existing sync
suite must remain green. Installed gates and one actualJava read-only model metadata
inspection with ordinary controlled principal and validtoken negative controls where
supported; no model computation or invented project/publication workflow. Guide
review, exactsource/install/harness/live/privacy/cleanup, normalPR/CI/eligiblemerge
and postmerge required. FullRC-P4.1/A/B/scientific-study gates remain open.

## Use

```python
from klab_client import AsyncResourceInspector

inspector = AsyncResourceInspector(async_client, max_items=100)
models = await inspector.list("MODEL")
# Choose an exact served URN; no semantic model selection is inferred by this list.
model = await inspector.retrieve(models[0].urn, "MODEL")
resources = await inspector.resolve(model.urn, "MODEL")
```

Use ResourceInspector for synchronous Client, AsyncResourceInspector for AsyncClient.
Whole lists are bounded, not paginated; duplicates retained. Scope/service checks
bracket the actual read, but no multi-request revisionlease is implied. Immutable
advertisements and providerURLs stay inert metadata, not credential-routing instructions.
Model version/dependencies can be None; labels/observables do not prove applicability,
Resolver choice or actual computation. Native cancellation only stops local requests.

## Initial candidate checkpoint

003078ecd:119focused/3,285installed/all17gates/lint/build/sourceparityPASS.
Wheel284de2b518c8ba59ee2ea5ae5b7e820bea9cff8c1f2c512741ce76287202d256;
sdist e881a17983371693c87fc9013556c08eb971c51700b5885e88a6727397e472c5.
This candidate starts atacceptedPR225 and excludes offsetPR226's8newtests/fix.
PR226hostedchecks nowPASS; normalmerge/postmerge/integration then combinedpackage
validation must precede one actual read-only modelmetadata run. No livePASSpredeclared.
