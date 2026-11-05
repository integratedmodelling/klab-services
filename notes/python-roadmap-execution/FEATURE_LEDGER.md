# Stable A+B feature ledger

2026-11-05 UTC. The original complete modified/historical roadmaps and current
42-row acceptance map remain source authorities. This file preserves the Phase0
queue and requirement/scenario crosswalk after moving from unaccepted Q1 to Q5.
Initial full crosswalk is additionally preserved at local branch
`feature/python-fresh-process-recovery:notes/python-roadmap-execution/FEATURE_LEDGER.md`
and external `phase0-validation.json`. No requirement or failed evidence is dropped.

## Coherent queue (value / effort / risk kept separate)

|ID|Scientist workflow, tests and exclusions|Value / effort / risk|State / dependency|
|---|---|---|---|
|Q1 A recovery|Installed fresh-process same completedJob/observation/focus,20 independentcells, no disposal/replay, foreign/deleted controls; exclude in-flight/Runtime-generation|High / small / medium|IMPLEMENTED_UNVERIFIED;internal harnessFAILED;pause and revisit after independent work|
|Q2 A connection|Certificate/token,explicit service selection,owned scientific journey and freshprocess; issuer/origin/expiry/outage/mixedversion negatives; productionHub separate|High / medium / medium|PENDING;P0|
|Q3 A vector|Native advertised GeoJSON→bounded vector interpretation if actual sourcegap; multipolygon/holes/null/precision/CRS/budget tests; no guessed dictionary or mediation|High / medium / medium|PENDING;P0|
|Q4 A categories/time/scale|Actual native classlegend/area,time slices,multi-shard and realprovider stream/soak; independent oracles; no invented remote block/revision protocol|High / medium-large / high|PENDING;split by actual supported coherent contract;#123only specific lease claims|
|Q5 B parser|Existing four-family SourcePreviews→actual Resources parser/source/identity/invalid acceptance; no write/publication/whole semantic closure claim|High / small-medium / medium|LOCAL_ACCEPTED_BOUNDED;normalPR/CI/merge pending;independent of Q1|
|Q6 B mutations|Inventory implemented route/schema, then supported import/annotation/ADD/edit/publish one workflow perPR; rights/conflict/rollback only where authoritative|High / medium / high|PENDING;Q5forparse-dependentflow;absentJava contracts remain blockers|
|Q7 B semantics/resources|Actual context applicability/dependency/version/access and served selection; reuse#200; no Pythonmodelchoice|High / medium / medium|PENDING;P0;Q6onlyifnewassetrequired|
|Q8 A/B notebook/agents|Ownwaitcancel,boundedconcurrency,multiprincipalrace,managedlifecycle/outcomes/cleanup; no compiler/scheduler/workerport|High / medium / medium-high|PENDING;Q1/Q2fornotebook;agentinspectionindependent;#125/#126specificcontracts|
|Q9 A/B release|Exactinstalled integratedjourney/compatibility/crossPython/extras/docs/security/scale; packagingnotpublicapproval|High / medium / high|PENDING;eligibleincrements plus#129maintainersupportdecision|

## Actual deliveries / incomplete work

|Item|Branch/base/head/PR/merge|Tested package/environment and exact evidence|Disposition|
|---|---|---|---|
|Q1|feature/python-fresh-process-recovery; basefe46ddc5e; head36e25ba7c; noPR/merge|Python3.12.7,wheelba40939b815ee762ac6597c9f882b1a2d4600c1205ee46320b3ed43b0ea051dc;2869offline/16gates/lint;18focusedlatestcontrols. Java tuplePROGRAM_STATUS. q1-live-v1/v2private result/logs|NOT_ACCEPTED: initialoverstrictPOSTconnectaudit; correctionmisreadScopeRequestroot instead ofconfiguration.id. Latestknownfixunitpassed,no thirdliveattempt. Ownedcleanupallpassed. Notexternal/Javadefect|
|Q5|feature/python-source-parser-acceptance; basefe46ddc5e; tested96f6b0b243f739693e4a86850364e1e34b0531a2;PR/mergepending|Wheel0b59c80fe627510c8fc5b5f1e1c3752e96d65b717bf1407413a74d238f445fd6;2862offline/16gates/lint/sourceparity;ownedJavaTESTtuplePROGRAM_STATUS|q5-live-v2PASSED all4families/invalidrefusals/sourceidentity/zeroHTTPblank/nonauthoringroutes;initialv1fixturefailureretained;cleanupPASSED. RC-P4.2remainsPARTIAL;reviewpython-client/docs/reviews/parser-preview-acceptance.md|

## Complete42-row crosswalk and constituent scenario families

Broad rows stay PARTIAL until all agreed checks close; accepted bounded slices
are retained. Counts:24originalcriteria=22phaseacceptance statements+2continuous;
18additionalcapabilities. Currentprimary statuses:31PARTIAL,5ACCEPTED,
2IMPLEMENTED_UNVERIFIED,1NOT_IMPLEMENTED,1BLOCKED_EXTERNAL,2DEFERRED_APPROVED.

|ID|Remaining checks / preserved accepted proof|Queue or exact external gate|
|---|---|---|
|RC-P0|Inventory,invalid/unsupported/unavailable,mixedversion,agreedscope|Q9/#129|
|RC-P1.1|Credential→catalog→typedwork;genuineHubwalkthrough distinct|Q2;issuer/licence/consent external|
|RC-P1.2|FreshprocesssameJob/observation,readwithoutdispose;pending/history/restart separate|Q1;retainedhistory/incarnation#89|
|RC-P1.3|Collaboratorread/forbiddenwrite/privateparent/expirypositivecontrols|Q1/Q2/Q8|
|RC-P1.4|Dependencyoutage/recovery/mixedcaps/no untrustedforwarding|Q2/Q9|
|RC-P1.5|Preserve#200baseline/namespace/scenarioplanvalues;observer/project/executedgridremaining|Q7;forcedmodel unsupported|
|RC-P2.1|Preserve#19720cells;multi-shard/false/category/time remaining|Q4;realfixture/exporterrelease|
|RC-P2.2|Timewindow/dtype/CRS/units/mask/maintainedrevision|Q4;#123lease only,advertisedfixednativealternative|
|RC-P2.3|Actualproviderversusmatchedindependentdata/datum/tolerance|Q4/R1–3/#130artifact|
|RC-P2.4|Preserve#198socketfaults;realJava/providerstream/soakmemory|Q4/Q9|
|RC-P2.5|Raster/xarrayaxes/categorydictionary/worldview/vector|Q3/Q4|
|RC-P3.1|Twoclientcommittedconvergence,duplicates/races|Q8/#126authoritativeversion|
|RC-P3.2|Preserve#199ACCEPTEDexplicitdisconnect/refetch/staleunknown|Q9regression;notdurablereplay|
|RC-P3.3|Cancelrequestversusterminal/expiry/completion/restart/UNKNOWN/noreplay|Q1/Q8/#89history|
|RC-P3.4|Originalresources/models/components,lineage/semanticupdates|Q7;Javafull extractiongap|
|RC-P3.5|Boundednotebook/multiprincipal/ownwaitcancel/noshareddelete|Q8|
|RC-P4.1|Meaning/applicability/dependency/version/access/unsupporteddiagnosis|Q7|
|RC-P4.2|Actualparse/validate/invalid/stalerevisionnevercurrent|Q5/Q6;DTOaloneinsufficient|
|RC-P4.3|Import/annotate/compute/inputprovenance|Q6;actualschema/route/rightsfirst|
|RC-P4.4|Authorizededit/publish/version/lock/history/conflict|Q6/#129actualJavacontract|
|RC-P4.5|Agentoutcome/reconnect/privateownership/terminalcleanup|Q8/#125managedprincipal/incarnation|
|RC-P5|Workerexecution/isolation/validoutput|DEFERRED_APPROVED TargetC/#127|
|RC-R1|Offline/wire/wheel/live/mixedversion/scientificnegatives|EachQ+Q9|
|RC-R2|Support/version/migration/limitations/publicreleaseapproval|Q9/#129;packagenotpermission|
|CAP-01|Certificate/token/profile/renewalseam/secretstoreifselected|Q2;notgenericTLS|
|CAP-02|Catalog/selection/health/peers/trust|Q2;ready≠reachable|
|CAP-03|Originqualifiedrouting/partialproviderfailures|Q2/Q7;#124composition/migration separate|
|CAP-04|Scopes/observe/register/query/focus/grid/relationships|Q1/Q7|
|CAP-05|Semanticsearch/predicates/composition/revisions|Q7;unknown revision retained|
|CAP-06|PreserveACCEPTEDboundedtypedplans/recordedexecution|Q9regression;RC-P3.4full lineage separate|
|CAP-07|Jobs/poll/cancel/sync/async/concurrency|Q1/Q8;no ambiguousreplay|
|CAP-08|PreserveACCEPTEDNUMBER/BOOLEAN/TEXT/null/order/duplicates|Bounded1..256;noguessedreshape|
|CAP-09|Nativeformat/descriptors/time/space/units/categories|Q3/Q4;testfixnotpublishedsupport|
|CAP-10|Partialgraph/origins/refresh/invalidate/stale|#199slice/Q8/#126version|
|CAP-11|Best-effortevents/polling/resync|Q8/#126durablecursor separate|
|CAP-12|Preserve#196ACCEPTEdofflinesnapshots|Noqueuedreplay/localengine|
|CAP-13|Resource/model/query/dependency/exportschemas|Q6/Q7|
|CAP-14|Authoring/behaviors actualauthorizedactions|Q5/Q6/Q8|
|CAP-15|OptionalNumPy/pandas/xarray/vector/notebook|Q3/Q4/Q8;optionalimports|
|CAP-16|Package/compatibility/fixtures/support/scale|Q9;asyncfileexport/restore stillundelivered|
|CAP-17|SeparateproviderSDK/embeddedruntime|DEFERRED_APPROVED TargetC/#127|
|CAP-18|PreserveACCEPTEDorigins/noreplay/errors/optionalimports|Regresseachincrement|

## External decisions / minimal unblock

Allrequiredissues89/123/124/125/126/127/129/130/193OPEN; component11OPENat
90f425e0…,base3f98082d…. Runtime/resources/componentmaintainers must identify
authoritative specificschemas/versions/ownedfixtures;deploymentoperator supplies
credentials/consent;scientificcustodians supplyoriginal data/reference/licences;
productowner supplies scope/version/releaseapproval. These are proposed owner
roles,notassigned reviewers. Requestonlytheactual missingcontract/fixture—not
blanketJavaAPIparity. #123/#126/#124/#11do notblockunrelatedsingle-year native
or read-onlyparserwork. MissingreportcompilerisnotPythonpresentation. No automatic
historicalontology/source/algorithmtranslation or workerSDK is authorized.
