# Proposal-review records

Backend proposal/ontology review contracts and implementation evidence, grouped
here from the repository root. These records describe their original checkpoints;
reported test results are historical, not checks rerun during this organization.

- [Proposal-review contract](PROPOSAL_REVIEW_CONTRACT.md): protocol and integration boundaries.
- [Dossier mapping](DOSSIER_MAPPING.md): research-to-proposal mapping and incomplete conversions.
- [Schema validation increment](SCHEMA_VALIDATION_INCREMENT.md): later schema-provider implementation and verification.
- [Validation increment](VALIDATION_INCREMENT.md): earlier validation changes and offline pilot.
- [Backend result](BACKEND_RESULT.md): original backend handoff and test report.
- [Review evidence](review-evidence/): preserved reports, diagnostic output and historical characterization.

## Tooling

From the repository root, run the offline Java review suites with:

```powershell
.\tools\review\run-review-tests.ps1
# Optional isolated Maven cache configuration:
.\tools\review\run-review-tests.ps1 -LocalRepository '<task-local-repository>' -ReadOnlyRepository '<existing-cache>'
```

The runner resolves the repository root independently of your current directory.
Its existing sibling `test-home` and `backend-final-tests.log` locations are unchanged.
The offline Python package validator remains at
[`tools/review/validate_package.py`](../../tools/review/validate_package.py).

All five reports formerly lived at the repository root; `review-evidence/` formerly
lived there too. Relative evidence references in these reports still resolve together.
Original machine-specific paths inside historical evidence are retained as provenance.

[Documentation index](../README.md)
