# Contribution review: PR #102 tooling-only scope

## Recommendation

**Ready for maintainer review, conditional on final-candidate checks.** The user
explicitly narrowed this contribution to integration of workflow and acceptance
tooling despite the documented native exporter defect. This is not scientific
milestone acceptance. The existing strict checks and deliverable gates remain
unchanged, and failed live evidence is not converted into a pass.

## Reviewed state

- Repository: `integratedmodelling/klab-services`.
- Target: unchanged `feature/python-experiment-runner` at
  `b130b33ab97aabc1dded0d99a7103810159482c9`.
- Executable candidate: `ced45f17691ce6a78b6a489ecca4a319564c69be`.
- This subsequent pass changes scope/review documentation only. Final commit
  identity and hosted outcomes must be verified before merge.
- Method: `docs/contributor-guides/KLAB_CLI_Contribution_Review.md`. Its evidence
  standards remain in force. Publication and conditional normal merge are user
  authorized; no retarget, history rewrite or check bypass is authorized.

## Capability and boundaries

The integrated tools prepare explicit fixtures, orchestrate scientific checks,
verify an owned-local deployment, execute notebooks, measure correctness-first
throughput and check deliverable evidence. They are useful orchestration and
fail-closed validation infrastructure. They do not supply a compatible scientific
deployment or independently certify scientific correctness merely by being merged.
Scientific dependencies remain optional; services remain authoritative.

## Findings, ordered by consequence

1. **Upstream native-export defect; disclosed nonblocking for tooling integration.**
   `tools/workflows/discover_compare_explain.py:88` correctly rejects absent native
   band units. Two Jobs completed in one context; the unchanged TIFF has units
   `(None,)` while the observation reports `m`. Reproduced offline from preserved
   bytes. [Geospatial issue #10](https://github.com/integratedmodelling/klab.component.geospatial/issues/10)
   provides the exact artifact, reproduction and implementation evidence. The
   tooling continues to fail rather than invent units. It remains blocking for
   future scientific acceptance, not for this explicitly narrowed code integration.
2. **Reference declaration error; deferred fixture-development work.** The external
   candidate fixture declared float64, but the exporter contract specifies
   TYPE_FLOAT. Correct it from that contract before another scientific run. No
   failing artifact was copied into the oracle. No supported live fixture is
   bundled or advertised by this PR.
3. **Unrun acceptance; explicit deferred work, not successful results.** Full
   matrix/fidelity, recorded evidence, fresh-process restoration, strengthened
   permissions, scientific notebook, throughput and final delivery remain unproven
   together at this candidate. Merging tooling must not close these requirements.
4. **Original delivery defect corrected in executable candidate.** The adapter now
   actually verifies supplied local deployment evidence before/after acceptance;
   the builder requires the hash-bound report. No trust-me switch was added.

No new blocking tooling defect was demonstrated in this pass. This is not an
exhaustive proof of reliability, security or scientific correctness. The earlier
unexplained #100 ProtocolError remains unexplained; later passing checks are not
claimed as its fix.

## Validation actually performed

| Check | Revision/environment | Status | Establishes / limits |
| --- | --- | --- | --- |
| Unit/integration and existing coverage gates | ced45f176 / Python 3.12 Windows | Passed: 1,678 tests | Engineering behavior; new verifier has 100% lines/branches |
| Focused verifier/tool tests, fatal lint, wheel/sdist | ced45f176 / Windows | Passed | Tooling and package checks, not live scientific acceptance |
| Hosted Python 3.11-3.13, core-only, Java and Jenkins | ced45f176 | Passed | Exact executable candidate CI |
| Slice pre-run deployment verification | ced45f176 / owned local launch | Passed | Process/bytes/service bindings, not remote cryptographic attestation |
| Native export scientific check | ced45f176 / third bounded slice | Failed | Missing band units after 2 completed Jobs; expected failure preserved |
| Owned object and JVM cleanup | third slice | Passed | Scope releases confirmed; 13 owned JVM identities absent; original listeners unchanged |
| Complete scientific acceptance/throughput/delivery | current scope | Deferred, not passed | Retained as future gates, not merge criteria for tooling-only scope |
| Final documentation candidate checks | commit containing this report | Pending at authoring | Must be reverified before merge |

Exact commands: workflow pytest/coverage, `check_feature_coverage.py`, Ruff and
build invocations are defined in `.github/workflows/python-client.yml`. The final
check run and SHA must be linked in the PR status; historical CI does not validate
new executable changes.

## Scientific and lifecycle evidence

The controlled constant checks only support the reported values/grid/CRS/transform;
they do not prove nonuniform orientation, missingness, valid zero or terrain
accuracy. The strict native-unit assertion stopped the scientific journey before
evidence/restoration. No synthetic replacement export or assertion suppression was
used. Mutation intent and failed evidence were retained; no ambiguous write was
replayed. Local deployment verification is operator-reviewed provenance, not a
hostile-author or remote attestation scheme.

## Contributor coordination

The Java exporter defect is filed in its owning component repository, not repaired
inside this Python PR. No maintainer assignment or acceptance is inferred. Further
component changes require separate authorization and review. Upstream resolution
does not by itself satisfy all deferred scientific requirements.

## Smallest next actions

1. Verify final documentation candidate checks and unchanged executable tree.
2. Merge normally only if that candidate meets the narrowed tooling criteria.
3. Keep scientific completion open as listed in the delivery guide; resume it only
   with a compatible exporter and corrected independent fixture.

## Suggested PR description

Integrates workflow, strict acceptance, notebook, deployment-evidence, throughput
and delivery tooling. Explicitly excludes scientific milestone completion from
this merge. Links upstream issue #10, preserves failed live evidence, and records
the deferred original scientific gates. No test/validator or runtime readiness
claim is weakened to obtain the merge.
