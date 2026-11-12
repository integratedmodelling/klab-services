# Dashboard appearance

The dashboard and the four service extension packages share `src/styles/theme.css`.
It contains the light and dark palettes, font stacks, panel radius, content width,
and gutter. Change semantic properties there to configure an installation; components
consume `--klab-*` properties rather than assigning their own colours.

Use `--klab-surface` for panels, `--klab-surface-alt` for inset areas,
`--klab-ink` and `--klab-muted` for text, `--klab-action` for actions and focus,
and the `success`, `warning`, and `danger` pairs for feedback. Keep each text/background
pair accessible in both themes. The main page layout and Quasar adaptations live in
`src/styles.css`. Embedded applications map the same palette to their existing
`--ui-*` interface in `src/styles/application-ui.css`.

The header theme button defaults to light and remembers an explicit choice in
`localStorage` under `klab-dashboard-theme`, per service origin. Other tabs on that
origin follow changes. If browser storage is blocked, switching still works for the
current page. Actor applications inherit the dashboard theme unless their snapshot
explicitly requests `light` or `dark`; standalone embedded applications default to light.
The elephant SVG and favicon are copied unchanged from the sibling klab-ide project.

Run the existing production build (`npm run build`) to generate both dashboard and
embedded UI resources. Restart services with the rebuilt UI artifact to load the new
HTML shell: `KlabWebUiController` caches the shell when its JVM starts. No Java routes,
authentication settings, request payloads, or API contracts change.

`playwright.design.config.ts` runs existing isolated form, authentication, diagram and
review regressions alongside theme persistence and responsive navigation checks. Its
Vite server uses port 4229 because an existing browser-history regression expects it.
Before/after captures, metrics, and the audit report are in the repository's
`.taste-skill` directory. The optional visual preview there proxies public APIs from
the running services, but skips SSO on its unregistered port; use the normal service
origins after deployment for sign-in.
