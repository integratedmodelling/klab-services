# k.Actors application UI

This is the proposed application UI contract for k.LAB 1.0. It is a living design document:
update it with each implementation stage and record changes to public conventions here.
Applications describe their interfaces in ordinary k.Actors actions, groups, metadata and verbs.
An installed toolkit translates that description into a user interface. Quasar is the first
production target; Flutter is a prospective target; JavaFX is a small workflow testing target
for `klab-ide`.

## Status and boundaries

Stages 0–3 have an initial executable implementation. The portable contract below is broader than
the first renderer; implemented subsets and remaining work are stated explicitly.

| Surface | Status |
| --- | --- |
| Groups, trailing metadata, tags and action annotations | Existing language syntax and semantic model |
| Action execution hooks | Existing `RuntimeAgent.Scope.beforeAction` / `afterAction` |
| Vue/Quasar shell, authenticated API client, installed-component web modules | Existing service web integration |
| Immutable UI nodes, versioned snapshots, input envelope, toolkit SPI | Implemented initial scaffold in `api.view.application` |
| In-process presentation, input filtering, renderer detach/reattach and snapshot replay | Implemented `ApplicationUiSession` with headless regression tests |
| Row/column flex, action mounts, basic `core.ui` verbs and actor emitters | Implemented with invocation-local mount cursors |
| Published URN launch, project READ, retained state and dedicated WebSocket bus | Implemented on the runtime service |
| Quasar renderer, direct routes, light/dark themes and named CSS | Implemented initial subset |
| Localized constants and UTF-8 properties bundles | Implemented with generic preamble properties |
| JavaFX, Flutter, advanced layouts and component toolkit discovery | Later stages |
| Restart recovery and durable application checkpoints | Proposed, distinct from reconnecting to a live server |

The design uses current language/runtime contracts and current service web integration. Legacy UI
implementations and their metadata catalogs are not specifications for this work. No grammar
extensions are required by the initial implementation. Examples using the implemented subset are
executable; wider widget and layout catalogs remain proposals. The existing WebsocketsBus remains
separate from the new same-origin application bus.

Related references: [k.Actors](AGENTS.md), [service web architecture](WEBUI_ARCHITECTURE.md),
[web component extensions](WEBUI_EXTENSION.md), and [core agents](AGENTS_REFERENCE.md).

## Application and rendering model

An application instance owns one runtime agent, its private session, state, UI tree and registered
event emitters. `main` creates the primary interface once. Action invocation executes ordinary
behavior and may create or update UI nodes in a mount associated with that action. UI descriptions
are created by executing actions, so conditions, loops and imported components work naturally.
A static scan of source is insufficient to reconstruct a running interface.

The server produces a toolkit-neutral tree. Each node has an opaque instance-local ID, canonical
type, JSON properties, declared input event names and children. Server-side bindings map IDs to
source locations, tags and actor continuations; those bindings are never supplied by clients.
Tags resolve within a component instance and action mount. Two instances of the same component
therefore cannot overwrite each other's `#submit` widget. Duplicate tags within one mount are
validation errors. Untagged nodes receive generated IDs stable for that mount's lifetime.
The initial application body uses mount ID `main`; surface IDs default to their action names and
can be set with the annotation's `id` parameter. An unqualified tag is local to the current mount.
Use `/main/status` or `/details/field` for an explicit application-wide reference. Mount IDs must
be unique, and `main` is reserved for the application body.
Surface roots are also exposed as tags in `main`, so `ui.show("details")` from the body or
`ui.show("/main/details")` from another surface addresses the same dialog. Within a surface,
`surface` is its reserved root tag. Delayed UI operations from disposed mounts are discarded.

Toolkits render the same tree and send typed inputs back to the owning application. They do not
evaluate k.Actors, choose actor methods or own domain state. Rendering and export capabilities
must be declared separately: a Flutter project exporter may generate a client, but still needs
the runtime protocol for server-backed workflows. Exporting source must not serialize credentials,
server continuations or Java objects into the generated project.

## Layout metadata proposal

Group metadata follows the closing parenthesis. `:key value` supplies a value, `+flag` enables a
boolean, `!flag` disables it, and `#tag` names the resulting container. Keys below are a new portable
contract; they do not inherit unspecified legacy behavior. A group without layout metadata inside
a UI mount becomes a vertical flex container. Ordinary non-UI groups keep their execution meaning.

```kactors
(
    ui.text("Observation workspace")
    (
        ui.button("Run") #run
        ui.button("Cancel") #cancel
    ) :layout "row" :gap "8px" :justify "end" #controls
) :layout "column" +fill :padding "16px" #workspace
```

| Metadata | Accepted values and semantics |
| --- | --- |
| `:layout` | `row`, `column`, `grid`, `stack`, `tabs`, `split`, `scroll`; row/column are flex containers; other layouts require declared toolkit capability |
| `:direction` | `normal`, `reverse`; affects row/column flow |
| `:wrap` | `nowrap` (default), `wrap`, `wrap-reverse` |
| `:justify` | `start` (default), `end`, `center`, `space-between`, `space-around`, `space-evenly` on the main axis |
| `:align` | `stretch` (default), `start`, `end`, `center`, `baseline` on the cross axis |
| `:aligncontent` | `start`, `end`, `center`, `stretch`, `space-between`, `space-around`, `space-evenly` for wrapped lines |
| `:self` | `auto` (default), `start`, `end`, `center`, `stretch`, `baseline`; item cross-axis override |
| `:grow`, `:shrink`, `:basis`, `:order` | Nonnegative grow/shrink numbers (defaults 0/1), basis length or `auto`, signed integer order (default 0) |
| `+fill`, `+hfill`, `+vfill` | Fill available space in both axes or the indicated axis; normalization derives grow/stretch using the parent's direction |
| `:width`, `:height` | Nonnegative numeric logical pixels or string lengths: `px`, `%`, `rem`, `vw`, `vh`, or `auto` |
| `:minwidth`, `:maxwidth`, `:minheight`, `:maxheight` | Same lengths; `none` allowed for maxima; minima default to zero so nested flex content can shrink |
| `:gap`, `:rowgap`, `:columngap` | Nonnegative lengths; gap supplies both axes unless an axis overrides it |
| `:padding`, `:margin` | One length or CSS-style 2/3/4 space-separated lengths; `auto` only for margins; negative margins allowed |
| `:overflow`, `:overflowx`, `:overflowy` | `visible`, `hidden`, `auto`, `scroll`; per-axis overrides general setting |
| `+hidden`, `!hidden` | Hidden nodes occupy no layout space and accept no inputs; false by default |
| `+invisible`, `!invisible` | Preserve layout space while suppressing paint and inputs; false by default |
| `+disabled`, `!disabled` | Disable node and descendant interaction, preserving layout; false by default |
| `:columns`, `:rows`, `:columnspan`, `:rowspan` | Grid track definitions and positive spans; separate optional grid capability |
| `:split`, `:selected` | Split ratio in [0,1], or selected child tag for tabs/stack |
| `:background`, `:color`, `:border`, `:radius`, `:font`, `:fontsize` | Theme tokens and restricted literal style values; validate before translating |
| `:class` | Space-separated classes supplied by the selected named stylesheets |
| `:style` | Reserved extension escape hatch; raw text is rejected initially. Use `ui.style(tag, properties)` for portable property updates |
| `:label`, `:description`, `:role`, `:tabindex` | Accessible name, help, semantic role, keyboard order; toolkit must preserve keyboard access |
| `:tooltip`, `:icon`, `:testid` | Presentation/help tokens and stable test selector |
| `:responsive` | Map from named breakpoint (`xs`, `sm`, `md`, `lg`, `xl`) to layout overrides; toolkit resolves viewport changes locally |

Use quoted lengths to avoid ambiguity with scientific quantities. Percentages resolve against a
definite parent size; an indefinite parent produces an intrinsic size and a diagnostic. Default
breakpoints, in logical pixels, are xs=0, sm=600, md=1024, lg=1440, xl=1920; active overrides cascade
in that order. Responsive data uses ordinary map values where supported by the current grammar;
real-file parser tests must prove this before enabling it. No arbitrary CSS expressions are part
of the portable length contract.

Explicit grow/size/alignment settings override derived fill settings; explicit axis gap/overflow
settings override general ones. Contradictory duplicate declarations, invalid enum values and
negative sizes are errors with source locations. Unknown portable keys are diagnostics. Toolkit
extensions must be namespaced in the normalized tree and declared by a capability manifest.
Unsupported required features fail before mounting; an explicitly optional feature may degrade
with a visible diagnostic. Initially, row/column, direction, wrapping, grow/shrink/basis/order,
alignment, sizes, spacing, overflow, fill flags, visibility, disabled state and classes are supported.
Grid, stack, tabs, split, responsive overrides, advanced style tokens and complete unknown-key
diagnostics belong to stage 4. Raw CSS/JavaScript properties are rejected. Hidden, invisible and
disabled ancestors block input.

## Localization

Use the existing localized-constant syntax: `ui.button(#RUN)` or `ui.text(#TITLE)`. No dedicated
`locale` keyword is reintroduced. Generic preamble properties select presentation:

```kactors
web app examples.workflow
    "Localized application"
    version 1.0
    with properties {
        locale: "en",
        locales: ("en", "es", "es-ES"),
        translations: "i18n/messages",
        stylesheets: ("light", "styles/workflow")
    }
    using core.ui as ui
```

Launch supplies the browser's BCP 47 locale. If `locales` is declared, an unsupported request falls
back to `locale`. Otherwise, any well-formed locale can use the bundle fallback chain. For `es-ES`,
the declaration above merges these UTF-8 Java properties files, in order:

1. `ui/i18n/messages.properties`
2. `ui/i18n/messages_es.properties`
3. `ui/i18n/messages_es_ES.properties`

More specific files override earlier values. The server JVM locale never affects fallback.
Missing entries remain visible as `#KEY`. For example:

```properties
# ui/i18n/messages_es.properties
TITLE=Mi aplicación
RUN=Ejecutar
NAME=Nombre
READY=Preparado
```

Bundles are retrieved through the existing project-material API with project READ checks. The
browser receives resolved text, not a file-reading capability. Presentation is installed before
agent construction so constants in `init` resolve too. Locale and resolved translations stay
stable on reconnect. Dynamic locale switching, component-specific bundles, pluralization and data
formatting are later extensions. Never use translated labels as stable option or domain IDs.

The shared annotation grammar currently accepts strings rather than unquoted localized constants
in named parameters. For `title`, `label`, `description` and `tooltip`, use a quoted key marker such
as `@dialog(title="#TITLE")`; the UI layer resolves it from the same bundle. Widget and group values
continue to use the native unquoted `#TITLE` syntax.

## Named stylesheets and base themes

Use ordinary CSS without requiring a preprocessor or a utility-class language. `stylesheets`
selects names in cascade order. `light` and `dark` are built-in base styles; the last base name wins.
The optional `theme` property selects a base when neither is named. Other names resolve to
`ui/<name>.css` in the project's material directory. For `styles/workflow`, create
`ui/styles/workflow.css`:

```css
:scope {
  --ui-primary: #315da8;
  --ui-accent: #00796b;
}
.summary { padding: 1rem; border: 1px solid var(--ui-border); border-radius: .75rem; }
.muted { color: var(--ui-muted); }
```

Apply classes explicitly with `:class "summary muted"`. Base scaffolding uses `--ui-background`,
`--ui-surface`, `--ui-text`, `--ui-muted`, `--ui-border`, `--ui-primary`, `--ui-accent` and
`--ui-danger`. Quasar control colors map to these variables. Light and dark provide complete
starting palettes; the renderer respects reduced-motion preferences.

The Quasar renderer installs authorized, project-authored CSS in native CSS `@scope` blocks rooted
at `.application-ui`. Use rules and variables within that scope; avoid outer at-rules, remote
imports and global resets. Stylesheets are trusted application source, not user input. CSS bodies
are pinned for the instance and restored on reconnect; each file is limited to 128 KiB. This web
convention does not imply automatic CSS-to-Flutter translation. Native toolkits must declare their
supported style tokens and class mappings. JavaFX testing need only apply the basic palette.

## Action-bound interfaces

Annotations decorate ordinary actions and do not change calling syntax. The proposed set is:

| Annotation | Mount and lifecycle |
| --- | --- |
| `@header`, `@footer` | Application shell regions, created at startup by default |
| `@drawer` | Shell side region, created at startup, configurable side/overlay and visibility |
| `@toolbar`, `@menu` | Named navigation/action regions, mounted on explicit invocation unless `initial=true` |
| `@panel` | Reusable embedded region, usable in applications, behaviors and components |
| `@window` | Agent interface surface, attachable to a digital twin agent's inspector without making the behavior an application |
| `@dialog` | On-demand nonblocking dialog |
| `@modal` | On-demand dialog restricting interaction to its surface until dismissed |
| `@notification` | On-demand transient message/action surface |

Common named parameters: `id` (defaults to action name), `title`, `initial`, `visible`, `retain`,
`singleton`, `width`, `height`, `position`, `animation`, `duration`, `dismissible`, `breakpoint`.
`drawer` also accepts `side` (`start`/`end`) and `overlay`; dialogs accept `closeonescape` and
`closeonoutside`. Animations use portable `none`, `fade`, `slide`, `scale` tokens with durations in
milliseconds; respect reduced-motion preferences. Unsupported optional animations fall back to none.

Startup order is init, initial shell actions in declaration order, then main. Initial shell actions
must have no required arguments. Ordinary behaviors do not automatically create an application
shell: their `@window` is mounted only when an authorized host requests the agent interface.
Only one surface annotation per action is allowed initially. Header/footer IDs are unique per shell;
multiple drawers are allowed with distinct IDs.

Calling a surface action selects its mount and executes its body. Default `singleton=true` reuses
the surface, replacing its contents atomically and disposing previous widget emitters. The default
`retain=true` keeps surface state while hidden; closing with retain=false destroys the mount.
Repeated surfaces (`singleton=false`) receive distinct instance IDs and tags. Reopening a retained
surface through a visibility command does not rerun its action. Recursive/reentrant mount creation
must use invocation-local mount context and avoid overwriting a concurrent action's tree.

Imported k.Actors components and libraries contribute interfaces by executing their actions within
the caller's mount. Component instances own nested tag namespaces; libraries use a distinct
invocation namespace. Existing import/static/instance rules still apply. Reusable agent interfaces
retain their owning agent and permissions when embedded in another application's tree.

## `core.ui` agent and widget verbs

Applications will import `core.ui as ui`. This is a proposed Java actor extension, registered by the
current component actor mechanism. Widget verbs declare `EMITTER` semantics and register a server
continuation; they do not synchronously return a widget value. A tagged call binds a widget for
later control through `ui` commands. Passive presentation verbs are functions without emitted input.

| Family | Proposed verbs | Inputs emitted to match actions |
| --- | --- | --- |
| Basic actions | `button`, `iconbutton`, `link` | `click`: modifiers and optional navigation intent |
| Boolean/choice | `checkbox`, `switch`, `radio`, `select`, `multiselect` | `change`: boolean or selected stable option ID(s) |
| Text/numeric input | `input`, `textarea`, `number`, `slider`, `range`, `date`, `time`, `color` | Validated `change`/`commit`; optional debounced `input` |
| Forms | `form`, `field`, `submit`, `reset` | `submit`: validated field-value map; `reset` |
| Collections | `list`, `table`, `tree` | `select`, `activate`, `sort`, `page`, `expand`; stable item IDs |
| Navigation | `tabs`, `stepper`, `pagination`, `breadcrumbs` | `change`: stable destination/step/page ID |
| Content | `text`, `markdown`, `image`, `icon`, `separator`, `badge`, `progress`, `spinner` | Passive; sanitize markdown and restrict image sources |
| Media/files | `upload`, `download`, `audio`, `video` | Bounded file handles and playback events; no arbitrary server paths |
| Generic visualizations (future) | `map`, `chart` | Selection/filter events through explicit visualization capabilities |

Widget options use keyed arguments and call metadata, not toolkit constructors. The first subset
is text, button, checkbox, input, select, number and progress. JavaFX may implement only that subset
plus basic row/column layout and dialogs, reporting all other required types as unsupported.
Toolkits may contribute advanced namespaced widgets with schemas and event contracts.
Native ObservationCard, GeometryCard, SemanticComposer, provenance and system views belong to the
separate [k.LAB component contract](UI_KLAB_COMPONENTS.md). Simplified `ui.observation` and
`ui.provenance` verbs are not specified by this initial contract.

Each emitted value has `{event, value}` plus optional widget-specific data. Match handlers can
inspect these fields through ordinary k.Actors value access. Transport sequence/revision fields
remain runtime concerns. Node-specific schemas validate lengths, types, ranges, selections and
form constraints on the server; client validation is only assistance. High-frequency inputs are
debounced/coalesced, while clicks and submissions preserve order. Uploads use authorized HTTP
transfers and opaque handles rather than binary WebSocket frames.

Proposed control functions: `ui.show("tag")`, `ui.hide("tag")`, `ui.enable("tag")`,
`ui.disable("tag")`, `ui.set("tag", value)`, `ui.style("tag", properties)`,
`ui.focus("tag")`, `ui.close("tag")`, `ui.remove("tag")` and `ui.notify(message)`.
`ui.get("tag")` reads the canonical server-side widget value. These functions update state without
emitting user input, preventing feedback loops. Focus and transient notifications are effects,
not persistent state to replay on every reconnect. Tag-directed `.show`/`.hide` sugar should only
be added if current receiver resolution can support it without introducing new language rules;
the explicit `ui.show` form is the initial contract.

```kactors
web app examples.workflow
    "A small application workflow."
    version 1.0
    using core.ui as ui

action main:
    (
        ui.text("Ready") #status
        ui.button("Run"):
            event -> ui.set("status", "Running")
    ) :layout "column" :gap "12px" #workspace

@footer
action footer:
    ui.text("Workflow testing")
```

## Server state and reconnect

An application instance outlives its browser connection. Separate the published application URN
(which identifies code) from the opaque running instance ID (which identifies one user's state).
Refreshing the page or reconnecting must not start a second agent, rerun init/main, duplicate
subscriptions or reset fields. Starting a new independent instance is an explicit operation.

The server retains domain/actor state, canonical widget values, mount visibility, selection and
validation state, event bindings, the current tree revision and the accepted input sequence.
Widget values update on the server before the corresponding action is scheduled. Invalid edits
may retain a separate draft and validation result. The renderer may keep transient focus, hover
and scroll hints; those are optional restoration hints, never authoritative application state.
Secrets such as password fields need widget-specific retention/redaction policy and must not be
replayed as ordinary snapshot properties.

Reconnect protocol:

1. Authenticate again and authorize ownership/access to the running instance and READ on the source
   project. A URN or instance ID alone grants no access. Revoked access terminates the attachment.
2. Negotiate protocol and toolkit capabilities; bind the connection to the server-selected instance.
3. Return one consistent snapshot and its last accepted input sequence. Rebuild the renderer solely
   from this state and resume sequences above the cursor. Full snapshots are the initial protocol;
   revision-based deltas can follow later.
4. Keep actor subscriptions intact. A superseded connection cannot deliver input. The initial
   scaffold permits one attached renderer per instance; multi-view collaboration needs a separate
   event arbitration contract.
5. Reject duplicate inputs, old tree revisions, missing nodes and undeclared events. Stale inputs
   trigger resynchronization. Do not replay uncertain clicks automatically; a lost acknowledgement
   may already have caused a server-side effect.

Disconnect detaches the renderer and starts a configurable retention lease. The application can
continue background work and update its tree while detached. Explicit stop, lease expiry, revoked
authorization or owner session termination dispose emitters, mounts, private session and transport
resources. Logout removes local resume hints and detaches the connection; server policy determines
whether the retained instance survives logout. Do not conflate network loss with explicit stop.

`ApplicationUiSession` retains immutable snapshots and its event callback in memory;
detach/attach replays the latest revision and preserves the input cursor. It invalidates callbacks
from previous renderers. The running agent retains actor state; `ApplicationUiModel` retains field
values, mounts and emitter bindings. `ApplicationUiManager` supplies authorization, idempotent
creation, bounded tickets and retention leases. Rendering failure detaches the renderer while
retaining the latest accepted server tree. `close()` is terminal and clears it. Draft-validation
retention and restart recovery remain later work.

For restart recovery, introduce versioned checkpoints containing application URN + resolved source
version/digest, owner identity, domain state, widget/mount state and cursor. Reconstruct runtime
bindings through a dedicated restore lifecycle; Java futures, callbacks and threads cannot be
serialized. Define migration/rejection for changed code and restart policy for external operations.
Do not rerun startup effects during restore. Exactly-once external effects require transactional
or idempotent domain operations beyond transport deduplication. Durable recovery is a later stage,
not an implied guarantee of in-memory reconnection.

## Launch and authorization bridge

Entry route: `<runtime-service-context>/apps/<percent-encoded-application-URN>`. This is a new
namespace beside the existing `/ui/<component-name>` compiled web extensions. Encode the URN as one
path segment and decode once; do not interpret it as a filesystem path or fetch arbitrary URLs.
The public shell can display sign-in; protected data and execution require authentication.

REST operations are `POST /api/v1/ui/instances` (resolve, authorize, create or explicitly
resume), `GET /api/v1/ui/instances/{id}` (authorized snapshot/cursor), and
`DELETE /api/v1/ui/instances/{id}` (explicit stop). GET of a shell route never starts an agent.
Creation accepts an idempotency key so browser retries cannot duplicate application instances.
The body is `{urn, locale, idempotencyKey, resume}`; omit `resume` to create an independent instance.
`POST /api/v1/ui/instances/{id}/ticket` issues the attachment ticket. Application data responses
use `Cache-Control: no-store`.
The response includes the instance ID and authorized WebSocket attachment information. A resume
hint may be stored as a non-secret ID locally; service credentials stay in the shell's memory.

The bridge resolves a published application through Resources, verifies its containing project's
READ privilege using the authenticated user, checks application kind/platform compatibility and
starts the current Runtime agent path in a private user session. Never authorize a caller-provided
source body as if it were the published URN. Pin source version for the instance lifetime. Recheck
access on resume and respond to revocation during long-lived connections. Application permission
does not confer permission for resources or operations used by its actions; normal runtime checks
remain in force.
Browser scopes receive caller-bound clients built from the host's trusted service addresses and
current status. Host-authenticated clients and service privileges are not copied into the browser
scope. This replaces the IDE's service advertisement for a browser-launched application.

Host-classpath applications use `<serviceId>:app:<local-name>`, taking `serviceId` exactly from
service capabilities. The directory `META-INF/klab/apps/<local-name>/` contains `main.kactor`,
`application.properties` with a required `project=<project-URN>` binding, and optional `ui/` bundle
and stylesheet files. Resources parses the packaged source after project READ succeeds. Classpath
presence cannot bypass READ. Installed-component manifests, collision checks, explicit source
digests and Resources-catalog publication remain stage 4. Both published URNs and classpath aliases
use the same project permission check.

Launch through a runtime service; other service shells can link to its application URL. The
initial bridge requires a local runtime peer and does not proxy a live UI over another service's
transport. An unavailable runtime or Resources service produces an explicit unavailable result.

## Dedicated interaction transport

Use the dedicated same-origin WebSocket endpoint `/ui-bus`, bound to application instances.
It must not relay frequent UI input through AMQP. Existing service messaging remains available
for runtime/service events, separately from this interaction path. The existing WebsocketsBus
is not an authentication or application-instance isolation implementation for this feature.

Reuse the shell's authenticated HTTP client for authorization/attachment setup. Browser WebSocket
clients cannot set arbitrary Authorization headers: use an authenticated, short-lived, single-use
attachment ticket with origin checks, expiration, owner binding and replay prevention. Exchange
it in the first socket frame with a short authentication timeout; do not put credentials in URLs.
Enforce allowed origin and authorization again on attachment; expire/refresh attachments when
credentials expire. Never authorize by a client-chosen STOMP destination or node ID.
The ticket retains the service credential actually presented on its authenticated HTTP request.
The principal's upstream hub token is not a substitute: renewal must preserve opaque credential
revocation and expiration checks. Neither credential is sent through the socket URL or snapshots.

Protocol v1 carries snapshot, input, acknowledgement, resync, error and connection lifecycle messages. Each
connection is bound to one instance; snapshots include protocol version and revision, inputs
include sequence, revision, node ID, event name and JSON value. A first frame `{ticket}` authenticates
the socket; the server sends `snapshot` then `ready` with the accepted sequence cursor. Subsequent
frames are `{type: "input", value: <UiEvent>}` or `{type: "ping"}`. Input acknowledgement (`ack`)
means the value is retained and its reaction is scheduled, not that a domain operation completed.
`resync` includes the current snapshot and cursor. Accepted values are reduced on a bounded instance
queue; reactions execute in order on a separate bounded executor, outside socket IO.

Defaults: 30-second single-use tickets; five seconds for first-frame authentication; 64 KiB inbound
frames; 256 KiB outbound snapshots; 32 queued outbound messages; 256 pending reducers/reactions;
60 inputs/second per attachment; eight retained instances per user and 128 per service. Token and
project READ are rechecked on attachment and every ten seconds while connected. Expired credentials
detach; revoked project access terminates the instance. `klab.ui.retention-seconds` controls the
detached lease (default 1800). Full snapshots replace deltas for this first implementation.

## Toolkit installation and configuration

`UiToolkit` is a small current Java SPI: stable ID, supported node types and an instance renderer
with ordered snapshot delivery and input callback. `ApplicationUiSession` accepts a selected
implementation explicitly today. The initial SPI does not discover components or export projects.

Next, add a toolkit contribution hook to `KlabComponent` and lookup in `ComponentRegistry`, using
the existing trusted component classloader and web-module allowlist. Register immutable descriptors
with protocol range, platforms, widgets, layouts, input schemas, renderer module ID, export support
and defaults. Reject duplicate IDs and incompatible versions; choose a toolkit through per-service
configuration (proposed `klab.ui.toolkit=quasar`) with optional authorized application overrides.
Keep the host's Vue/Quasar runtime shared through the current `@klab/webui` contract. Do not bundle
conflicting host libraries in component archives. Retain the selected component version while its
application instances exist, or explicitly terminate/migrate them before unload.

Quasar will mount a recursive renderer in the existing shell, translating canonical nodes to
approved components. JavaFX consumes the same tree/events through an adapter provided outside
the service API, so `klab-ide` can attach a test view without adding JavaFX dependencies to services.
Flutter can implement the live protocol and optionally a separate build/export contribution;
generated Flutter projects require toolchain/version configuration and their own validation stage.

## Staged implementation and acceptance gates

| Stage | Work and ownership | Acceptance gate |
| --- | --- | --- |
| 0 — contracts and testing ground (implemented) | Core API value types/SPI; core-services in-memory state owner | Headless tests cover event rejection, duplicate IDs, toolkit capability failure, lifecycle, state replay and obsolete connections |
| 1 — executable vertical slice (initial subset implemented) | Core `ui` actor and invocation-local immutable group/action mount cursors; emitter bindings | A real parsed `.kactor` creates nested text/button/input nodes; click executes a handler and updates text; cursors survive asynchronous callbacks |
| 2 — stateful launch and transport (initial subset implemented) | Runtime/Resources authorize published URNs and host-classpath aliases; registry owns session/lease; dedicated bus binds identity and instance | Ownership, READ, launch retry, event validation, ticket replay/origin checks, field retention and socket replacement are tested |
| 3 — Quasar first usable application (initial subset implemented) | `/apps/` entry, basic widgets, shell surfaces/dialogs, flex, localized text and named CSS | Browser fixture covers authentication gate, field/button workflow, CSS, narrow viewport, reconnect and page rebuild; real socket test covers transport serialization and state replay |
| 4 — reusable components and toolkit installation | Registry contribution/configuration, full layout validation, annotations, nested imported component mounts and capability negotiation | Installed toolkit selected without rebuilding host; duplicate/unsupported features fail predictably; two component instances isolate tags; unload lifecycle tested |
| 5 — JavaFX workflow harness | Adapter and IDE integration in `klab-ide`; shared headless conformance fixtures | Same workflow fixture produces equivalent server transitions from Quasar and JavaFX; unsupported widgets produce explicit diagnostics; view thread and detach disposal tested |
| 6 — full instrumentation and durable state | Advanced widgets/scientific views, responsive/theme/a11y coverage, state checkpoint/restore/migration, telemetry and load testing | Published capability matrix; restart recovery proves no duplicate startup effects; bounded queues and lease cleanup verified under load |
| 7 — Flutter feasibility and export | Live renderer and optional component-provided project exporter | Generated project builds with a pinned SDK; mobile client authenticates, resumes state and exercises shared event conformance fixtures |

Stages 1–3 produce the first usable browser workflow. State ownership and reconnect are required
parts of that milestone. JavaFX remains a testing tool. Durable restart recovery and Flutter export
are separately gated so an initial browser milestone does not imply those guarantees.

## Using the current Java testing ground

For a browser workflow, follow the [example setup](examples/ui/README.md). Build the runtime and
shared browser assets together before starting the service:

```powershell
mvn -pl klab.services.runtime.server -am -Pwebui "-DskipTests" package
```

The example uses the real-source regression fixture with companion translations and CSS. It does
not create a project or change a running service automatically.

Construct an `ApplicationUiSession` with a callback that receives accepted `UiEvent`s. Supply a
`UiToolkit` adapter and call `present(UiNode)` to publish a full tree. Close/detach calls must release
renderer resources; renderers marshal to their own UI thread and must not synchronously emit input
from open/render/close. `present`/dispatch/attach are serialized. Input handlers should enqueue
actor work and return promptly. The application bridge supplies typed widget validation, bounded
queues, rate limits and authorization; additional toolkit transports must provide equivalent checks.

Call `detach()` to release only the view; continue `present()` while disconnected; `attach(toolkit)`
replays the retained tree. `resumeState()` captures snapshot and input cursor atomically;
`lastAcceptedSequence()` also exposes the cursor alone. Call `close()` only to
terminate the UI state owner. A standalone Java session has no lease timer; the service manager
adds retention leases. Neither layer provides disk persistence yet.

Focused regression test command (PowerShell):

```powershell
mvn -pl klab.core.services -am "-Dtest=ApplicationUiControllerTest,ApplicationUiSessionTest,ApplicationUiRuntimeTest,ApplicationUiManagerTest,ServiceUiApplicationLauncherTest,ApplicationUiWebSocketTest,ComponentRegistryActorVerbTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Run `npm run typecheck`, `npm run build` and `npm run test:application` in
`klab.core.services/src/main/webui`. Set `KLAB_TEST_BROWSER_CHANNEL=msedge` to use installed Edge.
The browser fixture uses controlled authentication/transport responses; the Java socket test uses
a real embedded server. A deployed Runtime + Resources + hub sign-in smoke test remains a separate
live-stack check. No live service is restarted by these tests.

The initial `core.ui` catalog is text, progress, button, checkbox, input, number, select, get, set,
show, hide, enable, disable, close, remove and style. Surface actions support header/footer/drawer,
panel/window, dialog/modal and the other named surface containers above within application scopes.
Initial header/footer/drawer actions run before main, after construction/init. Invoking an annotated
action currently replaces its mount and emitter bindings; showing a retained mount does not rerun
it. Repeated nonsingleton mounts, transactional rollback of partially built surfaces, complete
annotation option validation, animation mappings and UI-hosted ordinary behaviors remain stage 4.
