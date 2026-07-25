# k.Actors language guide

This file is a user-level introduction and reference for k.Actors as implemented in the current
`klab-services` distribution. It is also intended to give contributors and coding agents enough
context to read, validate, test, and extend k.Actors code without having to reconstruct the
language model from Java interfaces.

The current Xtext grammar is the authority for source syntax. It lives in the sibling
`klab-languages` repository at
`org.integratedmodelling.languages.kactors/src/org/integratedmodelling/languages/KActors.xtext`.
The `KActorsBehavior`, `KActorsAction`, `KActorsStatement`, and `KActorsValue` interfaces in
`klab.core.api` define the semantic contract after parsing. The conceptual background comes from
[section 5 of the k.LAB technical note](https://docs.integratedmodelling.org/technote/index.html#_the_reactivity_layer_behaviors_and_applications).
That note predates the current grammar, so use it for motivation rather than exact syntax.

## 1. What k.Actors is for

k.LAB separates scientific information into resource, semantic, and reactivity layers. k.IM
describes what observations mean and how they can be resolved. k.Actors describes how observations
and other runtime agents behave: what they do when started, which messages they accept, what events
they emit, and how they react to events from other agents.

A k.Actors source file defines one **behavior**. At runtime, a behavior becomes an actor with:

- state initialized by an optional `init` action;
- a set of named actions that can receive calls;
- an optional `main` action run after initialization;
- access to imported k.Actors behaviors and Java actor extensions;
- an event bus through which actions can yield an interceptable value once or fire repeatedly;
- a scope connecting the actor to a user, session, digital twin, or observation.

This model supports several usages:

- reactive behavior attached to observations in a digital twin;
- session applications exposing dynamic user interfaces, executable by modular front-ends;
- reusable components that can be imported by other actors and implement UI elements;
- synchronous batch scripts;
- comprehensive test cases covering the entire k.LAB lifecycle;
- reusable traits that contribute an inherited agent personality, and action libraries;
- bridges to Java extensions supplied by k.LAB components.

The core idiom is deliberately verbal. A statement such as `timer.in(15.s)` asks the actor named
`timer` to perform its `in` action. If that action supplies or emits a value (in the example, the current time after 15 seconds have passed from the invocation), a match block after
the call states what the current actor should do with it.

## 2. A minimal behavior

```kactors
behavior examples.clock
    "Wait for a timer event and print it."
    version 1.0
    using
        core.timer as timer,
        core.console as console

action main:
    timer.in(15.s):
        time -> console.format("The timer supplied %s", time)
```

The preamble declares the behavior kind and URN, gives it a mandatory description and version, and
imports two actors under local aliases. `main` starts automatically when the behavior starts. The
call to `timer.in` installs a match action; when the timer supplies a value, that value is bound to the
`time` variable to be consumed in the following statement.

Whitespace is not semantically significant. Indentation is strongly recommended because it makes
the statement and match structure legible. Both single-line `//` and multi-line `/* ... */` comments are accepted.

## 3. Behavior preamble

The general shape of a file is:

```kactors
<kind> <behavior.urn>
    "Human-readable description"
    version <version>
    [worldview <worldview>]
    [inherits <parent.one> [, <parent.two>]]
    [using
        <behavior.or.extension> as <alias>[,
        <another.behavior> as <alias>]]

<actions>
```

`version` is required by the grammar. A nonblank description is required by semantic validation.
`using`, `worldview`, and `inherits` are optional and may occur in any order in the preamble.

Behavior URNs are dot-separated lowercase paths. They identify behaviors to resource services and
must be chosen as stable, globally meaningful names when the behavior will be shared.

### 3.1. Behavior kinds

| Source declaration | Intended use                                                                                                                             | Lifecycle expectations |
| --- |------------------------------------------------------------------------------------------------------------------------------------------| --- |
| `behavior` or `behaviour` | A behavior normally bound to an observation, but also runnable independently                                                             | Actor-like and normally long-lived |
| `app` | A session-level interactive application                                                                                                  | Long-lived until explicitly stopped |
| `desktop app`, `web app`, `mobile app` | An application specialized for a front-end platform                                                                                      | Long-lived until explicitly stopped |
| `public ... app` | A public application declaration, available through a k.LAB service facing an authenticated user; it may also carry a platform qualifier | Long-lived until explicitly stopped |
| `component` | A self-contained composable actor created and used from another agent, commonly a UI component                                             | May initialize and run `main`; long-lived while its owner uses it |
| `script` | A synchronous batch job in a session scope                                                                                               | Expected to finish unless it starts emitter work |
| `task` | A restricted behavior attached to any observation for post-processing, documentation, or monitoring                                      | Runs in the request context; cannot modify the knowledge graph |
| `user` | The behavior instrumenting the user that owns the request                                                                                  | Exactly one instance per root user scope |
| `testcase` | A collection of actions, normally annotated with `@test`, run under a test scope                                                         | Runs explicitly and produces test results |
| `trait` | An inheritable agent personality contributing state and actions                                                                           | May declare `init` and `main`; both are adopted through inheritance |
| `library` | A reusable collection of callable actions                                                                                                  | Cannot declare `init` or `main` and is not started independently |

A `USER` behavior always runs in the root user scope that owns the requesting session or context.
Components and traits cannot be started through direct runtime agent creation; they are
instantiated only as imports or incorporated through inheritance.

### 3.2. Imports

Imports make actions from another k.Actors behavior or a Java actor extension addressable:

```kactors
using
    core.timer as timer,
    core.console as console
```

Every import must have a local alias. Aliases must be unique, and `self` is reserved. Calls to an
undeclared receiver are errors unless the receiver is a known actor-valued variable. Imported
actors may be resolved from connected resource services or from installed Java components.

A static action may be called directly on an imported alias. A non-static action requires an actor
instance, normally created through the runtime's `new` facility. Constructor arguments for Java
actors and `init` arguments for k.Actors behaviors are matched by the runtime validator.

### 3.3. Inheritance and worldviews

`inherits` names behaviors whose actions and protected state become available to the new behavior:

```kactors
behavior examples.specialized
    "Specialize a reusable behavior."
    version 1.0
    inherits examples.common
```

Inherited `init` actions run before the local `init`. Local actions may override inherited actions;
use `@override` to make that intent explicit. Inherited actor state may be changed with `set`, but
must not be redeclared.

Every behavior kind may inherit a `trait`. Otherwise the inherited behavior must have the same
kind as its child. The two specialized behavior kinds are exceptions: `user` and `task` behaviors
may also inherit an ordinary `behavior`. For example, an application may inherit another
application or any trait, but it cannot inherit a script or an ordinary behavior.

`worldview` selects the semantic worldview used to interpret observable declarations and semantic
operations in the behavior.

## 4. Actions

Actions are the callable units of a behavior:

```kactors
action greet(name):
    console.format("Hello %s", name)

static action describe:
    return "A description"
```

An action name is a lowercase identifier. Arguments are untyped names and may be supplied
positionally or by name at call sites. Duplicate action names and duplicate argument names are
errors.

Annotations precede an action:

```kactors
@test
action parses_units:
    assert unit("mg/dl").space is empty
```

Annotations carry runtime conventions rather than changing the basic syntax. Common conventions
include `@test` for test actions, `@override` for inherited actions, and `@handle(...)` for actions
that handle runtime messages or scheduled events. Available annotations and their arguments depend
on installed runtime components.

### 4.1. Special action names

- `init` is the constructor-like action. Define actor state here with `def`.
- `main` runs after initialization when the behavior is started.
- Other actions run only when called, inherited, or selected through an annotation such as
  `@handle` or `@test`.

Traits may declare `init` and `main`: these participate in the inherited initialization and
startup behavior of the adopting agent. Components may also declare both because each component is
an actor with its own construction and startup lifecycle. Libraries cannot declare either special
action because they are action collections rather than constructed or started actors.

### 4.2. Function, supplier, and emitter actions

Analysis assigns every action one effective execution type:

- A **function** executes synchronously and returns normally. It does not expose reactive match
  actions.
- A **supplier** eventually completes once. A `return` executed inside a match action supplies its
  result and removes the listener.
- An **emitter** contains `fire` and may produce zero, one, or many events while its actor remains
  alive. It may use a reactive `return` to stop scheduled emissions and remove its listeners; the
  required return value is available as an exit code.

These properties propagate through calls. If `main` calls another action that calls an emitter,
`main` is effectively emitter-like too. The same applies to suppliers, including actions resolved
from imported behaviors or Java extensions. An action that both fires values and performs a
reactive return remains an emitter; the return is its cleanup/termination path and its operand is
the exit code.

The runtime uses the effective types of `init` and `main` to decide whether a started actor can
finish or must remain alive. Actor-like behaviors, applications, and components are normally
persistent; scripts are normally finite unless their effective work is emitter-like. A trait's
special actions affect the lifecycle of the agent that inherits it rather than starting the trait
independently. Libraries have no lifecycle entry points.

### 4.3. Agent messages and `@handle`

Running agents communicate through their agent URNs. Communication is bidirectional: a remote
client handle can control and message its service-side peer, agents can message other agents, and
an agent can send a message to the scope that created it. Agent handles remain ordinary
serializable beans; reconnecting a deserialized handle uses its URN rather than serialized broker
or listener state.

Custom k.Actors messages are identified by an uppercase constant. Bind an action to one exact
message constant with either form of the `@handle` annotation:

```kactors
@handle(TEMPERATURE_CHANGED)
action temperature_changed(reading, sender):
    console.format("Received %s", reading)

@handle(class=RESET_REQUESTED)
action reset(payload):
    set status READY
```

The annotation's main unnamed argument, or its named `class` argument, must be a `CONSTANT`.
When a matching custom message arrives:

- the runtime invokes the annotated action asynchronously using its inferred function, supplier,
  or emitter execution type;
- every declared argument except the exact reserved name `sender` receives the message payload;
- an argument named `sender`, if present, receives an agent handle for the sending URN. Messages
  sent through that handle are addressed back to the sender and identify the current agent as
  their source;
- an exception escaping the handler fails the owning agent and is reported as a lifecycle status
  change.

Handlers contributed by inherited behaviors remain active and execute against the retained
inherited behavior instance, preserving its initialized state. A local handler for the same
constant overrides inherited handlers. If multiple inherited behaviors handle the same constant,
the first behavior in the inheritance order wins.

Custom message payloads should normally use portable scalar, list, map, or other types already
supported by the runtime's Jackson configuration. Component-defined serializable DTOs are also
supported when the extension registers the same payload class on both communicating runtimes.
If a receiver does not recognize an advertised DTO class, it receives the decoded map and a
warning instead of loading an arbitrary class named by the message.

Start, stop, status request, status change, and failure are runtime lifecycle messages rather than
custom constants. Remote handles use these to control a running peer and maintain their local
view of its state. Status includes the represented observation ID (`-1` when unbound), when the
agent first started, and the latest message or reactor activity, allowing clients to calculate
idle time. Correlated `ask`/reply is not implemented yet; a handler can currently respond by
sending a normal message through its injected `sender` handle.

Messaging is available only when the scope used to create or reconnect the agent has a connected
messaging channel. Agent creation still succeeds without one: messaging is disabled and the
returned agent contains an info-level notification explaining why.

### 4.4. Interactive agent consoles

Use `@stdin` to make an action receive lines from an attached client-side agent console:

```kactors
behavior examples.console
    "A manually testable console behavior."
    version 1.0
    using
        core.console as console

@stdin
action read_line(line, sender):
    console.format("received: %s%n", line)
```

`@stdin` is shorthand for the reserved `STDIN` agent-message class and takes no annotation
arguments. The action follows the same binding rules as `@handle`: each ordinary parameter
receives the input line and an optional parameter named exactly `sender` receives the sending
agent handle. Inherited `@stdin` actions are retained; a local one overrides them.

The core `console` actor sends output to every console currently attached to the agent. It exposes:

- `print(values...)` and `println(values...)` for standard output;
- `format(pattern, values...)` and its `printf` alias;
- `error(values...)`, `errorln(values...)`, and `errorf(pattern, values...)` for standard error;
- `flush`, which flushes the local fallback writer.

An `AgentConsole` attaches when constructed with a connected client-side `Agent` handle. Its
`sendLine(...)` method forwards one logical line, `onOutput(...)` receives standard-output and
standard-error chunks, and `run(...)` provides a blocking terminal loop over ordinary Java input
and print streams. Closing the console detaches it without stopping or disconnecting the agent.
If no remote console is attached, the core console verbs fall back to the runtime's local output
stream.

## 5. Calls, arguments, and event matching

### 5.1. Calling actions

Calls use a local action name or an explicit receiver:

```kactors
helper
self.helper
timer.in(15.s)
console.format("Value: %s", value)
service.lookup(key="elevation", refresh=true)
```

A naked action name addresses `self`. An explicit prefix addresses an imported actor or an actor
stored in a visible variable.

Arguments may be positional, named, or mixed as permitted by the target action. Inline metadata can
also be included in the argument list:

```kactors
button("Run" :tooltip "Start the computation" +enabled)
```

Metadata is interpreted by the receiving verb or front end; it is not a substitute for ordinary
action arguments.

### 5.2. Match actions

Append `:` to a supplier or emitter call to react to its output:

```kactors
timer.in(15.s):
    elapsed -> console.format("Elapsed: %s", elapsed)
```

Use parentheses for multiple alternatives:

```kactors
worker.run(job): (
    result -> console.print(result)
    empty -> console.print("No result")
    exception as error -> console.print(error)
)
```

A function call cannot have match actions. An emitter cannot be used where a single value is
required, such as the right side of an assignment or as a condition. A supplier may be used there;
execution waits for its single result.

Current match syntax includes:

| Pattern | Meaning |
| --- | --- |
| `value -> ...` | Bind the emitted value to `value` |
| `first, second -> ...` | Destructure sequential values into local names |
| `true`, `false`, a number, quantity, or uppercase constant | Match that literal/category |
| `Type as value` | Match a runtime type and optionally capture it |
| `%regular expression% as text` | Match text with a regular expression and optionally capture it |
| `{{semantic observable}} as observation` | Match an observation by semantics |
| `in (one, two, three) as value` | Match membership in a set of values |
| `[boolean expression] as value` | Match using an expression evaluated in scope |
| `@annotation as value` | Match an annotated object |
| `unknown` | Match no-data |
| `empty` | Match an empty result |
| `exception as error` | Match an error or exception and optionally capture it |
| `*` | Match any ordinary value |
| `#` | Match anything, including values not accepted by `*` |

Names introduced by a match are local to its action-on-match. A value captured with `as` has the
same scope.

### 5.3. Concurrency and `then`

Reactive calls normally install listeners and let execution continue. Prefix a following statement
with `then` when it must wait for the preceding reactive call to supply its first value. When the
preceding statement is a group, every reactive call in that group must supply a value before
execution continues:

```kactors
(
    worker.prepare(job): ready -> console.print(ready)
    then worker.commit(job)
)
```

Using `then` without a preceding reactive call is suspicious and produces a warning. Calls used as
assignment values, conditions, `return` values, or `fire` values are also value-required and
therefore synchronize when the target is a supplier.

## 6. Values and expressions

k.Actors has literals but delegates mathematical and logical expressions to a language service.
Expressions appear between square brackets and are compiled independently, with identifiers
validated against variables visible at that point:

```kactors
if [temperature > threshold] alert(temperature)
return [value * scale + offset]
```

`self` refers to the current agent inside expressions. Other conventional k.LAB bindings may be
available according to runtime scope.

Common value forms include:

| Form | Example |
| --- | --- |
| Number | `10`, `3.14` |
| Boolean | `true`, `false` |
| String | `"hello"` |
| Quantity | `15.s`, `10.km`, `5/m` |
| Range | `0 to 100` |
| Constant | `READY` |
| Identifier | `temperature` |
| Semantic observable | `{{geography:Elevation in m}}` |
| Expression | `[temperature > 20]` |
| Ternary value | `ready ? "yes" : "no"` |
| List | `(1, 2, 3)` |
| Map | `{name: "sample", count: 3}` |
| Localized string key | `#WELCOME_MESSAGE` |
| Current/numbered event or argument value | `$`, `$$`, `$0`, `$1`, ... |

A value prefixed with a backtick is deferred: evaluation is postponed so the receiving action can
evaluate it in the appropriate actor scope.

Square brackets always delimit an expression, so `[1, 2, 3]` is an expression producing a list,
whereas `(1, 2, 3)` is the grammar's literal list form.

## 7. State and variable scope

k.Actors distinguishes actor state from frame-local variables.

### 7.1. Actor state

Declare actor state with `def` in `init`:

```kactors
action init:
    def count 0
    def status READY
```

Change known actor state with `set` in later actions:

```kactors
action increment:
    set status RUNNING
```

`def` outside `init`, `set` of unknown state, redeclaration of inherited state, and assignment to an
import alias are invalid.

### 7.2. Frame-local variables

Use `<-` for variables local to the current action or group and its nested groups:

```kactors
action report(value):
    formatted <- console.format("Value: %s", value)
    doubled <- [value * 2]
    console.print(formatted)
```

The right side may be a literal, a square-bracket expression, a function, or a supplier. A local
variable cannot shadow actor state. Variables introduced by action arguments, loop iteration, and
match captures are also frame-local.

An `as` clause adapts the evaluated object to a named behavior before storing it:

```kactors
specialized <- existing as examples.specialized_worker
specialized.process(job)
```

Behavior adaptation is legal only on local `<-` assignments. The target behavior must resolve in
the active runtime environment, and its adapter must accept the original value's type. After
validation, the assigned variable is treated as an agent implementing the target behavior, so its
action calls are validated against that behavior. Adaptation does not mutate the original object;
the runtime adapter decides whether to wrap it, derive a new agent, or reject the conversion.

## 8. Control flow and statements

Every action contains one or more statements. Parentheses create a group, which establishes a
nested frame and can carry metadata or a tag:

```kactors
(
    console.print("first")
    console.print("second")
) :name "Example group" #example_group
```

Supported control flow follows familiar forms:

```kactors
if [value > 10] console.print("large")
else if [value > 0] console.print("positive")
else console.print("zero or negative")

while [running] tick

do tick while [running]

for item in items (
    if [item == stop_value] break
    process(item)
)
```

Conditions may be literals, identifiers, expressions, or calls. They must evaluate to boolean.
`for` iterables may be values, expressions, or calls and must evaluate to an iterable. `break` is
only valid inside a loop.

### 8.1. `return`, `fire`, and `fail`

```kactors
return result
return compute(input)

fire event
fire [buildEvent(self)]

fail "The request cannot be completed"
```

`return` completes a synchronous function, or completes a supplier with a value when executed
inside a match action. In an emitter, a reactive return stops scheduled emission and removes
listeners without changing the action's emitter type; its required operand is available as an exit
code. `fire` publishes an event without completing an emitter. `fail` aborts with an optional
message; failure in `init` or `main` terminates the actor.

Every `return` has an operand. This is part of the grammar contract as well as the value returned
by functions and suppliers; emitters interpret the same operand as an exit code when terminating
their reactive work.

### 8.2. Assertions and test cases

Assertions are primarily intended for `testcase` behaviors:

```kactors
testcase examples.units
    "Check unit parsing."
    version 1.0
    using
        core.units as units

@test
action parses_space_dimension:
    assert units.dimension("mg/m^3", axis="space") is 3
```

An assertion may check that a call or expression succeeds, use `is ok`, or compare its result with
a value. Several assertions may be comma-separated and may share statement arguments. Assertions
in non-test behaviors are allowed with a warning and may be omitted from production compilation.

### 8.3. Embedded text

Text between matching `%%%` markers is an executable text statement, chiefly used by applications:

```kactors
%%%
  ## Results

  Select an observation to begin.
%%%
```

The application/front-end determines how Markdown, templates, and text metadata are rendered.

## 9. Metadata and tags

Statements, groups, calls, and annotations can carry metadata. The syntax is:

- `:key value` for keyed metadata;
- `+flag` for a positive flag;
- `!flag` for a negative flag;
- `#tag` to give a statement or group a stable local identity.

For example:

```kactors
(
    button("Run") #run_button
) :layout "horizontal" +enabled
```

Metadata semantics are conventional and depend on the receiving actor, compiler, or application
front end. Tags are especially important in applications: they let later actions address a widget
or layout element without changing ordinary variable scope. `#lowercase` is a tag, while
`#UPPERCASE` is a localized-string reference.

## 10. Major usage patterns

### 10.1. Observation behaviors

A `behavior` can be bound to observations created by k.IM models. The behavior then runs with the
observation as its semantic and runtime context, allowing it to react to scheduled events, inspect
state, communicate with other observed agents, or trigger new observations.

Binding belongs to k.IM rather than the k.Actors grammar. A typical k.IM model uses a `@bind`
annotation naming the behavior and may add a selection condition. The technical note's city example
illustrates this pattern, but consult the current k.IM grammar before copying its older binding
syntax.

Use observation behaviors when the modeled system changes structurally in response to events, not
merely when a value can be recomputed by an ordinary process model.

### 10.2. Applications

An `app` is incorporated into a session actor. Its action/group structure describes both behavior
and, through UI verbs and metadata, a view hierarchy. UI verbs create widgets; their emitted events
drive match actions; tagged widgets can be enabled, disabled, reset, or updated later.

```kactors
web app examples.observer
    "Submit an observation from a browser application."
    version 1.0
    using
        core.ui as ui,
        core.runtime as runtime

action main:
    (
        ui.button("Observe elevation"):
            click -> runtime.submit({{geography:Elevation in m}}):
                result -> ui.show(result)
    ) :layout "vertical" #main_panel
```

Exact UI verbs and metadata are supplied by installed components. The group/tag/event pattern is
part of k.Actors; widget names and styling keys are extension APIs and must be checked against the
active component version.

### 10.3. Components

A `component` is a self-contained actor that exists in its own right but is created, owned, and
used from inside another agent rather than bound directly to an observation. Components are useful
for reusable UI panels, controllers, or reactive subsystems. Their public actions form the
component API; `init` accepts construction state and `main` starts their behavior.

### 10.4. Scripts

A `script` automates a finite batch workflow in session scope. Prefer functions and suppliers, and
let `main` return when work is complete. Calling an emitter makes the effective script lifecycle
persistent, so only do so when the script is intentionally a long-running monitor.

```kactors
script examples.batch
    "Compute one observation and report completion."
    version 1.0
    using
        core.runtime as runtime,
        core.console as console

action main:
    result <- runtime.submit({{geography:Elevation in m}})
    console.print(result)
```

### 10.5. Tasks and user behaviors

A `task` is observation-scoped like an ordinary behavior, but the observation need not have
`SemanticType.AGENT`. Tasks run with restricted capabilities: they may post-process results,
produce documentation, or monitor resources and observations, but cannot modify the knowledge
graph. Every task declares `main`.

A `user` behavior instruments the root `UserScope` owning the request. It can configure or react to
user-level activity independently of the session or context from which it was requested. A runtime
retains at most one user-behavior instance for each root user scope it serves. Different users may
therefore have independent USER agents; stopping or releasing one permits a replacement for that
same user to be created.

### 10.6. Test cases

A `testcase` groups independently runnable `@test` actions. Use it for runtime services, semantic
operations, actors, and application behavior—not only pure functions. Test scopes collect action
and assertion results for later reporting.

Keep each test action focused, use descriptive action names, and prefer explicit match branches for
`empty` and `exception` when testing reactive calls.

### 10.7. Traits and libraries

A `trait` is an inheritable agent personality. It can package actions and protected state, and it
may define `init` and `main`; those special actions are incorporated into the adopting agent
through inheritance. Traits are not instantiated or bound independently. Use small traits with
clear action contracts and declare overrides explicitly in consuming behaviors.

A `library` is an importable collection of callable actions. It is not an inherited personality
and has no construction or startup lifecycle, so `init` and `main` are invalid in a library.

### 10.8. Java actor extensions

Components can expose Java classes as actors and Java methods as verbs. From k.Actors they look like
ordinary imports and calls. The runtime validator is responsible for:

- locating the component and actor class;
- matching call arguments to Java parameters;
- distinguishing static verbs from instance verbs;
- classifying each verb as a function, supplier, or emitter;
- reporting the Java runtime class used by generated code.

This boundary is what lets k.Actors remain small while exposing the full k.LAB runtime and
third-party libraries.

## 11. Validation checklist

Syntactically valid code can still be logically invalid. Before compilation, behavior analysis
checks at least the following:

- the behavior has a nonblank description;
- imports have unique aliases and do not use `self`;
- actions and action arguments are not duplicated;
- libraries do not declare `init` or `main`; traits and components may declare both;
- calls to `self` name a known local or inherited action;
- explicit receivers are imported or are visible actor variables;
- function calls do not declare match actions;
- emitters are not used where one value is required;
- calls and their arguments satisfy extension-provided validation;
- identifiers in values and expressions are visible in the current lexical scope;
- assignments obey actor/frame scope and do not overwrite imports or inherited state;
- conditions are boolean and `for` inputs are iterable;
- `break` occurs inside a loop;
- `then` follows reactive work worth waiting for;
- `fire` and `return` statements supply exactly one value or call;
- reactive returns in emitters retain their value as an exit code, while the action remains an
  emitter.

Warnings and errors retain source offsets and lengths, allowing IDEs and clients to place
diagnostics on the responsible code.

## 12. Running and evolution

The resources service parses a `.kactor` source into `KActorsBehavior`. The runtime service accepts
that behavior at its agent-running endpoint, analyzes it, optionally compiles it to Java, and starts
it in the requested user/session scope. The returned runtime agent can receive messages and report
status until it finishes or is explicitly stopped. When the creating scope is connected to
messaging, the returned handle's URN also identifies its bidirectional message endpoint; otherwise
the agent runs normally with messaging disabled and an explanatory info notification.

Scope ownership follows the behavior kind:

- a `task` or ordinary `behavior` runs in the exact user, session, or context scope from the
  request;
- a `script`, `app`, or `testcase` receives a new private session traced from the requesting
  user's root scope. The runtime releases that session automatically when the agent finishes,
  fails during startup, or is explicitly stopped;
- a `USER` behavior runs in the root user scope that owns the request;
- a component or trait cannot be run independently.

Only tasks and behaviors may request an observation binding. A positive observation ID requires a
context-scoped request; the runtime retrieves that observation from the context's knowledge graph
and fails creation if it does not exist. The running agent exposes both the represented
observation, when any, and its exact creation scope to Java extensions through the `RuntimeAgent`
API. A source-only compile creates neither an agent nor a private session.

When called in a focused context, the runtime may bind a `BEHAVIOR` or `TASK` to the focused
observation. The runtime owns that observation; remote
handles retain only its numeric ID. `DO_NOT_BIND_OBSERVATION` forces an unbound agent. Calls made
from a user scope, such as manual runs from the IDE, remain user-scoped and do not require a
context.

The parser grammar is ahead of parts of the Java compiler, and extension-provided verb catalogs are
still evolving. When documentation, historical examples, and implementation disagree:

1. use `KActors.xtext` for accepted source syntax;
2. use the `KActors*` API interfaces and `KActorsVisitor` validation for current semantic rules;
3. use tests in `klab.services.resources` to prove parsing/adaptation assumptions with real files;
4. use `BehaviorAnalyzerTest` for semantic and lifecycle rules;
5. treat the 2021 technical note and older example projects as conceptual or migration material.

When adding a language construct, update all five surfaces together: grammar, syntax adapter,
public API model, visitor/analyzer, and real-file tests. A construct is not complete merely because
the Xtext parser accepts it.
