# Public keyed dictionary snapshot prerequisite

Accepted base891b746f82cc4a7fa5cac3518394688df06d0d8f (PR220). Separate owned
core worktree/branch feature/keyed-dictionary-snapshot-api; no shared targetclasses
or local Maven artifacts may be overwritten. Component baseline a8575ba passed22
Java tests; GDAL JNI unavailable warning retained. No component writer changed.

Add a backward-compatible default DataKey.dictionarySnapshot() returning Optional
empty for legacy/unsupported keys. Actual ConceptDataKey exposes its existing
immutable durable Dictionary snapshot, and read-only wrappers expose their captured
snapshot, not the allocator's later state. Preserve exact version/worldview/target/
ordered definition-label-authority entries/fingerprint; missingcode0 is implicit
and not a positive entry. No lookup/reasoner/network/binding/translation/mutation.

An accessor is a metadata prerequisite, not a promise of atomicity with a live
scanner or immunity to concurrent dictionary growth. Consumers must retain their
snapshot and check scanned codes against it, refusing unknown codes. Never infer
semantic identity/worldview/authority from labels or integer arithmetic. No JSON
format, code allocation, persistence schema, dependency, scientific model or wire
endpoint changes. Existing unsupported implementations stay unsupported explicitly.

Tests: legacy default absence; allocator metadata fidelity/immutable detached state;
read-only captured snapshot after append; restored durable metadata without lazy
semantic lookup; real keyed scanners match their planned dictionary. Compile/test
only isolated owned outputs with pinned dependencies; exact review/normalPR/CI/
merge remains required. No artifact publication or categorical Runtime advertisement.
