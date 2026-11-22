# Concept and observable documentation

The Resources and Reasoner services expose Markdown through the shared information endpoint:

```http
GET /api/v1/info/OBSERVABLE/{urn}
Accept: text/markdown
```

Use the configured `ServicesAPI.INFO` base path and percent-encode the complete URN as a path
segment, including spaces in composed expressions. `CONCEPT` is also supported. A concept
expression is valid observable syntax, so CLI clients always use `OBSERVABLE`.
The request uses the normal authenticated user scope. Missing documentation returns HTTP 404;
successful responses use `text/markdown;charset=UTF-8` and `Cache-Control: private, no-store`.
Existing JSON requests with `infoClass` and PNG requests remain available.

In Java, request `service.info(urn, KnowledgeClass.OBSERVABLE, String.class, userScope)`.
The common service client negotiates Markdown for concept/observable String projections.
Other String projections retain their existing behavior.

## Integrated worldview report

On the Reasoner, request `GET /api/v1/info/WORLDVIEW/current` with `Accept: text/markdown`
in an authenticated user scope. The loaded worldview URN or ID can also select the report.
The Java equivalent is `reasoner.info("current", KnowledgeClass.WORLDVIEW, String.class, scope)`.
The staged Reasoner reads the accepted generation. A request without an available accepted
runtime can fail until admission completes. An unknown worldview selector returns 404.

The report lists ontology documents in load order with source hashes, imports, declarations,
nesting, notes and authored clauses. Each domain includes its translated logical axioms.
Direct inferred named superclasses, strategy documents, authority bindings and diagnostics
provide context for interpreting those declarations. Use the individual semantic reports for
axiom provenance and the authorized Resources `GET /api/v1/list/WORLDVIEW` snapshots for the
original ontology and strategy source. When multiple providers contribute, the report
identifies the selected provider of each project. Local projects override remote copies as a
unit; a file removed locally stays removed. Match ontology URNs and SHA-256 hashes of UTF-8 source
before combining providers. Report caching is private and requires revalidation. The Reasoner
reuses a rendered report within the same semantic revision and refinement state.

## CLI and IDE

```text
reason info biology:Tree
reason info presence of biology:Tree
reason info -s biology:Tree
reason info --syntax presence of biology:Tree
```

The default command requests the Reasoner report; `-s` and `--syntax` request Resources.
The command sends the complete expression without resolving it through the Reasoner first,
so syntax inspection works independently of a Reasoner connection. Its `MarkdownDocument`
result is rendered by the IDE's existing Flexmark Markdown-to-BBCode adapter and AtlantaFX
layout component. Missing URNs, services and results produce command errors.

## Docstrings

`KimConceptStatement.getDocstring()` and `KimModel.getDocstring()` contain Markdown source.
Keep that source intact in semantic beans, metadata, indexes and service responses; render it
at the documentation view rather than converting it to HTML in the service layer.

The current paths are:

- `LanguageAdapter.adaptConceptDefinition` copies the worldview declaration description into
  `KimConceptStatementImpl.docstring`. `NavigableKimConceptStatement` delegates it unchanged.
- `ReasonerService` stores concept docstrings as RDFS comments. `OWLMetadata` exposes those
  comments in concept metadata; `CoreAliasDocumentation` preserves hosted core interpretations
  and publishes their descriptions under `Metadata.DC_COMMENT`.
- `SyntacticDocumentation` includes declaration docstrings in the Markdown information report;
  `CoreAliasDocumentation.describe` includes alias interpretations in the Reasoner report.
  The IDE's `CommandResult` already renders these reports as Markdown.
- `ConceptCodelistBuilder` carries concept docstrings into codelist and entry descriptions.
  `Indexer` copies concept/model docstrings into indexed descriptions.
- Model beans and `NavigableKimModel` retain/delegate docstrings, but `LanguageAdapter.adaptModel`
  still leaves their population to future literate programming support. There is currently no
  model docstring documentation view in `klab-ide`.
- In `klab-ide`, `ObservableCard` selects the first nonblank `DC_COMMENT`/`RDFS_COMMENT` and
  renders it using `BBCodeNodeRenderer.fromMarkdown` and AtlantaFX `BBCodeParser.createLayout`.
  `StyledKimToken.create` also carries `DC_COMMENT` into token descriptions; `Theme.semanticExpression`
  renders these tooltip descriptions with the same Markdown support.

Plain source editors and generic metadata inspection retain their source representation.
Future model/concept documentation views should use the same Markdown renderer.

## Report contents and provenance

Resources documents the adapted `KimConcept`/`KimObservable` properties, including types and
flags, expression structure, traits, roles, unary operators and comparison operands, clause
fillers, logical operands, value operators, units/currency/ranges, resolution options, metadata,
annotations and available diagnostics/source coordinates. Named references lead to their
Worldview declarations, including docstrings, retained clause source and parent/alias targets.
Enclosing declarations are shown explicitly as inheritance context for nested concepts.
Unavailable source declarations are identified rather than guessed, including authority
concepts whose source is outside the workspace.

The Reasoner documents the resolved `Concept`/`Observable`, including types/status, metadata,
annotations, resolution directives and specialization data, and its direct and effective
clause projections. Effective API projections may include inherited restrictions and may
select one filler from several. The OWL report preserves full class expressions, quantifiers,
Boolean expressions and cardinalities instead of flattening them into a single filler.

OWL sections distinguish expressions asserted on the requested OWL class from expressions
inherited through named superclass and equivalent-class links. Each entry names its asserting
class and ontology. Imports, equivalent-class expressions, intersections, cyclic hierarchies,
annotations and all axioms referencing the requested class are included. Restriction fillers
and union alternatives are **not** treated as superclasses. Inferred named superclasses and
their supplying axioms are listed separately when inference is available; failures are reported
as unavailable. This is not a complete enumeration of every possible logical entailment.

k.LAB aliases may share an OWL identity without an OWL equivalence axiom. An asserted axiom on
that shared identity is not necessarily an assertion in the alias's own Worldview source.
Use Resources for source provenance and the Reasoner for the resulting OWL identity.

Source clause retention does not imply OWL translation. The gaps tracked in
[the Worldview OWL audit](WORLDVIEW_OWL_TRANSLATION_AUDIT.md) remain visible as source clauses
in the syntactic report, even when the semantic report has no corresponding restrictions.
An empty projection is reported as “None reported”; a failed read is reported as “Unavailable”.
Neither is evidence that a source clause was successfully translated.

Core ontology concepts are automatically abstract. Abstract status does not automatically
carry to subclasses. Abstract and subjective attributes in a direct expression contribute
their status; clause fillers and unary-operator targets do not. The report shows current
bean status separately from OWL annotations, as specified in [Observables](OBSERVABLES.md).

## Keeping reports current

`SemanticDocumentation` enumerates readable bean properties and records, including nested
collections and metadata, with cycle detection and stable property ordering. New bean fields
therefore appear without a second serialization schema. Source clauses are read from the
passive declaration structure retained by LanguageAdapter. `OwlDocumentation` reads actual
OWL axioms, so newly compiled properties/restriction kinds appear automatically.

When adding semantic clauses or validation, retain their bean data, source context and
notifications; extend `SemanticInfoDocumentation` when a new named Reasoner projection is
introduced. Add regression assertions to the documentation tests alongside the translation
tests. Keep asserted/inherited provenance explicit and preserve any unsupported source
clauses in the syntactic report.
