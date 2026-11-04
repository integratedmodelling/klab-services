# Contribution review: deterministic Runtime fixture copying (#140)

Current disposition: **MERGED** at `071f4bfd9368f2165e7821b3ce0d504c70ce02dd`.
Actual refreshed-base installed-wheel tests and hosted/Jenkins checks passed;
author-agent review addendum is recorded on GitHub and in `PR_CONTRIBUTOR_REVIEWS.md`.
The recommendation below is the preserved historical pre-integration checkpoint,
not a current blocker. Original frozen fixture incompatibility remains issue#193.

## Recommendation

**Needs contract/coordination evidence for merge**; no demonstrated defect found
in the bounded deterministic-copy correction. General Java/Jenkins and combined
candidate gates remain unresolved. This is author-agent review, not maintainer approval.

## Reviewed state

- Base `e09a7de7cac6b48526fd8b7ac72879abb36a77a3`.
- Candidate `28fb28bb4e5e6a000eaeecffb1b3e9e258863f4b`, clean at test launch.
- Scope: fixture copy order, partial failure evidence; no Java or numerical change.
- Changed code inspected read-only before tests. Original immutable byte/oracle
  validation, exclusive writes and no-retry/no-rollback behavior remain unchanged.

## Capability and boundaries

Sorting the relative filename/body entries makes the successfully copied prefix
deterministic when a later write fails. Both normal/reversed collection orders are
tested. It does not provide successful semantic admission or scientific execution.

## Findings

No blocking code defect found in the narrow diff. Sorting occurs after bounded
collection and before exclusive destination writes. Relative-path validation and
byte pre/postimage checks remain in force. No authorization or remote lifecycle
behavior is changed; these dimensions are N/A for this fixture-only correction.

## Validation actually performed

`python -m pytest -q python-client/tests/unit/test_prepare_runtime_fixture.py`
on the exact candidate: **PASSED** (47 tests; inspect captured stdout).
Python 3.12.7 from the pre-existing agent-AMQP environment. This is not a clean
installed-wheel test or a full Python/Java suite pass. An initial attempt with the
old main-checkout venv was **BLOCKED** before collection (`httpx` absent), and is
retained separately; no product regression or pass is inferred from that attempt.

Evidence: `klab-pr140-focused-readyenv-20261104/run.json` under the local evidence
root. Combined-base tests and current hosted checks must pass before acceptance.

## Smallest next actions

Integrate the backend baseline, retest this focused contribution on that base,
verify required CI/Jenkins and maintainer requirements, then merge through GitHub.
Keep #139 and #141 as focused dependents, not folded into this bugfix.
