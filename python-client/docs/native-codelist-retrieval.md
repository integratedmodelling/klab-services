# Existing native CODELIST retrieval

Base6af50a1dc83459a17ed065030ca6960addda9b11 (PR227). Independent Python
RC-P2.2/CAP-09 dictionary prerequisite while sourcepreviewPR228CI runs. Reuse same
ownedworktree/separate featurebranch; preserve228candidate, categorical/peer/research.

Demonstrated gap: both Client.resources.retrieve and AsyncClient.resources.retrieve
reject CODELIST beforeHTTP. Java KnowledgeClass.CODELIST maps to Codelist, which
extendsKlabAsset; ResourcesProvider.retrieve1355–1356 delegates to source-derived
ConceptCodelistBuilder via1382–1387. No Java production change needed. Reproducer
native-codelist-retrieval-preflight.json retained; no realnetwork/Javafailure claimed.

Smallestcorrection: add shared retrieval-only class selector for CODELIST and use it
in bothfacades. Preserve existing generic KlabAsset(urn,raw) response, scope/service/
byte/error handling and native-null MissingAssetError. Do NOT extend list/resolve/
ResourceInspector capability without an actualcontract, interpret portablecodes,
flatten entryarrays/collide numeric-string keys, createauthority mappings, contextualize,
invokeprovider/worker SDK or infercode validity/scientific classes/units/revisions.
Existing supportedclasses and KlabAsset→RESOURCE mapping unchanged.

Tests: beforefix CODELISTpositive retrieval failslocal for bothfacades; afterfix exact
encoded rootURN/class/scopedGET, fullportablepayloadretention including mixedcodes/
order/preferences/provenance, unknown/null/error/malformedcontrols, alloldclasses
regressions. CODELISTlist/resolve remainlocalUnsupportedOperationError/0HTTP.
Installedgates and one ordinary actualJava knownconcept CODELIST read/missingroot
control afterpositivelookup required. An empty nativecodebook is not categorical
computation, and no unannotated fixture may be presented as a nonemptymapping.

No historical FREL/ESA/other study dictionary recovered or guessed. Actual scientific
category export/component registration remains blocked by existing authorization/
delivery gates. Guide review/exactidentity/privacy/cleanup/normalPR/CI/eligiblemerge/
postmerge required; fullA/B/studies remain incomplete. No Java repair campaign.

## Candidate validation

Accepted #228 integrated normally; current base includes
`13b723aa3c361e011031e76e5a7730ae0caf28c1`. Candidate
`2f94029fb2cdf15f98b9d34bc7ee5c3767902901` passed 134 focused tests, 3,369
fresh installed tests, all 17 gates, lint/build/source parity. Both positive
retrieval tests failed locally before the selector correction. Package/source/raw
harness identity verified; no coverage thresholds changed.

- Wheel: `c6541ab05e0f6066c77eda56d2bcae9e3c08cbc935b0ac98834b6a34f3a64c1a`
- Sdist: `0b65f4d24d5d6c29808d7753e53617a46e11daeb98ff0010a9e89196df52bde1`

One fresh owned `native-codelist-retrieval-live` run is active on this candidate.
No live success predeclared. Raw portable code entries remain uninterpreted; an
empty native concept dictionary cannot substantiate categorical computations.

## Exact-candidate Java outcome

`2f94029fb2cdf15f98b9d34bc7ee5c3767902901` actual Java PASS: declared root
`consolidation:Region`, native CONCEPT/root/service identity and entries checked.
The served entry array was empty: **empty-metadata retrieval only**, not a usable
categorical mapping or recovered FREL dictionary. Unknown root produced typed
MissingAssetError/HTTP200 after positive lookup. CODELIST list/resolve remained
local UnsupportedOperationError with zero HTTP. Three audited capability/retrieve
GETs, zero inspection mutations/scientific submissions after scope preparation.
Reader/process/listener cleanup passed; unrelated Java unchanged. Exact SDK/install/
raw-harness/live identity, private report hash and credential absence verified.
Final focused checks, normal PR/hosted CI/eligible merge/postmerge remain required.
