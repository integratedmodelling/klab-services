# k.Actors application UI

This is the proposed application UI contract for k.LAB 1.0. It is a living design document:
update it with each implementation stage and record changes to public conventions here.
Applications describe their interfaces in ordinary k.Actors actions, groups, metadata and verbs.
An installed toolkit translates that description into a user interface. Quasar is the first
production target; Flutter is a prospective target; JavaFX is a small workflow testing target
for `klab-ide`.

## Status and boundaries

Stages 0–4 and the widget extensions in stages 4a–4b have an executable implementation. The portable contract below is broader than
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
| Quasar renderer, direct routes, light/dark themes and named CSS | Implemented, including extended widget catalog below |
| Forms, collections, navigation, safe content, file handles and media | Implemented initial portable contracts; Quasar target |
| Widget references, subscriptions and fluent file/collection messages | Implemented `core.widget` / `core.ui_file` handles with explicit lifetime errors |
| Localized constants and UTF-8 properties bundles | Implemented with generic preamble properties |
| Named-action component instances, nested imports, tagged insertion/removal | Implemented with isolated namespaces and staged construction |
| Installed component toolkit discovery, configuration and version leases | Implemented for browser renderer modules |
| JavaFX, Flutter and advanced layouts | Later stages |
| Restart recovery and durable application checkpoints | Proposed, distinct from reconnecting to a live server |

The design uses current language/runtime contracts and current service web integration. Legacy UI
implementations and their metadata catalogs are not specifications for this work. No grammar
extensions are required by the initial implementation. Examples using the implemented subset are
executable; advanced layout capabilities and the future map/chart family remain proposals. The existing WebsocketsBus remains
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
Unknown keys now fail explicitly, and supported flex values are validated. Grid, stack, tabs,
split, responsive overrides and advanced style tokens remain unsupported capabilities for later
instrumentation. Raw CSS/JavaScript properties are rejected. Hidden, invisible and
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

That list describes the wider proposed contract. Current annotations accept `id`, localized `title`,
`initial`, `hidden` (use it instead of proposed `visible`), portable flex/sizing/class properties,
`dismissible`, `closeonescape`, `closeonoutside`, and dialog `animation`/`duration` (0–10000 ms).
Other proposed surface options raise explicit metadata errors. `@panel` and `@window` also describe
the reusable named actions accepted by `ui.mount`; `mount` supplies their instance identity.

Startup order is init, initial shell actions in declaration order, then main. Initial shell actions
must have no required arguments. Ordinary behaviors do not automatically create an application
shell: their `@window` is mounted only when an authorized host requests the agent interface.
Only one surface annotation per action is allowed initially. Header/footer IDs are unique per shell;
multiple drawers are allowed with distinct IDs.

Calling a surface action selects its mount and executes its body. Current ordinary action calls
replace their singleton surface and dispose previous widget emitters. Visibility commands retain
state and do not rerun actions. For repeated instances, use `ui.mount`; for destruction use
`ui.remove`. `singleton=false` and `retain=false` are rejected with instructions to use these explicit
verbs. Atomic ordinary singleton replacement and these optional shorthand annotations remain
proposals; dynamic `mount` construction already commits its subtree atomically.

Imported k.Actors components and libraries contribute interfaces by executing their actions within
the caller's mount. Component instances own nested tag namespaces; libraries use a distinct
invocation namespace. Existing import/static/instance rules still apply. Reusable agent interfaces
retain their owning agent and permissions when embedded in another application's tree.

## `core.ui` agent and widget verbs

### Implemented verb parameters

Import `core.ui as ui`. Scope and metadata are injected; only the arguments below appear in source.
Call metadata goes **inside the parentheses after positional arguments**, or after the call/match
block. For example `ui.input(#NAME :value "Ada" :maxlength 80 :placeholder "Full name" +dense) #name`.
Lists use parentheses: `ui.select("Region" :options ("EU", "AF", "AS") :value "EU") #region`.
Tags and option IDs remain untranslated. Each emitter fires a map with `event` and `value`.

| Verb and positional arguments | Implemented metadata and defaults | Result/input |
| --- | --- | --- |
| `text(text)` | `label` can override positional text; shared layout/help/classes below | Plain escaped text; no input |
| `progress(value)` | Fraction in [0,1]; `+indeterminate`, `color`, `label` for accessibility | No input; `set` changes progress |
| `button(label)` | `icon`, `color="primary"`, `size` (`xs/sm/md/lg/xl`), `+dense`, `+flat`, `+outlined`, `+round`, `+loading`, `+readonly` | `{event:"click", value:null}`; loading/read-only blocks server input |
| `checkbox(label)` | `value=false`, `color`, `+dense`, `+readonly` | `{event:"change", value:boolean}` |
| `input(label)` | `value=""`, `type="text"` (`text/email/password/tel/url/search/textarea`), `placeholder`, `hint`, `error`, `maxlength=16384` (1–16384), `debounce=300` ms (0–2000), `+required`, `+readonly`, `+dense`, `outlined=true`, `color` | `{event:"change", value:string}`; length/required/read-only checked on server. Input type is presentation, not email/URL domain validation |
| `number(label)` | `value=0` (or `min` when given), finite `min/max`, positive `step`, `debounce=300`, `placeholder`, `hint`, `error`, `+readonly`, `+dense`, `outlined=true`, `color` | `{event:"change", value:number}`; finite/range checked on server. Step is the browser increment, not a rounding rule |
| `select(label)` | Required `options`: distinct strings (up to 10000); `value=""`, `hint`, `error`, `+required`, `+readonly`, `+dense`, `outlined=true`, `color` | `{event:"change", value:string}`; server requires a declared option ID |
| `get(tag)` | None | Current authoritative `value`; text uses `label`, so `get` on text returns null |
| `set(tag, value)` | None | Sets value (or text label), validates primitive value/range; does not fire user input |
| `show(tag)`, `hide(tag)` | None | Set hidden state, retaining contents and values |
| `enable(tag)`, `disable(tag)` | None | Enable/disable node and descendants |
| `close(tag)` | None | Hide the tagged surface; contents retained |
| `remove(tag)` | None | Remove tagged subtree, tag aliases, action lifetime and widget bindings |
| `clear(tag)` | None | Remove a container's children and their bindings; retain the target container |
| `style(tag, properties)` | Map of the supported portable metadata below, e.g. `{class:"summary", hidden:false}` | Validate then merge metadata; no arbitrary CSS text or event-handler attributes |
| `mount(target, instance, action, arguments)` | Four positional arguments; `arguments` is a list, including `()` for no arguments | Mount named `@panel`/`@window` action; see composition below |

Every primitive accepts portable row/column flex sizing, spacing, visibility, disabled state,
`class`, `tooltip`, `testid`, `role`, `tabindex`, accessible `label` and `description`. Configuration
has meaning only for the widget types listed above; for example `placeholder` configures input,
not text. Color tokens are `primary`, `secondary`, `accent`, `positive`, `negative`, `info`, `warning`,
`dark`; CSS classes supply further styling. Boolean flags also accept `:key true/false`. `error` is
an authored error message; client validation does not execute arbitrary expressions. Required
fields may start empty; user changes must satisfy the server contract. Author domain validation
in action reactions. Plain text never evaluates HTML. Unknown metadata and invalid portable values
raise explicit errors; advanced proposed layouts below remain unsupported in this renderer.

### Post-construction widget messages

Every widget factory returns a server-local `core.widget` handle. This includes passive content
such as `text` and `image`. Interactive factories retain their emitter semantics: assignment
captures the newly constructed widget immediately, while input events arrive later through a
subscription. The widget itself owns the emitter. The stored variable is only a handle to that
emitter; it is not an emitter in its own right. Creating or copying a handle starts no second widget or stream.
The handle exposes the actor messages below; it is not a browser component, a widget value, or an
event payload. `events()` subscribes to the referenced widget rather than creating another emitter.

```kactors
btn <- ui.button("Click me")
btn.events():
    event -> (
        btn.settext("I'm disabled now")
        btn.enabled(false)
    )
```

The current grammar does **not** accept a reaction after the right-hand side of an assignment
(`btn <- ui.button(...): ...`). Separate creation from `btn.events()` and use an explicit match such as `event ->`, as above.
Call names are lowercase in the current grammar, hence `settext`, rather than `setText`. Continue importing
`using core.ui as ui`; widget handles need no additional import or direct constructor.

Existing tag-directed operations remain supported:

```kactors
ui.button("Run") #run
btn <- ui.widget("run")
btn.settext("Ready to run")
btn.style({outlined: true, dense: true})
```

`ui.widget(tag)` can also address a tagged container or annotated surface, using the same local,
component and absolute tag namespaces as `ui.set` and `ui.style`. It resolves the tag **once** and
captures that original node. A saved handle cannot accidentally address a new widget that later
reuses the tag. Unknown tags raise the existing `Unknown UI tag` error.

| Message | Behavior |
| --- | --- |
| `get()` / `set(value)` | Read/write the canonical value, with the same type, range and selection checks as `ui.get`/`ui.set` |
| `gettext()` / `settext(text)` | Read/write displayed text or label. For inputs, changes the label; use `set` to edit their value. For text, Markdown and badges, changes the displayed content |
| `enabled(boolean)` / `enable()` / `disable()` | Set the widget's own enabled state; a disabled ancestor still blocks interaction |
| `visible(boolean)` / `show()` / `hide()` | Set the widget's own visibility; a hidden ancestor still hides it |
| `readonly(boolean)` / `loading(boolean)` | Set validated read-only/loading flags; programmatic changes do not emit input |
| `options(ids)` / `items(rows)` | Replace choice/navigation options or collection data using the same validation as `style`; preserve the selected ID or clear it with `set` first |
| `file()` | Get a `core.ui_file` reference for the current upload/download file; errors when no file is available |
| `source(widgetOrFile)` | Set a downloader's source from an uploader/downloader reference, a `core.ui_file` reference, or a legacy opaque-handle map |
| `content(name, text, mediaType)` | Generate a UTF-8 file, bind it to this downloader, and return its `core.ui_file` reference |
| `style(properties)` | Update validated appearance, layout and widget options, e.g. `{label: "Run", disabled: true}` |
| `close()` | Hide the widget/surface, retaining its state |
| `remove()` | Destroy the original node, descendants and event bindings |
| `clear()` | Remove a container's children while retaining the container; rejects leaf widgets |
| `exists()` | Test whether this original widget is still live; returns false after disposal |
| `events()` | Subscribe to an interactive widget's ordinary `{event, value}` payloads. Multiple subscribers are independent; this does not replace reactions on the original factory call. Passive widgets and layout/surface handles reject this subscription |

Control messages update the authoritative server tree without firing synthetic input. They are
reflected by Quasar and retained through detach/reconnect. Pass a handle to ordinary k.Actors
actions when needed; reacquire a tagged widget with `ui.widget` from other actions. Handles remain
inside the running server application and must not be placed in JSON widget properties, sent to
the browser, or treated as durable restart checkpoints.

A handle's lifetime ends when its node is removed, its component/mount is replaced or cleared,
its construction emitter completes, or its application stops. Emitter completion disposes the
widget and its subscriptions. Returning the immediate construction handle does **not** complete
that emitter. This is an explicit constructor contract; ordinary emitters still cannot be assigned.
The compiler explains that their events must be subscribed to instead.

Calling a message on an expired handle raises `Widget handle is no longer valid`, with a reason
such as `widget was removed or its mount was replaced`, `widget emitter has completed`, or
`application has stopped`. `exists()` is the exception: it returns false so expected disposal can
be handled explicitly:

```kactors
if btn.exists() (
    btn.settext("Ready")
    btn.enabled(true)
)
```

The check and subsequent messages are separate operations; concurrent removal can still cause a
later message to fail. Invalid `set` values, unsupported `style` options and attempts to `clear` a
leaf widget remain validation errors, not silent no-ops. Existing delayed tag-directed commands
from disposed mount scopes continue to be discarded.

For Java widget extensions, `@Verb(executionType=EMITTER, returnsHandle="core.widget")` declares
an immediate construction handle separately from emitted event values. Component discovery
requires a non-void emitter method. `producesAgent` continues to describe ordinary returned or
emitted agents; it does not imply this construction-handle lifetime contract. The factory must
return promptly and keep its emitter scope alive until the widget is disposed.

### Fluent file and collection references

Widget messages avoid decoding transport maps in ordinary application code. The file bytes remain
bounded, instance-owned HTTP transfers; a `core.ui_file` reference is a server-side capability to
those bytes, not a filesystem path or another emitter.

```kactors
upload <- ui.upload("Upload CSV" :accept ".csv,text/csv" :maxbytes 1048576)
download <- ui.download("Download uploaded file")
upload.events():
    event -> (
        download.source(upload)
        file <- upload.file()
        status.settext(file.text())
    )
report <- ui.download("Generated report")
generated <- report.content("report.csv", "id,value\nsample,42\n", "text/csv")
report.source(generated)
```

Here `status` is a previously captured text widget, such as `status <- ui.text("Ready")`.
`source(upload)` takes that uploader's currently committed file; call it in each upload reaction
to refresh the downloader after another selection. It does not create a continuous binding.
`source(upload.file())` is equivalent, and `source(event.value)` remains available through an
expression such as `download.source([event.value])` for compatibility with the wire payload.
`content` is a synchronous construction function; its result can be saved or ignored.

| `core.ui_file` message | Contract |
| --- | --- |
| `name()` / `type()` / `size()` | Filename, claimed MIME type, and byte count |
| `text()` | Decode UTF-8 on the server; malformed UTF-8 raises an explicit validation error |
| `bytes()` | Return a defensive byte-array copy to server-side code |
| `metadata()` | Return the legacy JSON handle map `{id, name, mediaType, size}` when needed by existing `ui.set` code |
| `exists()` | Whether the original bytes remain in this application; false after file release or application stop |

Removing an uploader releases its uploaded bytes and invalidates references to those bytes,
including copies held by downloaders. Generated files remain instance-owned until stop/expiry.
Other file messages raise `UI file handle is no longer valid` after release. Cross-application
sources are rejected, and `source("C:/...")` or a server path is never accepted. The existing
4 MiB per-file, 16-file and 16 MiB per-instance bounds apply equally to `content` and HTTP uploads.
No browser protocol changes or new filesystem privileges are introduced.

The same style of direct messages applies to other widget families:

```kactors
choice <- ui.select("Region" :options ("eu", "af") :value "eu")
choice.options(("eu", "as"))
choice.set("as")
choice.readonly(true)
accounts <- ui.list("Accounts" :items ({id: "a", label: "Alice"}, {id: "b", label: "Bob"}))
accounts.items(({id: "a", label: "Ada"}, {id: "b", label: "Bob"}))
```

`options` is supported on choice/navigation widgets; `items` on lists/tables/trees. Wrong widget
types raise descriptive errors. These operations preserve schema checks and never simulate user
selection. See the complete [widget-handle application](examples/ui/widget-handles.kactor), parsed
and executed by `UiWidgetHandleTest`, for button reactions, file forwarding, generated reports,
choice updates and collection updates together.

### Tagged containers and dynamic action components

`#tag` following a group exposes its generated container to controls and `mount`. The renderer
includes `data-ui-tag` with the namespace-qualified tag for inspection; the server alone resolves
targets. Components can only target containers/surfaces, never an input or button.

```kactors
action main:
    (ui.text("Accounts")) :layout "column" :gap "12px" #accounts
    ui.mount("accounts", "alice", "account", ("Alice", "alice@example.org"))
    ui.mount("accounts", "bob", "account", ("Bob", "bob@example.org"))

@panel
action account(name, address):
    ui.input(name :value address :type "email" :maxlength 160) #email
    ui.button("Remove"):
        event -> ui.remove("surface")
```

The instance tag must be a lowercase identifier with optional digits, `_` or `-`. It is unique in
the caller's namespace. The example exposes `main/alice`, `main/alice/email`, `main/bob/email` and
each component's local `surface` alias. Relative tags resolve in the calling component namespace;
absolute tags start with `/`, such as `/main/alice/email`. `ui.remove("alice")` in main removes only
Alice; `ui.clear("accounts")` removes all children but keeps `accounts` available for later mounts.
Remove before reusing the same instance tag; duplicate live tags fail explicitly.

Actions may come from an imported k.Actors behavior/library: use `"cards.account"`, where `cards`
is the import alias. Its annotations and argument count are checked before construction, using
the existing trusted compiled import. Nested mounts inherit the component action owner, allowing
its local named actions to compose further components. No closure syntax or client-selected actor
method invocation is introduced. Socket events still address only server-created widget IDs.

Construction is staged: the subtree becomes visible after its action completes successfully.
Failure discards its nodes and bindings; it does not roll back domain side effects performed by
the action, or edits to other mounts. Imported delegate domain state follows its existing lifetime;
mount instances isolate UI tags and values and do not create a separate agent per instance.
Removing a target during asynchronous construction cancels its UI lifetime and prevents
late callbacks from recreating nodes. Successful instances retain their state through reconnect.
Ordinary annotated action invocation retains its singleton replacement semantics; explicit `mount`
provides multiple independent instances. External live agents and their ownership/delegation bridge
remain separate from importing reusable UI action code.

The families below are implemented for Quasar, except the explicitly future visualization row. The `core.ui`
Java actor is registered by the current component mechanism. Widget verbs declare `EMITTER` semantics and register a server
continuation. They may synchronously return a `core.widget` construction handle when assigned;
this is distinct from the canonical widget value and from later event payloads. A tagged call also
binds a widget for later control through `ui` commands. Passive presentation verbs are functions without emitted input.

| Family | Verbs or options (implemented unless marked future) | Inputs emitted to match actions |
| --- | --- | --- |
| Basic actions | `button`, `iconbutton`, `link` | `click`: null; optional authored `href` on links |
| Boolean/choice | `checkbox`, `switch`, `radio`, `select`, `multiselect` | `change`: boolean or selected stable option ID(s) |
| Text/numeric input | `input`, `textarea`, `number`, `slider`, `range`, `date`, `time`, `color` | Validated `change`; text inputs debounced, sliders emit on release |
| Forms | Group `+form`, widget `:field`, `submit`, `reset` | `submit`: validated field-value map; `reset` |
| Collections | `list`, `table`, `tree` | `select`, `activate`, `sort`, `page`, `expand`; stable item IDs |
| Navigation | `tabs`, `stepper`, `pagination`, `breadcrumbs` | `change`: stable destination/step/page ID |
| Content | `text`, `markdown`, `image`, `icon`, `separator`, `badge`, `progress`, `spinner` | Passive; sanitize markdown and restrict image sources |
| Media/files | `upload`, `download`, `audio`, `video` | Bounded file handles and playback events; no arbitrary server paths |
| Generic visualizations (future) | `map`, `chart` | Selection/filter events through explicit visualization capabilities |

Widget options use keyed arguments and call metadata, not toolkit constructors. The primitive
subset is text, button, checkbox, input, select, number and progress; the following extended
contracts add every non-future family above. JavaFX may implement only that subset
plus basic row/column layout and dialogs, reporting all other required types as unsupported.
Toolkits may contribute advanced namespaced widgets with schemas and event contracts.
Native ObservationCard, GeometryCard, SemanticComposer, provenance and system views belong to the
separate [k.LAB component contract](UI_KLAB_COMPONENTS.md). Simplified `ui.observation` and
`ui.provenance` verbs are not specified by this initial contract.

### Extended widget options (Quasar)

Every new widget verb takes one positional string: its accessible label. For `markdown`, `badge`
and `icon`, that string is also the initial content/name. `separator("")` needs no visible label.
Existing shared layout, visibility, disabled, class and help options continue to apply. Interactive
widgets support `readonly`; it blocks server input as well as client editing. Option strings are
stable IDs and currently also their displayed labels. `ui.set` updates canonical `value` without
emitting input; `ui.style` can update validated configuration such as `items`, `options` or `src`.

| Verbs/options | Configuration and defaults | Emitted `{event, value}` |
| --- | --- | --- |
| `iconbutton`, `link` | Button options; `iconbutton` displays `icon` with accessible label; `link` is flat and optionally accepts `href` | `click`, null; authored navigation opens a separate tab |
| `switch` | Checkbox options, `value=false` | `change`, boolean |
| `radio`, `multiselect` | Required `options`: distinct strings, at most 10000. Radio value defaults to empty string; multiselect to empty list. `required`, `dense`, `color`; multiselect also `hint`, `error`, `outlined` | `change`, one ID or a distinct list of IDs; server rejects undeclared selections |
| `textarea` | Same options as `input`; multiline presentation | `change`, string |
| `slider`, `range` | `min=0`, `max=100`, positive `step=1`; slider defaults to min; range to `{min: min, max: max}` | `change` on release, number or `{min, max}`; finite bounds and ordered endpoints checked on server |
| `date`, `time`, `color` | `value=""`, `required`; date uses `YYYY-MM-DD`, time `HH:mm` (24-hour), color `#RRGGBB`. Date/time accept input help/debounce options | `change`, string; calendar dates, time and color format checked on server |
| Group `+form`, widget `:field "name"` | A tagged group owns descendant fields. Field names are ASCII identifiers, at most 80 characters. Forms cannot nest. Disabled/hidden fields are excluded | No group emitter; `submit`/`reset` provide form events |
| `submit`, `reset` | Button options; nearest ancestor form by default. `:form "profile"` or `:form "/main/profile"` addresses a tagged form explicitly | Client sends null. Server emits `submit` with validated canonical field map, or `reset` with restored authored-default map. Duplicate fields or invalid required values reject submission; required fields may be cleared while editing a form |
| `list`, `table`, `tree` | Required `items`: list of maps with unique nonempty string `id` and string `label`; other cell values may be included. Tree maps optionally contain `children`. At most 10000 IDs, nesting at most 32. `value=""` is selected ID | `select`, ID; `activate`, ID (double click or Enter on a list/tree item or selected table row) |
| `table` | Required `columns`: distinct cell property names (1–100); `pagesize=10` (1–1000), `page=1`, optional `sort` column and `descending=false`. Quasar sorts/pages the authored rows locally | Also `sort`, `{column, descending}` and `page`, integer; column/page validated and retained on server |
| `tree` | `expanded`: list of node IDs, default empty | Also `expand`, complete expanded-ID list; selection and expansion retained |
| `tabs`, `stepper`, `breadcrumbs` | Required distinct string `options`, `value=""`; set an initial option for a selected tab/step. These are navigation controls; reactions show/hide or mount associated content | `change`, destination ID |
| `pagination` | Required integer `max` (1–10000), `min=1`, `value=min` | `change`, integer page within bounds |
| `markdown` | Positional text, later `value` through `set`; safe subset: headings, emphasis, inline code, bullets and links. Raw HTML is escaped | Passive |
| `image` | Required `src`; `alt` defaults to label | Passive |
| `icon`, `separator`, `badge`, `spinner` | `icon` uses `icon` or positional name; badge uses label or later value; `color`, supported `size` where applicable | Passive; spinner exposes status label |
| `upload` | `accept` comma-separated MIME types, MIME wildcards or extensions; `maxbytes=4194304` (1–4194304); `hint`, `outlined`. Single-file picker | `change`, `{id, name, mediaType, size}` after successful HTTP transfer; bound to originating widget and instance |
| `download` | `value` is a handle from upload or `ui.file`; disabled until populated. Button options | `click`, null after the authorized download starts |
| `audio`, `video` | Required `src`; `controls=true`, `loop=false`, `muted=false`. No autoplay. Native browser playback controls inside the Quasar renderer | `play`, `pause`, `ended`, with current time in seconds |

`src`, `href` and Markdown links accept HTTPS URLs or relative URLs. Protocol-relative URLs,
credentials, control characters, JavaScript, data and local-file URLs are rejected. Media and images
are browser-fetched resources: protected service resources requiring bearer headers need an
application-specific delivery mechanism. The server does not fetch these URLs. Markdown is a
restricted dialect, not full CommonMark. Raw HTML and executable extensions are never evaluated.

Files use authenticated HTTP, with no file bytes in the UI snapshot or WebSocket input.
`POST /api/v1/ui/instances/{id}/files/{nodeId}?name=...&mediaType=...` accepts a raw
`application/octet-stream` body. The owner and project READ are checked, then the live upload widget's
visibility, read-only state, type filter and size limit. `GET .../{id}/files/{handle}` returns JSON
`{name, mediaType, content}` with base64 content; the built-in renderer creates a temporary download
Blob. Responses use `no-store`. Downloads use attachment semantics even for HTML or SVG content.
A handle grants no cross-instance access and names never resolve to server paths.

Each instance retains at most 16 files and 16 MiB total; each file is at most 4 MiB. Files remain
available through reconnect and are released on application stop/expiry; uploaded files are also
released when their originating widget is removed. The initial store is memory-only. `accept`
checks the claimed MIME type and filename, not file contents: reactions must validate domain data.
`ui.file(name, text, mediaType)` creates a UTF-8 text file and returns its opaque handle map;
`ui.readfile(id)` returns a defensive byte-array copy to server-side actor code. Neither reads an
arbitrary path. Use an explicit new application instance after exhausting this initial bounded store.

### Worked examples

The complete [extended-widget application](examples/ui/widgets.kactor) combines these examples and
is parsed and executed by `UiWidgetCatalogTest`. The media URLs there are illustrative resources;
replace them with published HTTPS media. See the existing [launch setup](examples/ui/README.md).

#### A form with validation and reset

```kactors
(
    ui.input("Name" :field "name" :value "Ada" +required :debounce 0) #name
    ui.multiselect("Regions" :field "regions" :options ("eu", "af", "as")) #regions
    ui.range("Interval" :field "interval" :min 0 :max 100 :value {min: 10, max: 80}) #interval
    ui.submit("Save"):
        event -> ui.set("summary", [event.value.toString()])
    ui.reset("Reset")
) +form :layout "column" :gap "12px" #profile
```

Create `ui.text("Ready") #summary` before this group. A valid Save reaction receives, for example,
`{event: "submit", value: {name: "Ada", regions: ["eu"], interval: {min: 10, max: 80}}}`.
The client cannot supply that map; the server builds it from retained field values. Reset restores
`Ada`, an empty regions list and the authored range, without emitting individual field changes.
Use `ui.submit("Save profile" :form "profile")` outside the group to submit the same form.
`debounce=0` is useful when every keystroke should be committed before an immediate submission.
Business validation belongs in the submit reaction; use authored `error` messages for feedback.

#### Upload, inspect and offer a download

```kactors
ui.upload("Upload CSV" :accept ".csv,text/csv" :maxbytes 1048576):
    event -> ui.set("copy", [event.value])
ui.download("Download uploaded file") #copy
ui.download("Download generated report") #report
ui.set("report", ui.file("report.csv", "id,value\nsample,42\n", "text/csv"))
```

The upload reaction receives a handle map, for example
`{event: "change", value: {id: "opaque-id", name: "data.csv", mediaType: "text/csv", size: 512}}`.
`ui.set("copy", [event.value])` makes the authorized uploaded bytes downloadable. Server-side
processing can call `ui.readfile([event.value.id])` to inspect the bytes before accepting them.
The generated report follows the same access checks and lifetime as an uploaded file. Neither the
browser nor k.Actors source supplies a server filesystem path.

#### Stable IDs, tables and trees

```kactors
ui.table("Measurements" :columns ("label", "amount") :pagesize 5
    :items ({id: "a", label: "Alpha", amount: 10}, {id: "b", label: "Beta", amount: 20})) #measurements
ui.tree("Hierarchy"
    :items ({id: "root", label: "Root", children: ({id: "leaf", label: "Leaf"})})
    :expanded ("root")) #hierarchy
```

Selecting Alpha emits `{event: "select", value: "a"}`. Sorting emits, for example,
`{event: "sort", value: {column: "amount", descending: false}}`; opening a tree branch emits the
complete expanded-ID list. Replace a collection through
`ui.style("measurements", {items: (...), value: "a"})`, keeping a valid selected ID, sort and page.
Display labels may change independently of identity. The initial table loads authored rows in one
snapshot; the protocol's 256 KiB snapshot limit still applies. Remote/virtualized datasets remain a later extension.

#### Navigation and media

```kactors
ui.tabs("Sections" :options ("overview", "details") :value "overview"):
    event -> ui.set("summary", [event.value])
ui.stepper("Workflow" :options ("prepare", "review", "finish") :value "prepare") #steps
ui.pagination("Page" :max 20 :value 1) #page
ui.audio("Recording" :src "https://example.org/recording.mp3") #recording
```

Tabs and steps emit stable IDs; pagination emits an integer. Mount or show the corresponding
content in the reaction. Playback emits `{event: "pause", value: 12.5}` for a pause at 12.5 seconds;
playback position is transient and is not automatically sought/replayed on reconnect.

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
digests and Resources-catalog publication remain follow-up work. Both published URNs and classpath aliases
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

`UiToolkit` remains the Java rendering SPI for in-process adapters. Installed browser toolkits
contribute `UiToolkitDescriptor` objects through `KlabComponent.uiToolkits()` and declare their ESM
renderer in `webUiModules()`. Set the per-service Spring property `klab.ui.toolkit` (default `quasar`)
to select a toolkit for **new** applications. Existing instances keep their selection across
disconnect/reconnect. Application/client-controlled overrides are not enabled.

```java
@Override
public List<UiToolkitDescriptor> uiToolkits() {
  return List.of(new UiToolkitDescriptor("my-toolkit", 1, Set.of("web"),
      Set.of("container", "text", "button", "input"), Set.of("row", "column"), "my-ui-renderer"));
}

@Override
public Map<String, String> webUiModules() {
  return Map.of("my-ui-renderer", "renderer.js");
}
```

The prebuilt `META-INF/klab/webui/renderer.js` default-exports a Vue component receiving `snapshot`,
`disabled` and the existing dashboard `context`. It emits `input` with `{nodeId,name,value}`;
the host adds protocol revision/sequence, authentication and transport. Import shared Vue APIs
from `@klab/webui` (or externalized `vue`), using the existing host import map. The renderer owns
its scaffolding, accessibility and toolkit dependencies; `disabled` must block offline input.
On unmount, release listeners/timers. The browser regression exercises actual dynamic ESM loading.

Only registered, STARTED components contribute. Unknown/duplicate toolkit IDs, incompatible protocol
versions, undeclared modules and unsupported nodes/layouts fail explicitly. Core input schemas and
the server state owner remain authoritative across toolkit choices. The first descriptor supports
live protocol version 1 with the primitive and extended types above and row/column layouts; custom native widget
schemas and source exporters remain separate future contracts. Descriptors are immutable snapshots.
An application lease retains the contributing component version while disconnected too. Unload,
remove and hot replacement refuse while any such lease exists; stopping/expiring the last instance
releases it. Rebuild/restart the host once to obtain this extension hook; subsequent renderer
components install through the component mechanism without rebuilding the host.
Publish changed renderer assets under a new component version: browser module URLs and imports
are versioned, and same-version renderer cache invalidation is not implemented yet.

Quasar mounts the built-in recursive renderer in the existing shell. JavaFX consumes the same
tree/events through an adapter provided outside
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
| 4 — reusable components and toolkit installation (implemented initial contract) | Component descriptors/configuration/version leases; validated flex/widget metadata; tagged named-action mounts including nested imports | Installed browser module loads without rebuilding host; duplicate/unsupported features fail explicitly; independent component instances and binding disposal tested; component unload blocked until leases close |
| 4a — extended portable widget catalog (implemented) | All non-future families above; form/field options, authoritative values, bounded HTTP file handles and Quasar controls | Parsed example and headless form/schema/file tests; browser catalog, transfers and existing reconnect workflow |
| 4b — addressable widgets and fluent operations (implemented) | Construction references separate from widget emitters; event subscriptions, checked disposal and bounded UI file references | Parsed button/file workflows, independent subscriber lifetimes, stale-reference errors, defensive byte copies and cross-instance rejection |
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
mvn -pl klab.core.services -am "-Dtest=UiWidgetHandleTest,UiWidgetCatalogTest,ApplicationUiControllerTest,ApplicationUiSessionTest,ApplicationUiRuntimeTest,ApplicationUiManagerTest,ServiceUiApplicationLauncherTest,ApplicationUiWebSocketTest,ComponentRegistryActorVerbTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Run `npm run typecheck`, `npm run build` and `npm run test:application` in
`klab.core.services/src/main/webui`. Set `KLAB_TEST_BROWSER_CHANNEL=msedge` to use installed Edge.
The browser fixture uses controlled authentication/transport responses; the Java socket test uses
a real embedded server. A deployed Runtime + Resources + hub sign-in smoke test remains a separate
live-stack check. No live service is restarted by these tests.

The `core.ui` catalog includes the primitive and extended widget tables above, get, set,
show, hide, enable, disable, close, remove, clear, style, mount, widget, file and readfile.
Factories and `widget(tag)` return `core.widget` message handles as documented above. Forms and fields
use options on existing groups/widgets. Only map/chart remain future-only widget verbs. Surface actions support header/footer/drawer,
panel/window, dialog/modal and the other named surface containers above within application scopes.
Initial header/footer/drawer actions run before main, after construction/init. Invoking an annotated
action currently replaces its mount and emitter bindings; showing a retained mount does not rerun
it. Repeated instances use `ui.mount` and explicit removal. Dialog animations map `none/fade/slide/scale`
and `duration` to Quasar transitions; other surface animations are not implemented. Advanced layout
families, full Markdown, remote collections, durable/streamed file stores, complete surface annotation options, ordinary singleton replacement rollback, native
widget schemas, application publication digests and hosting external live agent interfaces remain
follow-up instrumentation. Dynamic component construction rolls back only its UI subtree.
