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

PR226 now merged21750594eebe119d551d5820e861fd615afffdae, allCI/ancestry/source/
174exactsourcepostmergePASS; branchretired. Accepted ancestry merged normally,
combinedinstalledchecksACTIVE. Live audit and mutation counters refer to the
inspection phase after standard explicit user-scope service advertisement; they
do not assert that authentication/preparation made no POST requests.

Integratede1449085180edfe3815a263fcf6465dc087d810a:3,293installed/all17gates/
lint/build/sourceparityPASS; exact source/package/rawharness identity verified.
Wheel12d368a1e098afaa5ac4cc97834dff103578449cb171da8342796d2653cf35ca;
sdistcc6d079b877253f8023b6b36596a62bfe5a082a2d9d44fb857d0383924528c1b.
One owned async-resource-inspection-live run ACTIVE; no livePASSpredeclared.

## First live failure and bounded harness diagnosis

e14490851 firstlive FAIL retained: workflow incorrectly required full list/retrieve
DTO equality. ResourcesProvider.retrieve1371–1372 fills a missing KimAsset serviceId;
list1391–1430 returns workspace objects directly. This is source-derived likely
enrichment, not yet captured as the exact first-run difference and not a Java/SDK
defect. All owned processes/listeners closed, unrelatedJava unchanged, reportprivacy
verified. No scientific submissions or inspection writes occurred before failure.
Corrected guard records differing fields/rawkeys and accepts only a missing serviceId
filled with the serving Resources identity; every other field/rawvalue must match.
Tests refuse foreign serviceId, changed label/unit, and accept identical views or
that exact source-defined enrichment. Corrected packagevalidation ACTIVE; a new
live run must confirm exact differences. Old failure is not promoted to PASS.

Corrected1b91fbc44:124focused/3,298installed/all17gates/lint/build/sourceparityPASS.
Wheel77d25578753c34efc1d1285cf0f218a53110b552442a6644b8e9987dd22d6599;
sdist3a3c9a13471d68f16eeb856c0307189734285585004aae51d2b824f5099f3968.
New async-resource-inspection-corrected-live run ACTIVE; no PASSpredeclared. SDK
production implementation unchanged; correction is liveworkflow/guardtests only.

## Second live diagnosis / native null-asset compatibility prerequisite

1b91fbc44 correctedlive FAIL retained. It confirmed exactly raw/provider serviceId
enrichment, allothermodel fields unchanged. Later unknown-model retrieval raised
ProtocolError invalidJSON, not MissingAssetError; do NOT count that as absence.
Current ResourceCRUDController.retrieve59–74 returns provider result directly and
ResourcesProvider.retrieve1324–1374 can returnnull for missingassets. Native Spring
null can be HTTP200 with zero responsebytes instead of JSON `null`/404. Existing
transport requires JSON for allsuccesses, preventing typedmissing result on that
supported read route. No Java production defect/repair campaign is required.

Small prerequisite correction within metadata-read feature: shared transport may
map exactly resources/GET/native `/api/v1/retrieve/<class>/<urn>` HTTP200/zero-byte/
JSON response to MissingAssetError. Do not map ProtocolError broadly, nonempty
invalidJSON/whitespace bodies, unrelated routes, writes or otherstatus responses.
Positive/negative sync/nativeasync tests must failbeforefix and prove narrowroute/
body/status behavior afterfix. Native live run must independently reach typedabsence
after successful existing-model lookup; sourceinspection is not a liveabsencepass.
All prior failures/cleanup remain preserved; correctedpackage/newlive required.
