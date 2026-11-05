# Contribution review: native CODELIST retrieval

## Recommendation

Ready for maintainer review of retrieval-only CODELIST compatibility. Contributor/CLI review under
`KLAB_CLI_Contribution_Review.md`, not human approval. Existing Java retrieval
contract needs only a bounded Python class-selector correction, not Java repair.

## Reviewed state and contracts

Integration `13b723aa3c361e011031e76e5a7730ae0caf28c1` (#228); current branch
`feature/python-native-codelist-retrieval`. Contract `b983929d7` precedes
implementation `15ce01595`. Accepted parent integrated normally in the same owned
worktree; unrelated research, categorical candidate and peer failures preserved.

Java `KnowledgeClass.CODELIST` maps to `Codelist`, which extends `KlabAsset`.
`ResourcesProvider.retrieve` derives concept codebooks through the existing
`ConceptCodelistBuilder`. Python's shared class gate rejected this class before HTTP.
The new retrieval-only selector is shared by sync/native async facades; list/resolve
continue through the unchanged restrictive gate. No endpoint, credential, principal,
provider routing, dependency or worker API expansion.

## Findings and limits

Missing Python compatibility: both positive retrieval tests failed before correction
with local UnsupportedOperationError; no network/Java defect inferred. The smallest
correction preserves generic `KlabAsset(urn, raw)` decoding rather than imposing a
new scientific codebook interpretation. Numeric and string codes remain distinct;
entry order/preferences and all served fields retained. No dict flattening, mapping
creation, unit/class inference, revision or scientific value claim.

No important/blocking defect demonstrated after focused tests in this bounded scope;
full installed/live evidence still needed. Unsupported operations remain local and
zero-HTTP. Current native-null/malformed/auth/server errors retain typed behavior.

## Validation actually performed

| Check | Revision/environment | Result | Establishes / limits |
|---|---|---|---|
| New positive retrieval tests before fix | b983929d7, Windows Python 3.12 | 2 failed | Both facades reject CODELIST locally despite native route contract |
| New tests plus async client/resource inspector | 15ce01595, exact source | 106 passed | Mocked route/encoding/scope/full payload/absence/error/unsupported controls, not science |
| Expanded focused/package/17 gates | 2f94029fb, Windows Python 3.12 | Passed: 134 focused / 3,369 installed / all 17 gates | Build/install/source parity/lint, existing class selector and empty-codebook guard; exact package/source/harness identity verified |
| Actual declared-root Java metadata | 2f94029fb, owned fixture | Passed | Native root/type/service/entries and typed missing-root HTTP200 after positive lookup; empty entries establish metadata only; 3 GETs and no inspection/scientific writes |

## Scientific, privacy and lifecycle boundaries

Live root is declared `consolidation:Region`, not an invented annotated code tree.
Zero entries are explicitly accepted only as empty metadata retrieval; this does
not establish a usable categorical mapping, categorical computation, unpublished
component readiness or Kaduna FREL provenance. Original study gates remain blocked.

Controlled ordinary TEST principal/local federation, not production Hub/license
acceptance. Audit begins after required user-scope preparation and permits only
capability/retrieve GETs. No contexts, authoring, job submission or scientific
execution. Native async transport closes locally; owned stack/process/listener
cleanup and exact source/install/harness/live privacy binding required before PR.

Actual Java acceptance now verified: empty `consolidation:Region` concept codebook,
typed missing-root control, local list/resolve zero-HTTP refusal and 3 audited GETs.
No historical dictionary or categorical calculation established. Local reader/owned
processes/listeners closed, unrelated Java unchanged; exact source/install/raw-harness/
live identity and report credential absence/hash verified in the private
`native-codelist-retrieval-final-validation.json` receipt. No important/blocking
defect demonstrated in this bounded scope after validation; not exhaustive proof
or human maintainer acceptance.

## Coordination and next actions

No Java/component publication, custodian contact, grammar/compiler or NiFi work.
Full RC-P2.2/CAP-09/A+B remain incomplete. Installed gates and one actual Java
metadata run/evidence binding passed. Finish final focused/map checks, normal PR/CI/eligible merge,
ancestry/tree equality and exact-source postmerge. Continue independent queue after
delivery rather than treating this prerequisite as whole-program completion.
