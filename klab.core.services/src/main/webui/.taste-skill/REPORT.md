GATE: ERROR (sandbox Chrome timeout; automated copy, consistency and layout checks did not complete)

Knowledge explorer: software / scientific interface. Voice: technical, factual, using the
existing Reasoner and IDE terminology. The existing Web UI's compact layout, neutral
palette, system fonts, 4px corners and blue actions remain authoritative. The user's
request for a tight working explorer takes precedence over marketing-page heroes,
palette deals, decorative motion, photographs and full-screen advertising sections.

The design-taste-frontend skill was applied after the component prototype. Semantic
colors are defined centrally in theme.css with separate accessible light/dark values.
No photographs, stand-ins, invented business facts, contact forms, external fonts,
decorative assets or new graph dependencies are used. No waivers were added.

The initial skill gate could not complete under the restricted Windows sandbox:
consistency.mjs could not walk the page and check.mjs timed out. The gate was not rerun,
as required by its error instruction. This is not a gate pass; automatic copy, layout,
consistency and performance certification remains unverified.

The skill's unmodified shoot.mjs subsequently worked with the bundled Node 24 and
unrestricted headless Chrome. Its images were inspected at 1440, 1024, 768 and 390px,
including dark at 1440/390 and composer overlays at desktop/mobile. Sources:
`.taste-skill/shots/1440.png`, `1024.png`, `768.png`, `390.png`, `dark-1440.png`,
`dark-390.png`, `composer-1440.png`, `composer-dark-390.png` (generated local evidence,
ignored by Git). The graph is the main surface; controls wrap on mobile, the source
tree scrolls independently, suggestions overlay the workspace and close after selection,
and the inspector preserves readable identifiers. Graph-only hosts remove the tree
and its toggle entirely. Mobile users can pan/zoom and use the keyboard focus chooser.

All 18 browser regressions pass, using actual Vue components and Sprotty/ELK with isolated
Reasoner transport fixtures. Checks cover composition, semantic color tokens, graph
recentering, source-tree filtering/history, graph-only configuration changes, independent
instances, keyboard navigation, cancellation, retries, stale queries, typing during edits,
authority insertion revisions, uncertain edit recovery, session expiry, and absence of
horizontal overflow at 1440, 1024, 768, 390 and 320 in both themes.

The Java reactor was compiled through Reasoner Server, with the focused catalog and
authorized/no-store controller tests passing. Typechecking and both production Web UI
builds pass. Vite retains its existing Quasar import and large-chunk warnings.

Follow-up validation on 2026-11-12 used the running local Reasoner with the user's
authorized service secret, retained exclusively in the test runner. The loaded catalog
contained 26 ontologies. Live inspection confirmed that `imod:Subject` resolves to
`odo:Subject`, and `inherent` returns a valid HTTP 200 with an empty body. The production
parser now accepts this absence, canonical focus preserves the source-tree selection,
and cached Back navigation makes no further HTTP requests. Independent relation queries
run concurrently with a six-request bound; successful empty results are cached too.
Reload replaces the cache. The updated suite passes 26 isolated browser regressions,
including chevron visibility and mouse/keyboard expansion in light and dark themes,
plus the separately enabled live test. The production bundles and typecheck also pass.
The running packaged service still serves its older bundle and must load a rebuilt
package to pick up these fixes. Remote authority availability remains unverified.
The relationship parity follow-up adds all 30 IDE switches with identical order, labels,
descriptions and defaults, including source-only containment, emergence and declared
identity/realm requirements. Subclass is one switch; influences share one cached query;
native bearer, application scope and implied roles retain the IDE's type guards. The
catalog now transports typed declared links and keeps Boolean requirements grouped.
The expanded chooser scrolls and stays within narrow viewports. Validation passes 32
isolated browser/model checks, four Java tests and the authenticated live navigation
test, plus typechecking and both production builds. The current live server's older
catalog does not expose declared links; those layers require the rebuilt API package.
