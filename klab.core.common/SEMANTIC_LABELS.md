# Semantic display labels

`SemanticLabels` builds presentation text without changing OWL class names, semantic URNs,
reference names, or code identifiers. Both `displayName()` and `displayLabel()` use this text.

* `Metadata.DISPLAY_LABEL` (`klab:displayLabel`) stores the singular concept label in OWL.
  Collective views pluralize the head noun using the existing English inflector, leaving
  restrictions such as "of a region" unchanged. Singular and collective copies share the
  singular label, so switching perspective never double-pluralizes or mutates the original.
* Unary OWL factories and logical combinations write display annotations. `SemanticsBuilder`
  assembles restricted labels from syntax and resolved atomic labels, including predicates,
  relationship endpoints and other clauses. Replacing an OWL display label removes the old
  annotation so reloading cannot select a stale value.
* `Metadata.SUGGESTED_NAME` (`im:suggested-name`) is computed for observable and observation
  metadata and travels in ordinary service serialization. It is a default, not an identifier.
* Observation names prefer an explicit name, then an identity-derived name, then explicit
  label metadata, then the observable display label. Observable `named` clauses are honored.
  Runtime graph persistence uses the same readable fallback. Code names remain separate for
  actuator bindings and other programmatic references.

Examples: `each earth:Region` becomes "Regions"; a collective terrestrial region becomes
"Terrestrial regions"; presence becomes "Presence of region"; count becomes "Number of
regions"; rate becomes "Rate of change of temperature". Compound labels retain comparison
and restriction operands to distinguish otherwise similar observables.

Value-operator text is supported when present in syntax (e.g. "greater than 20"). This
naming support does not implement or validate the value operators' reasoning behavior.
Legacy snapshots can derive labels from semantic definitions, but a bare internal OWL ID
alone cannot reconstruct its original meaning. Existing persisted explicit names are not
rewritten by this mechanism.
