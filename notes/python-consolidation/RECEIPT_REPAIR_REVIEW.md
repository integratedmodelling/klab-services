# Selected query input repair — contributor review

Author-agent review, not maintainer approval. Reviewed against the contribution
guide on the integrated candidate based on `fe17e899c9e2fa039ade982d675e9564b414d6e1`.
Original dirty repair remains untouched in its retained source worktree.

## Contract and scope

The original consumer-rollback reproducer could not compile its selected alias:
the generic cohort-input path correctly rejects an unassociated multi-source
query. Preserve that rejection. Add a separate association capability carrying
the original issued receipt, root transaction, native cohort and exact compiled
consumer/reference port. This is not a new public query authorization mechanism.
Transported bindings are structural scheduler hints, never issuance authority;
execution performs a read-only authorized re-query before accepting them.

## Review findings and corrections

- Imported capability bound receipt issuance to the root transaction; original,
  copied, tampered, duplicate-alias, foreign-root and rollback controls retained.
- Review found a mutation gap between capture and graph storage. Retain the plan
  and recheck consumer identity, child identity, index and alias at storage.
- The new transport test exposed lazy `GeometryImpl.key` cache population changing
  an otherwise identical receipt payload. Normalize only that derived cache field
  for that exact implementation class. All support dimensions, parameters,
  metadata, counts, receipt and semantic-evidence guards remain compared.
- Added fresh-read, count-tamper and changed-support rejection checks; scheduler
  transport checks include alias and native-cohort-header tampering.

## Evidence and recommendation

`C:/Users/lumsd/Downloads/k1/.consolidation-evidence/receipt-cache-corrected-reactor-20261104/`
records the exact dirty diff and new-source hashes, JDK21, offline Maven command,
stdout/stderr and copied XML. **222 tests, zero failures/errors/skips**. Original
rollback assertion passes unchanged. Earlier failures remain in neighboring
`receipt-*` evidence directories. The final consumer-identity strengthening passed
the 43-test focused rerun, and the clean develop-based PR #191 candidate passed
159 wider tests followed by 90 tests in the exact new hosted selection. No installed-client real-Java acceptance
or broad Runtime-suite pass is inferred. No credentials, services, scientific Jobs
or shared-system state were used by these bounded tests.

Published as focused services PR #191; Jenkins passes and GitHub Actions checks
remain queued at the last query. Merge only through normal GitHub checks. This review does not accept the entire
Python milestone or waive the separately frozen ODO fixture mismatch.
