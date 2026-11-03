# Exact accepted-input restoration (under verification)

This is an isolated lifecycle correction based on integration `5785c1baa`, not a
semantic-rule change, dependency/authentication change, deployment or scientific
execution approval. No Runtime Job is submitted by its regressions.

## Reproduced defect

Read-only service probe15 captured provider-selection keys A/B/A with all other
preflight fields unchanged. Candidate outcomes VALID/FAILED/VALID advanced accepted
revisions1/1/2. The last build used the earlier exact-success authority-bootstrap
cache entry and produced the same semantic-content fingerprint. A native resolving
call failed at the accepted-generation-change guard while revision1 became2;
seven later calls succeeded. No sessions, contexts or scientific Jobs were created.
All14 recorded JVM identities were independently absent after cleanup and3,905
backend hashes matched. The cause of temporary provider-selection unavailability
and the full historical Job failure chain remain unproven.

The before-fix regression is retained: restoring exact A after failed B expected
accepted revision1 but obtained2. This establishes avoidable republication of an
actor that was still accepted, not permission to weaken the generation guard.

## Bounded correction

The runtime associates full preflight keys with the exact immutable inputs it
submitted. A fresh source/provider-selection snapshot may restore only the input
that still owns the accepted slot and whose complete key matches. Source order,
source hashes, dependency/configuration revision, authority catalog, provider
selection, bindings and owner/rights provenance remain part of that comparison.
Semantic-content fingerprint equality is deliberately **not** a reuse criterion.

The coordinator rechecks the existing publication guard outside its monitor and
then rechecks exact accepted-input identity before reconciling current status.
Superseded candidate work is cancelled; the accepted actor/revision and live leases
are unchanged. Its compiler diagnostics and cleanup-failure evidence are retained.
Unknown/unscoped input identities cannot acquire restoration proof. Proof tracking
is bounded to accepted/latest inputs and cleared on explicit invalidation/closure.

Changed provenance and stale publication proof must take the ordinary validation
path. Explicit invalidation must force revalidation. No scientific request is
automatically retried, no candidate publication is globally disabled, and the
in-flight generation-change guard remains intact for actual replacements.

## Current gates

The baseline failed as expected and is preserved outside the checkout. The initial
candidate regression then passed50 focused cases. The first pass's owner negative
fixture omitted its required credential ID; that failure is preserved and the
fixture now uses a valid authenticated identity. The first broader launch had a
new test-only setup error (`lease()` instead of the coordinator's existing
`accepted().orElseThrow()`); it is retained and corrected without changing production
APIs. The subsequent instrumented broader run completed86 cases:83 pass, with
three failures in unchanged `SemanticValidationPublicationTest`.

A clean detached `5785c1baa` control independently reproduces exactly those three
test/kind/type/message signatures: a duplicate supplied notification and two
uninitialized mocked `monotoneRefinements()` errors. Its remaining five cases pass.
The full failed commands/XML reports remain preserved. No baseline test is deleted,
rewritten, declared passing or repaired in this correction. The dedicated positive
CI selection covers the restoration/candidate/lease/capability cases; that selection
is not a claim that the broader publication suite passes.

All14 new restoration/coordinator cases in that broader run pass, including guard
denial, mid-guard owner change, replacement/closure races, cancelled candidate cleanup,
bounded proof tracking and in-flight resolving without replay. All changed executable
lines were covered in the first report, with remaining branch gaps being addressed
without coverage exclusions. A fresh positive run now passes79 cases. All39 changed
executable lines and62 changed-line branch outcomes are covered, with100% unexcluded
instruction/line/branch coverage for all four new helpers. The13-module service/graph
package build passes. Packaging used explicit `skipTests` after the verified test run;
it is not an additional full test-suite pass.

## Integrated and real-service closeout

Executable candidate `b5049c7949e38528a92e88d22387da3ba59004ac` integrates the now-merged
PR120/121 diagnostics. Its old A/B/A characterization is updated to require exact
restoration without republication. All120 integrated diagnostics/restoration/gate/
index cases pass. General build, both diagnostic jobs, both restoration jobs with
coverage gates, and Jenkins pass for that executable head.

The first live setup (run16) stopped before services/imports/sessions/contexts/Jobs
because the fresh core JAR lacked the dashboard bundle required by the unchanged
harness. Its KeyError, manifest and independently absent two setup JVMs are retained.
The existing pinned `webui` profile (`npm ci`, no upgrades) builds the required
dashboard. Fresh launch attestation matches2,607 compiled classes to packaged JARs,
seven current declared Maven closures,3,891 launch inputs and1,153 main sources.
Historical dependency closures are not merged and frontend advisories are not fixed.

Fresh read-only run17 passes all eight native resolving calls,10 concepts each,
with accepted revision1 throughout. The trace contains a successful candidate and
a later failed provider-selection candidate. Final status is bean/logical VALID,
accepted revision1 and input generation9, without a second accepted publication.
This verifies restoration under the reproduced transition, not merely a quiet run.
Runtime context listing/HTTP cleanup pass. All14 JVM identities are independently
absent; all3,891 launch hashes,1,153 source hashes and fixture launch hashes match.
No matching owned visible windows remain; unrelated processes/windows are untouched.
There are zero sessions, contexts, publication requests or scientific Jobs.

Verification result SHA-256:
`f3a3560879250232727d3a8015c922c5ddf3073fcbf35ec4ffba619b077ce3cd`.
Native Reasoner log SHA-256:
`1c6d52d389aa2b5a9e86c5498c5a62ee03bfa87d074926931ca6a322904f89c0`.

The bounded lifecycle implementation and offline/read-only acceptance are complete;
final documentation-head CI and merge review remain. This is author/agent-conducted
review, not independent maintainer approval. It does not establish numerical or
automatic-hook activity acceptance for PR108/117/118, repair the original bare
Elevation query, or claim scientific reproduction. Those runs remain preserved;
the next work is integration into PR117 and its separately bounded acceptance.
