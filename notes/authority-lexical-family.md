# Authority lexical families

`Reasoner.lexicalRoot` identifies the author-selected predicate family. An ordinary top-level
worldview declaration establishes a boundary even if it has a declared parent in another
ontology. Nested declarations inherit their enclosing boundary. These rules apply to attributes,
realms, identities, roles and domains; they are not an identity-only classification convention.

For an authority identity, the explicit configuration's `rootIdentity` is the lexical anchor.
That concept may itself be nested in the worldview. Provider parent edges and mapped semantic
boundaries remain useful inheritance evidence, but neither selects a different lexical family.
The current generation's `AuthorityBindings` maps the exact internal authority ontology namespace
to this anchor, and lookup checks that the requested canonical class is actually loaded there.
Validated binding configuration prepares the anchor's normal identity restricting property
(`is<Anchor>`). This is necessary for a nested anchor: ordinary nested declarations share their
enclosing family's property, while an explicit authority binding selects that nested concept as
its own family. Observable composition can then use the anchor through the existing OWL
restriction path. Property preparation occurs during configuration, never during root lookup.
Caller flags and metadata alone cannot establish this membership. Loaded class metadata also
controls authored specializations of authority identities: a transported concept cannot invent
a `baseDeclaration`, redirect `originalTrait`, or remove its authority flag to resurrect a
released internal bridge. The anchor must be a loaded,
non-bottom worldview identity. No `originalTrait` annotation is repurposed for authority binding.

Codelist aliases return the same canonical authority concept, so direct authority tokens,
internal concepts and aliases use the same anchor. The existing codelist configuration maps
provider codelist IDs to local namespaces and has no independent worldview-root field. This
change introduces no per-codelist roots or configuration syntax. Supporting independently bound
codelists would require a separately specified authoring contract.

Lookup reads current bindings and loaded concepts only: it performs no provider resolution,
hierarchy fetch or admission. A deferred identity can have a known lexical family while still
failing complete-hierarchy admission. Captured/replayed configurations reconstruct the same
family mapping; absent/released bindings or missing classes in another generation return no
authority root. Transported concepts are checked against current loaded membership, not trusted
as proof of provider admission. Existing provider/configuration snapshots remain unchanged.
The no-binding fallback guard applies to generated internal authority concepts, not ordinary
worldview-authored specializations carrying an inherited authority type. Authored top-level
boundaries and their nested descendants retain the ordinary declaration rules.

`AuthorityLexicalRootTest` covers nested anchors versus mapped parents, direct/codelist aliases,
JSON/HTTP equivalence, replay and generation isolation, absence of hierarchy/provider calls,
invalid membership/anchors, and ordinary predicate declaration boundaries. Existing cycle tests
in `SemanticsBuilderTraitTest` continue to exercise missing/cyclic ordinary ancestry.
`LexicalRootAuthoringTest` exercises real k.WV parsing, adaptation and private production
worldview compilation, including nested declarations and separately authored top-level
boundaries with parents in another ontology, for attributes, realms, identities and roles.
It also checks that nested authority source retains its binding and codelist parameters.
The lightweight HTTP fixture exercises `ReasonerClient` JSON transport into the real lookup;
it is not a complete Spring service boot or a live authority-provider ingestion test.
