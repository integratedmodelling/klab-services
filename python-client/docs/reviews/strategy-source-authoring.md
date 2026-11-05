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
