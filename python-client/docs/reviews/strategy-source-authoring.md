# Contributor review: one owned strategy-source draft family

Author/CLI review, not independent human approval. Base accepted integration
`cfb7bf48119ad8b395390b4a3e08953cdd134133`; branch
`feature/python-strategy-source-authoring`. Implementation/acceptance are separate.

## Fit and scope

Add only `OBSERVATION_STRATEGY_DOCUMENT` to existing Authoring admission. Reuse
existing source/class/service identity, source budgets, explicit literal consent,
same-session newly confirmed project and unknown-write/no-replay handling. No new
public role-provisioning API or general editing layer. Semantic validity, strategy
execution, ontology/worldview mutation and publication remain unproved/excluded.

Actual Java prerequisites PR207/208 accepted independently; no unaccepted stack.
Private main-only provider classes are explicitly hash/source/ancestor validated
for owned Resources JVMs only. Test classes/controllers must never reach the live
classpath. Shared classes/services remain untouched. A distinct signed TEST operator
gets ROLE_ADMINISTRATOR before its first scope creation; scientist not elevated.

## Findings / validation pending

Important preflight correction: workspace creation is authenticated-only, so it is
not a valid ordinary-user CREATE denial control. Actual project/document grant
guards now use explicit403 after PR207; tests must prove denied assets absent.
Ontology creation has worldview-project gates; one strategy family is chosen
instead of silently broadening/migrating scientific projects.

Important local-review correction: negative-control low-level writes can have an
unknown outcome on transport/server/protocol failure. Convert those failures to
SubmissionOutcomeUnknown and stop dependent cleanup, just like public Authoring.
Only explicit AuthorizationError counts as denial; generic500 never passes.

Initial source/installed test invocations exposed environment mismatch (old installed
SDK, then deliberate noneditable runner guard). Preserve those failures; rebuild
and install candidate before acceptance. No need to weaken guards or scientific
fixtures. Full installed gates running; live authoring NOT_RUN, no PR yet.

First installed run: 2,985 tests passed, four failed because the retained parser
preview test hard-coded the historical namespace-only Authoring family set. Update
that exact contract assertion to namespace+strategy only; previews still cannot
be supplied as ParsedSource and unsupported ontology/behavior writes stay blocked.
No test/coverage gate removed. Final installed run required after this correction
and the unknown-negative-write safeguard above.

## Required acceptance before PR

Exact actual parser class/source/URN/service identity; matching CREATE and retrieved
source/project; invalid source refusal; actual ordinary project and document403
controls with operator absence checks; confirmed owned project deletion followed
by empty workspace deletion and listing absence. Unknown writes stop dependent
mutations with private evidence retained; cleanup errors cannot cascade.

Verify ResourcesProvider class-load origin/hashes against accepted main-only overlay;
all owned processes/listeners closed and unrelated Java unchanged. No scientific
output or original replication claim. Complete installed coverage/review/final-head
normal CI and eligible merge; no human approval or contributor assignment inferred.

## Corrected installed candidate — live acceptance pending

Candidate `35a022211`, accepted base `cfb7bf481`: final installed noneditable
Python3.12.7 run `ab-q6-strategy-final` PASS:2,994tests/17coveragegates/lint/build/
source parity; 126focused authoring/preview/overlay/preflight tests PASS. Existing
Rasterio warning retained. Wheel
`f879dcc011db02a3c4513cd660b9b62594d6dedc71c6f428b27a20b2f5c38d53`;
sdist `0d88b10ecf665139f91a75691fa794785c325ab54e3e3f51130c3734ad4217b1`.
Earlier failed installed run is retained; no coverage gate removed.

Actual owned Java command (active, not predeclared pass):
`run_reliability_acceptance.py --server-repo <checkout> --java <Temurin21> --components <corrected-components.json> --state-dir <q6-strategy-live> --scenario strategy-authoring --allow-owned-authoring --resources-overlay <accepted-resource-overlay.json>`.
No shared service/class replacement or ontology/scientific mutation is authorized
by this command. Provider class-load identity, exact source, permissions, server
object cleanup and process cleanup remain separate required evidence.

## First live failure and one bounded harness correction

`q6-strategy-live` FAILED at missing-project retrieval: backend returned an empty
HTTP body rather than JSON null/404, so the client truthfully raised ProtocolError.
Before failure, parser/CREATE/exact retrieved source/class/project, invalid source
and both ordinary typed403 write denials had succeeded. Owned project then workspace
deletion and all process/listener cleanup passed; unrelated Java unchanged. This
is not complete acceptance, and ProtocolError must not be counted as asset absence.

Correct only the absence proof: successfully list each family as operator, require
the known-created project/strategy to be visible in that same response, then require
denied names absent. No SDK missing-retrieval semantics are changed; no writes are
replayed. One fresh corrected owned run after final installed validation; if another
gate fails, preserve exact evidence and continue independent work rather than a
fixture-repair loop. Earlier failed run and empty-body limitation remain recorded.

Corrected executable `739081adf`: `ab-q6-strategy-listing-final` PASS:
2,994installedtests/17coveragegates/lint/build/sourceparity and126focusedtests.
Wheel `3a41c315b7283aaf5dd8741ceffb058eb507040c8c7bb1ff172d07bde6b16d43`;
sdist `37ec3e2e24128b0338a6bb1a86019cde98c83afce2d37940fb3f5ae88d0d30a3`.
One corrected live run `q6-strategy-listing-live` active; no pass predeclared.
First run class-load log confirms ResourcesProvider loaded from validated private
main-classes, not test classes or overwritten shared output. Receipt
`q6-first-live-diagnosis.json`; corrected-run origin must be verified separately.

## Final exact-candidate acceptance and recommendation

**Ready for bounded maintainer review**, author/CLI only, not independent human
approval. Tested739081adfc7f294d9326d9f61e2450bddec0e460; acceptedbasecfb7bf481.
Subsequent edits are Markdown only, verified before evidence reuse. Full diff
reviewed for usefulness/architecture/contracts/science/identity/lifecycle/state/
performance/maintainability/contributor fit. No remaining demonstrated important
or blocking defect in this one-family contract. No reviewer assigned or overlap
inferred. Support/release/other authoring capabilities stay separate.

| Command/check | Environment | Verified outcome |
|---|---|---|
| `run_gates.py --repo <checkout> --label ab-q6-strategy-listing-final` | Noneditable Python3.12.7,739081adf | PASS:2,994tests/17gates/lint/build/sourceparity; existing Rasterio warning retained |
| Focused authoring/preview/overlay/preflight tests | Same installed candidate | PASS:126; unsupported ontology/behavior writes and preview-as-write-input refusal retained |
| Owned runner command above, replacing state with `q6-strategy-listing-live` | Pinned Temurin21/core/components; accepted main-only provider; distinctsignedTESTadministrator | PASS:parser/class/URN/serviceidentity,CREATE/exactretrievedsource+comments+whitespace/project,invalidsource refusal,ordinaryproject/document403and positive-controlled denied-nameabsence |
| Creator cleanup | Same live state | PASS:projectDELETEthenemptyworkspaceDELETE acknowledgements/listingabsence; no write replay |
| Provider identity/process cleanup | q6-final-candidate-validation.json | PASS:actualclassorigin/private+sharedhashes verified; ownedprocesses/listenersclosed,existingJavaunchanged |
| Hosted final-head checks/normal eligible merge | Final documentation head | Pending; required before merge |

Actual uniquely named draft sourceSHA256
`66a5fae3adec1849639b8db2e26985a6e0c7f476ea75793bac23414d5c287fdb`.
Wheel/sdist corrected hashes above. Earlier failures retained, not recast as passes.
Empty-body missing-retrieval remains separate; no SDK/server error remap here.
No ontology/worldview mutation, strategy scientific execution, immutable revision,
general update/merge/publish, production operator provisioning or concurrency/scale
acceptance. Unknown writes stop dependent cleanup with private evidence retained;
no transaction/rollback promise. FullQ6/B/acceptance rows remainPARTIAL. Next:
normal final-head PR/CI/protections, exact-head eligible merge, ancestry and cleanup.
