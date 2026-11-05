# Contribution review: native async resource/model inspection

## Recommendation

Ready for maintainer review for bounded native async metadata inspection and native
nullasset compatibility. Contributor/
CLI review under KLAB_CLI_Contribution_Review.md, not human approval. Existing
AsyncResources list/retrieve/resolve contracts are available; no Java repair needed.
Typed awaited inspection reuses immutable conversion and preserves unavailable data.

## Reviewed state

Repository klab-services, integrationc178e390ee7ec5b9f301b92edbe5debfdf1b88f2
(PR225); current feature/python-async-resource-inspection. Contract54d7258e9
preceded code. OffsetPR226 independentlyunderCI; separate branch fromacceptedbase
in same ownedworktree, reviewed226candidate preserved. No unrelated changes.

Latest accepted integration21750594eebe119d551d5820e861fd615afffdae (PR226),
allCI/ancestry/source/174exactsourcepostmergePASS; successful branchretired.
Accepted ancestry normally merged into inspectionbranch; combinedinstalledchecks
ACTIVE before actualJava metadata run. Initial counts do not include lateroffsettests.

## Capability and boundaries

`await AsyncResourceInspector(client).list/retrieve/resolve` returns existing immutable
AssetInspection/ResourceResolution DTOs, not raw unvalidated notebook metadata.
Pure resolution conversion factored from synchelper; pure asset/advertisement checks
shared unchanged. Native async network/cancellation, no thread/syncClient wrapper.
No semantic applicability/Resolverselection/execution guarantee, dependencyclosure,
authoritative version inference, model forcing, import/edit/publish or provider
routing/trust expansion. Missing versions/dependencies stay unavailable. No Java change.

## Findings, ordered by consequence

Missing Python capability (not a broken existing sync promise): ResourceInspector
deliberately refuses AsyncClient while native low-level resource routes exist.
New facade overrides all three public network operations and origin/call/driftcheck
paths with await. Sync path retains behavior, with only pure resolution decoding
extracted. No demonstrated important/blocking defect after initial tests; expanded
installed/live evidence required, not an exhaustive proof.

Firstlive found a blocking harness assumption: complete list/retrieve equality.
Java retrieve fills absent KimAsset serviceId while list returns workspace objects
directly (ResourcesProvider1371–1372/1391–1430). This source-derived diagnosis is
not claimed as captured first-run raw evidence. New guard records differences and
permits only absent→serving-serviceId enrichment; foreignIDs/othermetadata/unit drift
still fail. No SDK/Java defect demonstrated. Corrected exactcandidate/live evidence
required before recommendation; firstfailedrun and cleanup/privacy receipt retained.

Secondlive1b91fbc44 confirmed exactly serviceId-only enrichment, then FAILED the
unknown-model control with invalidJSON ProtocolError. It was NOT counted as absence.
Important Python compatibility gap: current Resources retrieve returns providernull
directly, which native Spring may emit as HTTP200/zero bytes; shared transport
requiresJSON even there. Scoped correction recognizes only JSON-mode/resources/
GET/nativeasset-retrieve/HTTP200/exactemptybody as MissingAssetError. NonemptybadJSON,
whitespace, othermethods/routes/statuses/services remain strict; no generic catch of
ProtocolError. Two positive-controlled native/sync tests FAILED beforefix while24
narrownegative/text controls passed. Full correctedpackage/newlive required. NoJava
production changes. Both failedruns and actualenrichment/cleanup/privacy receipts retained.

## Validation actually performed

|Check / command|Revision/environment|Status|Establishes / limits|
|---|---|---|---|
|pytest async+sync resourceinspector tests|candidate sources, Windows Python3.12.7|Passed:76 initially|Native routes/immutable/error/budget/identity/redaction/cancel controls; mocked metadata not selection/science|
|Expanded parity/malformedresolution plus asyncclient tests and installed17gates|003078ecd|Passed:119focused/3,285installed/all17gates|Build/install/sourceparity/lint; beforePR226integration, no coverage gates changed|
|ActualJava read-only modelmetadata workflow|fresh ownedfixture|Not run|List/retrieve/resolve exactidentity/missing positivecontrol and audit; no computation claim|
|Combinedaccepted226 package/gates|e14490851|Passed:3,293/all17gates|Exact source/install/rawharness binding, no thresholds changed|
|IntegratedactualJava metadata workflow|e14490851|Running|One fresh ownedrun, no older live evidence reused|
|First integratedJava metadata workflow result|e14490851|Failed:AssertionError|Harness required exactDTOequality; cleanup/credentialabsencePASS, no Java defect demonstrated|
|Corrected serviceId-enrichment guard tests/fullpackage|corrected candidate|Running|Explicit allowedcontract and negative foreignID/label/unit controls; no predeclared livePASS|
|Corrected focused/package result|1b91fbc44|Passed:124focused/3,298installed/all17gates|No SDKproduction/Java changes to fix harness; same explicit negative guards|
|Corrected actualJava metadata workflow|1b91fbc44|Running|New evidence root, no old live outcome reused|
|Second actualJava workflow result|1b91fbc44|Failed:ProtocolError on unknown-model JSONdecode|ServiceId-only enrichment actuallyconfirmed; typedabsenceNOTpassed; cleanup/privacyPASS|
|Native emptyasset regression beforefix|currentSDKbeforecompatibilitypatch|Failed2/Passed24|Both sync/nativeasync emptyHTTP200retrieve cases expose gap; otherroute/body/status/text controls stay strict|
|Native-null compatibility focused/installed correction|cdf28153b|Passed:282focused/3,324installed/all17gates|Includes syncclient/nativeasynctransport regressions; lint/build/parityPASS; no Java change|
|New native-null actualJava metadata suite|cdf28153b|Running|Records missingmodel HTTPstatus; strict ProtocolError still not accepted as absence|
|Final native-null actualJava result|cdf28153b|Passed|3listedmodels/existingmodel retrieve+resolve, serviceId-only enrichment, typedmissingHTTP200exactemptyguard;11GET/0inspectionwrites/0scientificsubmissions andcleanup|

## Scientific and lifecycle evidence

New claim is metadata inspection, not independent scientific accuracy. Actual Java
metadata must provide listed identity with matching retrieval/resolution; unavailable
version/dependency data not reconstructed. Missingmodel is only accepted as typed
MissingAssetError after a positive same-family lookup. HTTPaudit allows only readGET
operations after user-scope preparation; no scientific submission, context or authoring
mutation. Controlled TEST/local-federation is not productionHub/licensing acceptance.
Exactcandidate/privacy/ownedprocess-listener cleanup still required.

Finalexactcandidatecdf28153b metadataPASS independently confirms source-defined
serviceId enrichment and typednative nullasset response. Version unavailable preserved,
not reconstructed. Reportstatus200 is set only by exactzero-byte retrievecompatibility;
it is not a reinterpretation of ProtocolError. Local reader/ownedprocess/listener
cleanupPASS; unrelatedJava unchanged. ExactSDK/install/rawharness/liveidentity and
credentialabsence/reporthash verified in async-resource-inspection-final-validation.json.
Prior two failedruns retained separately. No important/blocking defect demonstrated
after these corrections in bounded scope; not exhaustiveproof or human acceptance.

## Contributor coordination

No Java/component publication, authority/principal policy, NiFi or original scientific
model change. FullRC-P4.1/metadata applicability/version/history and import/release
contracts remain open. Failedpeer/categorical and original research evidence retained.

## Smallest next actions

Finish finalfocused/map checks, then normal exact-head PR/CI/eligiblemerge/ancestry/
postmerge. Accepted226 normally integrated;3,324installed/all17gates/exactactualJava
metadata/privatebinding verified. No further unchanged live run or Java repair needed.
Continue independently executable queue afterward; broader scientific/contract gates remain.

## Suggested PR description

Expose native async checked MODEL/RESOURCE metadata inspection with sync-parity
immutable DTOs and existing budgets/origin/redaction/error semantics. No invented
applicability/dependencyclosure/version or provider trust. Include source/installed/
boundedactualJava metadata evidence; not a scientific execution/reproduction claim.
