# Working on k.LAB services

## Before each task

Trace whether the change can affect observation, resolution, catalog publication, or validation
after a save or restart. Include indirect callers of shared DTOs, serializers, client facades,
cache keys and dependency upgrades. For an unrelated change, the performance check ends here.

For an affected path, identify **how often the new work runs** before editing. A lookup inside
the model-port loop can run for every port of every candidate for every member. Pick a counter
that would expose that multiplication and retain a comparable known-good measurement.

## While implementing

- **Source validation.** Reuse the namespace admission for its exact model during publication.
  Reuse a persisted static result when the source, validator, caller, worldview and dependency
  proof match. Keep executable admission bound to the actual observation context.
- **Reasoning and resolution.** Batch model ports in the retained Reasoner cycle. Use a
  pairwise OWL test when the caller needs one relationship. Defer ranking until discovery
  produces candidates. Account for any new service call or hierarchy traversal inside a loop.
- **Transport and optional features.** Request a manifest or worldview routing header when
  that is all the caller consumes. Check payload size as well as request count. Check whether
  a feature or trace is enabled before doing the lookup, serialization or reasoning it needs.
- **Caching.** Canonicalize unordered inputs and preserve meaningful order. Tie reuse and
  invalidation to the state the answer depends on. A no-op should leave an eligible proof
  reusable. For member results, account for geometry,
  authority access, transaction state and rollback before sharing even a `NO_MODEL` answer.

## Before completing affected work

Run the check that exercises the changed path. Report the workload and outcomes, the chosen
counter before and after, and the warm median when timing applies. Fix unexplained extra work
on unchanged input. For an intentional increase, record the requirement and measured cost
alongside the change. [Benchmark selection and comparison rules](docs/RESOLUTION_PROFILING.md#checking-changes-for-regressions)
