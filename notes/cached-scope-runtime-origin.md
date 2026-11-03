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

Source-matched bounded HTTP acceptance, final CI and review remain required.
Two mocked Runtime identities are not two deployed
Runtimes. Issue89's nonlocal memberships/native-client/restart/ACL/scale gates and
issue124's composition/migration contracts remain open. No scientific Job, model,
algorithm, dependency, credential or production deployment change is introduced.
