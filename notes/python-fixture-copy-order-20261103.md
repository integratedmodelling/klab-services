# Deterministic fixture copy order

Base e09a7de7cac6b48526fd8b7ac72879abb36a77a3. Small prerequisite bugfix,
separate from Job binding and read-only source-preview features.

PR139's initial push Python3.11/3.12/3.13 runs failed
`test_write_failure_preserves_partial_output_and_original_inputs`; its PR run
passed. Raw failed logs from run41674217767 are retained in sibling
`python-job-binding-evidence-20261103/python-push-ci-failure.log`.
The test assumes `imod/agency.kwv` is written before the injected failure at
`imod/earth.kwv`, but `_collect` inherited filesystem rglob order and `_prepare`
iterated that dict without sorting. This is an internal deterministic-order
defect, not Java incompatibility or evidence that all Python checks were green.

Reproduction requires no live service or Linux filesystem: parameterize the
existing failure test with sorted and reversed `_collect` results. On unchanged
production code, normal order passed and reversed order failed at the same
missing-agency assertion. The before-fix log is retained in sibling
`python-fixture-copy-order-evidence-20261103/before-fix-reversed-order.log`.

The fix sorts copy entries by relative path. It does not change fixture bytes,
contracts, hashes, source tree, numerical oracle, receipt meaning, mutation
replay policy, service startup or scientific execution. Both forced orders now
preserve the same deterministic partial prefix, do not publish a success receipt,
and retain all original inputs after the injected write failure. Failed output
is not deleted, retried or reinterpreted as successful.

From `python-client/`, install the declared CI extras, then:

```powershell
python -m pytest -q tests/unit/test_prepare_runtime_fixture.py -k write_failure_preserves
python -m pytest -q tests/unit tests/integration --cov=klab_client --cov=prepare_runtime_fixture --cov-branch --cov-report=json
```

Local verification:47 fixture tests and2,209 full offline tests PASS; all13
declared Python gates (nine coverage gates), lint and wheel/sdist PASS. Gates
were invoked from their declared working directories. Exact commands and logs
are retained in the evidence sibling's `gates.json` and `gate-*.log` files.
No service was launched or real Runtime/scientific Job created/resumed/cancelled.
This is author verification, not independent maintainer approval. Full Java
acceptance remains blocked by the documented EXTENT/ATTRIBUTE dependency mismatch;
this separate PR must not bypass or merge over those failing checks.
