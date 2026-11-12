# Knowledge explorer components

`SemanticComposer` and `WorldviewBrowser` are independent Vue 3 components exported from
the stable `@klab/webui` browser module (`src/plugin-api.ts`). They take an authenticated
`ServiceApi`, without depending on dashboard context, Quasar, or a global store. Include
the host's shared `assets/flowchart-viewer.css` when using the browser module outside the
dashboard; it also carries the knowledge components' styles. Both inherit the shared theme
tokens and `[data-theme="light|dark"]`.

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { SemanticComposer, WorldviewBrowser } from '@klab/webui';
import type { ServiceApi, SemanticResponse } from '@klab/webui';
defineProps<{ api: ServiceApi }>();
const focus = ref('earth:Landscape');
function locate(response: SemanticResponse) {
  const concept = response.observable?.semantics || response.currentConcept;
  if (concept) focus.value = concept.urn;
}
</script>
<template>
  <SemanticComposer :api="api" @change="locate" @select="locate" />
  <WorldviewBrowser :api="api" :focus="focus" :show-tree="false" />
</template>
```

This is also the embedding boundary for custom k.Actors component UIs: import these
components in the custom Vue UI and forward their events through the host's UI event
contract. This change does not add new built-in k.Actors widget types or language verbs.

## Composer

Props: `api`, optional `authorities` (catalog authority descriptors), and `disabled`.
Events: `change` for every server-confirmed snapshot, `select` for the arrow/Ctrl+Enter
host action, and `error`. Exposed methods: `focus()` and `submit()`.

Typing opens the overlay and debounces search by 350 ms. Arrow keys move through matches;
Enter chooses the highlighted match or accepts a requested literal; exact-match Space
also chooses. Backspace on the empty pending field undoes one confirmed step. Parentheses
operate on scopes when permitted. Escape closes suggestions. Confirmed tokens retain the
server's colors, font styles, whitespace and documentation; the editable suffix is ordinary
text. The overlay closes on selection, focus leaving the component, or Escape.

All semantic requests use `POST api/v1/semanticSearch`, carry increasing request IDs,
retain the server search ID, and serialize edits. A choice carries the exact proposal ID
and response revision. Obsolete TOKEN matches cannot be selected, and text typed while an
edit is running survives its reply. Requests have a 30-second timeout. Failed edits are
never replayed automatically: Retry obtains the server state with TOKEN. Sessions are
released on unmount. Expired sessions retain the displayed expression and require an
explicit Restart; restarting preserves pending text. Authority search uses canonical codes and the same acknowledged
semantic revision for IDENTITY; providers are never called directly from the browser.

## Worldview browser

Props: `api`, optional `focus` (concept URN/expression), `catalog`, `disabled`, and
`showTree` (default **true**). `showTree: false` hides the tree and its toggle. The source
catalog is still needed for declaration-only graph relationships; an embedding host can
provide it through `catalog` to avoid fetching it. The browser responds to changes in this
configuration. Events: `loaded` (catalog), `select` (resolved focused concept), and `error`.
Exposed methods: `center(urn)`, `refresh()`, and `fit()`.

The source hierarchy is loaded from authenticated `GET api/v1/worldview/explorer`.
The transport includes local authority discovery IDs, but no source text, provider
parameters or configuration IDs. It preserves declaration nesting and qualifies local
concept names with their ontology. The tree represents **authored nesting**, not inferred
subsumption. The graph uses the IDE's complete 30-switch relationship set, with identical
labels, order, descriptions and defaults. Subclass controls parents and children together.
The seven influence layers share a single cached `influences` request per concept.
Identity traits, attributes and realm qualifications have independent switches. The other
layers cover described type, roles, native bearer, declared containment/emergence/identity
and realm requirements, adjacency, causation, compresence, goal, cooccurrence, relativity,
relationship endpoints, application scope, implied roles and logical operands.

`declaredLinks` in the catalog contains only the four typed source contracts, indexed at
both endpoints. Boolean operands remain grouped; containment is restricted to matching
substantial/occurrent families and emergence requires a configuration source, as in the
IDE. Predicate application through `of` is separate from a quality/process native bearer.
No source-text heuristics or tree nesting are used to invent graph edges. The updated
Reasoner API and Web UI must be deployed together to include these source contracts.

Selecting a tree item, graph node or keyboard focus option loads its neighborhood. Back
retraces focus history. Depth is 1–4; traversal is cycle-safe, limited to 150 concepts,
and explicitly reports truncation. At most six HTTP relation requests run concurrently;
independent relation types do not wait for each other. Each browser keeps a bounded cache
of resolved concepts and successful relation results, including absent relations. Back,
Home and changing depth/layers reuse these results. Reload, a new catalog or a different
API instance replaces the cache; failed and cancelled requests are never cached.
The production transport accepts empty HTTP 200/204 bodies from nullable Spring endpoints
as absence, while malformed nonempty JSON still reports the failing endpoint.
Superseded graph/catalog requests and ELK workers are cancelled; graph requests have a
45-second deadline. A relation failure reports an error rather than presenting incomplete
data as an empty neighborhood.

The graph reuses the existing open-source Eclipse Sprotty renderer and ELK worker layout.
ELK's layered algorithm provides directional, orthogonal routing and edge labels without
adding another graph dependency. See [Sprotty](https://sprotty.org/) and
[ELK Layered](https://eclipse.dev/elk/reference/algorithms/org-eclipse-elk-layered.html).

## Verification

From the Web UI directory: `npm run typecheck`, `npm run build`, and
`npm run test:knowledge`. Tests use isolated transport fixtures, not a running Reasoner.
The suite uses the production `ServiceApi` parser and the Quasar stylesheet. To also run
the opt-in local service check, set `KLAB_LIVE_REASONER=http://127.0.0.1:8091/reasoner`
and select the `live Reasoner` test. The runner reads `~/.klab/services/reasoner/secret.key`
and authenticates its proxy requests; the secret is never passed into the browser.
This test checks actual nullable responses, canonical aliases and cached navigation.
The local preview is `/tests/knowledge.html?preview&standalone`; add `graph-only` or
`theme=dark`. The production Reasoner page requires an authenticated network session.
Rebuild the Reasoner server and the Web UI to deploy the catalog endpoint and components
together.
