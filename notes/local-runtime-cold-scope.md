# Cold owner scope restoration on the exact hosting Runtime

**Bounded implementation and fresh restart acceptance PASS in both creation
orders. Final documentation-head CI and merge review pending.** Historical failures
below are retained, not relabelled by the later success.

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

## Preserved follow-up failure

Run29 confirms the cold-owner request correction: all four native probes and all
four owner/Runtime/nonlocal-group descriptor checks pass, as does ordinary-member
session-release denial. Overall it still FAILS: the cold context's disposal returns
HTTP200/false, three earlier releases confirm and session disposal is withheld.
All19 recorded JVM identities are independently absent; all launch/source/fixture
hashes match. The stopped isolated graph and unconfirmed disposal remain retained,
never replayed. Empty live-context listing is not persisted-disposal evidence.
Result SHA-256: `65527ed46548d6edd93694e232dc30372afc42ecc73a30d2a20045f845291f85`.

Executable8232a8d9f CI passes but is insufficient for acceptance. A narrowly bounded
diagnostic now logs disposal exception type and at most eight stack locations,
without exception messages, context payloads or credentials. Release still returns
false on failure and never retries. The51 scope tests plus10 root-release tests,
changed-code coverage in both modules, exception-message exclusion and13-module
packaging pass. At that stage a fresh diagnostic run was required to establish the
cause; those checks alone did not authorize merge or restart acceptance.

Run31 then identifies the disposal failure: `KlabServiceAccessException` from
`ServiceUserScope.getService` at `ServiceContextScope.closeContextResources:1061`.
The cold reconstructed root has no Runtime service for its final manager release.
A focused pre-correction regression reproduces exactly that exception after twin
disposal. Run31's false release and withheld session disposal remain preserved,
with all19 JVM identities absent and launch/source/fixture hashes unchanged.
Its result SHA-256 is `b015088475dcba90cc62f64e9e062118c5faad56d9100a16accb30a0bd45576c`.

The root is now bound to the exact local host after copying parent services, only
when the resolved originating Runtime is the local service itself. This is a
lifecycle binding on the registered root, not a user advertisement, remote discovery
or grant of caller authority. Remote-origin paths remain unchanged. The52 scope
tests plus10 release tests now pass; all six changed executable lines and six
branch outcomes across both modules are covered without exclusions. Exception-
message exclusion and13-module packaging pass. A fixture-status omission in the
first binding-candidate test remains preserved separately.

## Accepted fresh restart/disposal results

Executable `6f509b2d9cdaa810e45c8f4697fec40fb805f118` passes run32 (Alice-first)
and run33 (Bob-first). Each creates one session and four contexts, then explicitly
restarts only the owned Runtime while retaining the graph process and Runtime ID.
Post-restart owner and public-outsider native reconnects, all four cold scoped
owner/Runtime/group descriptor checks, exact local parents, four positive native
reads and five HTTP403 denials pass. Ordinary members cannot release the shared
session. The full pre/post registration-log inventory has one session, no auxiliary.

Each run confirms four context disposals and privileged final session disposal.
All four manager releases are independently present in Runtime logs; no disposal
failure diagnostic occurs. Runtime context listing and cleanup checks pass.
All19 JVM identities are independently absent;3,898 launch hashes,1,154 main-source
hashes and fixture/client launch hashes match. No matching owned visible windows
remain; unrelated processes/windows are untouched. Zero scientific Jobs and no
automatic mutation replay occur. Attestation matches2,612 class bytes to packaged
JARs and seven fresh current declared closures; no historical closures are mixed.

Run32 result SHA-256:
`34f1326ea0dff6ea53630fe7ee41a4e13f1fa6904ad2cab795b7b59e72d46862`.
Run33 result SHA-256:
`be41606e44fbb7add4b575cd21d8bb945442747beddfe59cbfad7cdda0b352ff`.

Earlier28/29/31 failure records and stopped graphs remain retained; their unknown
historical disposal is not inferred from later fresh runs. This is controlled
single-Runtime graph-retaining restart acceptance, not crash atomicity, scale,
scientific acceptance or two-deployment validation. Other issue89 gates remain open.
Review is author/agent-conducted, not independent maintainer approval. See
[sanitized results](local-runtime-cold-scope-results-20261103.json).
