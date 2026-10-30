# Private description-reconciliation hierarchy index

Description reconciliation prepares its axioms before applying them to the private ontology
manager. During that preparation interval, `AssertedHierarchyIndex` indexes disjoint-class
axioms and the asserted superclass/equivalent expressions of named classes. Preparation entries
share this index, while their existing bounds and provenance caches remain entry-local.

Ancestry answers are memoized only for searches starting with an empty visited set. Recursive
answers are path-dependent in cyclic graphs and cannot safely be cached by expression pair.
The existing rules remain unchanged: a union bound accepts any alternative, an intersection
bound requires all alternatives, a candidate intersection can establish an ancestor through
any operand, and a candidate union must establish it through every operand. Missing asserted
evidence remains unresolved rather than becoming proof of incompatibility.

The index is private to one preparation phase, thread-confined, and bounded to 65,536 root
answers. A manager change listener invalidates all indexed assertions and answers, including
declarations introduced by resolution callbacks. Ontology membership uses object identity,
because replacing an ontology with an empty object of the same ID may emit no axiom event and
OWLAPI ontology equality compares IDs. Active DL reasoning bypasses this asserted index.
Its listener is removed and references discarded before any prepared axioms are applied, on
normal completion or cancellation. No cache is shared with accepted actors, other managers,
other reconciliation phases or later authority generations. General concurrent ontology
mutation is not introduced or supported by this scratch index.

Cancellation checkpoints precede warm-cache returns and occur during index construction and
ancestry traversal. Only completed builds and completed root answers are published; exceptions
are not cached. Existing DL entailment memoization and flush behavior are unchanged.

`AssertedHierarchyIndexTest` compares cold/warm searches in both query orders against the
frozen pre-index traversal over cyclic, equivalent and compound expressions. It also checks
axiom changes, same-ID ontology replacement, disjointness, multiple bounds, cancellation,
owner/phase isolation and transition to/from DL reasoning. Existing
`BearerCompatibilityMemoizationTest`, `BroadDescriptionContractTest` and
`ReconciliationCancellationTest` retain their separate semantic and cancellation contracts.
