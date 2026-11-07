# Ontology review runtime, diagram and administrative deletion repair

7 November 2026. Services baseline: 886cb76cfb4131351111799d8cf7fa266ddf5716.
Companion IDE baseline: 6e97cdcb70ccf74c5d921daf713b1b957c0de10a.
Changes are prepared in the existing feature branches. The user's original checkouts,
running services and stored flows are not modified.

## Confirmed causes

### Browser preparation HTTP 500

The user's supplied service log shows ServiceUserScope.getService throwing from
WorkflowBehaviorBridge.Session.targetActors, then context, available,
WorkflowManager.getActions and WorkflowController.actions. Browser authentication
created an identified service scope without registering its hosting Resources service.
The review extension does not pass through the separate application launcher's
service setup, so refreshing repeated the same failure before actions could be shown.

The failure is reproduced through the actual controller with an unadvertised browser
scope. The repair registers only the authenticated hosting service at login. It does
not copy privileged remote clients, grant new roles, or weaken target/project access.
The application launcher preserves already supplied services while filling missing
application dependencies.

Earlier preparation tests covered parser/action behavior but used advertised or mocked
service scopes; the previous browser scenario supplied canned preparation output.
Those tests did not establish this browser-authentication boundary. New regressions
exercise the production parser, component registry, compiler and controller using a
real HubWebAuthentication implementation with a synthetic Hub profile response. The workflow API uses loopback HTTP; there is no live IdP or production account.

### Disconnected workflow graph

The staged review schema deliberately contains only the INIT transition. Peer/community
rounds, coalescing and termination are managed by the review command protocol. Rendering
only schema transitions therefore showed disconnected stages. No restriction in that
picture meant that an editor could only exit.

The existing ELK/PNG renderer now has separate projections:
- A clearly labeled planned lifecycle, including parallel/optional peer and community
  rounds, coalescing, return to review and termination.
- A saved-flow graph built from authorized projected stages, actual transactions and
  actual review cycles. It does not invent hidden stages or future history.

The IDE retains the informative descriptions and adds the original service-backed image
viewer and PNG export alongside them. Graph responses contain structural fields, never
attachment contents, private feedback, candidate documents or arbitrary metadata.

### Missing deletion control

The pilot hid deletion and blocked whole-flow removal in the service. The user clarified
that this operation must remain administrative, including local-service administrators.

Deletion now requires ADMIN server-side; editor or creator status alone is insufficient.
The IDE also checks the selected service's advertised ADMINISTER permission, rather than
assuming a plain client scope has service-side roles or treating a localhost URL as a grant.
Confirmation explicitly describes irreversible removal of the flow, its stages, review
history and attachments. Project files and already exported archives are not removed.

Individual frozen stages and cycle evidence remain immutable. The separately authorized
whole-flow operation runs veto callbacks before deletion, removes the aggregate before
attachment cleanup, and reports incomplete cleanup rather than claiming success. Failed
aggregate deletion leaves its payloads intact. No actual user flow is deleted by this repair.

Existing notification/archive replay records may retain operational IDs and receipts.
They cannot restore a flow or dispatch new archives without a fresh existing-flow
authorization check; notification delivery remains disabled. This operation is not
secure erasure of external archives.

## DELETE result and retry safety

The Resources client previously discarded the DELETE result and returned true even when
HTTP deletion was rejected. The repaired client requires the actual Boolean response.
A false response preserves the editor and sidebar. Rejected, missing or malformed
responses are reported, and an unknown outcome requires a refresh before another try.
The narrow HTTP implementation disables automatic DELETE retries, redirects and
authentication replay and bounds response headers, body and overall duration. Other
legacy DELETE callers are unchanged.

## Verification

Completed focused checks:
- Genuine parser/compiler/controller preparation: 3 tests passed. These cover browser
  and advertised IDE-like service scopes, exact ontology bytes, five generated
  artifacts, fresh-manager reload and stale replay rejection.
- Two real-runtime browser journeys passed: web create/prepare/reload, and web opening
  an already prepared IDE-like candidate. Neither supplies a canned proposal or
  mocked preparation result. Each persists one five-artifact preparation at revision 2.
- Existing review browser regression suite: 32 tests passed.
- Web typecheck plus standalone and embedded production builds passed. Vite reports
  existing Quasar bundle and chunk-size warnings.
- Strict DELETE and neighboring HTTP tests: 17 tests passed, including eight new
  real-loopback cases. Dropped responses, hostile headers and slow/trickling responses
  do not trigger another DELETE request.
- Administrative aggregate deletion: 6 tests passed, including denied editor/creator,
  actual ADMINISTER scope, veto, failed aggregate removal and partial cleanup outcomes.
- Graph tests: 15 tests passed across adapter, renderer, service and authorized flow
  integration. Generated planned and saved-history PNGs were inspected.
- IDE aggregate against the matching rebuilt Services: 385 tests, 384 passed. The one
  error is the previously reproduced baseline SemanticComposer sizing assertion
  (expected 451.2, actual 454.4). All nine new native-control tests passed, including
  the final draft-schema graph and successful-refresh warning cases.
- Native IDE synthetic fixture: descriptions and genuine service-rendered PNG display;
  PNG export and cancel; deletion cancel, service failure, refresh reconciliation and
  successful sidebar/tab cleanup. Exported PNG matched the service PNG byte-for-byte.

The final selected Services aggregate passed 345 tests with no failures, errors or skips:
common 70, core 73, reasoner 43, resolver 2, Resources 148, Resources server 9. The
first reactor's server module encountered a JUnit parameter-conversion linkage mismatch
before two new test bodies executed. They were changed to ordinary test wrappers with
the same shared assertions; the narrow server rerun passed and packaged/installed
successfully. Earlier modules had already passed and installed. This is selected
cross-module regression coverage, not a whole-repository test pass.

The packaged server distribution's API/common/core/Resources/server jars matched the
isolated installed jars by SHA256. The user's original checkouts remained clean.
No production login, deployment or remote CI run for these new local commits is claimed.

### Boundaries of this evidence

The runtime HTTP harness uses the real HubWebAuthentication implementation with an
injected synthetic Hub profile response. It runs the production workflow parser,
component registry, compiler, controller and manager. Its flow store and project
content/material access are test fixtures. This verifies the supplied exception's
scope boundary and actual preparation behavior, not a live IdP, real project filesystem,
production security filter chain or durable service-process restart.

The native IDE check uses synthetic services with genuine rendered graph bytes. The
companion HTTP transport tests exercise actual network responses separately. No user's
stored flows were deleted, and no running user service or Modeler was restarted. The
user's failing deployment is on a different, inaccessible machine. Its rebuilt behavior
must still be checked there. Real TLS/proxy integration was not exercised by the
loopback transport tests. Build or install Services first, then build the matching IDE.
### Reproducing the preparation regression

Use JDK 21 and the repository's Maven configuration. In this workspace Maven runs
offline with an isolated local repository and the existing installed dependencies as
tails. The runtime tests also use the same isolated `user.home` and
Mockito/Byte Buddy Java agent used by the other workflow tests. Native database tests select their own JUnit temporary directory through the explicit `-dataDir` option.

From the Services root:

```text
mvn -o -pl klab.services.resources.server -am test -Dtest=WorkflowPreparationRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false
```

To run the browser bridge, use the same command with
`-Dtest=WorkflowPreparationBrowserIT -Dklab.test.preparation.browser=true`. Wait for
`../preparation-http-fixture.json`; its `url` is an ephemeral loopback endpoint. The
bridge is opt-in and times out after ten minutes. Do not recompile/install its runtime
jars while it is serving.

In a second terminal, from `klab.core.services/src/main/webui`, set both
`VITE_REVIEW_E2E_API` and `KLAB_REVIEW_E2E_API` to that URL. Optionally set
`KLAB_TEST_BROWSER_CHANNEL=msedge` and `KLAB_REVIEW_E2E_REPORT` to an output JSON path.
Then run:

```text
node node_modules/@playwright/test/cli.js test --config playwright.review-runtime.config.ts
```

Finally POST `/fixture/stop` on that loopback URL with
`X-Review-Fixture-Principal: editor`. This is a synthetic fixture identity, not a login
credential. The two completed browser runs were stopped and exited successfully.
