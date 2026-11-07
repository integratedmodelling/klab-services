# Ontology review stage saving and visible operation progress

7 November 2026. Services baseline: 00b99c85feb9f2d77bf1e908b9a39e4b82db5894.
Companion IDE baseline: aee6bc9751231ed503256ce3a0dc9d1dbcb728b0.

## Confirmed save-failure mechanism

The exact message, `Revision-checked stage details are not supported`, comes from the
ResourcesService interface default. The current source already implements that method
in ResourcesClient, ResourcesMerger and ResourcesProvider. Therefore the message does
not, by itself, establish a missing override in the current source or a server error.

A read-only runtime comparison reproduced the exact exception using this computer's
older default Maven-cache core.common jar with the newer API. Reflection resolved the
method to ResourcesService and the call threw before any HTTP request. The same real
client fixture with the matching isolated common jar resolved it to ResourcesClient
and completed one HTTP request, returning the expected flow and updated revision.

- Older default-cache common jar SHA256:
  `5C32D77AFBA7BBACD62C8E427B0C855E286F24FE48CF45C56E900C09534EA084`.
- Matching isolated common jar before this repair SHA256:
  `08F1725217676A24384F62AAB07ED546FB8D23FAF62DCC7687435F15668AF5E7`.

The user's testing machine is not accessible. This demonstrates the concrete mismatch
mechanism, not that machine's loaded classpath. Workflow version 0.4 identifies the
workflow definition, not the IDE's loaded Java dependency versions. The IDE diagnostic
now identifies the actual service implementation, supplying artifact and implementation
version when an unsupported stage save occurs, without discarding edited fields.

The IDE depends on core.common through modeler. Installing only the API does not update
that client implementation. At minimum, install core.common and its upstream API from
the same Services revision into the Maven repository used by the IDE, then rebuild and
restart the IDE. From the Services root, the relevant module selection is
`mvn -pl klab.core.common -am install`. The matching core.services application-UI
artifact must also be installed when applying this repair's web changes. Normal IDE
test/package now checks the concrete client and merger methods to catch mixed artifacts.

## Additional confirmed defects repaired

- The old client stage-save call could swallow HTTP errors and accept missing or invalid
  results. It now preserves rejection status, requires a valid updated flow/stage and
  revision, and does not retry or route a mutation to another supplier.
- ResourcesMerger inherited unsupported defaults for five pilot action/review operations.
  These now delegate to the selected primary service, preserving existing routing.
- IDE menus/opening, stage saves and refresh could perform service calls on the JavaFX
  thread. Opening now shows a cancellable loading tab immediately, coalesces repeated
  opens and preserves an already open editor. Save/refresh and preparation show explicit
  pending feedback and keep network work off the UI thread.
- Web preparation used an invisible busy flag, nested loads cleared it too early, and
  later context loads could erase an earlier error. Scoped operation state now keeps
  controls locked until completion, reports preparation/save progress beside the action,
  and shows the result and next step. Errors remain visible with readable contrast.

## Verification

- Selected Services reactor/install: 73 tests passed, zero failures/errors/skips.
  Breakdown: common 32, core 11, Resources 21, Resources server 9. This is selected
  affected-path coverage, not the full repository suite.
- The new real-client HTTP integration has two JUnit cases. Its successful save and
  preparation journey exercises both advertised native and browser service scopes.
  Existing genuine parser/compiler/controller preparation tests also passed.
- Web review regressions: 41 passed, including delayed discovery, preparation and
  material loading; conflict/unknown-result recovery; leaving the page while reads or
  an accepted preparation are pending. The accepted mutation completes once, without
  stale callbacks changing the next page's URL or publishing review results.
- Genuine-runtime browser journeys: both passed, including saving details before
  preparation and loading an already prepared IDE-like candidate without repeating it.
- Typecheck and both standalone/embedded production builds passed. Existing Quasar
  bundle/chunk-size warnings remain.
- Final packaged Java API/common/core/Resources/server jars exactly match the isolated
  installed jars and server product copies by SHA256. The active web bundle and embedded
  assets match the final build; source maps contain both changed Vue components exactly.
- IDE focused suite: 53 passed, including delayed opening/save/preparation and real
  dependency compatibility checks. Full IDE suite: 393 tests, 392 passed; its sole
  error is the previously reproduced SemanticComposer sizing assertion (451.2 vs 454.4).
  No workflow/progress/compatibility test failed in the full suite.
- Independent source review confirmed paired workflow dirty/in-flight close guards,
  preservation of later pairing requests during a pending open, and disposed-component
  suppression of late URL or review publications.

Normal non-Conveyor IDE packaging passed, including the packaged application-UI check. The shaded ResourcesClient, ResourcesMerger and ResourcesService class bytes exactly match the verified dependencies; concrete workflow methods were checked in that artifact. Manual desktop visual inspection was not completed in this run because Chrome permission remained pending. Automated browser screenshots and JavaFX scene tests were completed. The synthetic HTTP fixture was stopped successfully after both browser tests.
## Scope and testing limits

The full-client HTTP regression uses ServiceClientCatalog to create the actual
ResourcesClient and calls it through ResourcesService. The loopback endpoint runs the
real WorkflowController/provider/manager/parser/compiler. Hub profile, project content,
flow storage and identity selection are synthetic fixtures, not a live login or the
production security filter chain. Failed 409/403 saves preserve the aggregate and blobs;
preparation uses the returned saved revision and cannot duplicate a completed result.

No user flow is deleted or modified. No live service/Modeler is restarted. The original
checkouts and default Maven cache are not changed. No push, deployment or remote CI pass
for the new local changes is claimed.

## Reproducing the real-client path

With matching JDK 21 dependencies and the repository's test settings, run from Services:

```text
mvn -pl klab.services.resources.server -am test -Dtest=WorkflowPreparationClientHttpTest,WorkflowPreparationRuntimeTest,ReviewCycleHttpPrincipalTest -Dsurefire.failIfNoSpecifiedTests=false
```

This verification used an isolated Maven repository, isolated `user.home` and the
existing Byte Buddy 1.17.5 Java agent. The complete selected validation additionally
included ResourcesStageDetailsHttpTest, ResourcesWorkflowDelegationTest,
ResourcesMergerTest, ResourcesInitializationTransportTest, ResourcesFlowDeleteHttpTest,
FlowSerializationTest, WorkflowRequestBindingTest, HubWebAuthenticationTest,
ServiceUiApplicationLauncherTest, WorkflowManagerAuthorizationTest,
OntologyReviewBootstrapTest and ReviewPreparationEditingTest.

The browser reproduction uses the existing opt-in WorkflowPreparationBrowserIT bridge
and `playwright.review-runtime.config.ts`, as described in the preceding runtime repair
verification note. Its final test now saves title/description before preparing, checks
visible pending feedback and duplicate-click protection, then reloads the exact result.
