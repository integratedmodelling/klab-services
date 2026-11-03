# Native reconnect: preserve the descriptor parent without creating a session

Follow-up to issue #89, based on merged PR128. Native membership probes in both
creation orders returned preliminary PASS, but later inventory audits correctly
reject runs24/25 overall: public outsider reconnection created an unrelated default
session and used it as the local context's parent. The extra session was outside
the one-session budget and its disposal was not confirmed. Four recorded contexts
and the main session were released; all18 JVM identities are absent and source/
launch hashes match. Original PASS files and appended FAIL audits remain preserved.
Stopped isolated state must not be restarted to replay mutations or conceal failure.

Two unchanged-baseline HTTP regressions reproduce the product defect: parent
`caller-default` instead of `actual-parent`, and an unexpected default-session call.
The correction locally reconstructs only the authorized returned descriptor's exact
parent, keyed by Runtime/caller/session. Independent clients synchronize local
parent publication. It never declares a remote default session. Returned context
and Runtime identities must match the request before adopting a descriptor.

The local parent is not proof of session ownership or a grant of parent access.
Context owner and requester remain distinct; existing server ACLs and ordinary
shared-session disposal restrictions remain authoritative. No membership, broker
provisioning, authentication, dependency or scientific-algorithm rule changes.

37 focused tests pass, including10 new reconnect cases: existing and unrelated
parents, caller/Runtime isolation, changed descriptors and concurrent clients.
All10 changed executable lines and6 changed branch outcomes are covered without
exclusions. The13-module webui/service/graph package and seven fresh current Maven
closures pass. Test fixtures intentionally lack a usable broker; their AMQP error
logs are not messaging acceptance. Packaging skips tests after the verified suite.

## Genuine native acceptance

Fresh run26 (Alice-first) and run27 (Bob-first) pass against executable candidate
`27399f00c88c358de0cff4fcc0c2e4f528942fb0`. Each uses four separate native Java
processes, including a second process for the first username and a public outsider
reconnect. Each verifies four positive native API reads, five explicit HTTP403
denials, matching caller/owner/Runtime/group descriptors and forbidden ordinary
shared-session disposal. Every positive reconnect additionally asserts the exact
descriptor parent, Runtime and requester. Full Runtime registration-log inventories
show only the single expected nonlocal session; no auxiliary default session exists.

In each run four contexts and the single session have confirmed releases, Runtime
context listing is empty, all18 JVM identities are independently absent,3,898 launch
hashes/1,154 main-source hashes and all fixture/client hashes remain unchanged.
No matching owned visible windows remain; unrelated processes/windows are untouched.
Launch attestation matches2,612 compiled classes to packaged JARs and seven current
declared closures. There are zero scientific Jobs and no automatic mutation replay.
Privileged final disposal of the shared session is a cleanup operation, not an
ordinary-member capability. Failed runs23/24/25 and stopped isolated state remain
preserved; later success does not establish historical auxiliary-session disposal.

Run26 result SHA-256:
`bcdf6cbb70fc90e4a3e794b5cff9652e325c2edbfc3d11bd2fd15c89af11f3e7`.
Run27 result SHA-256:
`b71b3df0082d2dd88304a06e14dea4cab35d703a44576a519665df185de3ed30`.

Final documentation-head CI and merge review remain pending. Review is author/
agent-conducted, not independent maintainer approval. These are membership and
native reconnect acceptance, not scientific, graph-retaining restart or two-deployed-
Runtime acceptance. Broader issue89 gates remain unfinished. See
[sanitized results](native-reconnect-parent-results-20261103.json).
