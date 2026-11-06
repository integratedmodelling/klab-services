# Source-only agent preview

## Native awaited preview

```python
from klab_client import AsyncAgentPreviews

preview = await AsyncAgentPreviews(async_client).preview(behavior_source)
print(preview.java_code)  # generated source, NOT compiled/executed Java
```

The awaited facade shares the synchronous source, identity, credential, payload
and result checks and fixed nonexecution flags. It awaits ordinary AsyncClient
transport directly; no worker thread, hidden loop, broker, session, disposal, or
automatic request retry is introduced. Cancellation stops the local wait, not
proof of remote rollback; lost responses never become a successful preview.
Only a matching AsyncClient is accepted; AgentPreviews remains sync-only.

Native async protocol tests are new Python evidence. Historical served synchronous
translation below is retained, not relabelled as new async Java execution or science.
No simulator endpoint is needed for this implementation correction. Grammar and
semantic validation remain server responsibilities; no Python k.Actors parser or
compiler is introduced. See `docs/AGENTS.md` for the language authority hierarchy.

PR111 now implements a bounded, nonexecuting slice of the agent roadmap. Full
remote lifecycle/control remains incomplete; this API does not return an Agent.
The final implementation passes45 source-derived cases and2,208 total tests, nine
unexcluded coverage gates, lint, packaging and executable-head CI. All79 new module
lines and22 branches are covered. Served run19 retains a harness route-prefix
failure before parsing; run20 retains a source-fidelity rejection before Runtime
translation. Both have independently verified14-JVM cleanup and unchanged hashes.
Fresh served run21 passes the bounded preview and independently verified cleanup.
Final documentation-head CI and merge review remain; failures are not relabelled.

```python
from klab_client import AgentPreviews

preview = AgentPreviews(client).preview(behavior_source)
print(preview.java_code)
```

Only BEHAVIOR source is admitted. Resources parses it using the existing
`POST /parseAsset/BEHAVIOR` API; its DTO goes unchanged to Runtime's existing
`POST /api/v1/instantiate/agent`. The caller cannot supply a DTO or override flags.
Input bytes are sent unchanged. The deployed parser's `ParsedObjectImpl.sourceCode`
retains the actual syntax node and calls Java `trim()`, as verified in its declared
dependency bytecode. The client admits only unchanged source or outer space/tab/
newline trimming, reports that difference and retains separate submitted/parser
source SHA-256 values. Internal edits, class changes and absent source fail closed.
No parsed DTO is reconstructed, and no whitespace is changed in the submitted body.
`compileOnly`, `doNotStart`, `doNotBindSession`, `doNotBindObservation` and
`reportJavaCode` are fixed true, with observationId=-1. Runtime's compileOnly maps
to `DO_NOT_COMPILE_JAVA`: it translates source, but does not compile Java, register
an agent, start actions, open broker messaging or create a private session.

The result requires viable generated code, matching class/behavior identity, no
agent URN/scope, no start timestamp, no observation binding and explicitly false
alive/messaging/deferred-start flags. Service origins/IDs are pinned and checked
before and after translation. Source/payload/response byte bounds and credential
exclusion apply; raw returned data is frozen and omitted from repr. There is no
replay, execution/start/stop, reconnect, custom messaging or scientific algorithm.

The original [lifecycle audit](agent-lifecycle-contract.md) remains historical
evidence and a list of full lifecycle acceptance requirements. Shared-worldview,
authentication/provisioning changes, production deployment and publication are not
part of this preview. Generated source is not compiled Java or execution evidence.
Full execution/control and terminal-cleanup work is tracked in issue #125; it is
not completed or replaced by this narrower preview feature.

## Served acceptance and evidence limits

Executable Python candidate `3a2fe7705` is tested against the source-matched Runtime
executable `ca9297335d28cfdf76132ecd8f049ac2f3d4adca` (documentation head
`e3fc91345502ecb0a48e0de957326279fdc5ca0d`). All launch/source hashes are verified
before and after; current declared closures are reused unchanged, not mixed with
historical dependency sets. No Java product, authentication or dependency change
is introduced by this Python preview.

Run21 sends exactly one Resources parse and one fixed-flags Runtime preview, with
origin-relative request allow-lists denying all other mutating routes. Resources
and Runtime identities match. The supplied source body is unchanged; parser outer
newline trimming is explicitly reported with two distinct hashes. Runtime returns
viable generated source, no agent URN or scope, and alive/messagingConnected=false.
There are zero agent starts/stops, private sessions, contexts, Jobs or broker-control
connections. Runtime context listing and HTTP cleanup pass.

All14 JVM identities are independently absent;3,898 launch hashes,1,154 main-source
hashes and all fixture/SDK launch hashes match. No matching owned visible windows
remain; unrelated services/windows are untouched. The3,818 received UTF-8 source
bytes hash to `6d7a1d316d7dab0808eb63965162daae6e9ff0fc63a5c27da37a06ff7cc3f14c`.
The original displayed `.java` evidence file has3,928 bytes because Windows text
output expanded110 LF line endings to CRLF. It is retained separately; reversing
only that recorded translation exactly matches the received-byte hash. No generated
Java is compiled or executed.

Served result SHA-256:
`5dbf656df3687f82e65404a777cdc58c4701adeb9de314f07315fc6690733a5f`.
All34 packaged Python source modules and both byte-pinned sdist contracts match.
The result is source-translation acceptance, not lifecycle, Java-compiler or
scientific acceptance. Review is author/agent-conducted, not independent maintainer
approval. See [sanitized results](results/agent-source-preview-20261103.json).
