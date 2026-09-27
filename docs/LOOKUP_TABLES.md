# Lookup tables, classifications, and discretizations

This document records the implementation plan for carrying k.IM inline classifications,
discretizations, and lookup tables from source to scalar runtime execution. It concerns value
classification (`classified into`) rather than the separate classification contextualization that
attributes predicates to members of a collective.

## Current path and gaps

The accepted source forms already exist in
`klab-languages/org.integratedmodelling.languages.kim/.../Kim.xtext`:

- `classified into` and `discretized into` use an ordered `Classification`;
- `lookup (...) into` uses a one-way table;
- `match (...) to` uses row and column classifiers;
- `according to` and uppercase table identifiers represent named mappings.

The first loss of information is in `ModelSyntaxImpl`. Its branches for inline classification,
classification-property lookup, inline lookup, and named lookup are TODOs, so no
`ModelSyntax.Contextualization` is produced. The Resources `LanguageAdapter` consequently sees
only expressions and function calls even though `Contextualizable`, `ContextualizableImpl`, and
the `KimClassifier`/`KimClassification`/`KimLookupTable` beans already expose placeholders.

If those placeholders are populated, `DataflowCompiler.adaptContextualizer()` already emits the
`klab.core.lut.resolver` functor and places the mapping in a `ServiceCall`. Runtime batches that
functor into `ScalarComputationGroovy`, whose LUT branch is currently empty. The scanner execution
and shard scheduling architecture is therefore already the correct endpoint; a second
contextualizer protocol is unnecessary.

Several existing classes are useful but incomplete:

- `KimTableImpl` previously did not store or return table rows;
- the API `Classifier`, `Classification`, and `LookupTable` interfaces mix portable data with
  runtime behavior and do not expose enough matcher payload to reconstruct an implementation;
- the legacy `LookupTable` cache uses concatenated strings, is unbounded, and is not safe for
  concurrent shard execution;
- `according to` has no lowering step or code-annotation index, and named table declarations are
  not registered;
- the Jackson transport registry does not prove round trips for nested classifier/table payloads.

## Recommended representation

Introduce one portable, data-only mapping bean in `klab.core.api`, provisionally named
`ValueMapping`. Both classifications and lookup tables lower to this bean before a model leaves
Resources.

The bean should contain:

- a stable schema version and source kind (`CLASSIFICATION`, `DISCRETIZATION`, `LOOKUP`, or
  `TWO_WAY_LOOKUP`);
- ordered input descriptors: dependency ID or abstract-predicate reference, plus row/column role;
- an explicit result descriptor and result `Artifact.Type`;
- ordered rows whose cells are data-only matcher specifications;
- for two-way tables, ordered row and column matcher vectors and a result matrix;
- optional source encoding and lexical location for diagnostics;
- validation facts needed after transport, including the discretization flag and normalized
  interval boundaries.

A matcher specification should be a closed tagged value, not a live expression or reasoner
object. Its variants are catch-all excluding no-data, catch-anything including no-data, no-data,
boolean, number, interval with independent bound inclusion, text, concept, set/OR, and expression.
Negation is orthogonal. Concept payloads on the wire are transportable `KimConcept` beans; Runtime
adapts them once to operational `Concept` objects using the Reasoner connected to its scope.

Classification is the one-input special case: input `self`, one matcher per row, and a concept
result. Discretization is the same representation plus validation requiring numeric interval
matchers, non-overlap, contiguity, and the chosen finite-domain rule. This preserves one runtime
implementation while retaining enough origin information for validation and messages.

`according to <scheme>` is a second one-input special case. It lowers to an exact integral-code
mapping derived from `@code("<scheme>", <integer>)` annotations on concepts compatible with the
model output. It should use the same transported mapping contract and scalar executor, but may use
a specialized primitive long-to-concept representation internally.

Keep the existing `Kim*` classes as source-language beans. Keep `Classification` and `LookupTable`
as compatibility/data-key APIs until callers migrate, but do not use either as the wire contract.
This avoids serializing behavior-bearing objects and prevents Runtime compilation concerns from
leaking back into the language adapter.

## Delivery stages

### Implemented foundation (September 2026)

The syntax-to-transport portions of stages 1, 2, 3, and 4, plus the general Runtime compiler and
cache in stages 5 and 6, are now implemented for inline classifications, discretizations, one-way
and two-way lookup tables, `according to`, and local uppercase table references:

- `klab-languages` creates EMF-independent `ValueMappingSyntax` and `ClassifierSyntax` snapshots
  instead of dropping the four model-action branches. Uppercase `define` tables use the same
  snapshot, including headers and non-matching descriptive columns. The language API package is
  now exported by the OSGi bundle.
- Resources translates snapshots into the existing transportable `KimClassification` and
  `KimLookupTable` beans. It registers local table definitions before model adaptation, so forward
  references resolve deterministically, and validates table shape, arguments, result columns,
  uniform result types, target compatibility, and discretization interval continuity.
- Jackson registers classifiers, classifications, tables, lookup arguments, dates, and quantities;
  its typed-container support now reconstructs classifier arrays rather than untyped lists.
- `staging.vxii.test.lut.basic.RECREATION_OPPORTUNITY_TABLE` is the reference fixture. Its first
  two columns are match inputs, `score` is the result, and `description` is transported unchanged
  as an ignored (`*`) fourth column. Parser and JSON tests retain all nine rows and descriptions.
- Runtime compiles each transported classification or lookup table once into an immutable
  `CompiledValueMapping`. The generated scalar executor binds its declared inputs to typed storage
  scanners, keeps the compiled mapping in a constructor field, and performs only a small lookup
  call per cell in the shared scalar loop.
- The compiled mapping implements first-match-wins matching for literal, interval, concept, set,
  date, quantity, expression, wildcard, null, and negated classifiers. Concepts and expressions
  are resolved or compiled once; expression-backed result cells are still evaluated per location.
- A mapping-local Caffeine cache stores typed immutable input tuples to selected rule indexes,
  including a negative sentinel. Its default maximum is 16,384 entries, configurable with
  `klab.runtime.valueMapping.cache.maxSize`; a value of zero disables it. Hit, miss, eviction, and
  size statistics are available from the compiled mapping.
- The Resources `retrieve` endpoint accepts `Codelist.class` and the URN of a root predicate. The
  owning service walks that declaration's descendants and derives a portable codelist from their
  `@code` annotations. One response may contain several code authorities without conflating equal
  numeric codes from different schemes.
- Resolver accepts `according to` only on a model whose primary output is `type of X`, recovers X
  through `Reasoner.describedType`, and retrieves the codelist from the Resources service owning
  X's namespace. It embeds both the codelist and selected authority in the LUT service call.
- Runtime compiles the selected authority into exact integral matchers and resolves each transported
  `KimConcept` once through its scope Reasoner. Unknown, fractional, non-finite, and overflowing
  inputs produce no-data.

The normalized runtime-only `ValueMapping` described below remains the next boundary. Until that
lowering is implemented in Resolver, the transport uses the completed `Kim*` semantic beans,
`Codelist`, and the existing LUT functor parameters. Imported named-table lookup and
abstract-predicate lookup arguments remain future stages. Runtime rejects unresolved forms rather
than guessing their dependencies.

### 1. Capture complete syntax in `klab-languages`

Add data-only syntax interfaces for classifier, classification, table, lookup arguments, and named
mapping references under `org.integratedmodelling.languages.api`. Extend
`ModelSyntax.Contextualization` so its contextualizable can carry each form. Implement the four
TODO branches in `ModelSyntaxImpl`, preserving order, source ranges, `otherwise` versus `*`, `#`
versus `unknown`, inclusive/exclusive bounds, negation, set members, expressions, table headers,
and row/column qualification.

Validate structural rules there when they are independent of the worldview: rectangular tables,
argument/column arity, exactly one result column for one-way tables, exactly two dimensions for
two-way tables, and homogeneous syntactic result types when determinable.

Tests must parse real `.kim` files for every classifier variant, omitted lookup arguments, named
references, two-way tables, malformed dimensions, and the legacy `test12.kim`/`test14.kim`
examples.

### 2. Lower syntax to the portable semantic bean in Resources

Teach `LanguageAdapter.adaptContextualizable()` to translate the new syntax snapshots into
`ValueMapping`. Resolve concept declarations with the same semantic adaptation used elsewhere,
convert quantities and dates deliberately, and retain `ExpressionCode` without compiling it.

Register named table definitions in the namespace symbol pass before adapting model bodies so
forward references are deterministic. Preserve `according to` as a typed scheme reference through
Resources; its semantic expansion requires the resolved target observable and belongs in the
Resolver/Reasoner step described below.

Semantic validation belongs at this boundary: result compatibility with the model observable,
valid abstract-predicate arguments, consistent concept families, expression return type, and the
extra discretization checks. Diagnostics must point back to the source classifier or cell.

### 3. Derive and resolve `@code` codelists — implemented

Recognize `@code("scheme", integer)` on ontology concepts. The Resources service that owns the
ontology derives a codelist on demand when `retrieve(rootConceptUrn, Codelist.class, scope)` is
called. The codelist is reconstructed from concept annotations rather than authored into or
persisted beside the ontology, and contains transportable `KimConcept` values.

The initial contract is intentionally narrow:

- the scheme is a nonblank lower-case path identifier and comparison is exact;
- the code is a signed 64-bit integer;
- a concept has at most one code in a scheme, but may participate in several schemes;
- codes must be unique among descendants of the requested root within one scheme;
- the same scheme and code may be reused in an unrelated semantic hierarchy;
- a finite integral numeric input such as `232` or `232.0` matches code `232`; fractional,
  non-finite, overflowing, and unregistered values produce no-data.

During dataflow compilation, Resolver requires the primary model output to be `type of X`, uses
`Reasoner.describedType` to recover X, and asks the Resources service that originated X for its
codelist. The selected scheme and codelist are carried together in the dataflow. Runtime never
scans an ontology or interprets annotations. Missing schemes, empty mappings, malformed
annotations, and duplicate codes fail before execution. Because the codelist travels in each
compiled dataflow and Runtime caches belong to that compiled instance, a newly resolved dataflow
after an ontology update cannot accidentally reuse the prior mapping.

### 4. Prove transport independently

Register the new bean and nested matcher types in `JacksonConfiguration`. Add round-trip tests for
the bean alone, inside `Contextualizable`, inside the LUT `ServiceCall`, and inside a complete
`Dataflow`. Assert semantic equality, row ordering, interval openness, no-data distinctions,
expressions, and concept identity after reconstruction.

During migration, `DataflowCompiler.adaptContextualizer()` should accept the normalized bean and
emit one parameter name, `mapping`, instead of the current `classification`/`lookupTable` split.
It should add the mapping's dependency IDs to the actuator input contract so Runtime binds the
right scanners without analyzing generated code.

### 5. Compile once at Runtime — implemented for transported mappings

Runtime now builds an immutable `CompiledValueMapping` from the validated transported schema,
compiles expression matchers once, resolves concepts through the Runtime Reasoner once, and
exposes a scalar `lookup` operation. A later optimization may specialize validated discretizations
into breakpoint searches; the current ordered rules preserve the required first-match semantics.

Extend `ScalarComputationGroovy.BuilderImpl.Step` with a mapping step. Bind each mapping input to a
typed scanner using its declared dependency and native sharding type. Pass the compiled mapping as
a constructor field to the generated scalar class and emit only a small per-cell call in the main
loop. Concept results go through the target `Storage.KeyScanner` and its `DataKey`; numeric and
boolean results remain primitive. Multiple consecutive scalar operations must still compile into
one loop.

The runtime implementation should preserve first-match-wins behavior. No-data must be represented
explicitly rather than inferred from a cached `null`, and computed result cells must be evaluated
for every location even when their matching row is cached.

For an `according to` mapping, compilation should select a specialized exact-code encoder backed by
an immutable primitive numeric lookup where practical. This is still a compiled value mapping, not
a separate contextualizer, and writes concept results through the same target `KeyScanner`.

### 6. Cache safely and observably — implemented

The implementation uses the following caches:

- a match cache from an immutable typed input tuple to a row/cell index, including a negative
  result sentinel;
- the existing Reasoner subsumption cache for semantic matching; no duplicate mapping-local
  subsumption cache is introduced.

Cache the selected row, not the computed output, whenever a result is expression-backed. Cache a
literal output only as an optional optimization. Use Caffeine with a configurable maximum size;
compiled mappings may run concurrently across shards, so neither cache nor key construction may
use mutable shared arrays. Do not use `toString()` keys: they collide across types and concepts.

Hit, miss, eviction, and current-size counters are exposed by `CompiledValueMapping.statistics()`
without entering the transported bean. The maximum-size system property provides a conservative
default and allows caching to be disabled in tests.

### 7. Compatibility and cleanup

Once end-to-end tests pass, adapt callers of the old executable `Classification` and `LookupTable`
interfaces to the normalized mapping/compiler. Preserve `DataKey` behavior with a view over the
compiled result dictionary, including stable concept order and code zero for missing values.
Deprecate the old runtime `lookup(...)` method only after storage and visualization consumers use
that view.

Keep the member-attribution `Contextualization.CLASSIFICATION` path and
`MemberClassifierExecutor` unchanged; it is semantically unrelated despite the shared name.

## Acceptance tests

The feature is complete only when the following pass across service boundaries:

1. A parsed `classified into` model survives Resources registration and model retrieval with every
   ordered rule intact.
2. Resolver output survives JSON transport with the normalized mapping embedded in the actuator.
3. Runtime classifies primitive numeric, boolean, text, no-data, and concept inputs over multiple
   shards with first-match-wins semantics.
4. One-way and two-way tables bind the intended scanners, including ignored columns and abstract
   predicate arguments.
5. Discretization rejects gaps, overlaps, invalid boundary combinations, and incompatible result
   concepts before execution.
6. Expression-backed matchers and results compile once; computed results are not incorrectly
   cached.
7. Repeated inputs demonstrate cache hits, concurrent shard execution is deterministic, and cache
   size remains bounded.
8. Keyed concept output uses the storage dictionary consistently across shard persistence and
   reconstruction.
9. `according to corine` resolves only `@code("corine", ...)` annotations compatible with the
   requested result semantics, accepts integral values across numeric storage types, maps unknown
   codes to no-data, and is invalidated when the worldview commitment changes.

The first vertical slice should deliberately be small: numeric one-input classification to a
concept result, including parse, adaptation, transport, resolver carriage, scanner execution, and
cache assertions. One-way multi-input and two-way tables can then reuse the proven transport and
executor without creating another pipeline.
