# Current managed-agent acceptance preflight

Accepted base: `c7dea3373c0fc1a4cbf9d577bd066b69374e6ca3` (PR211).
This is a source/provisioning preflight, not managed-agent live acceptance.

## Existing implementation to reuse

`AgentExecutions`, `DeferredAgent`, native AMQP control and explicit binding guards
already exist. Java Runtime creates agent-owned private sessions, instruments their
messaging from user federation data, and has session release/scope removal paths.
Do not replace these with a Python agent runtime or claim they are absent.

Current source anchors:

- `RuntimeService.initializeMessaging`: embedded broker creation is commented out;
  this does not mean externally provisioned federation brokers are unsupported.
- `RuntimeService.instrumentAgentSession`: reads the user's federation broker and
  calls `session.setupMessaging` when configured.
- `RuntimeService.releaseAgentSession`: combines session release with scope-manager
  removal. A reported STOPPED message does not independently prove those outcomes.
- `Klab.getFederationData`: explicit user federation data takes priority; otherwise
  one group carrying `federation.id=true` supplies its name and
  `federation.broker.url`; multiple matching groups are rejected. This is a current
  source contract, not evidence that the owned Hub fixture supplies those fields.
- `AMQPChannel.connect`: Java uses `ConnectionFactory.setUri` with the federation
  broker URI. Broker credentials/ACLs must be provisioned privately; never retain
  credential-bearing URIs in public reports.

## Required evidence before live execution

1. Trace authenticated Hub group/custom-property decoding into the actual Runtime
   user identity. Do not invent JWT claims or assume an advertised group is used.
2. Provision only an isolated owned broker with explicit principal/ACL bindings;
   do not reuse shared Modeler credentials, services or queues.
3. Independently establish the principal and Runtime process generation used by
   `AgentControlBinding`. Never derive incarnation from uptime, serverId, agentURN
   or an observation ID. Binding fields remain operator assertions, not wire proof.
4. Define independent cleanup witnesses for owned registration, private session,
   graph state and broker resources before starting execution. STOPPED, PUBLISHED
   and local queue deletion are not remote cleanup proof.
5. Revalidate exact current Java artifacts. Historical language-build blockers in
   older agent documentation are not current evidence after accepted full packaging.

The existing owned `fixture_hub.py` does not provision federation broker data.

### Authenticated group transport trace

`ScopeManager.createUserIdentity` copies authenticated username/email/token, then
for a service with `PartnerIdentity` obtains its authenticating Hub and service
token and calls the existing `ServicesAPI.HUB.USER_BASE_ID_SERVICES` route with
authentication. Returned `GroupImpl` objects populate the actual user identity;
otherwise it uses authorization groups. `getOrCreateUserScope` caches by username
and installs federation messaging when the scope is first created. Provisioning
must therefore be ready before the first scope, not patched into a cached user.

The route constant is `HUB_API_BASE + "/users/services/{id}"`. The owned fixture's
handler currently implements enrollment `do_POST` only, with empty group arrays;
there is no user-services GET. A future fixture increment must implement that
existing authenticated contract, verify the service token and restrict returned
groups to explicitly provisioned test identities. Confirm the actual service
identity takes this partner branch in a fresh isolated run before claiming success.
Adding broker fields to JWT `perms` would not reproduce this path. Browser Hub
authentication is a different path and currently turns permissions into named
groups without these custom properties; do not equate the two workflows.

The portable Qpid/Java wire fixture is not a managed Runtime acceptance run, and
its explicit unconfirmed publication mode cannot be promoted to delivery proof.
No managed execution has been attempted by this increment. Client
`remote_cleanup="UNKNOWN"` remains truthful and unchanged.

## Separate gates

PR211 accepts one actual installed native async notebook journey, not managed
agents, durable events, async saved-handle restoration or production onboarding.
Q7's unresolved hierarchy run remains preserved on branch
`feature/python-current-semantic-inspection`. A/B and the three studies remain
incomplete. Provisioning preflight is actionable work, not an external-only
all-work stoppage or permission to publish/release scientific assets.
