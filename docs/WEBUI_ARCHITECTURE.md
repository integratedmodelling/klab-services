# k.LAB service web UI architecture

## Purpose

Every Spring-based k.LAB service can serve the same public Vue 3 dashboard at its context root. For
example, the reasoner dashboard is available at `http://localhost:8091/reasoner/`. Existing REST
clients continue to use the JSON endpoints below that context path.

The dashboard is intentionally split into two layers:

- `klab.core.services` owns the Spring controller, configuration contract, Vue/Quasar shell,
  authentication client, generic status display, and generic capability display.
- Each concrete `*.server` module owns its title and service-specific panels. Panels receive the
  same context and API client, so later interfaces can be developed without changing the shell.

## Request flow

The browser loads `index.html` through `KlabWebUiController`. The shell then requests:

| Endpoint | Authentication | Purpose |
| --- | --- | --- |
| `/` | Public | Vue single-page application |
| `/public/ui/config` | Public | Header, logo, authentication settings, links, and panel manifest |
| `/public/status` | Public, identity-aware when a token is present | Lightweight operational telemetry |
| `/public/capabilities` | Public, identity-aware when a token is present | Service identity, transports, components, and permissions |

Status and capabilities are refreshed every ten seconds. A failed refresh keeps the previous data
visible and displays a retry action. This keeps the dashboard useful while a service is starting or
temporarily busy.

`WebUiConfiguration` is the server-to-client contract. `ServiceNetworkedInstance` builds the
defaults and calls `configureWebUi(WebUiConfiguration.Builder)`. A derived Spring service overrides
that hook to change branding or add ordered `DashboardPanel` records.

## Frontend layout

The frontend source is in `klab.core.services/src/main/webui`:

- `src/App.vue` coordinates configuration, authentication, polling, and panel composition.
- `src/services/api.ts` is the sole generic HTTP boundary. It refreshes the Keycloak token and adds
  `Authorization: Bearer <token>` when an identity exists.
- `src/services/auth.ts` owns the Keycloak adapter. Tokens remain in memory; they are never copied to
  local or session storage.
- `src/components/StatusGrid.vue` and `CapabilitiesPanel.vue` are the common public dashboard.
- `src/components/ExtensionPanel.vue` enforces the `requiresAuthentication` presentation rule and
  renders the configured component.
- `vite.config.ts` discovers service components and creates the component registry at build time.

Panels receive a `DashboardContext` prop with the current configuration, status, capabilities,
authentication state, shared API client, and `refresh()` callback. The TypeScript interface in
`src/types.ts` is the generic downstream component contract.

The authorization requirement on a panel controls its presentation only. Server endpoints remain
the authority for access control. A component must never treat the presence of a panel or a decoded
browser token as authorization.

## Keycloak integration

The public configuration contains only the values safe for a browser-based OpenID Connect client:
the Keycloak base URL, realm, and public client ID. There is no client secret in the application.
The compact header control starts the normal Keycloak authorization-code flow with PKCE; Keycloak
owns username/password collection. The initial dashboard remains anonymous by using `check-sso`
instead of `login-required`.

Configure the server settings with Spring properties (for example in `application.properties` or
environment-backed configuration):

```properties
klab.webui.keycloak.url=https://identity.example.org
klab.webui.keycloak.realm=im
klab.webui.keycloak.client-id=klab-dashboard
```

Spring's equivalent environment variables are `KLAB_WEBUI_KEYCLOAK_URL`,
`KLAB_WEBUI_KEYCLOAK_REALM`, and `KLAB_WEBUI_KEYCLOAK_CLIENT_ID`.

If `klab.webui.keycloak.url` is absent, the dashboard stays public and shows no sign-in action. The
Keycloak client must be public, use authorization code + PKCE, allow the exact service dashboard
redirect URIs, and allow the service origin as a web origin.

The service authorization filter accepts both the historical raw JWT header and the standard
`Bearer` form produced by browser clients.

## Build and packaging

The normal Java build remains independent of Node.js. The `webui` Maven profile installs its pinned
Node/npm toolchain, performs a reproducible `npm ci`, type-checks the Vue sources, and runs the Vite
production build.

Compile the dashboard alone:

```bash
./mvnw -pl klab.core.services -Pwebui generate-resources
```

Package the generated files in `klab.core.services`:

```bash
./mvnw -pl klab.core.services -am -Pwebui package
```

Vite writes into `target/generated-resources/webui/static`. The Maven profile adds the parent
directory as a resource root, yielding Spring Boot's expected `classpath:/static/index.html` and
`classpath:/static/assets/*` layout.

Dependency versions are exact in `package.json` and `package-lock.json`. This avoids a dashboard
changing when a Java-only service build is repeated.

## Security boundaries

- The SPA, its assets, configuration, status, health, and public capabilities remain anonymous.
- A token is attached automatically when present, allowing capabilities and downstream components
  to receive identity-specific responses.
- Protected REST endpoints remain covered by `ServiceSecurityConfiguration` and the existing k.LAB
  role/permission checks.
- The panel manifest contains presentation metadata, never secrets or authorization decisions.
- Remote component URLs are not executed. Every Vue extension is reviewed and compiled into the
  artifact, keeping the service compatible with a strict content-security policy.
