# Python delivery backend prerequisite — integration review

Author-agent review using the contributor guide; no maintainer approval implied.
Base: `feature/python-experiment-runner` at `e09a7de7cac6b48526fd8b7ac72879abb36a77a3`.
Product candidate before this documentation: `c66007d3c20ece5551d01fd75ec57ff3b3adddee`.

## Scope and ancestry

Promote the existing committed backend integration `5980cb54d` plus actual develop
`196bb9dc142eafc92f4456c5073933fa98ad1c42` to the Python delivery branch. Preserve
all upstream history; no rebase, squash, forced push or new language snapshot.
This resolves the old delivery build's removed EXTENT/ATTRIBUTE API references.
Include the focused selected-query repair from PR #191 as an explicit ancestor.
Merge #191 to develop first, then refresh/retest this prerequisite if develop moves.
No pending Python feature PR #133–#141 is silently folded into this integration.
Their focused branches and acceptance requirements remain separate.

The large delivery delta consists substantially of already-merged upstream Java
work and preserved integration commits, not a claim of an exhaustive fresh review
of every inherited line. Source-compatibility, scope authorization/currentness,
receipt association and rollback are covered by the targeted current-base reactor.
Three feature coverage workflows now compare against current-develop ancestry,
not unrelated upstream changes since ancient parents. All changed executable
integration lines/branches and existing helper counters remain required at 100%.
The local gate checks passed on the Java-identical combined checkout.

## Verified and incomplete gates

- **PASS:** clean candidate `c66007d3c`, JDK21, offline Maven Runtime-server
  dependency reactor with the original selected rollback/security/authorization/
  restoration/configuration suite, 222 tests, zero failures/errors/skips.
- Separate PR #191 clean develop-based source passed 159 selected tests; its exact
  focused hosted selection separately passed 90 tests locally. Counts are not
  interchangeable with the 222-test integration selection.
- **PASS:** fresh noneditable installed wheel at documentation head `1829e0c9a`,
  2,208 offline tests, complete installed Python source hash/inventory equality.
  One expected Rasterio georeferencing warning is retained.
- **Pending:** current GitHub build/Jenkins and appropriate scoped checks. PR #191
  merged normally as develop commit `1e75df4e9`; the subsequent develop merge into
  this branch changed ancestry only (no product tree delta).
- **Still failed:** original immutable ODO hash fixture expects `496bc663…`, while
  current bundled bytes are `5c020dac…`. No original assertion was changed or
  excluded. A separately labelled current-core synthetic fixture is being checked;
  this is not permission to call the old fixture passed on new bytes.
- **Not accepted:** installed-Python real-Java numerical/provenance/recovery workflow.
  A separate owned nonconflicting stack is in progress; Modeler services untouched.

Exact commands/source status/logs/XML are in the local established consolidation
evidence root under `backend-prerequisite-clean-candidate-20261104` and
`backend-installed-python-20261104`. The authoritative milestone ledger remains
`notes/python-consolidation/` on the active consolidation branch.

Recommendation: **draft prerequisite, not merge-ready** until applicable gates
finish and the frozen/current fixture contract is explicitly reconciled. Do not
hold an independently qualifying focused PR merely because this integration or
scientific gate remains unresolved.
