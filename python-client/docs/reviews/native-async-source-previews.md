# Contribution review: native async source previews

## Recommendation

Needs contract/coordination evidence for actualJava parser claim; contributor/CLI
review under KLAB_CLI_Contribution_Review.md, not human approval. Existing parser
routes are supported. Native awaited facade shares checks rather than porting a
grammar or treating parser success as authoring/semantic validity.

## Reviewed state

klab-services acceptedbase21750594e(PR226); branch feature/python-async-source-previews,
contract007d1a13d beforecode. Same ownedworktree; reviewedPR227candidate/failures
preserved. PR227 nowmerged6af50a1dc but exactsourcepostmerge checks/parentintegration
wait until this branch's activepackagegate finishes; no checkout mutation duringgate.

## Capability and boundaries

`await AsyncSourcePreviews(async_client).parse(...)` yields existing immutable
SourcePreview for namespace/ontology/strategy/behavior. Shared source/byte/credential
preflight, transport/endpoint/credential/service binding and parser class/source/URN/
hash validation preserve sync semantics. Explicit edge-trim policy records bothhashes.
No threadwrapper/nested eventloop, localgrammar, Authoring.ParsedSource admission,
import/edit/publish/compile/execute or scientific migration. Scope-independent parser
receipt is not a revision-aware semanticvalidity/sourcepersistence/production promise.

## Findings, ordered by consequence

Missing Python capability: existing SourcePreviews requires sync Client although
native AsyncResources capabilities/transport already support parser calls. Pure
validation and before/after binding helpers extracted for bothfacades; no important/
blocking defect demonstrated after78initial tests. Native cancellation stops local
request only; serverparser abort is not promised. NoJava/grammar defect demonstrated.
Fullpackage/integrated/live evidence remains required, not an exhaustive proof.

## Validation actually performed

|Check / command|Revision/environment|Status|Establishes / limits|
|---|---|---|---|
|pytest test_async_source_previews.py+test_source_previews.py|candidate sources, WindowsPython3.12.7|Passed:78|Fourwireclasses/syncparity/immutablehash/trim/secret/DTO/origin/type/cancel controls, mockedparser notgrammar proof|
|Expandedsource+asyncclient/fullinstalled17gates|candidate|Running|No predeclaredresult or gatechanges|
|Initial expanded/package result|2ef2fed06|Passed:117focused/3,281installed/all17gates|Lint/build/install/parity beforePR227parentintegration; no coveragegatechanges|
|ActualJava4family parserworkflow|freshownedfixture|Notrun|Required positive/invalid/sourcehash/serviceidentity/noauthoring audit; not semanticvalidity/science|

## Scientific and lifecycle evidence

Reuse existing Java-validated parser_preview.DOCUMENTS/validate_receipt syntax
fixtures, not historicalstudy models. Namespace setto3 is parser text, not computed
data. Invalid syntax may raise checked ServerError/ProtocolError; it must not yield
a SourcePreview. Blank/local policy failures must make zeroHTTP. Auditafterstandard
user-scopepreparation allows only capabilityGET/parserPOST, no submission/persistence/
compilation/runtimeexecution. Exactcandidate/privacy/ownedcleanup stillpending.

## Contributor coordination

No language/compiler/Java/component/NiFi work or publication. FourclassDTO map
already supported; no grammar syntax added. FullRC-P4.2/knowledge revisions,
authoring and studies remain open. Failedpeer/categorical/originalresearch preserved.

## Smallest next actions

Finish currentgate, exact227postmerge/normalparentintegration then combinedgates,
one actualfourfamily parserrun, exactidentity/privacy/cleanup, finalguide review/
normalPR/CI/eligiblemerge/postmerge; continue independentqueue.

## Suggested PR description

Expose native async read-only sourcepreview receipts with shared validation and
syncparity for supported fourdocument classes. Preserve source hashes/explicittrim/
servicebinding and secret safety; no authoring or semanticvalidity promise. Report
focused/installed/integrated/actualJava/parsernegative/cleanup evidence separately.
