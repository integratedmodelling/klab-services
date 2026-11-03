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

Fresh native membership runs with explicit local-parent assertions, complete
registered-session inventory, independent cleanup, final CI and review remain
pending. Do not merge or call issue89 complete based on local tests alone. Broader
restart, two-deployed-Runtime and other issue89 gates remain separately unfinished.
