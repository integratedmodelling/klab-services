# Conservative feedback selection

This advisory developer tool does not replace mandatory CI/coverage or release
acceptance. Run from python-client:

```console
python tools/select_scientific_tests.py --changed python-client/docs/typed-observe.md
python tools/select_scientific_tests.py --changed python-client/src/klab_client/dto.py
python tools/select_scientific_tests.py --changed klab.core.api/src/main/java/org/integratedmodelling/klab/api/ServicesAPI.java
python tools/select_scientific_tests.py --git-base EXACT_COMPARISON_SHA
python tools/select_scientific_tests.py --capability CAP-10
python tools/select_scientific_tests.py --full
python -m pytest -q tests/unit tests/integration
```

Output version1JSON includes paths/capabilities, rules, included/excluded families,
exact interpreter/pytest command and NOT_RUN status. Execute the printed command
for feedback; JSON itself is not a test run. Smoke and origin/binding/lifecycle
negatives accompany every focused selection. Unknown/shared DTO/transport/data
decoding/analysis,selector,tests,fixture,CI or ambiguous changes broaden to all
offline tests and require N/S. Declarations can broaden, never suppress path risks.
Documentation-only means Python client explanatory Markdown, not language/ontology
or architecture instructions; unknown docs broaden.

Java changes require affected implemented API review and existing module/CI checks,
not an automatic full Java suite. No Java command is guessed. Required N/S live
work uses the existing owned pinned stack and scientific manifest adapter after
those independent contributions are integrated; selection does not fake a run or
provision a deployment. Release broad validation includes N/S, relevant Java/component
contracts,version/security matrix; offline green alone is insufficient.

CI prints PR-base rationale and still executes every existing full required gate.
Manual/scheduled runs explicitly select broad offline validation. The weekly schedule
does **not** execute L4 on an unprovisioned hosted worker; that remains NOT_RUN until
an authorized pinned Java worker and fixture artifacts are supplied. This is an
explicit remaining acceptance gate, not an always-skipped scientific test.

Same-source serial offline comparison:
`python tools/measure_scientific_selection.py --output FRESH_EXISTING_PARENT/evidence`.
It runs full baseline plus docs/decoder/Java-DTO feedback, retains commands/JUnit/
wall time/counts and exactSHA, never runsJava. Both paths omit coverage; mandatory
coverage remains separate. No speedup may be claimed before measured results.
