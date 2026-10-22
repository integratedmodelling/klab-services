# Application workflow test

Copy the executable [k.Actors fixture](../../../klab.core.services/src/test/resources/ui/workflow.kactor)
into a Resources project as `apps/tests/ui/workflow.kactor`. Copy this directory's `ui/` folder
into the same project. The fixture declares `tests.ui.workflow`; preserve that URN for this test.

After Resources has loaded the project, start a runtime service with its web UI built and hub
authentication configured. Open `<runtime-service-context>/apps/tests.ui.workflow` and sign in as
a user with READ on the project. Enter a name, click Run, open Details, refresh the page, and check
that the name and status remain. Reconnect replaces the renderer while retaining the server agent.
Stop explicitly ends that instance. New instance starts a separate workflow.

The fixture uses locale, translation-bundle and stylesheet properties in its preamble. Add
`locales: ("en", "es", "es-ES")` to restrict language choices, or replace `"light"` with `"dark"`
in `stylesheets` to use the dark base palette. With no locale restriction, an unavailable bundle
falls back to `messages.properties`. Edit `ui/styles/workflow.css` to customize variables and
classes. The fixture itself is parsed, compiled and exercised by `ApplicationUiRuntimeTest`.

See [UI.md](../../UI.md) for the full contract, build/test commands, authorization and current limits.
