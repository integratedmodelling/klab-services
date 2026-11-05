# Contributor review: explicit resource-access HTTP denial

2026-11-05 UTC; author/CLI review, not independent human approval.
Base554b5e30b9433300610ff66c4f79f6d564018827; branchfix/resource-access-forbidden.
Direct Q6 permission-contract prerequisite, not general server stabilization.

## Demonstrated defect / bounded change

ResourcesProvider1091–1094 and2629–2632 throw KlabResourceAccessException for
project/document CREATE denials. Compiled baseline probe selected
handleDefaultException and observedHTTP500, matching KlabErrorHandler's fallback.
This makes Python classify a denied ADD as unknown server/write outcome. It must
not be counted as authorization acceptance or 'fixed' by granting user permissions.
Externalresource-access-baseline.json pins sourcehead,compiledhandler/probesource
hashes and status; no services or shared classfiles changed for that probe.

Change only the two explicit project-create/project-operation grant guards to
throw existing KlabAuthorizationException, already mapped to sanitized403ProblemDetail.
Do **not** globally map KlabResourceAccessException: source inspection shows it also
represents unavailable resources, transport failures and elapsed deadlines. That
overbroad initial approach was rejected before commit/testing. No permission/grant/
controller/storage/model/SDK/scientific/worldview changes. Unrelated exceptions retain500; missing assets and
explicit conflict statuses keep their existing handlers. Source-derived workspace
creation is authenticated-only and distinct from project/document CREATE; do not
invent a universal ordinary-user workspace-denial rule.

## Validation contract / recommendation

Ready for bounded maintainer review after the exact-candidate checks below;
not independent human approval. Focused real SpringMockMvc
tests must verify actual guard denial types,unchangedallowedgrant,routing,PUTdenial403,JSONproblemtype,secret/project/stacktrace
non-disclosure,existingauthorization/missing/invalid/conflict/fallback/binary-error
behavior. Compile/run only the changed provider and targeted tests in private
classes against the pinned current classpath; leave user/shared targetclasses
and services unchanged. Hosted packaging command is retained, with a separately
named focused KlabErrorHandlerTest/ResourceCreationAuthorizationTest Maven step,
not a full Java suite claim. Existing HTTP advice source is unchanged.

Normal final-head hosted checks/protections and exact ancestry/postmerge/branch
retirement remain required. Q6 feature branch is preserved independently with
contract-only work, not stacked on this unaccepted fix. No source authoring or
scientific write has been performed for this change. FullA/B remain incomplete.

## Exact-candidate validation and limits

Implementation `9a39e8a39cd33f29a199fb9087e10b6c925b7f53`; only subsequent
Markdown checkpoint edits permitted before evidence reuse. Full feature diff,
caller semantics and generic exception uses inspected. No remaining demonstrated
important/blocking defect in this bounded correction. Permission calculations,
allow branches, file storage and scientific behavior are unchanged. The original
HTTP advice implementation is unchanged; unrelated resource/transport/deadline
failures deliberately keep their prior classification.

| Check / command | Exact environment/evidence | Result / limit |
|---|---|---|
| Compiled-baseline `ResourceAccessBaselineProbe` | Temurin21.0.12.1+1,currentpinnedclasspaths; externalresource-access-baseline.json | PASS:reproducedgenericresourceexception→fallback500;sourceprojectguardsusedthatwrongtype |
| `run_resource_authorization_tests.py` | 9a39e8a39;isolatedjavacmain/testclasses;JUnit5.10.1/Platform1.10.1,Mockito5.18.0,SpringTest6.2.5 | PASS:compileprovider+tests;9focusedtests,0skips;actualgrantguardsdenywithKlabAuthorizationException,allowunchanged;realSpringMockMvc403sanitizedproblem;genericfallback500/missing/invalid/conflict/binaryroutesretained |
| `pytest test_authoring.py test_source_previews.py test_roadmap_acceptance_map.py` | ExistinginstalledSDK, unchanged production Python | PASS:108tests;notnewwheel/liveauthoringacceptance |
| Workflow YAML/pin/focused-command inspection | ExternalPyYAML6.0.2, no projectdependencychange | PASS:oldfullpackagingcommand+officialsourcepinretained;focusedMavenstepadded |
| Sharedclassfiles/processes | Candidate compile mainclasses in private evidence directory only | PASS:oldhandler/providerclasshashesunchanged;noownedJava/cmdprocessremains;nosharedservicestart/stop |
| Hostedfullpackaging+focusedMaventests/Pythonchecks | Finaldocumentationhead | Pending;allselectednormalchecksrequired |

Pythonwheel: **N/A, Java-only exception-contract change**. FullJavasuite and actual
source-authoring/scientific execution:NOT_RUN/notclaimed. Local test tool jars are
hashed explicitly in `resource-access-candidate.json`; hosted Maven uses declared
project dependencies and must pass independently. No class byte-equivalence to a
future full build/release is asserted. The private main-only compiled provider is
retained for later explicit owned Q6 validation; shared Modeler classes stay intact.

Scientific meaning/large-data performance N/A: no calculation/data path is changed.
Identity/privacy matter:403hasgenericAccessdenied with no project identity/grant
details/stacktrace; permissions remain authoritative in the existing service. No
retry is introduced. Contributor overlap not inferred; nobody assigned. Smallest
next action:normal final-head PR/CI/protections/eligiblemerge, then exact accepted
source Q6 strategy draft/permission validation. Scope-specific unrelated material/
workflow error semantics are not silently declared fixed.

## Accepted routing CI prerequisite and final-head revalidation

PR208 merged normally at `079bb15bfe3fa29fbe8528b4569572123f8c4cd4`, reviewed
`6dda63cd0dbdaba46a84ca192e2b7fd76936c223`. Both routing jobs, packaging, Python,
Windows/core and Jenkins checks passed. Merge ancestry/source equality verified;
owned CI branch retired. Only workflow bootstrap and its review changed.

Accepted integration is now incorporated into PR207. The failed old routing run
41970836627 remains preserved, not waived. Its original packaging log independently
verifies KlabErrorHandlerTest7 and ResourceCreationAuthorizationTest2, each with
zero failures/errors/skips. Tested provider/test source and private class hashes
must remain exact; final-head hosted checks including routing and the two focused
Maven test counts must pass again before normal eligibility/merge. No live services,
shared classfiles or scientific inputs changed for this prerequisite.
