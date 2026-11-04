# Read-only integration preflight — 2026-11-04

## Recommendation

**Needs contract/coordination evidence.** Local isolated candidate assembly is
appropriate; remote merge is not yet safe. No maintainer approval is inferred.

## Reviewed state

- Delivery target: `feature/python-experiment-runner`,
  `e09a7de7cac6b48526fd8b7ac72879abb36a77a3`.
- Existing committed upstream-integration checkpoint:
  `5980cb54d9dad826722425fad2c10f3aea56d57a`.
- Latest fetched develop: `196bb9dc142eafc92f4456c5073933fa98ad1c42`.
- Merge base of integration checkpoint/develop:
  `95678100e9d9e7bf19b6fd267a47aa9c6dca55ad` (#190).
- Delivery is an ancestor of the integration checkpoint. Its committed changes
  include the previously merged backend stabilization, original Python contracts,
  fixture repairs and historical evidence notes. The full delivery/checkpoint diff
  spans 395 files; this is not a small new production correction or an exhaustive
  fresh code review of all inherited upstream code.
- Three newer upstream commits change 87 files including model candidate discovery,
  scope/currentness and configuration-recompute contracts. Their inclusion requires
  fresh regression checks; older reports cannot certify them.
- Dirty receipt repairs in the two older worktrees are excluded. They have been
  copied to a private preservation archive and remain untouched at their sources.

## Findings

1. **Blocking build prerequisite:** PR #140's actual GitHub build log
   `41675679902` fails in `LanguageAdapter.java:2006–2007`, referring to removed
   `EXTENT`/`ATTRIBUTE` constants. Existing upstream integration addresses source
   compatibility; replacing historical language JARs is not an acceptable shortcut.
2. **Scientific acceptance uncertainty:** recovered Runtime reports retain an
   issued-query-receipt association error, and Resources reports retain the ODO
   byte oracle mismatch. Preserve both. Their current-candidate outcome and direct
   effect on the chosen Python workflow require reproduction.
3. **Stack drift:** #135 is based on the earlier #134 head `19a0b8437`, not current
   `bcf7a4406`. The missing parent commit changes only a CI-composability test, but
   it still requires integration and retesting rather than a false ancestry claim.
4. **CI/approval:** no submitted reviews on the nine open PRs at baseline; general
   builds fail and Jenkins reports ERROR. Delivery reports unprotected, which does
   not waive these checks or replace maintainer acceptance requirements.

## Validation actually performed

Git ancestry, complete changed-file inventories, live PR bodies/reviews/checks,
issue discussions, repository rules/protection metadata, dependency trees and
effective POM capture. Historical XML reports were copied and recounted, not rerun.
No remote scientific objects, shared services, credentials or source inputs changed.

## Smallest next actions

Assemble a **local-only** candidate retaining the committed upstream-integration
checkpoint and fetched develop. Record merge parents/conflicts, compile and run
affected tests. Resolve scientific prerequisites without changing expected values.
Then review/test each focused PR against that actual integration base. Publish or
merge only after required checks and maintainer requirements are satisfied.
