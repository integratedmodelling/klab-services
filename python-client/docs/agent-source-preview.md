# Source-only agent preview

PR111 now implements a bounded, nonexecuting slice of the agent roadmap. Full
remote lifecycle/control remains incomplete; this API does not return an Agent.
Implementation has42 source-derived offline cases and100% new-module line/branch
coverage. Native served acceptance, full-suite/package checks and CI are pending.

```python
from klab_client import AgentPreviews

preview = AgentPreviews(client).preview(behavior_source)
print(preview.java_code)
```

Only exact BEHAVIOR source is admitted. Resources parses it using the existing
`POST /parseAsset/BEHAVIOR` API; its DTO goes unchanged to Runtime's existing
`POST /api/v1/instantiate/agent`. The caller cannot supply a DTO or override flags.
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
