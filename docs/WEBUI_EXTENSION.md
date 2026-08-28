# Extending the k.LAB service web UI

## Change a service dashboard

Override `configureWebUi` in the concrete `ServiceNetworkedInstance` subclass. The four server
modules contain examples.

```java
@Override
protected void configureWebUi(WebUiConfiguration.Builder dashboard) {
  dashboard
      .title("My k.LAB service")
      .subtitle("A concise description of the service")
      .logoUrl("my-service-logo.svg")
      .link("Documentation", "https://docs.example.org", true)
      .panel(
          "catalog",
          "Catalog",
          "Browse the objects advertised by this service.",
          "catalog-browser",
          100,
          true)
      .page(
          "catalog",
          "Resource catalog",
          "Browse and manage the resources advertised by this service.",
          "catalog-workspace",
          100,
          true);
}
```

Panel arguments are, in order: stable panel ID, heading, description, Vue component ID, sort order,
and whether the shell should require a signed-in user before mounting the component. IDs should be
lowercase kebab-case and remain stable across releases.

Full-page arguments follow the same pattern: stable URL name, title, description, Vue component ID,
sort order, and authentication requirement. The example is mounted at `/ui/catalog`. Registered
pages automatically become links in the dashboard header, ordered by `order`; no separate link
configuration is needed.

`logoUrl` is resolved relative to the service context root. Replace the core `klab-logo.svg` for a
global brand or package another static resource under the same Spring Boot static-resource rules
and point the configuration at it.

## Add a Vue single-file component

Create the source under the server module:

```text
klab.services.example.server/
  src/main/webui/extensions/CatalogBrowser.vue
  src/main/webui/extensions/CatalogWorkspace.vue
```

The component ID is generated from the filename: `CatalogBrowser.vue` becomes `catalog-browser`.
`CatalogWorkspace.vue` becomes `catalog-workspace` and can be referenced by a full-page record. Use
the generated ID in the corresponding Java configuration. Filenames must be unique across every
extension directory included in a build.

An extension receives one required `context` prop:

```vue
<script setup lang="ts">
import type { DashboardContext } from "@klab-dashboard/types";

const props = defineProps<{ context: DashboardContext }>();

async function loadPrivateData() {
  // The shared client refreshes and sends the Keycloak token when one exists.
  return props.context.api.get("api/example/items");
}
</script>

<template>
  <q-btn label="Load items" @click="loadPrivateData" />
</template>
```

The context provides:

| Member | Meaning |
| --- | --- |
| `config` | Immutable dashboard configuration returned by this service |
| `status` | Latest public service status, or `null` while unavailable |
| `capabilities` | Latest capabilities calculated for the current identity |
| `auth` | Reactive authentication readiness, identity, and error state |
| `api.get(path)` | JSON GET using the current token when available |
| `api.request(path, init)` | Generic authenticated fetch for other HTTP methods |
| `refresh()` | Refresh shared status and capabilities immediately |

Use Quasar components for controls and layout. Quasar and Vue are already supplied by the shell;
do not add another application root or another copy of either framework.

The same prop contract applies to dashboard panels and full-page components. A full-page component
owns the content area below the shared service header, so it should provide its own responsive
working layout rather than wrapping everything in another application shell.

## Discover extensions outside this repository

The build automatically scans the four standard server modules. A downstream service in another
directory can add one or more extension roots with `KLAB_WEBUI_EXTENSION_DIRS`. Separate multiple
directories with the platform path separator (`:` on Unix, `;` on Windows) before running the
`webui` Maven profile or `npm run build`.

Only `.vue` files under those directories are included. Extension registration is compile-time by
design: a service never downloads and executes an untrusted remote component at runtime.

## Authentication rules for extensions

Set `requiresAuthentication` when a component has no useful anonymous state. A dashboard panel is
replaced by a sign-in prompt. A full-page component is not mounted; after the initial `check-sso`
finishes, a persistent sign-in dialog opens over the requested URL. Keycloak returns to that exact
URL after authentication, and the shared API client then sends a standard bearer token and refreshes
it shortly before expiry.

This flag is user experience, not security. Every backing controller must still enforce the
appropriate role, CRUD permission, and resource privilege. Components should handle `401`, `403`,
empty, loading, and error responses without assuming the token grants a particular permission.

Do not store tokens in local storage, session storage, URLs, component state, logs, or persisted
Pinia/Vue state. Use `context.api` so token lifecycle remains centralized.

## Development loop

From `klab.core.services/src/main/webui`:

```bash
npm ci
npm run dev
```

The development server needs a proxy or a service origin for `/public/*` requests. Production uses
same-origin relative paths and therefore works under every service context path without CORS.

Before committing an extension:

1. Run `npm run typecheck` or the Maven `webui` profile.
2. Verify anonymous, authenticated, loading, empty, error, and narrow-screen states.
3. Confirm every protected operation is rejected server-side without a valid token.
4. Keep component IDs and the Java manifest synchronized.
5. Rebuild the core service artifact so the new compiled component is packaged.
