---
name: k.LAB interface baseline
description: Existing web interface tokens and conventions; current styling is not the approved future direction.
colors:
  dashboard-primary: "#18b795"
  dashboard-secondary: "#9ab558"
  dashboard-dark: "#092f2a"
  dashboard-background: "#f5f7f8"
  dashboard-background-end: "#eef2f3"
  dashboard-surface: "rgba(255, 255, 255, .92)"
  dashboard-text: "#18312d"
  dashboard-muted: "#657773"
  dashboard-border: "#dfe7e6"
  dashboard-label: "#287b69"
  dashboard-active-link: "#24735f"
  dashboard-status-start: "#287f6d"
  dashboard-status-end: "#1e6357"
  status-ok: "#2e9b72"
  status-warning: "#d89220"
  status-down: "#d95c5c"
  error-text: "#8c3030"
  error-background: "#fbecec"
  error-border: "#f1caca"
  advisory-text: "#805c1d"
  advisory-background: "#fff7e5"
  advisory-border: "#f0dfb5"
  application-background: "#f7f9fc"
  application-surface: "#ffffff"
  application-text: "#17283b"
  application-muted: "#53667b"
  application-border: "#cad5e2"
  application-primary: "#2459a8"
  application-accent: "#00796b"
  application-danger: "#b3261e"
  application-dark-background: "#141c28"
  application-dark-surface: "#202d3e"
  application-dark-text: "#e5edf7"
  application-dark-muted: "#afc1d7"
  application-dark-border: "#43556e"
  application-dark-primary: "#83b4ff"
  application-dark-accent: "#6ed6c4"
  application-dark-danger: "#ffb4ab"
  review-control-border: "#70999d"
  review-editor-background: "#10272a"
  review-editor-text: "#edf7f7"
  review-progress-background: "#edf7f3"
  review-progress-text: "#183f35"
  review-progress-border: "#b9d4cc"
  permission-background: "#00695c"
  permission-text: "#e0f2f1"
typography:
  dashboard-display:
    fontFamily: 'Georgia, "Times New Roman", serif'
    fontSize: "clamp(34px, 5vw, 60px)"
    fontWeight: 400
    lineHeight: 1.02
    letterSpacing: "-.025em"
  dashboard-title:
    fontFamily: 'Georgia, "Times New Roman", serif'
    fontSize: "28px"
    fontWeight: 400
    lineHeight: 1.25
  full-page-title:
    fontFamily: 'Georgia, "Times New Roman", serif'
    fontSize: "clamp(30px, 4vw, 48px)"
    fontWeight: 400
    lineHeight: 1.15
  shell-body:
    fontFamily: 'Roboto, -apple-system, "Helvetica Neue", Helvetica, Arial, sans-serif'
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "21px"
  global-root:
    fontFamily: 'ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
  review-body:
    fontSize: "1rem"
    lineHeight: 1.55
  review-section:
    fontSize: "1.375rem"
    fontWeight: 600
    lineHeight: 1.3
  review-document-title:
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.35
  review-task:
    fontSize: "1.125rem"
    fontWeight: 600
    lineHeight: 1.4
  review-field-label:
    fontSize: ".875rem"
    fontWeight: 600
  application-title:
    fontSize: "1.25rem"
    fontWeight: 600
    lineHeight: 1.4
  dashboard-label:
    fontSize: "11px"
    fontWeight: 750
    letterSpacing: ".17em"
  review-source:
    fontFamily: "monospace"
rounded:
  quasar-button: "3px"
  review-control: "4px"
  feedback-banner: "6px"
  component-list: "10px"
  application-scaffold: "12px"
  dashboard-card: "16px"
  authentication-dialog: "18px"
spacing:
  field-label-gap: "6px"
  review-control-padding: "8px"
  control-gap: "12px"
  metric-gap: "14px"
  region-padding: "16px"
  dashboard-content-gap: "18px"
  application-main-padding: "20px"
  authentication-padding: "24px"
components:
  dashboard-card:
    backgroundColor: "{colors.dashboard-surface}"
    textColor: "{colors.dashboard-text}"
    rounded: "{rounded.dashboard-card}"
    padding: "10px"
  application-button:
    backgroundColor: "{colors.application-primary}"
    textColor: "{colors.application-background}"
    rounded: "{rounded.quasar-button}"
    padding: "4px 16px"
  review-control:
    backgroundColor: "transparent"
    rounded: "{rounded.review-control}"
    padding: "{spacing.review-control-padding}"
  review-editor:
    backgroundColor: "{colors.review-editor-background}"
    textColor: "{colors.review-editor-text}"
    padding: "12px"
  review-input:
    backgroundColor: "transparent"
    rounded: "{rounded.review-control}"
    padding: "{spacing.review-control-padding}"
  permission-chip:
    backgroundColor: "{colors.permission-background}"
    textColor: "{colors.permission-text}"
    rounded: "12px"
    padding: "0 .4em"
    height: "1.5em"
---

# Design System: k.LAB interface baseline

## Overview

**Creative North Star: "A semantic web infrastructural stack for integrated science"** This is the user's description of the system, not an invented visual metaphor. The desired language emphasizes scientific publishing and editorial clarity. Component character should be approachable and softly framed, with restrained depth and flat task panels taking priority.

**Authority boundary:** the user explicitly stated that the current style is not a choice they want. This document inventories the existing implementation; it does not approve its palette, serif headings, gradients, or component styling as the future identity. The frontmatter gives exact baseline values for reproducing current behavior. Future redesign work must establish a new visual direction rather than treat these recorded values as user-mandated aesthetics. Quasar is the user-confirmed preferred UI stack; framework preference is independent of visual preference.

There are three related but distinct implementations: the shared service dashboard, the themeable k.Actors application renderer, and the comparatively primitive review interfaces. Typography and colors differ between their entry points. Their differences are evidence to preserve when diagnosing the present UI, not a deliberate global design theory.

**Key Characteristics:**

- Dashboard: pale background, green accents, serif headings, rounded cards, compact telemetry labels.
- k.Actors: scoped light/dark variables, Quasar controls, bordered application regions.
- Review: native form controls, explicit fieldsets, source-oriented editors, side-by-side candidate and feedback areas.
- Confirmed direction: scientific editorial clarity, functional color naming, restrained depth, approachable framing.

Evidence was inspected on 2026-11-07. Sources: `klab.core.services/src/main/webui/src/styles.css`, `src/styles/application-ui.css`, `src/main.ts`, shared Vue components, and `klab.services.resources.server/src/main/webui/extensions/ResourcesReview.vue` and `ReviewCyclesPanel.vue`. Dashboard and review fixtures were rendered at desktop and 390px mobile widths; the actual application scaffold was rendered with a synthetic snapshot in light desktop and dark mobile variants. Captures are local generated evidence under `target/design-documentation/`, not committed regression goldens. This pass did not verify a connected service or native IDE styling.

## Colors

Use functional names. The dashboard and application palettes have separate owners and should not be conflated when maintaining the current implementation.

### Primary

- **Dashboard primary:** the shell's Quasar brand primary, supplied in `src/main.ts`; it is distinct from the operational-state card gradient and heading-label color.
- **Application primary:** scoped `--ui-primary`, mapped to `--q-primary`. The dark theme supplies its own lighter primary. Use the renderer's theme scope when reproducing application controls.

### Secondary

- **Dashboard secondary:** Quasar secondary, currently used for refresh controls, capability icons, and the health bar.
- **Application accent:** scoped `--ui-accent`, mapped to Quasar secondary/accent and used by the explicit application focus outline.

### Neutral

- **Dashboard background, surface, text, muted, and border:** pale page layers, translucent cards, dark text, supporting text, and thin separators respectively.
- **Application background, surface, text, muted, and border:** independent light-theme variables with a complete dark-theme override. Dialogs carry the application theme explicitly.
- **Review editor background/text:** dark editing area with light source text, even on an otherwise light page. This is existing behavior, not a commitment to dark editors in a redesign.

### State and feedback

Operational, available, and unavailable states combine a colored dot with an explicit text value. Error and advisory banners pair text with tinted backgrounds and borders. Review pending/success messages use a separate pale progress treatment. Do not collapse these roles into the primary action color.

**The Scope Rule.** Current application themes belong inside the application renderer. Dashboard brand values are not replacements for `--ui-*` variables, and a dark application does not imply a dark dashboard.

## Typography

The dashboard pairs Georgia headings with Quasar's effective sans-serif body stack. The typeset/polish pass removed the unused Inter root declaration; the root now names OS fallbacks, while Quasar pages continue to set their own body font. No text-font asset was added. Document the cascade as it exists rather than asserting one unified font system.

| Role | Existing behavior |
| --- | --- |
| Dashboard display | Georgia, fluid frontmatter scale; at the 580px breakpoint it becomes 35px. |
| Full-page heading | Georgia; see `full-page-title`. Explicit leading overrides Quasar's larger heading default. |
| Dashboard section title | Georgia; see `dashboard-title`. Authentication heading is a local 27px variant. |
| Shell body | Quasar stack and body metrics recorded in `shell-body`; observed in the rendered dashboard. |
| Root declaration | OS fallback `global-root`; Quasar's body cascade remains independent. Current review fixtures now install Quasar as the production shell does. |
| Review roles | Relative body, section, document-title, task and field-label roles are recorded above. At a 16px root their appearance is preserved; the review body follows a larger browser default text size. Source editors keep their monospace role. |
| Application title | The `application-title` scale; rendered standalone application inherits Quasar's body stack. |
| Eyebrow/service label | `dashboard-label`, uppercase through CSS; metric labels use weight 700 and tighter `.11em` tracking. |
| Metrics | 28px, weight 650, `-.03em` tracking; memory uses the smaller 21px variant. |
| Review source | Monospace; short descriptive textareas explicitly revert to inherited font. |

There is no single mathematical type scale across these surfaces. Serif dashboard headings and small tracked labels are observed conventions, not the user's approved future typography. The scoped operational typography refinements and measured results are recorded in `.impeccable/review/typeset/implementation.md`; this does not establish a replacement global identity.

## Layout

Dashboard header width is capped at 1240px; main/footer at 1180px, each normally subtracting 40px from viewport width. Header height is at least 78px. Dashboard main padding is 62px above and 80px below. Full-page extensions use a wider 1440px cap and 38px/64px vertical padding.

Metrics use `1.25fr repeat(3, 1fr)` with the recorded metric gap. Main dashboard content uses two equal columns. At 900px, metrics become two columns, content becomes one, and header links give way to a compact menu. At 580px, metrics become one column; outer width subtracts 24px, the brand kicker disappears, the logo shrinks, and the footer stacks. Global body minimum width is 320px.

The application scaffold uses header/footer across the full width with a 240px drawer beside the main region, at least 65vh high. At 600px, regions stack into one column and the drawer becomes a horizontal section with a bottom border. Main region padding is recorded in the frontmatter. The outer application component is capped at 1500px with 24px padding, reduced to 10px on mobile. Individual k.Actors layouts remain driven by node properties; the scaffold is not a fixed grid for every application.

Review content is capped at 1500px. Controls wrap with 10–12px gaps; labels sit above fields. Candidate and feedback/context use two equal columns with a 20px gap and stack at 850px. Candidate textareas use 55vh height with a 280px minimum; short textareas use 100px height and an 80px minimum. The standalone review fixture has no viewport meta tag, so its mobile capture is evidence of that fixture, not full production mobile validation.

Spacing is not a uniform scale: frontmatter entries are reusable observed values, not a newly imposed spacing system.

## Elevation & Depth

**Confirmed preference: restrained depth, with flat task panels taking priority.** Existing applications and reviews rely mainly on borders, region placement, and background tone. Dashboard CSS declares a soft card shadow and a translucent header with blur, but shared dashboard cards also use Quasar's `flat` prop; the framework's no-shadow utility can suppress the declared card shadow. Do not mistake an unused declaration for a guaranteed visible effect.

The existing authentication dialog declares a stronger shadow. Review progress/success banners use a small shadow and sticky positioning so pending work remains visible. The exact declared shadows, header background layers, focus treatment, and motion behavior are preserved in the sidecar. They are inventory values, not instructions to spread elevation to every panel.

## Shapes

Dashboard cards are softly rounded; the authentication dialog has a slightly larger radius. Application scaffolds use a smaller rounded outline. Review buttons, inputs, and selects use compact corners and a thin control border; review source textareas do not share an explicit rounded-corner declaration. Quasar buttons retain framework geometry, with round variants for icon-only actions. Frontmatter records the observed radii by component rather than inventing a universal small/medium/large scale.

Borders carry more structural information in review and application panels than in the dashboard. Keep the distinction between a control outline, a region divider, and the source editor's frame when reproducing the baseline.

## Components

### Buttons

The preferred foundation is Quasar. Shared shell controls use its flat, round, unelevated, icon, loading, and disabled variants. Authentication actions explicitly use sentence case. k.Actors buttons use `no-caps`; their renderer exposes dense, flat, outlined, round, loading, size, color, and icon properties. Their default fill comes from the active application primary variable.

Review buttons are currently native HTML with transparent fill, inherited text color, the review control outline, and compact padding. Disabled opacity is `.55` and the pointer returns to default. This is a baseline to document, not a recommendation to bypass Quasar in new interfaces. There is no custom review hover/focus system in the inspected styles; browser behavior still applies.

### Cards and application regions

Shared metric/surface cards use translucent light fills, a thin border, recorded corner radius, and 10px outer padding plus Quasar section padding. The primary operational metric uses a green gradient with light text. Application header, drawer, and footer use the active surface variable and region dividers; the main region uses the application background. Do not turn each application region into a dashboard-style metric card by default.

### Fields and source editors

k.Actors inputs/selects are Quasar outlined fields by default; node properties can change density, read-only state, hint, error, and outline behavior. Theme variables control text and borders. Application node and button `:focus-visible` uses an accent outline (3px with 3px offset).

Review forms use visible native labels, legends, and explicit grouping. Source textareas use the dark editor treatment; short stage descriptions/preparation notes keep that background while switching back to inherited typography. Read-only, disabled, unsaved, and pending states remain distinct. The current large raw proposal editor is not evidence of a desired final review experience.

### Chips and navigation

Permission chips are dense Quasar chips using the framework's `teal-9` background and `teal-1` text. Header navigation is a horizontal set of compact links, with a darker hover and green active treatment; at 900px it becomes a dropdown. Authentication/account controls and protected-page dialogs are distinct from navigation links.

### Feedback, dialogs, and process diagrams

Dashboard loading uses Quasar skeletons/spinners. Refresh failures retain earlier data with a retry action. Reviews expose textual pending/success/error feedback; sticky progress includes a one-second spinner that stops under reduced motion. Dialog transitions use Quasar; the application renderer supports none/fade/slide/scale, defaults to scale and 300ms, and honors reduced motion. The shell's authentication dialog has its own scale transition; no global reduced-motion override was verified for it.

The reusable process diagram uses a bordered, rounded frame, a lightly tinted toolbar, a large SVG canvas, fit controls, selectable elements, and a text inspector. Its own local styles are not a global palette: selected nodes/edges use a stronger stroke; output ports use a separate warm accent. The canvas changes from 560px to 400px at 700px.

Sidecar component previews are small framework-free visual excerpts, not substitutes for the production Quasar components or full application state behavior.

## Do's and Don'ts

- **Do** treat Quasar as the preferred system UI stack.
- **Do** distinguish observed baseline styles from confirmed preferences and future design decisions.
- **Do** use functional color names and keep application themes scoped.
- **Do** retain visible labels, textual states, and the existing application focus/reduced-motion behavior during maintenance.
- **Do** prioritize flat task panels and restrained depth when interpreting the user's stated preferences.
- **Don't** present the current colors, gradients, serif headings, or review editor styling as an approved future identity.
- **Don't** infer a downloaded font from a fallback declaration or assume an isolated fixture shares the production cascade without checking it.
- **Don't** claim the primitive review forms represent the finished review experience.
- **Don't** infer full accessibility, authentication, or production-responsive verification from these isolated captures.

Format reference: [DESIGN.md specification](https://raw.githubusercontent.com/google-labs-code/design.md/main/docs/spec.md). Tokens and implementation findings above come from the local project; the linked specification supplies the document structure only.
