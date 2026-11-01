# Python client: current develop integration

## Candidate boundary

Local integration branch: `feature/python-client-develop-integration`.

- Python-client parent: `188caeb47b86f2eb114cd6f557cf839df0eb10ee`.
- Develop parent: `671a8181551a51b8a6b8080c3a13a2bf1cd0f048`.
- Common ancestor: `142cb08e76da133a0396340b7293396bb2dc1e2c`.

This branch combines those histories without modifying `feature/python-client` or
`develop`. It is not a release or authorization to publish artifacts or deploy.
PR #90 (`244883d7b04f1eae2cc22e799575d33a9bdb2a43`) is earlier integration
work, not this candidate. Historical notebooks used that earlier server revision;
their success is not a test of this combined tree.

## Integration decisions

- Preserve develop's bounded shared-default-session membership policy, including
  `sessionFederationId` on persisted descriptors and reconstructed parents.
  Shared-session membership does not grant access to private descendant contexts.
- Preserve develop's request-local context/session views, credentials, roles and
  Job ownership. Do not replace them with the older identity-only context copy.
- Keep a single managed-scope authorization helper and persisted-owner constant.
  Automatic merging otherwise introduces duplicate definitions.
- Reload authoritative context configuration on reconnect. Keep develop's cold
  configuration lookup and Runtime URL binding. Reject malformed requests,
  missing contexts and denied access with distinct domain errors.
- Parse persisted ACLs before the owner shortcut, so malformed stored permissions
  fail closed for owners as well as collaborators. Missing legacy ACLs remain
  owner-only.
- Use one authorization exception handler, with explicit problem-JSON content
  type for authorization, missing-resource and invalid-request responses, even
  when the caller requested a binary export. A textually clean merge otherwise
  introduces ambiguous Spring exception handlers.
- Retain the Python branch's null-result Job failure, storage-finalization and
  string-unit fixes, plus its Resources operational Reasoner dependency.
- Retain ACL-based context discovery, including explicitly shared/public contexts
  outside the caller's membership group. Membership alone is not an ACL.

No wire DTO fields, endpoint paths, session ID scheme or Python API are deliberately
removed by these resolutions. This is not a claim of complete behavioral backward
compatibility: authorization/error handling, Jobs, unit handling and service
readiness have intentional differences from unchanged develop.

## Regressions

New or extended regression checks cover:

- Spring handler registration and 400/403/404 responses for JSON, TIFF and GeoJSON
  Accept headers, without leaking exception diagnostics.
- Forged reconnect descriptors not granting access to another user's context.
- Cold configuration preserving parent membership, owner, Runtime identity and URLs.
- Explicit denied-access exceptions rather than silently missing configurations.
- Malformed persisted permissions rejected even for the owner; blank identities
  cannot read public contexts.
- Repeated same-ID finalization preserving a real allocation's numerical cell data.

Validation results and remaining gates must be reported for the actual candidate,
not inferred from earlier branch reports. Test output is kept outside the source
tree under the approved OpenCode temporary directory.

## Local validation, 16 October 2026

Python 3.12.7 on Windows, fresh isolated environments:

- Source: 988 offline tests passed, no failures/errors/skips.
- Installed wheel: the same 988 tests passed; import location confirmed in
  `site-packages`, not the source checkout.
- Existing changed-code and nine-module line/branch coverage gates passed;
  overall statement coverage 96.09%, branch coverage 93.41%.
- Existing Ruff E9/F63/F7/F82 selection passed.
- Wheel and sdist built successfully; core-only installed-wheel smoke passed
  without NumPy, Rasterio or xarray installed or imported.
- The Python 3.11/3.13 and Linux matrices were not rerun in this session.

The initial reused Python environment had incompatible inherited NumPy/pandas
packages and failed collection; the fresh isolated environment above resolves
that setup failure without changing client requirements.

The first Java attempt used stale locally cached language SNAPSHOTs and failed
compilation of unchanged develop `LanguageAdapter` APIs. Refreshing published
SNAPSHOTs supplies the required `ABOUT` and `getHeadExpression` contracts.
The aligned language builds are `20261016.113257`: observable `72`, worldview
`60`, kim `58`, observation `61`, kactors `57`. JDK: Temurin 21.0.12.1+1;
Maven wrapper: 3.9.5.

The initial same-ID storage regression completed its assertions but encountered
Windows mapped-file deletion during JUnit temporary-directory cleanup. The test
now uses real storage, cell data and manager registry with in-memory primitive
buffer allocation; it does not claim mapped-file persistence or restart coverage.

Final strict Java selection: **287 tests passed in 57 suites, zero failures,
errors or skips**. The same selection against unchanged develop `671a81815`
passed **243 tests, zero failures, errors or skips**; feature-only test classes
are absent from that baseline. All 243 identifiable baseline cases are present
and passed in the candidate. This is a selected-regression comparison, not a full
reactor test pass or the broader comparison required for promotion.

The source was an uncommitted, resolved merge during validation: buildnumber
output still identifies the Python parent, not a combined commit. A normalized
LF SHA-256 manifest of 1,464 Java/Groovy, Python SDK source and Maven/project files
identifies the tested working candidate:
`985e7eb9d7a827b158637d63e42a880d6681ed499da736b71b85344e200e4b00`.
Those original Java/SDK source files are unchanged; later functional harness
additions have a separate manifest and live record below.
Rerun applicable checks on the eventual committed promotion candidate.

Evidence in `C:\Users\lumsd\AppData\Local\Temp\opencode`:

- `python-develop-integration-source.json`: source hashes and aggregate manifest.
- `python-develop-integration-java-comparison.json`: suite/case outcomes for both trees.
- `python-develop-integration-java3.log`: successful candidate run, SHA-256
  `e07a8ef3988381a2bbf195b3d96a7c0b810c313a4469e10750ce6c0138e7da91`.
- `python-develop-baseline-java1.log`: successful baseline run, SHA-256
  `c063a71fd2c7b01363a7217bf8717221352387d03235ead613d09321954f2eb4`.
- `python-develop-integration-python2.xml`, `python-develop-integration-installed.xml`,
  `python-develop-integration-coverage.json`, and `python-develop-integration-dist/`:
  Python source/installed tests, coverage and built packages.

Reproduce the Java selection from the repository root with JDK 21 configured:

```powershell
$selection = @(
  'PersistedContextAccessTest,RuntimeContextAuthorizationTest,DomainErrorMappingTest,ManagedScopeAccessTest',
  'ScopeManagerTest,TokenAuthorizationFilterTest,FixtureControlsTest,StorageRegistrationTest,StorageFinalizationTest',
  'JobManagerTest,SemanticsBuilderUnitTest,Neo4jQueryExecutionTest,PollingFutureTest,KlabErrorHandlerTest,UnitMediationBaselineTest',
  'FederationSessionIdentityTest,FederatedScopeControllerTest,FederatedContextPermissionsTest,IdentityHeaderValidationTest',
  'FederatedIdentityTransportTest,ClientScopeCreationTest,ScopeAdvertisementsTest,ServiceClientCatalogTest,AMQPChannelTest',
  'UserScopeAdvertisementTest,*Authority*Test,CachedAuthorityTest,ConceptSyntaxCacheTest,SemanticSearchSessionTest,SemanticsBuilderTraitTest,Worldview*Test'
) -join ','
# Use -U instead of -nsu once if the language snapshots need refreshing.
.\mvnw.cmd -B -ntp -nsu -pl 'klab.services.resources.server,klab.services.reasoner.server,klab.services.resolver.server,klab.services.runtime.server,support/klab.support.graphdb' -am "-Dtest=$selection" '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## Promotion gates still required

### Live functional attempt: blocked, not a pass

The combined Java stack was started locally on 16 October with isolated signed
TEST credentials and the same pinned imod worldview used by the earlier demos.
The existing compute-and-analyze acceptance failed two tests at Reasoner HTTP 500
(`Accepted semantic knowledge is unavailable`). A new maintained
`develop-compatibility` suite now checks accepted semantic readiness first, then
is designed to perform independent Python scientific roundtrips and the existing
private/shared/exclusion and collaborator-first checks across a Runtime-only
restart. It is invoked by the default local acceptance runner and available as a
named functional suite in the exporter-enabled runner.

The extended offline unit/local-integration suite passed **1,010 tests**, with
zero failures/errors/skips; the existing lint selection also passed. These
offline harness/safety regressions do not turn the blocked live result green.

The actual named-suite run failed at readiness: bean VALID, logical UNRESOLVED,
accepted generation 0, with an **uncaptured numbered ODO OWL import**. Running
unchanged develop's graphdb/Resources/Reasoner reproduces the same diagnostic on
the same 28 worldview source hashes. This is an inherited deployment-dependency
blocker under this fixture, not proof that the Python client caused a regression
or that either stack is scientifically ready. The baseline comparison here is
readiness-only, not a full live workflow comparison.

All owned service exits were confirmed and cleanup returned zero. No production
stack was touched. The downstream scientific/restart assertions were not reached;
they must not be described as passing. Required captured semantic dependencies
must be supplied and the suite rerun without weakening candidate publication or
substituting unverified core ontology bytes. Resolving ODO alone may expose
additional dependencies.

See [functional test documentation](../python-client/docs/functional-testing.md)
and [the machine-readable result](../python-client/docs/results/develop-compatibility-verification.json)
for exact inputs, evidence hashes, rerun instructions and limitations.

### Remaining acceptance

- Rerun applicable strict Java/Python checks on the final committed candidate and
  execute isolated live workflows there; compare broader failures with unchanged develop.
- Two independently authenticated native Java clients sharing a nonlocal default
  session, both creation orders, plus graph-retaining restart and collaborator-first
  reconstruction. Mock identity tests are not a substitute for this acceptance.
- Existing scope-cache origin isolation remains separate work: cached bare scope
  IDs do not establish isolation between two Runtimes with the same scope ID. See
  [scope propagation plan](SCOPE_PROPAGATION_TODO.md).
- Genuine Hub onboarding, independent deployment/provider acceptance and relevant
  scientific/export checks. Controlled TEST fixtures do not establish production
  readiness or scale/soak behavior.
- Corrected GeoTIFF behavior requires separately building/reviewing/installing the
  geospatial component patch; merging this repository does not install it.

Before promotion, review deployment wiring: Jenkins publishes artifacts and enables
container builds on `develop`/`master`. Integration checks must use local test/package
goals, not `deploy`, and must not request the container-build override.
