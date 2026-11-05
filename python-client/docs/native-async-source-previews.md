# Native async source previews

Base21750594eebe119d551d5820e861fd615afffdae (PR226). Independent read-only
RC-P4.2/CAP-08 notebook slice while asyncinspectionPR227CI runs; same ownedworktree,
separate branch fromacceptedintegration. Preserve reviewed227candidate/failures.

Existing SourcePreviews deliberately requires synchronous Client and uses actual
Resources `/parseAsset/{knowledgeClass}` plus capabilities. Native async transport
and AsyncResources.capabilities already exist. Add AsyncSourcePreviews.parse with
the same namespace/ontology/strategy/behavior classes and immutable SourcePreview
receipt. Factor shared sourcebudget/credential checks, origin-binding checks and
parser DTO/hash validation so nativeasync cannot silently diverge from sync.

Await network operations directly; no threadwrapper/nested eventloop. Preserve exact
UTF8source/hash by default; edge whitespace may change only under explicit existing
allow_edge_trimming=True policy and must be recorded. No local parser, grammar or
semantic inference, Authoring.ParsedSource admission, import/edit/publish, compilation,
runtime model execution or Java production change. A valid parser receipt is not
revision-aware semantic validity, source persistence or historical study compatibility.

Tests: nativeasync/sync samepayload receipt parity, all4document classes, exact/trim
hashes, invalidUTF8/budgets/credentials beforeHTTP, payloadclass/source/URN/service
errors, capability/endpoint/transport/credential drift, native cancellation/type
refusal. Existing syncsuite must staygreen. Installedgates and one actualJava4family
parse/invalidcontrols workflow after user-scope preparation; no scientific job or
authoring writes. Exactcandidate/privacy/cleanup+guide review/normalPR/CI/eligible
merge/postmerge required; fullRC-P4.2/A/B and allstudy gates remain open.

Initial2ef2fed06:117focused/3,281installed/all17gates/lint/build/sourceparityPASS.
Thisbase excludes PR227asyncinspection/nullasset additions; itsacceptedmerge6af50a1dc
must be integrated andcombinedtree revalidated before one actualfourfamilyparserrun.
Wheel1bc76e6fb75266d942c2d61840fe6b61889d0b7d468144efa130208048cb045c;
sdistd06ea87bda4f5a414ce8bf5417955c92701a5b9aee78ec87de7666305cd3eaee.
No new liveparserPASS claimed from historical syncresults.
