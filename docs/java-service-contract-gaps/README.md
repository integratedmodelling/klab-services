# Java contract evidence

Open **[JAVA_GAPS_FOR_FERDINANDO.md](JAVA_GAPS_FOR_FERDINANDO.md)** for the single
canonical human-facing inventory, setup, commands, per-gap contracts, expected and
actual results, native blockers and issue crosswalk.

`gaps.yaml` is the synchronized machine-readable index, not an independent master
narrative. `schema.json`, `id-reservations.json`, historical acceptance/issue JSON,
source inventories, fixtures and `reproductions/` are supporting evidence.
Historical IDs/aliases and failed native receipts must not be removed or recycled.

From the repository root, with the prerequisites in the canonical quick start:

```sh
python developer-tools/java-gap-governance/govern.py validate
python developer-tools/java-gap-governance/govern.py generate --check
python developer-tools/java-gap-governance/source_coverage.py
python -m pytest -q developer-tools/java-gap-governance/tests
```

Generation never replaces canonical prose. New or changed Java-facing assumptions
must update the canonical document, synchronized index and runnable tests in the
same PR. Native NOT_RUN is not a pass; simulation, source inspection and historical
native observations remain separate evidence categories. No issue is closed or
reassigned automatically by these tools.
