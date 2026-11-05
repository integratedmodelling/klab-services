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

Needs final exact-candidate validation before a PR. Focused real SpringMockMvc
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
