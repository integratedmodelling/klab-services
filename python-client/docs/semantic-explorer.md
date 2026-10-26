# Semantic explorer

`SemanticExplorer(client, scope=None)` uses the configured Reasoner's existing HTTP
operations. Scientific/display packages are not required.

```python
from klab_client import SemanticExplorer

explorer = SemanticExplorer(connection.client)
page = explorer.search_authority("TAXA", "oak", limit=20)
print(page.summary())
# Inspect the page, then explicitly supply an actual canonical code, not a label:
candidate = page.select(chosen_code)
print(candidate.summary())
documentation_links = explorer.documentation(candidate)
concept = explorer.resolve_candidate(candidate)
parents = explorer.parents(concept.urn)
is_specialization = explorer.is_a(concept.urn, chosen_parent_urn)
```

`TAXA` is illustrative: the selected worldview must actually configure that local
name. A provider URN is not a configured local name. No automatic first-match or
label selection is performed. `total` counts the provider-returned list, not an
exhaustive external catalog. `next_offset=None` represents Java's `-1` end marker.
Non-OK statuses remain `UNSUPPORTED`, `UNAVAILABLE` or `FAILED`; they are not an
empty successful result. HTTP errors propagate separately.

Also supported: `resolve_concept`, `resolve_observable`, direct asserted `children`,
and `satisfiable`. `is_a(first, second)` means **first specializes second**, matching
the controller implementation even though its Swagger description reverses the
meaning. All meaning and decisions come from Java, not Python inference.

Each operation refreshes capabilities before/after and returns a knowledge stamp.
Observed service/worldview/scope/revision drift raises `StaleKnowledgeError`.
Missing/zero revisions explicitly have unknown freshness guarantees. There is no
semantic cache, permanent validity, or atomic snapshot claim. Resolution may
declare/materialize server knowledge; it is not universally side-effect-free.
Reuse a selected candidate only with its original explorer; refresh after drift.

Results retain immutable, redacted `raw` fields, including available classification,
edition, notifications and compatible extensions; absent fields are not invented.
Unknown semantic classifications reject rather than silently acquiring meaning.
`summary()` and escaped notebook HTML require no UI package. Documentation returns
MIME-to-URL descriptors only: the SDK never fetches these URLs, follows redirects,
executes provider HTML, or forwards the Reasoner credential to their origins.

Defaults: 100 hierarchy items, 1 MiB per HTTP response. Caller budgets are bounded
at 1,000 items and 16 MiB. Hierarchy is one direct page, not a transitive traversal.
Authority requests obey Java's limit 1..100, offset 0..10000, query 1..256 UTF-16
characters and optional filter length 128. Unsupported filters are decided by Java.

Implementation/offline checks are separate from live acceptance. See the
[contract inventory](discover-compare-explain-contracts.md) and
[feature contribution review](reviews/discover-compare-explain/semantic-explorer.md).
