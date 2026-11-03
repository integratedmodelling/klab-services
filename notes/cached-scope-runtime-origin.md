# Explicit Runtime origin on cached scopes

Bounded correction for the cached-origin gap in issue #89, based on merged PR111
`19668515a7307aec4b1e671207f5b2d595572a14`. It is not broader federation acceptance.

The unchanged product baseline fails all six new context/session A/B and B/A
regressions because no authorization exception is thrown for the other Runtime's
same wire ID. Baseline logs/XML are preserved before implementation. Initial test
compilation mistakes (unavailable parameterized-test imports and generic inference)
are separately retained, not called product reproductions. No dependency is added.

`ScopeManager.getScope(authorization, ..., runtimeId)` previously applied ACLs on
cached IDs without checking that explicit Runtime origin. Direct cached
`getOrCreateSessionScope` lookup had the same gap, including the runtime-verified
parent reconstruction path. Both now check the supplied origin before deriving
request authority or changing parent metadata. Mismatch, blank supplied origins
and unknown cached hosts fail with the existing authorization exception. No cached
scope is evicted, replaced, rebound or automatically reconstructed/replayed.

A matching origin does not bypass existing ACLs. Missing-origin legacy behavior
is unchanged: this is an affinity check for an explicitly supplied origin, not a
new authentication protocol or a claim that omitted provenance is established.
User-scope lookup is not treated as a Runtime-hosted session. The cache is still
keyed by wire ID; this fix denies collisions rather than enabling simultaneous
multi-origin composition or silently selecting another host.

The expanded scope suite passes43 tests, including13 new origin cases. All eight
changed executable lines, eight branch outcomes and all new helper counters have
zero misses, without exclusions. The13-module webui/service/graph package build and
seven fresh declared runtime dependency closures pass; packaging explicitly skips
tests after the verified suite and is not another full-suite result.

Fresh source-matched HTTP run22 passes both context and session sequences:
matching origin200, wrong origin403, matching origin200. This shows denial does
not evict or poison the accepted cached scope. It creates exactly one owned session
and context with two confirmed mutation receipts and zero scientific Job submissions.
Context/session release, Runtime empty-context listing, publisher socket absence
and HTTP cleanup all pass. All14 JVM identities are independently absent;3,898
launch hashes,1,154 main-source hashes and all fixture/SDK launch hashes match.
No matching owned visible windows remain; unrelated processes/windows are untouched.
Launch attestation matches2,612 compiled classes to packaged JARs and uses seven
fresh current Maven closures. Executable candidate: `949b977382d1a9f6047bce9baacc28d7761f1032`.

Served result SHA-256:
`269a4f76b169fc6336ed30ef11453e329929f26b70e52438b58f4162f833ca6e`.
Mutation-journal SHA-256:
`6fdd389b5c5b58a7dc2dddd9ab5de443a210d0c1b8e96927588e27235915548c`.

Final documentation-head CI and review remain required. Review is author/agent-
conducted, not independent maintainer approval. See [sanitized results](cached-scope-runtime-origin-results-20261103.json).
Two mocked Runtime identities are not two deployed
Runtimes. Issue89's nonlocal memberships/native-client/restart/ACL/scale gates and
issue124's composition/migration contracts remain open. No scientific Job, model,
algorithm, dependency, credential or production deployment change is introduced.
