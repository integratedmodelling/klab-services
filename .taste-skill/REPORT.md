GATE: OPEN 27 (rounds used up)

# k.LAB dashboard redesign

Implemented in the shared Vue/Quasar dashboard and all four service extension packages. The interface now uses a neutral light palette, a persistent dark alternative, a single sans-serif scale, blue actions, flat rectangular surfaces, readable identifiers, and the unchanged elephant SVG from klab-ide. Routes, request bodies, authentication contracts, registrations, and scientific operations are retained. No Java source or running JVM was changed.

## Before and after measurements

Measured on the Resources dashboard at 1440px unless otherwise stated. Live telemetry values are excluded because they change independently of design.

| Measurement | Before | After |
| --- | --- | --- |
| Main heading | 60px, Georgia | 32px, Segoe UI/system sans-serif |
| Metric panel radius | 16px | 4px |
| Left / right content edge | 130px / 130px | 80px / 80px |
| Gradients in shared and extension styles | 3 | 0 |
| Hex/RGB colour literals outside the new theme file | 182 | 1 data default; 0 presentation colours |
| Total colour literals | 182 across components/styles | 37, with 36 in the central theme file |
| Extension paragraph size | 13px | 16px |
| Extension paragraph contrast on white | 2.02:1 | 7.86:1 (dark: 7.79:1) |
| Horizontal overflow, 24 service/width combinations | 0px | 0px |
| Configured page routes exercised | 7 | 7 |
| Raw skill consistency findings, 1440 / 390 | 54 FAIL / 20 WARN | 39 FAIL / 12 WARN |
| Effective keyboard focus | FAIL | PASS |
| Text contrast | FAIL | PASS |

The one remaining component hex value is the colour picker's black data default, not a presentation colour. These are source counts, not claims about bundle reduction. Framework styles and the supplied SVG are excluded from the component count.

| Desktop page height | Before | After |
| --- | --- | --- |
| reasoner | 1141px | 1055px |
| resources | 1141px | 1055px |
| resolver | 1141px | 1055px |
| runtime | 1141px | 1055px |

Full identifiers can now wrap instead of disappearing behind ellipses. That increases some detail panels' height, including Resolver's, while keeping the full scientific endpoint identity available.

## Design decisions and implementation order

Kind: software, scientific service dashboard. Voice: technical and factual; retain service names and operational language. No invented facts, photographs, stand-ins, advertising sections, decorative scenes, or shaders.

The skill dealt stone-blueprint / instrument / sharp. Its deal is retained in plan.md and dealt-tokens.css. The scientific dashboard brief takes precedence over the marketing hero, full-screen section, animation, and decorative-moment recipes. The implemented palette is neutral paper/white with one blue action colour; local system fonts keep the interface available offline.

1. Type: compact service-specific headings, one sans-serif scale, tabular telemetry, monospace and wrapping identifiers.
2. Colour: shared semantic CSS variables in src/styles/theme.css; light/dark surfaces, text, actions, borders, selection, and feedback. Quasar and embedded applications consume the same palette.
3. Frame: equal edges, a continuous telemetry strip, restrained panels and a responsive two-row mobile header.
4. States: persistent theme selection, cross-tab updates, storage fallback, keyboard skip link, current-page semantics, explicit focus rings, loading, locked, error and empty states. Header measurement updates are deferred out of ResizeObserver delivery.
5. Components: Quasar buttons, fields, separators, menus, dialogs and permissions; native review/codelist inputs; workflow diagrams and actor application surfaces. Explicit actor application themes remain authoritative; otherwise they inherit the dashboard choice.

See ../klab.core.services/src/main/webui/THEME.md for configuration and build instructions.

## Functional verification

The final fixture suite passes all 77 tests (after/regressions-final.txt). The initial run passed 76 tests; its remaining browser-history assertion hard-coded the original port 4229. That test passed on the correct port, which is now used by the combined configuration. Focus/persistence/responsiveness tests also pass separately in after/theme-recheck.txt. Vite logs an intermittent Quasar ResizeObserver notification warning during responsive fixture tests; it does not fail the tests, and the production four-service audit records no page-script errors.

Existing regressions exercise credential submissions and write-only secrets, authentication exchange/renewal/logout, authority codelist proposals, review draft saving, uploads, downloads, conflict and uncertain-response recovery, feedback sharing, unsaved navigation, workflow selection/diagram inspection, actor applications, widgets and the packaged IDE renderer. They use isolated fixtures; no scientific records were created or modified on the live services.

The browser audit clicks every configured page route, component disclosure, refresh control, mobile service menu, and OpenAPI link on all four services. It verifies each Sign in handoff uses client klab-dashboard and returns to the same service origin. Actual account entry is not automated. Protected live forms are inspected through their authentication gates and exercised through the fixture suite, rather than reported as authenticated live submissions.

Root screenshots cover 1440, 1024, 820, 768, 390, and 320px. Dark screenshots cover all four dashboards. Twelve separate light/dark captures show the review, codelist, and credential form components at desktop and phone widths, with the production Quasar base styles; each capture also checks for horizontal overflow and passes. Codelist columns and long proposal identifiers wrap on phones. All audit measurements and recorded page errors are in before/measurements.json and after/measurements.json.

Type validation: vue-tsc --noEmit passed before and after edits. Both dashboard and embedded Vite builds passed. git diff --check passed. The raw source copy-lint baseline reports one FAIL and one WARN because it treats Vue interpolation and TypeScript exclamation operators as visible copy; retain before/copy-lint.txt as evidence. The final gate lints rendered copy and reports no copy-lint FAIL. Remaining copy-buttons/copy-actions warnings refer to operational labels and the dashboard's multiple independent controls; these are preserved deliberately.

## Skill checker and remaining findings

The preliminary full five-width inspection is retained in after/check. The final comparable two-width check, including its 320px walk, is in after/final-check. The latter passes text contrast, control naming, alt text, field labels, heading order, effective focus visibility, focus containment, viewport containment, and the 320px overflow/collision walk. The final narrow walk has no findings.

The gate has 27 unresolved, deduplicated findings. They concern the marketing layout contract: full-screen hero/sections/footer, one section and heading per screen, a selling action/closing action, decorative visuals/moments, the kit's .wrap containers, rules between data panels, a single-row mobile header, and metric figures larger than section headings. The user's scientific dashboard brief calls for compact telemetry, comparable panels, readable controls and real operations. These findings are reported openly; the interface was not padded with marketing sections to satisfy them.

Two explicit waivers:

- focus-ring — .q-virtual-scroll:focus: The checker scans Quasar's original outline reset without resolving the cascade. The shared stylesheet restores a 2px action-colour outline with !important on keyboard focus; the regression suite checks the effective button focus style. No framework stylesheet was modified.
- menu-toggle — Switch to dark theme: This is the theme switch, with aria-pressed and a state-dependent accessible name. It opens no menu. The checker selects it because its class includes toggle. The actual Service interfaces menu is a Quasar dropdown and is exercised on all four services.

Every remaining gate finding is listed below. Full descriptions and proposed kit fixes remain in after/gate-round-3.txt. The final run uses the actual build hash in the preview URL to force a fresh check: the monorepo's nested frontend source is outside the gate's usual root-level source discovery.

```text
  FAIL hero-fill        div#main-content > main.dashboard-main > section.status-grid "Operational state Operationa  [1440, 390]
       the next section shows on the first screen: it starts at 245px on a 900px screen (the hero ends at 217px)
  FAIL flush            main.dashboard-main > section.intro-row > div > p "Resource catalogs, adapters, and transp  [1440, 390]
       its last line sits 2px above the rule on top of section.status-grid
  FAIL flush            div#main-content > main.dashboard-main > section.dashboard-content > div.q-card.q-card--fl  [1440, 390]
       its first line sits 0px under the top edge of section.dashboard-content, where the ground changes
  FAIL text-only        div#main-content > main.dashboard-main > section.dashboard-content "Capabilities"  [1440]
       the closing section is only text: no picture, icon, mock or effect in it
  FAIL cta              div#main-content > main.dashboard-main > section.intro-row  [1440, 390]
       the hero has no button (the nav's does not count)
  FAIL cta              div#main-content > main.dashboard-main > section.dashboard-content "Capabilities"  [1440, 390]
       the last section before the footer ends with no button
  FAIL moment-none      (the page)  [1440]
       a software page (--type) deals no moment: every kind carries one, two on a long page (moments.md)
  FAIL hero-screen      div#main-content > main.dashboard-main > section.intro-row "RESOURCESResources overviewRes  [1440]
       the hero ends at 217px on a 900px screen, 683px above the fold: it ends at the fold, 1px either way (SR-1)
  FAIL hero-screen      div#main-content > main.dashboard-main > section.status-grid "Operational state Operationa  [1440]
       655px of the next section shows on the first screen (from 245px on a 900px screen): nothing below the hero shows at load (SR-1)
  FAIL section-height   div#main-content > footer.dashboard-footer "k.LAB service interfacePublic telemetry "  [1440]
       the section is 77px tall (0.1 screens), 824px short of one screen (900px): every section after the hero is one screen or two, 2px either way (SR-2)
  FAIL section-air      main.dashboard-main > section.dashboard-content > div.q-card.q-card--flat:nth-of-type(1) >  [1440]
       a rule (<hr>) between the section's content: sections and their parts are told apart by air and ground, not lines (SR-3)
  FAIL section-air      main.dashboard-main > section.dashboard-content > div.q-card.q-card--flat:nth-of-type(2) >  [1440]
       a rule (<hr>) between the section's content: sections and their parts are told apart by air and ground, not lines (SR-3)
  FAIL one-section      main.dashboard-main > section.intro-row > div > h1 "Resources overview"  [1440]
       the screen at scroll 0px (stride 0) shows 150px of section 2 and the heading of section 1 whole and the content of 3 sections, over 24px each beside section 3: a screen shows one section (another may peek 8px, 24px WARN)
  FAIL one-section      div#main-content > main.dashboard-main > section.status-grid "Operational state Operationa  [1440]
       the screen at scroll 121px (the top of section 1) shows 150px of section 2 and the content of 2 sections, over 24px each beside section 3: a screen shows one section (another may peek 8px, 24px WARN) (SR-4)
  FAIL one-heading      div.q-card.q-card--flat:nth-of-type(2) > div.q-card__section.q-card__section--vert:nth-of-  [1440]
       the section holds 2 h2 headings: one per section, first in its .wrap (SR-5)
  FAIL container-edges  div#app "Skip to contentIntegrated modellingk.LAB"  [1440]
       a <div> at the top of the page, outside header.nav, main, footer.closing and nav.actionbar (SR-6)
  FAIL container-edges  div#main-content > main.dashboard-main > section.intro-row "RESOURCESResources overviewRes  [1440]
       the section needs exactly one in-flow child .wrap (it has 2 in-flow children, 0 .wrap) (SR-6)
  FAIL container-edges  div#main-content > main.dashboard-main > section.status-grid "Operational state Operationa  [1440]
       the section needs exactly one in-flow child .wrap (it has 4 in-flow children, 0 .wrap) (and 2 more like it: "DiscoveryCapabilitieshub", "k.LAB service interfaceP") (SR-6)
  FAIL container-edges  div.q-card.q-card--flat:nth-of-type(2) > div.q-card__section.q-card__section--vert:nth-of-  [1440]
       the h2 starts 721px right of the container's left: every h1 and h2 starts on it, 2px either way (SR-6)
  FAIL container-edges  div.q-item.q-item-type > div.q-item__section.column:nth-of-type(2) > i.q-icon.notranslate   [1440]
       1 text block starts on a left line at x 121, 41px from the container's left: text starts on the container's left, or on one split line (x 623, 732, 841) (SR-6)
  FAIL container-edges  div.q-card.q-card--flat:nth-of-type(1) > div.q-card__section.q-card__section--vert:nth-of-  [1440]
       7 text blocks start on a left line at x 105, 25px from the container's left: text starts on the container's left, or on one split line (x 623, 732, 841) (also "Capabilities", "Service name", "Service ID") (and 1 more lik
  FAIL container-edges  div#main-content > main.dashboard-main > section.dashboard-content > div.q-card.q-card--fl  [1440]
       a box over half the container wide runs 80-752px, not on the container's lines at 80-1360px (SR-6)
  FAIL nav              div#app > div.q-layout.q-layout--standard > header.q-header.q-layout__section--marginal >   [390]
       nav links wrap onto 2 rows
  FAIL no-visual        div#main-content > main.dashboard-main > section.status-grid "Operational state Operationa  [390]
       no picture, effect or mock in this section; a feature section needs a visual
  FAIL no-visual        div#main-content > main.dashboard-main > section.dashboard-content "Capabilities"  [390]
       no picture, effect or mock in this section; a feature section needs a visual
  FAIL heading-scale    section.status-grid > div.q-card.q-card--flat:nth-of-type(2) > div.q-card__section.q-card_  [390]
       the figure "-1%" is set at 28px, larger than the section headings (h2 21px); 4 more like it
  FAIL container        page  [1440, 390]
       no .wrap
```

## Performance and limits

The original live-URL performance run stopped when Keycloak navigated the inspected target. before/perf.txt records the error; no baseline timing is inferred. The final visual preview skips automatic SSO because its port is not registered with the identity provider. Its timings therefore describe UI rendering, not end-to-end authentication. The host was busy, and the tool marks the timings accordingly.

The performance tool still flags the existing large JavaScript payload. This redesign retains the Quasar, shared plugin API, Sprotty and ELK stack; bundle restructuring is outside the requested visual changes. Exact transfer, parsing, FCP/LCP, blocking time, layout shift and warnings follow:

```text
perf 127.0.0.1:5190/resources/ @ 1440px
  PASS  host CPU load (before/after)               41/50 %        
  WARN  total transfer                             1956.9 KiB     html 1.5K  css 229.4K  js 1115K  font 125.8K  img 1.2K  other 484.1K
  PASS  requests                                   12             11 by load
  PASS  images bytes                               1.2 KiB        1 files
  PASS  largest image                              1.2 KiB        resources/klab-logo.svg
  FAIL  JS parsed                                  1114.5 KiB     2 files, 1115K gz
  WARN  CSS                                        229.4 KiB      2 files
  PASS  font families                              1              Material Icons
  PASS  font files                                 1              125.8K
  PASS  font bytes                                 125.8 KiB      
  PASS  images without width+height                0              
  PASS  below-fold images not lazy                 0              
  PASS  oversized images (natural > 2.2x shown)    0              
  PASS  WebGL live at load                         0              
  PASS  WebGL live at bottom                       0              
  PASS  WebGL peak live                            0              0 contexts made in all
  PASS  long tasks                                 2              runs 3, 1..2, max 230ms
  PASS  blocking time                              166 ms         runs 3, 96..189 ms; at load 166ms
  PASS  FCP (CPU, relative)                        376 ms         runs 3, 248..384
  PASS  LCP (CPU, relative)                        416 ms         runs 3, 284..428, p
  PASS  layout shift                               0.013          runs 3, 0.013..0.013; at load 0.013
  PASS  modules loaded, never mounted              0              
  PASS  broken requests                            0              
  PASS  page height                                1.2 screens    DOM 171 nodes

perf 127.0.0.1:5190/resources/ @ 390px
  PASS  host CPU load (before/after)               41/50 %        
  WARN  total transfer                             1955.8 KiB     html 1.5K  css 229.4K  js 1115K  font 125.8K  img 1.2K  other 482.9K
  PASS  requests                                   11             10 by load
  PASS  images bytes                               1.2 KiB        1 files
  PASS  largest image                              1.2 KiB        resources/klab-logo.svg
  FAIL  JS parsed                                  1114.5 KiB     2 files, 1115K gz
  WARN  CSS                                        229.4 KiB      2 files
  PASS  font families                              1              Material Icons
  PASS  font files                                 1              125.8K
  PASS  font bytes                                 125.8 KiB      
  PASS  images without width+height                0              
  WARN  below-fold images not lazy                 1              klab-logo.svg
  PASS  oversized images (natural > 2.2x shown)    0              
  PASS  WebGL live at load                         0              
  PASS  WebGL live at bottom                       0              
  PASS  WebGL peak live                            0              0 contexts made in all
  PASS  long tasks                                 2              runs 3, 2..2, max 252ms
  PASS  blocking time                              198 ms         runs 3, 175..208 ms; at load 198ms
  PASS  FCP (CPU, relative)                        404 ms         runs 3, 344..464
  PASS  LCP (CPU, relative)                        404 ms         runs 3, 344..464, h1
  PASS  layout shift                               0.000          runs 3, 0.000..0.000; at load 0.000
  PASS  modules loaded, never mounted              0              
  PASS  broken requests                            0              
  PASS  page height                                2.3 screens    DOM 171 nodes

SUMMARY 127.0.0.1:5190/resources/: 2 FAIL, 5 WARN  -> FAIL
```

## Running preview and deployment

Visual preview: http://127.0.0.1:5190/resources/ (also /reasoner/, /resolver/, /runtime/). It serves the production UI with live service telemetry. Its Sign in action is not functional on that unregistered preview origin; use the normal service origins after deployment for authentication. The production browser audit overlays built frontend files on the registered service origins, retaining their real authentication configuration and APIs.

The original ports 8091–8094 continue to run their existing JARs. Rebuild/package the UI artifact through the project's normal webui profile and restart the services with that artifact to activate the source changes there. KlabWebUiController caches its HTML shell at JVM startup. Source, generated resources, and the standalone preview are ready; no service restart or JAR replacement was performed.

No assumed prices, identities, capabilities or scientific facts were added. No form stand-in endpoint, invented brand, external art, or client asset slot is required. The elephant logo and favicon are exact copies of the supplied sibling SVG.
