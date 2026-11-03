# Cold owner scope restoration on the exact hosting Runtime

Issue89 follow-up after merged PR131 (`802e8cce2`). Graph-retaining restart run28
restarted only the owned Runtime; its graph process and service ID were retained.
Four native probes passed, including post-restart owner and public-outsider
reconnection. Overall acceptance nevertheless failed: a later scoped request for
Bob's own private context could not resolve its Runtime without re-advertisement.
Three context releases were confirmed; the fourth was rejected/unknown, so session
disposal was withheld. All19 JVM identities are independently absent;3,898 launch,
1,154 main-source and all fixture/client hashes match. The stopped graph and failed
result remain preserved, with no cleanup retry. An empty live list is not persisted
disposal evidence. Run28 result SHA-256:
`c28e22fb9bfee0fa43c2e3e3d5fab24729e46259acd982a00a41901d99902303`.

Two unchanged-baseline regressions reproduce null local-origin selection and null
owner-context reconstruction. `ScopeManager.findOriginatingRuntime` now selects
the local service only when it is a Runtime and its ID equals the explicit requested
origin. It then uses the existing authorized configuration lookup; it does not add
a bypass, impersonate a caller, discover services from header URLs or select a
different Runtime. Remote advertised-client lookup and status refresh remain as
before. Missing origin still fails closed; private denial still propagates before
any context/session declaration. Descriptor identity checks remain in place.

Expanded tests cover exact host, cold owner, wrong/missing origin, persisted lookup
denial, missing/wrong context and an advertised duplicate client. The existing
cached-origin CI suite includes these cases and retains unexcluded changed-code
coverage checks. The51-test expanded suite (eight new cold-scope cases) passes;
both changed executable lines and all four branch outcomes are covered without
exclusions. The13-module webui/service/graph package and seven current declared
closures pass. The initial candidate's one test-only object-identity assertion
failure remains retained; request-scoped caller identity, not shared object identity,
is the contract. Packaging skips tests after the verified suite.

Fresh graph-retaining restart acceptance in both creation orders, independent
cleanup and final CI/review remain pending. No credential, membership,
scientific-algorithm or dependency rule changes.
This does not establish two deployed Runtime or complete issue89 acceptance.
