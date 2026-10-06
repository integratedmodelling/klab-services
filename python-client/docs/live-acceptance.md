# Real-provider acceptance

The deterministic local fixture validates client/runtime mechanics and known
arithmetic. It is not a substitute for a real-provider dataset reference.

Configure the README's services/issued credentials, compatible elevation model
and dataset, then set `KLAB_ELEVATION_REFERENCE` to independently obtained JSON.
Run from python-client/:

```sh
python -m pytest -o addopts='' -o junit_family=xunit1 -m live -s tests/test_live.py --junitxml=live-acceptance.xml
```

PowerShell: prefix the same arguments with `& .\.venv\Scripts\python.exe`.
The suite compares actual cells across D2_XY/D2_YX/D2_XInvY, units, geometry,
completion and reconnect/readback. It preserves failed fixture IDs and releases
only successful test-owned contexts. Missing configuration/reference fails.

## Reference JSON

| Field | Required value |
|---|---|
| schema_version | 1 |
| semantic_definition / units | geography:Elevation in m / m |
| crs / bounds | EPSG:3857 / `[200000,200500,6000000,6000400]` |
| shape / curve | `[5,4]` / D2_YX |
| slice | `{"type":"INITIALIZATION","key":"0-0","start":0,"end":0}` |
| method | independent-provider or supported-java-workflow |
| source / dataset_id / dataset_revision | Nonblank citation/extraction record and pinned dataset/version |
| sampling / vertical_datum | Explicit provider policy/datum |
| absolute_tolerance_m / relative_tolerance | Finite nonnegative decimal strings justified by provider precision |
| values_yx | Exactly 20 numbers/decimal strings; null for missing cells; at least one valid cell |

Comparison requires exact missingness and
`abs(actual-expected) <= max(absolute_tolerance_m, abs(expected)*relative_tolerance)`.
The report records the reference hash and provenance. Do not derive expected
values from the Python run being validated. Java-workflow agreement proves
transport/workflow compatibility, not independent model accuracy. Index traversal
does not imply north/south orientation; audit provider grid-to-world mapping.
No provider reference has been invented or validated by the local constant-field
test. Keep real-provider results separately from local fixture reports.
