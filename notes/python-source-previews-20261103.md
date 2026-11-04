# Read-only source-document preview slice

Base: separate fixture-order prerequisite PR140@28fb28bb4 (underlying merged
base e09a7de7cac6b48526fd8b7ac72879abb36a77a3). Independent of unmerged agent,
Job-binding, graph and acceptance-map feature slices. No PR merge or publication.

Final local verification: **2266full offline tests, all14declared Python gates
(10coverage gates), lint and wheel/sdist PASS**. One native DTO serializer/
KnowledgeClass mapping test PASS. Remote CI is recorded in the PR; local tests
do not establish maintained-parser acceptance.

Retained external evidence SHA256:

| File | SHA256 |
| --- | --- |
| `gates.json` | `ffa58079bef91244674111960710a10ec9d44a0fb4f9cf7c0da0d992658d0f65` |
| `gate-01.log` | `07d166719dedc8620cb6ef4851a805ea320b759f09c7860008efec31c358e6c1` |
| `java-source-dto-wire-final.log` | `9c2678e51a5f436d1039cb0c211c6a818947c0942a39bfec0b975cc894fb4403` |

## Implementation and verified boundary

`SourcePreviews` calls only Resources capabilities and existing whole-document
`/parseAsset/{kind}` routes for namespace, ontology, strategy document and
behavior. It checks admitted native class names, source/URN/service/configuration
identity, finite UTF-8 budgets and server notifications. Default exact source;
optional explicit ASCII edge trimming records both source hashes. JSON escaping
cannot hide configured credentials in source or decoded nested metadata.

Its immutable `SourcePreview` is a different DTO from authoring `ParsedSource`.
Authoring.DOCUMENTS and its write methods are unchanged: no ontology/strategy/
behavior ADD/write, import, overwrite, runtime registration or execution is
enabled. Existing authoring tests pass alongside preview tests. No native
grammar/semantic algorithm has been ported or scientific meaning changed.

57 new focused tests pass;103 with original authoring tests. New module has89
executable lines/32branch outcomes at unexcluded100%. Deterministic HTTP mocks
verify positive/negative/source-policy/identity/immutability/write-isolation
contracts; no source preview is claimed to be a scientific result.

The actual production-Jackson DTO fixture/test passes with four native document
classes. It also checks KnowledgeClass.getAssetClass() mapping for each admitted
route name. **DTO serialization only, NOT live Resources parser acceptance**;
the fixture deliberately uses DTO-only text. Python consumes the same committed
fixture rather than a guessed private oracle. No production Java is changed.

## Portable commands / evidence

See `python-client/docs/source-previews.md` for installation, usage, both source
policies, no-write limits and test commands. Local external logs are retained
in sibling `python-source-previews-evidence-20261103` (`gates.json`, `gate-*.log`,
`java-source-dto-wire-final.log`). A fresh clone uses committed source/tests and
regenerates `klab.core.common/target/source-preview-wire-actual.json`; it does not
need a private Runtime stack, credentials or original-study files.

The fixture-order dependency is explicit: PR139 push CI uncovered a pre-existing
filesystem-order-sensitive partial-output test; separate PR140 supplied forced
normal/reverse traversal regression and sorted copying. This slice includes
that prerequisite rather than waiving Python CI or hiding its failed history.

## Acceptance not completed

Full Resources/Runtime builds remain blocked by the documented observable
language source/artifact mismatch: LanguageAdapter.java:2006 EXTENT and :2007
ATTRIBUTE unresolved. #89/#125 contain pinned repro/hash/source-pair handoffs.
Restore a coherent maintained pair, then run original source-family fixtures,
invalid-document/credential positive controls and semantic-validation/source-
fidelity tests against the actual maintained Resources parser. The DTO wire
test is not a replacement, and structural class checks do not establish source
semantics, persisted publication or scientific acceptance.

Broader invalid-document diagnostic editing, import/export authoring, version
history, edit conflict, project locks and publication remain unfinished. Parser
error notifications currently raise existing typed errors. This is not a full
authoring SDK, operation inventory or roadmap completion. Implementation/review
here is author-conducted, not independent maintainer approval. No real source
workspace/scientific Job was created, started, replayed, cancelled or disposed;
no persistent services or unrelated terminals were touched.
