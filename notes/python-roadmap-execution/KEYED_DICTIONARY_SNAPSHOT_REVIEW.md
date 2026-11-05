# Durable dictionary snapshot prerequisite — contributor review

Author/CLI review, not independent human approval. Accepted base
891b746f82cc4a7fa5cac3518394688df06d0d8f; branch
feature/keyed-dictionary-snapshot-api. Contract fd3370842 preceded code.

## Scope and rationale

Categorical export cannot safely infer durable identities from DataKey labels or
assume optional storage histograms always exist. The current allocator already
owns immutable KeyedData.Dictionary snapshots, but public key consumers cannot
obtain them. Expose metadata, not a new dictionary allocator or export format.

DataKey adds a default Optional-returning method: existing implementations need
not implement it and report unsupported metadata explicitly. ConceptDataKey wraps
its existing synchronized snapshot. Its read-only key returns the already-captured
dictionary rather than a new allocator snapshot. No code allocation, context
binding, concept resolution, semantic translation, integer recoding, persistence
schema, serialized JSON, HTTP endpoints or dependency changes. Method is not a
JavaBean getter; Optional is not added to a serialized record or durable field.

Snapshot entries preserve ordered positive codes and exact definition, label,
authority, worldview, target and existing fingerprint. Code zero remains implicit
missing. Immutability does not promise atomicity with a changing live scanner;
the contract requires consumers to reject codes absent from their snapshot.
Legacy absence must not trigger label-based reconstruction. No service-latest,
scientific-migration, publication or universal binary compatibility guarantee.

## Evidence and review checkpoint

Offline isolated Temurin21 reactor command:
`mvnw.cmd -B -ntp -o -pl klab.core.services -am test
"-Dtest=DictionarySnapshotTest,KeyedStorageTest"
"-Dsurefire.failIfNoSpecifiedTests=false"`
PASS: all four reactor projects, 14 tests (4 new/10 existing), zero failures/errors/
skips. All source/test compilation succeeded; existing compiler and Mockito dynamic
agent warnings retained. Initial PowerShell unquoted-property failure occurred
before compilation; fixed invocation, not production behavior or expectations.

New tests establish legacy absence, no binding during empty metadata reads, exact
source metadata, detached immutable snapshots, read-only captured size/code bounds,
no reasoner calls on metadata access, restored Unicode/authority identities with
duplicate labels but distinct semantic identities, and real planned scanner
dictionary/fingerprint equivalence. Existing keyed storage covers persistence,
missingness, translation/authority validation, concurrent producers and resampling.

Broader storage-package regressions are running. Exact-source receipt, final review,
normal final-head PR/hosted checks/eligible merge/ancestry verification remain
pending. No shared classes or Maven artifacts installed; builds stay in owned
worktree targets. No categorical writer/metadata encoding/Runtime registration or
client interop claim. Component baseline 22 tests passed independently; unavailable
GDAL JNI warning does not certify native GDAL plugins.

Source review found no demonstrated important/blocking defect in this metadata-only
slice. Review state is not delivery acceptance. Full A/B, Quality warning, broader
semantic/provider/time/revision matrices and authentic study blockers remain open.

## Broader validation / bounded recommendation

Same isolated reactor, test selector
`-Dtest=org.integratedmodelling.klab.runtime.storage.*Test`: PASS all four projects,
210 tests across 18 storage test classes, zero failures/errors/skips. Includes the
four new tests and ten existing keyed tests; counts overlap the earlier 14-test
pass, they are not additive. Existing warnings retained. `git diff --check` PASS.

Ready for bounded maintainer review after exact-source receipt binding. No remaining
demonstrated important/blocking defect in this metadata-only change. Normal hosted
checks/protections/eligible merge and ancestry/postmerge confirmation still required.
No component consumer or Runtime categorical writer has been modified. Do not use
this prerequisite pass to promote categorical fidelity, interoperability, original
study, full A/B or release acceptance.
