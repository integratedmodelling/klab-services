# Contributor review: actual Resources parser/source receipts

2026-11-05 UTC; CLI/author review, not independent human approval.
Independent branch `feature/python-source-parser-acceptance`, base
`fe46ddc5ec3c188f62cd1d0b0703c6e54a4b9241`; does not stack on Q1's unaccepted code.

## Scope and architecture

Closes a bounded evidence gap between four-family DTO wire compatibility and
actual Resources parsing. Reuses SourcePreviews, current controller/WorkspaceManager
and owned fixture runner; no public API/Java/compiler/dependency changes. Scientist
value: inspect a source receipt with actual service/source identity rather than
assuming JSON round-trip means parsing works. Whole RC-P4.2 stays PARTIAL because
maintained revision and semantic validation are separate obligations.

## Risk-ordered findings / controls

1. No fake acceptance: valid and invalid texts must reach the real parsers, with
   pinned source hashes and service identity. Unit tests only check harness guards.
   Actual four-family outcomes remain NOT_RUN until the isolated service result.
2. Parsers may return diagnostics, null or transport/server errors. Invalid source
   must never become success; the report records refusal error types without
   inventing authoritative fine-grained syntax diagnostics. Positive receipts must
   first succeed so a universally failing service cannot satisfy acceptance.
3. Edge trimming is explicit and both hashes retained; no other source rewrite is
   allowed. Source/worldview tests use new synthetic material, not altered historical
   research. No model weights, scientific values or method are inferred.
4. Only capabilitiesGET and the four parseAssetPOST routes are permitted in the
   workflow audit. No write/submit/import/start/release route is invoked. It creates
   no user scopes/Jobs, so there is no remote scientific work to dispose.
5. Actor source is parsed, not started. The k.Actors grammar/current semantic API
   remain authoritative; a `return 0` example claims no persistent agent lifecycle.
6. Parent private-state ACL, hidden process launches, component pins, no ambiguous
   replay and existing-Java preservation apply. Families are attempted independently
   and failures retained; there is no blanket skip or whole-B acceptance.

## Required validation / merge

Actual named four-family parser check, installed noneditable wheel/source parity,
offline/wire/coverage/lint/package checks, exact candidate identity, final hosted
checks and normal eligible merge remain required. No tests or acceptance are
predeclared passed. Evidence belongs outside Git under `ab-three-study-20261105/`.
The prior failed Q1 runs remain unaccepted and separately retained.
