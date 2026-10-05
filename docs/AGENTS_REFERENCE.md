# Core k.Actors agent reference

This reference documents every registered actor in
[CoreActorLibrary](../klab.core.services/src/main/java/org/integratedmodelling/klab/runtime/libraries/CoreActorLibrary.java).
Use [the language guide](AGENTS.md) for syntax, matching, concurrency, scope, and running
conventions. Actor names and verbs below are public k.Actors names, which can differ from Java
method names. Java injects `RuntimeAgent.Scope`; callers do not pass it as an argument.

Import a core actor using `core.<name> as <alias>`. Static verbs are called directly on the alias.
Instance verbs require a value returned by `new`, `current`, `wrap`, or another factory. Functions
return synchronously; suppliers supply one eventual value and support match blocks; emitters can
fire repeatedly until their call scope ends. Capturing a supplier with `<-` waits for its result.

| Agent | Purpose | Calling convention |
| --- | --- | --- |
| [core.agent](#coreagent) | Universal identity, construction and messaging | Implicitly inherited by behaviors; instance verbs except `duration` |
| [core.console](#coreconsole) | Agent stdout and stderr | Static functions |
| [core.document](#coredocument-and-document-subclasses) | Common project document contract | `wrap(document)`, then instance functions |
| [core.ontology](#coredocument-and-document-subclasses) | Ontology document | Inherits document functions; adds `domain` |
| [core.namespace](#coredocument-and-document-subclasses) | Model namespace | Inherits document functions; adds `scenario` |
| [core.strategy_document](#coredocument-and-document-subclasses) | Observation strategy document | Inherits document functions; adds `coverage` |
| [core.behavior_document](#coredocument-and-document-subclasses) | k.Actors source document | Inherits document functions; adds `category` |
| [core.project](#coreproject) | Project documents, settings and additional material | `wrap(project)`, then permission-checked functions |
| [core.context](#corecontext) | Digital twin access and observation submission | Static factories, then instance verbs |
| [core.email](#coreemail) | Service-configured outgoing email | Static checks and send suppliers |
| [core.file](#corefile) | Local file and directory operations | Static functions and bound path instances |
| [core.inspector](#coreinspector) | Asset, graph and storage checks | Static functions |
| [core.log](#corelog) | Scope-aware logging | Static functions |
| [core.strings](#corestrings) | String operations | Static functions |
| [core.timer](#coretimer) | Wall-clock scheduling | Static suppliers and emitters |
| [core.url](#coreurl) | URL inspection, HTTP requests and downloads | Static functions/suppliers and bound URL instances |

Actors supplied by other components have their own catalogs; an illustrative import in a language
example does not establish that the actor is shipped in this library. Static and instance verbs
have distinct names because the compiler's actor catalog indexes each verb by name.

## core.document and document subclasses

These agents wrap the four `KlabDocument` interfaces: `KimOntology`, `KimNamespace`,
`KimObservationStrategyDocument`, and `KActorsBehavior`. `core.document` is their common ancestor;
each subclass exposes all inherited verbs. The behavior-document wrapper represents source code,
not a running instance of that behavior. Applications, scripts, tests and components use the same
wrapper and retain their knowledge class.

All calls are functions. `wrap(document)` accepts a document bean and returns the corresponding
subclass. Each subclass also offers `wrap`, restricted to its own document type. Wrapping records
coordinates; it does not grant authority or capture a service session.

| Verb | Result and contract |
| --- | --- |
| `urn()` | Stable language URN |
| `kind()` | Document knowledge-class name |
| `project()` | Containing `core.project` handle |
| `read()` | Fresh document bean, resolved with the participant's READ permission |
| `source()` | Current source code |
| `version()` | Authored version as text |
| `statements()` | Current ordered statements |
| `notifications()` | Current validation notifications |
| `imports()` | Imported document namespace URNs |
| `update(source)` | Submit replacement source using UPDATE |
| `delete()` | Delete through the Resources API |
| `domain()` | Ontology only: domain concept |
| `scenario()` | Namespace only: scenario flag |
| `coverage()` | Strategy document only: coverage specification |
| `category()` | Behavior document only: k.Actors category name |

Read operations resolve afresh, so an existing handle sees edits and rechecks access. A missing
document or one resolved in a different project fails explicitly. Document update/delete retain
the Resources API's project-lock requirements. Save failures returned as error notifications
become exceptions in these actors.

## core.project

`wrap(project)` returns a handle to an existing k.LAB project. Functions use the services available
to the invoking user, retaining the original service ID where known. No credentials, permission
snapshot, service instance, or project bean are retained in the handle.

| Verb | Contract |
| --- | --- |
| `urn()` | Project name |
| `read()` | Current project bean; requires READ |
| `permissions()` | Current participant's effective project privileges |
| `documents()` | Handles for the project's current document collections; requires READ |
| `document(kind, urn)` | Resolve a document handle in this project; requires READ |
| `create_document(kind, urn, source)` | ADD a document; requires CREATE |
| `update_document(kind, urn, source)` | UPDATE a document; requires UPDATE and the existing edit lock |
| `delete_document(kind, urn)` | Delete a document; requires DELETE and the existing edit lock |
| `settings()` | Current project settings; requires READ |
| `update_settings(workspace, settings)` | Settings-only project REPLACE; requires UPDATE and the edit lock; existing administrator restrictions still apply |
| `lock()` / `unlock()` | Acquire/release the caller's existing project editing lock; requires UPDATE |
| `delete()` | Delete the project through the API; requires DELETE |
| `material(path)` | Additional material bytes, or null when absent; requires READ or UPDATE_METADATA |
| `write_material(path, bytes)` | CREATE_OR_UPDATE binary additional material; requires UPDATE_METADATA |
| `write_text(path, text)` | Same operation with UTF-8 text |
| `delete_material(path)` | Delete additional material; requires DELETE |

`kind` uses the API knowledge-class names: `ONTOLOGY`, `NAMESPACE`,
`OBSERVATION_STRATEGY_DOCUMENT`, `BEHAVIOR`, `APPLICATION`, `SCRIPT`, `TESTCASE`, or `COMPONENT`.
Document URNs are unqualified language URNs. Additional material uses canonical, slash-separated
project-relative paths, including its filename and extension. Parent directories are created as
needed. Binary payloads are limited to 32 MiB. Traversal, absolute paths, symlinks, canonical
document locations, `META-INF`, managed `resources`, and Git control paths are rejected.

Every operation checks both the participant's service privilege and access to the project.
Administrators retain their existing override. UPDATE_METADATA alone grants no document CRUD
or settings editing and cannot delete additional material. Material changes do not require an
UPDATE lock, but respect a lock held by a different user. On service-owned file projects they are
staged in Git without creating a commit, pushing, or staging unrelated paths. Other storage
types fail explicitly. See [the material API](RESOURCES.md#additional-project-material).

### Workflow target binding and restoration

Workflow `init` and later actions can request `document`, its applicable subtype name (`ontology`,
`namespace`, `strategy_document`, `behavior_document`), and `project`. For a document target, both
the document and containing project are bound. A project target binds `project`. Other target
types do not invent a containing document/project. Types can also select a unique wrapper.

```kactors
behavior example.review.instrumentation

action init(document, project):
    def target_document document
    def target_project project

action prepare:
    target_project.write_text("review/README.md", "Submission prepared for review")
```

These handles are exceptions to the workflow prohibition on storing live Java objects in globals:
their checkpoints contain only a versioned reference with URN, project, kind and service ID.
On resumption the wrapper subtype is restored and its operations resolve current data using the
**current participant**, even though the workflow behavior itself was resolved in its owner's
scope. Revocation takes effect on subsequent operations. The checkpoint map key
`$klabProjectActor` is reserved for this reference codec. Arbitrary returned document beans,
statements, settings and service objects are still not portable globals.

Project changes are external effects of a workflow action: a later failed workflow checkpoint
does not roll back a project write. Prefer idempotent `write_text`/`write_material` operations
when retrying actions. Workflow attachments remain separate from project additional material.

## core.agent

Every k.Actors behavior implicitly inherits this contract without an `inherits` or `using` clause.
The compiler binds instance verbs to the actual runtime agent or agent handle. They are ordinary
inherited actions and may be overridden; use `@override` to acknowledge the replacement.

| Verb | Type | Contract |
| --- | --- | --- |
| `new(arguments...)` | Function | On an imported behavior specification, construct an instance and pass arguments to `init`. On a Java specification, use its static `new` factory or compatible public constructor. The universal base implementation on an existing instance is invalid. |
| `tell(class, payload)` | Function, no result | Send a custom message identified by a constant, with one serializable payload. Returns immediately. |
| `ask(class, payload :timeout quantity)` | Supplier | Send a correlated request and supply its response. Default timeout is 30 seconds; `!timeout` disables the deadline. Handler failure completes the request exceptionally. |
| `name()` | Function → String | Non-unique display name of the recipient. |
| `urn()` | Function → String | Runtime-wide unique instance URN of the recipient. |
| `duration(quantity)` | Static function → TimeDuration | Convert a temporal quantity to a `TimeDuration`; a null quantity is invalid. |

```kactors
worker <- tools.new(configuration)
worker.tell(RELOAD, configuration)
result <- worker.ask(LOOKUP, key :timeout 10.s)
worker.ask(WAIT_FOR_EVENT, key !timeout):
    response -> process(response)
console.println(worker.name(), " ", worker.urn())
```

`tools` and `console` in this fragment must be imported by the enclosing behavior. Messaging
requires a connected channel in the agent's creation/reconnection scope. Agent creation still
succeeds without one and reports that messaging is disabled. A function or supplier `@handle`
action replies with its result. Emitter handlers can explicitly reply through their `sender`
handle. See [agent messages](AGENTS.md#43-agent-messages-and-handle) for handler binding and
serialization rules. `duration` is static and can be used through `core.agent as agent`.

## core.console

Import `core.console as console`. All verbs are functions with no result.

| Verb | Effect |
| --- | --- |
| `print(values...)` | Concatenate values on stdout without adding a newline. |
| `println(values...)` | The same, followed by the platform newline. |
| `format(pattern, values...)`, `printf(pattern, values...)` | Java `String.format` output on stdout; use `%n` to request a newline. |
| `error(values...)`, `errorln(values...)` | Concatenated stderr output, without/with a newline. |
| `errorf(pattern, values...)` | Java format string output on stderr. |
| `flush()` | Flush the local fallback writer. |

Values use `String.valueOf`, so null renders as `null`; no spaces are inserted automatically.
Output goes to attached agent consoles, or to the scope print writer for stdout and `System.err`
for stderr when no console receives it. Testcases also retain output under the producing action.
Invalid format strings raise the normal Java formatting exception.

## core.context

Import `core.context as context`. A context actor wraps a `ContextScope`; borrowing or focusing a
context does not create a second twin or take cleanup ownership. The detailed query, focus, and
persistence contract is in [Digital twins](DIGITALTWINS.md#the-corecontext-kactors-actor).

| Verb | Type | Contract |
| --- | --- | --- |
| `new(arguments... :name text :description text :persistence policy)` | Static function → core.context | Create a context from a session-scoped agent. Accepts a positional name and `Persistence`, or named creation metadata. Default name is `Unnamed context`; default persistence is `ONE_OFF`. Testcase contexts are registered for cleanup. |
| `current()` | Static function → core.context | Borrow the calling agent's context; fails if absent. |
| `wrap(contextScope)` | Static function → core.context | Borrow an existing, non-null `ContextScope`. |
| `focus(:within observation)` or `focus(:source observation :target observation)` | Instance function → core.context | Return a focused proxy. Unknown focus keys are errors. |
| `scope()` | Instance function → ContextScope | Underlying context scope. |
| `twin()`, `graph()`, `scheduler()`, `storagemanager()` | Instance functions | Underlying twin, knowledge graph, scheduler, and storage manager. |
| `timeline()` | Instance function → Map | Immutable snapshot of scheduler `epochStart`, `epochEnd`, and `resolution`; does not advance time. |
| `storage(observation)` | Instance function → Storage | Retrieve existing observation storage; backend failures propagate. |
| `members(cohort :limit n :offset n)` | Instance function → List | Snapshot of direct `HAS_MEMBER` observations; only limit and offset metadata are accepted. |
| `query(arguments...)` | Instance function → asset or List | Query by ID, URN, knowledge class, or observation semantics. Supports `:within`, `:source`, `:target`, `:along`, `:limit`, `:offset`, `:depth`, and `+all`. |
| `submit(arguments...)` | Instance supplier → Observation | Build and submit an observation in this twin, with calling-agent provenance. Accepts observable/concept semantics, identity URN, geometry, definition map, focus metadata, and `:namespace`/`:project` resolution constraints. |
| `close()` | Instance function, no result | Close the underlying context according to its persistence policy; affects all proxies. |

```kactors
ctx <- context.new("Email demo context")
result <- ctx.submit({{geography:Elevation in m}})
```

Use an instance for `query` and `submit`; the imported class alias alone has no context. Context
creation is restricted to session scope. Construction, submission, and queries can fail on invalid
arguments, absent services, inaccessible assets, or backend errors. Proposed event emitters and
context composition in the digital twin guide are not implemented verbs.

## core.email

Import `core.email as email`. All verbs are static; no `new` is needed. The actor uses the
`EmailManager` of the exact `BaseService` hosting the calling agent's service user, session, or
context scope. It never chooses a connected peer's mail configuration. A scope without a local
service-owned manager has unavailable email, which is an ordinary condition.

| Verb | Type | Contract |
| --- | --- | --- |
| `configured()` | Function → Boolean | True when the host service has enabled and complete email configuration. Does not connect to SMTP. |
| `status()` | Function → Map | Credential-free configuration snapshot described below. Does not connect to SMTP. |
| `send(to, subject, body)` | Supplier → Boolean | Send one UTF-8 plain text message from the configured sender. |
| `sendhtml(to, subject, body)` | Supplier → Boolean | Send one UTF-8 HTML message from the configured sender. The caller supplies the HTML. |

`status()` returns exactly these keys:

| Key | Type | Meaning |
| --- | --- | --- |
| `available` | Boolean | A local service-owned email manager exists in this scope. |
| `enabled` | Boolean | Effective `EMAIL_ENABLED`; false if unavailable. |
| `configured` | Boolean | Email is available, enabled, and all required settings are valid. |
| `missing` | List of String | Missing or invalid setting enum names, for example `EMAIL_SMTP_HOST` or `EMAIL_PASSWORD`. Empty when unavailable. May be nonempty while disabled. |

Neither status verb exposes SMTP hosts, addresses, usernames, passwords, or setting values. SMTP
and sender configuration is exclusively administrative, through the service setting API or its
dashboard Email tab. See [service email](SERVICES.md#outgoing-email) for all settings and defaults.

Both send verbs accept three strings: one recipient address, a single-line subject, and a body.
They run blocking SMTP work on a virtual thread and supply exactly one result:

- `true`: SMTP sending completed successfully. This means server acceptance, not confirmed inbox delivery.
- `false`: email is unavailable, disabled, or incompletely configured; no message was sent.
- `exception`: enabled and configured sending failed, or the message was invalid. Invalid message
  arguments raise `KlabIllegalArgumentException`; SMTP failures raise `KlabIOException`.

```kactors
script examples.email
    "Send a completion notice using service email."
    version 1.0
    using
        core.email as email,
        core.console as console

action main:
    email.send("recipient@example.org", "Observation complete", "The observation is ready."):
        true -> console.println("Message accepted by SMTP")
        false -> console.println("Email is unavailable or unconfigured")
        exception -> console.errorln("Email sending failed")
```

For a synchronous workflow, `sent <- email.send(...)` waits for completion. `configured()` is a
snapshot, not a guarantee that a subsequent send will succeed: settings can change and SMTP may
fail. The manager rereads settings for every send. There is no automatic retry, mail queue,
recipient-list/CC/BCC interface, attachment support, per-message sender override, or actor verb to
change credentials. Stopping an agent or cancelling a listener cannot recall an already sent mail.

## core.file

Import `core.file as file`. All verbs are synchronous functions. Static operations accept a path
on each call; `file.new(path)` returns a `core.file` handle bound to one normalized absolute path.
Construction does not create, open, or require the file to exist. Handles are immutable path
references, with no open stream or `close` requirement.

| Static verb | Bound instance equivalent | Result and contract |
| --- | --- | --- |
| `new(path)` | — | core.file handle for a String path or `file:` URI. |
| `inspect(path)` | `info()` | Map of filesystem metadata described below. |
| `exists(path)` | `present()` | Boolean. Whether the path currently exists. |
| `isfile(path)`, `isdirectory(path)` | `info()` fields `file`, `directory` | Boolean. Regular-file/directory checks. |
| `size(path)` | `info()` field `size` | Long. Size in bytes; static size propagates missing-path/backend failures. |
| `read(path)` | `text()` | String. Read the complete file as UTF-8 text. |
| `readbytes(path)` | `bytes()` | Java byte array. Read the complete file without decoding. |
| `write(path, text)` | `save(text)` | String path. Create or truncate the file and write UTF-8 text. |
| `writebytes(path, bytes)` | `savebytes(bytes)` | String path. Create or truncate and write a Java byte array, e.g. a result from `readbytes`. |
| `append(path, text)` | `appendtext(text)` | String path. Append UTF-8 text, creating the file if absent. No newline is inserted. |
| `mkdir(path)` | `makedirectories()` | String path. Create the directory and missing parent directories; an existing directory is accepted. |
| `list(path)` | `entries()` | Sorted List of normalized absolute paths for direct directory entries; includes files and directories, without recursive traversal. |
| `copy(source, destination)` | `copyto(destination)` | Static: destination String path. Instance: core.file destination handle. Copy one path without replacing an existing destination. |
| `move(source, destination)` | `moveto(destination)` | Static: destination String path. Instance: core.file destination handle. Move one path without replacing an existing destination. |
| `delete(path)` | `remove()` | Boolean. Delete a file or empty directory; false if already absent. Nonempty directories raise an error. |
| `temp(prefix)` | — | core.file handle for a newly created empty `.tmp` file in the JVM temporary directory. Prefix must have at least three characters. Caller owns deletion. |

Additional instance verbs: `path()` returns the bound absolute path, `parent()` returns a core.file
handle for its parent (null at a filesystem root), and `child(relative)` returns a core.file handle
for a relative child path. An absolute child argument is invalid; `..` segments are normalized and
can address a parent. These are path resolution operations, not filesystem confinement rules.

`info()`/`inspect()` returns `path` and `name` strings; `exists`, `file`, `directory`, `readable`,
and `writable` booleans; `size` in bytes for regular files (0 otherwise); and `modified` as epoch
milliseconds (null when absent). Metadata is a snapshot, not a lock against later filesystem changes.

Relative paths resolve against the service JVM's working directory; they are not automatically
relative to a project, workspace, or k.LAB work directory. Operations use the service process's
filesystem permissions. Checks follow the usual Java NIO link behavior; handles normalize paths
lexically rather than resolving them with `toRealPath`. Copy and move use Java NIO defaults,
including their symlink behavior, and directory copy is not recursive. A moved handle continues
to refer to the original path: use the handle returned by `moveto` for further access.

Writes, copies, moves, and downloads do not create missing parent directories; use `mkdir` first.
They are not transactionally atomic operations. Invalid path or null content arguments raise
`KlabIllegalArgumentException`; filesystem I/O failures raise `KlabIOException`. Missing paths are
ordinary false values only for existence checks and deletion, and appear as absent in metadata;
reading a missing file is an error. Local reads load the complete file into memory.

```kactors
script examples.files
    "Write and read a local report."
    version 1.0
    using core.file as file, core.console as console

action main:
    file.mkdir("reports")
    report <- file.new("reports/result.txt")
    report.save("Observation complete")
    report.appendtext("\n")
    console.println(report.text())
    snapshot <- report.copyto("reports/result-copy.txt")
    console.println(file.read(snapshot.path()))
```

## core.inspector

Import `core.inspector as inspector`. Every verb is a static function. Checks use captured evidence;
missing or unsupported evidence is not silently accepted. These predicates do not record an
assertion by themselves; use `assert inspector.<check>(...)` in a testcase. See
[Testing](TESTING.md) for data policies, snapshot limitations, and storage regression examples.

| Verb | Result and contract |
| --- | --- |
| `viable(assets... metadata)` | Boolean. Check all supported assets; print joined failure reasons through `core.console`. |
| `problems(assets... metadata)` | List of failure-reason strings using the same viability policy, without printing or recording assertions. |
| `present(asset)` | Boolean. True for any non-null captured value; does not assess viability. Argument is optional. |
| `resolved(observation, minimum)` | Boolean. Nonempty observation with finite positive coverage, at most 1 and at least minimum. Optional minimum defaults to 0 and must be in 0..1. |
| `hasdata(asset)` | Boolean. At least one valid value in scalar data or captured histograms. |
| `nodata(asset)` | Boolean. Nonempty evidence consisting entirely of no-data. |
| `complete(asset)` | Boolean. Valid data with zero missing values in every captured slice. |
| `inrange(asset, minimum, maximum)` | Boolean. Inclusive finite numeric bounds on scalar values or all captured histogram slices. Missing values are ignored; combine with `complete`. |
| `metadata(observation, key, expected)` | Boolean. The metadata key exists and is deeply equal to expected; expected is optional and may be null. |
| `graph(context)` | KnowledgeGraph. Accept a context actor/scope, digital twin, or knowledge graph. |
| `contains(context, asset)` | Boolean. Committed membership by runtime asset, positive integer ID, String URN, or `Urn`; null asset returns false. |
| `linked(context, source, target, relationship)` | Boolean. Directed relationship check including transaction-local links; null endpoints return false. |
| `acyclic(graph)` | Boolean. Captured directed JGraphT graph has no cycles. Empty graphs pass, null returns false, undirected graphs are invalid. |
| `storage(context, observation)` | Storage. Retrieve existing storage; null observation returns null, backend failures propagate. |
| `vertices(graph)`, `edges(graph)` | Integer. Counts in a captured JGraphT graph; null is invalid. |
| `scancheck(context, observation, curve, splits, samples)` | Boolean. Compare bounded samples across partitioned, export and indexed text reads. Splits: 1..256; samples per partition: 1..64; use a supported `Data.FillCurve` constant. |
| `unitcheck(context, observation, unit, factor, offset)` | Boolean. Compare a lazy unit view with an independent expected multiplier and offset across storage read routes. |
| `celltext(context, observation, curve, offset)` | String. Export-view cell at a zero-based traversal offset, preserving exact longs and missingness. |

`context` in membership, relationship, and storage verbs is a context actor or `ContextScope`.
Viability accepts observations, activities, dataflows, actuators, contexts/twins/knowledge graphs,
storage/shards/histograms, geometries, captured JGraphT graphs, and collections of supported assets.
It checks type-specific structure, notifications, resolved coverage, and data evidence. Empty
collections, cyclic structures, null, or unsupported types produce problems.

Viability metadata accepts only `data`, `nodata`, `resolved` (booleans) and `mincoverage` (finite
0..1). `+data` requires some valid data, `+nodata` requires all no-data, and `!nodata` requires
complete data. The historical `!resolved` flag explicitly requires resolution even for
substantial observations. Invalid policies and backend access failures raise exceptions.

## core.log

Import `core.log as log`. Static functions `info(values...)`, `warning(values...)`,
`error(values...)`, and `debug(values...)` forward their arguments to the calling scope's
corresponding notification/logging method. Without an underlying k.LAB scope they use
`Logging.INSTANCE`. They have no result. These are scope notifications/log entries; use
`core.console` for agent console stdout/stderr. No log subscription emitter is currently exposed.

## core.strings

Import `core.strings as strings`. All verbs are static functions and can be nested in other calls.
Search and replacement are literal except for `matches`, which uses Java regular expressions.

| Verb | Result and contract |
| --- | --- |
| `lowercase(text)`, `uppercase(text)` | String. Locale-independent case conversion using `Locale.ROOT`; null stays null. |
| `capitalize(text)` | String. Capitalize the first character using the shared string utility. |
| `labelize(identifier)` | String. Turn an identifier into a readable label; null stays null. |
| `trim(text)` | String. Strip surrounding Unicode whitespace; null stays null. |
| `normalize(text)` | String. Strip ends and replace internal whitespace with single spaces; null stays null. |
| `length(text)` | Integer. String length; null counts as zero. |
| `isempty(text)` | Boolean. True for null or zero-length text; whitespace alone is not empty. Argument is optional. |
| `contains(text, fragment)`, `startswith(text, prefix)`, `endswith(text, suffix)` | Boolean. Literal tests; either null argument yields false. |
| `equalsignorecase(text, other)` | Boolean. Case-insensitive equality; two nulls compare equal. |
| `indexof(text, fragment)` | Integer. First literal index or -1, including for null arguments. |
| `count(text, fragment)` | Integer. Non-overlapping literal occurrence count; null or empty inputs count as zero. |
| `matches(text, regex)` | Boolean. Full-string Java regex match; null arguments return false, invalid regex raises an exception. |
| `replace(text, target, replacement)` | String. Replace every literal target; null text/target returns text, null replacement means empty text. |
| `substring(text, start, end)` | String. Inclusive start and exclusive end; negative indices count from the end, bounds are clamped, reversed bounds return empty text, and null stays null. |
| `split(text, separator)` | List of String. Literal separator, preserving empty fields; null text returns an empty list, null/empty separator splits into Unicode code points. |
| `tokenize(text)` | List of String. Whitespace tokens retaining double-quoted phrases; null text returns an empty list. |
| `join(values, separator)` | String. Join an iterable; null iterable returns empty text, null separator inserts no delimiter. |
| `concat(values...)` | String. Concatenate `String.valueOf` representations without separators; no values returns empty text. |
| `repeat(text, times)` | String. Repeat text; negative times are treated as zero, null text stays null. |
| `abbreviate(text, width)` | String. Abbreviate to a maximum width with an ellipsis; null stays null, too-small widths are invalid. |

```kactors
console.println(strings.uppercase(strings.trim(message)))
```

## core.timer

Import `core.timer as timer`. Timers use wall-clock time, independently of a digital twin's
scheduler. Temporal quantities accept `ms`, `s`/`sec`, `min`, `h`/`hr`, and `d`; other units are
invalid. Quantity amounts are converted to long values, so fractional parts are truncated.

| Verb | Type | Contract |
| --- | --- | --- |
| `at(timeInstant, object)` | Supplier | Supply object at the absolute instant. A null object supplies the current `TimeInstant`. Past instants complete immediately. The second argument is required by the Java signature, but may be null. |
| `in(quantity, objects...)` | Supplier | Supply the first optional object after the interval, or the current `TimeInstant` when absent/null. Nonpositive delays complete immediately; extra objects are ignored. |
| `tick(quantity)` | Emitter → TimeInstant | Fire immediately and then at a fixed rate until the call scope ends. The period must convert to a positive number of milliseconds. |
| `random(quantity)` | Emitter → TimeInstant | Fire at randomized delays around the requested interval until the call scope ends. For intervals over 1 ms, each delay is sampled from roughly half to one-and-a-half the interval. |

Supplier timer threads are daemon threads and are cancelled when their future completes.
Emitter timers release their timer when the call scope completes or is interrupted. They keep
the calling behavior reactive while active; use finite suppliers for a finite script.

## core.url

Import `core.url as url`. `url.new(address)` returns a `core.url` handle bound to an absolute HTTP,
HTTPS, or `file:` URL. Inspection, construction, resolution, and encoding are functions that do
not contact the resource. Access operations are suppliers running on virtual threads; they support
both reactive match blocks and `<-` assignments. No connection or stream remains open after an
operation completes.

| Static verb | Bound instance equivalent | Type and contract |
| --- | --- | --- |
| `new(address)` | — | Function → core.url. Parse and normalize an absolute URL; unsupported schemes, malformed URLs, and embedded username/password credentials are invalid. |
| `inspect(address)` | `info()` | Function → Map. `address`, `scheme`, `host`, `port`, `path`, `query`, `fragment`. Absent host/query/fragment may be null; port -1 means unspecified. Query and fragment retain their percent encoding. |
| `resolve(base, relative)` | `child(relative)` | Function → core.url. Standard URI resolution; supports relative references and absolute replacement URLs. A base ending in `/` resolves children within that directory; otherwise its last path segment is replaced. |
| `encode(text)`, `decode(text)` | — | Function → String. UTF-8 form/query-value encoding and decoding: spaces become `+` and plus decodes as space. Encode individual values, not an entire URL or path. Invalid percent escapes are errors. |
| `read(address)` | `text()` | Supplier → String. GET and decode UTF-8; only HTTP 2xx is successful. |
| `readbytes(address)` | `bytes()` | Supplier → byte array. GET without text decoding; only HTTP 2xx is successful. |
| `request(address, method, body metadata)` | `fetch(method, body metadata)` | Supplier → Map. Perform an HTTP request and expose the response, including non-2xx status codes. Body is optional. Metadata options are described below. |
| `download(address, destination)` | `downloadto(destination)` | Supplier → core.file. GET successfully, then create or replace the local destination with response bytes. |

`address()` is an additional instance function returning the bound URL string. Handles retain no
request state: a `fetch` does not change the URL or establish defaults for subsequent operations.
The factory and resolution/download results declare `core.url`/`core.file` behavior contracts so
the k.Actors compiler can validate later instance calls.

HTTP request methods are case-sensitive `GET`, `HEAD`, `POST`, `PUT`, `DELETE`, and `OPTIONS`.
GET and HEAD reject a body; other methods accept an optional UTF-8 string body. Set a Content-Type
header appropriate for the payload; JSON bodies must already be serialized strings. `file:` URLs
support only GET, without a request body or request headers. PATCH is not supported by the JDK URL
transport used here.

`request`/`fetch` accepts these inline metadata options:

| Option | Default | Contract |
| --- | --- | --- |
| `:headers map` | Empty | Map of String header names to String values; CR/LF and invalid names are rejected. |
| `:timeout milliseconds` | `10000` | Positive integer connection and read-inactivity timeout, in milliseconds. This is not a total end-to-end deadline. |
| `:maxbytes count` | `16777216` (16 MiB) | Positive integer maximum response-body byte count. A larger body fails instead of returning truncated data. |

Unknown metadata keys are errors. Metadata and headers are copied before asynchronous work starts.
Convenience `read`, `readbytes`, and `download` use the defaults; use `request` or `fetch` for custom
headers, timeouts, and limits. The response map contains:

| Key | Type | Meaning |
| --- | --- | --- |
| `status` | Integer | HTTP status code; 200 for a successful file URL read. |
| `headers` | Map of String to List of String | Response headers, excluding the HTTP status line. Header-name capitalization is transport-dependent. |
| `body` | String | Response bytes decoded as UTF-8, regardless of the server's declared charset. |
| `bytes` | Java byte array | Original response bytes. HEAD responses contain empty text/bytes. |
| `address` | String | Requested normalized URL. |

HTTP 3xx redirects are returned without following them. Resolve the `Location` header against
the response address and explicitly request it when desired. HTTP 4xx/5xx are ordinary response
maps for `request`/`fetch`, with their error body where available; the convenience read/download
verbs fail on every non-2xx status. Network errors, timeouts, response-size violations, and local
download I/O failures complete suppliers exceptionally with `KlabIOException`. Invalid arguments
raise `KlabIllegalArgumentException`, either at call time for URL parsing or through the supplier
for request validation. HTTPS uses the JVM's standard trust and hostname verification.

Downloads finish reading and validating the remote response before opening the destination. A
failed HTTP response therefore preserves an existing destination, but local write failures are
not guaranteed to preserve it. Parent directories must already exist. There is no cache, cookie
jar, automatic retry, multipart upload, or persistent authenticated session; callers can provide
request headers explicitly. Agent shutdown/cancellation does not undo completed HTTP side effects
or file writes. Access uses the host process's filesystem and network capabilities.

```kactors
script examples.urls
    "Inspect and download a remote document."
    version 1.0
    using core.url as url, core.file as file, core.console as console

action main:
    file.mkdir("downloads")
    source <- url.new("https://example.org/report.txt")
    response <- source.fetch("GET" :timeout 5000 :maxbytes 1048576)
    console.println(response)
    url.download(source.address(), "downloads/report.txt"):
        document -> console.println(document.path())
        exception -> console.errorln("Download failed")
```

For a POST, use `source.fetch("POST", payload :headers {"Content-Type": "application/json"})`.
Static `url.read(...)` and instance `source.text()` have the same supplier semantics. Neither URL
nor file access requires email to be enabled; these are independent core agent capabilities.
