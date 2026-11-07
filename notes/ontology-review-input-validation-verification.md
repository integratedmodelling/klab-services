# Prepare review draft input validation

7 November 2026. Services base: a1c23918e513094705af7d93570846a2c33c8c14,
which was also origin/develop when this investigation started. Companion IDE base:
f10779460dee6322584e1d554624a226af44fa70. Existing feature branches and the user's
original checkouts were not modified.

## Confirmed failure

The supplied Resources trace reaches OntologyReviewBootstrap.Options at base line 26.
That guard rejects requestedTier below 1 or a null generation date. The production
caller always supplies LocalDate.now(UTC), so the trace identifies a non-positive tier.
The attachment contains no request body, so it does not establish the exact submitted
value. A fresh real-parser/compiler/controller regression using requested_tier=0
reproduced HTTP 500 and the same complete actor failure chain at those source lines.
The prior client-jar mismatch is not the cause of this failure.

Both the Options contract and proposal JSON schema require a minimum tier of 1.
The IDE accepted any integer. The browser's min=1 attribute did not enforce validity
because the preparation button invokes a click handler outside native form submission.

## Repair

- Validate bundled ontology-review preparation parameters after authorization and
  revision checks but before starting the asynchronous actor.
- Reuse the same option rules for both preflight and preparation. Numeric inputs must
  convert exactly to a positive Java integer; no rounding, overflow or silent coercion.
- Return HTTP 400 application/problem+json with code INVALID_REVIEW_PREPARATION_INPUT,
  the invalid field, and a fixed corrective message. Submitted values and nested
  exception messages are never included in this response.
- Preserve general exception redaction. Other runtime failures are not relabeled as
  input errors. The native transport still reports a status-only server rejection;
  the IDE's local validation supplies the tier guidance before any request.
- Prevent invalid tier submissions in the browser while preserving entered fields.
  The independently tested client changes are combined in this repair branch.

## Verification

The pre-fix regression failed as expected with HTTP 500 instead of the required 400.
Final selected Java coverage: 48 tests passed, zero failures/errors/skips across the
unchanged workflow/binding suites (36) and final HTTP suite (12). This is affected-path
coverage, not the full repository test suite. The first expanded run caught a response
serialization mismatch; the custom service ObjectMapper nested ProblemDetail extension
properties. The endpoint now emits explicit RFC 9457 fields, and all 12 HTTP tests passed
on the final code, including top-level code/field assertions.

New cases cover zero/negative tiers through both native and browser scopes, exact numeric
conversion, fractional/overflow/null/missing/wrong-type inputs, invalid identifiers and
blank editorial fields, safe diagnostic text, unchanged complete flow snapshots/blobs,
403 authorization precedence, 409 revision precedence, and successful correction against
the same revision through the actual ResourcesClient over HTTP.

Browser final regressions: 43 passed in headless Edge. Typecheck and both standalone and
embedded production builds passed. The tier guard is scoped to prepare-review-draft and
requested_tier; an unrelated custom integer action still accepts a negative value.
The combined UI source exactly matches the independently tested final UI commit.

Independent source review found no blocking issue. git diff --check passed.

Selected Java reproduction command (from Services, with the repository's JDK 21 test
configuration and an isolated Maven repository/user.home):

```text
mvn -pl klab.services.resources.server -am test -Dtest=WorkflowPreparationValidationTest,WorkflowPreparationRuntimeTest,WorkflowPreparationClientHttpTest,OntologyReviewBootstrapTest,OntologyReviewBootstrapActionTest,ReviewPreparationEditingTest,WorkflowManagerAuthorizationTest,WorkflowBehaviorBridgeTest,WorkflowRequestBindingTest,ReviewCycleHttpPrincipalTest -Dsurefire.failIfNoSpecifiedTests=false
```

This run used the installed Byte Buddy 1.17.5 Java agent and offline dependency resolution.
The final 12-test HTTP rerun selected WorkflowPreparationValidationTest,
WorkflowPreparationRuntimeTest, WorkflowPreparationClientHttpTest and
ReviewCycleHttpPrincipalTest after the response-format repair.

The HTTP fixture uses real workflow controller/provider/manager/parser/compiler code,
with synthetic identity, project content and in-memory storage. It is not a live login,
production security-filter test or restart of a deployed service. The native-client
case uses a catalog-created ResourcesClient across loopback HTTP to the same controller.

No user flow, stored ontology, live service or default Maven cache was modified.
No push, merge, deployment or remote CI result is claimed.
