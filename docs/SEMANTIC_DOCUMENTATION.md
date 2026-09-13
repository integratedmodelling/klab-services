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
