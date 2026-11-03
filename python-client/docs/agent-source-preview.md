# Source-only agent preview

PR111 now implements a bounded, nonexecuting slice of the agent roadmap. Full
remote lifecycle/control remains incomplete; this API does not return an Agent.
The first implementation passed42 source-derived cases and2,205 total tests, nine
unexcluded coverage gates, lint, packaging and CI. Served run19 retained a harness
route-prefix failure before parsing; run20 retained a source-fidelity rejection
before Runtime translation. Both have independently verified14-JVM cleanup and
unchanged launch/source/fixture hashes. Updated parser-fidelity regression tests
and native served acceptance remain pending; neither failure is called success.

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
