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
