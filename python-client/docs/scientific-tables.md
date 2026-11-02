# Indexed scientific reads as pandas tables

This feature adds a bounded **local view of existing scientific reads**. It does
not request a new calculation, normalize units, infer coordinates, repair exports,
port an ecological algorithm or complete the broader scientific milestone.

Install `klab-python-client[tabular]` (pandas remains optional). The core package
can import `ScientificDataOrigin` and `to_dataframe` without importing pandas,
NumPy, xarray or Rasterio. Unsupported categorical/unknown primitives fail rather
than being presented as numerical measurements.

## Use in a script or notebook

```python
from klab_client import to_dataframe

# observation is a computed, authorized bound result from the existing Client.
data = observation.fetch_data([0, 4, 4], curve="D2_YX")
frame = to_dataframe(data)  # Equivalent: data.to_dataframe()
print(frame)
print(frame.attrs["klab"])
```

With AsyncClient, await the read: `data = await observation.fetch_data(...)`.
Table conversion is local and deliberately synchronous; no network, background
loop or worker process is started by the converter.

The supplied offsets are **not** longitude/latitude, array indices in a guessed
reshape or timestamps. Choose the explicit fill curve and any temporal slice
according to the actual storage contract. The existing point-read limit stays
1–256 offsets. There is no new bulk/window, lazy or generic time-series API here.

## Data contract

Columns, in order:

| Column | Meaning |
| --- | --- |
| `offset` | Exact requested nonnegative offsets, with caller ordering and duplicates retained |
| `value` | Original decoded scalar objects; Python integers/Decimal/bool/text/null, not inferred floats |
| `wire_missing` | True only for the explicit `null` point-response token, not `pandas.isna` or a guessed validity mask |
| `wire_text` | Original scalar response text, retaining formatting/precision information |

`offset`, `value` and `wire_text` use object dtype. This avoids silently rounding
integers through float64, converting False/zero to missing, or losing Decimal
precision because one row is null. Nonfinite Decimal values remain nonfinite
values with their original wire text; the converter does not reclassify them as
explicit nulls. Scientific quality/finite-value checks remain the caller's task.

Primitive type and original decoded text must agree. Already transformed or
inconsistent manually constructed ScientificData cannot masquerade as an unchanged
source read. Supported declared artifact types are NUMBER, BOOLEAN and TEXT.
CONCEPT or a known CATEGORIZATION activity (even with numeric storage) needs a verified dictionary and worldview commitment before
an appropriate semantic categorical adapter can be supplied. Unknown type is not
inferred from Python values. A primitive NUMBER is not by itself proof of a
measurement's scientific meaning; unknown contextualization remains None.

`frame.attrs['klab']` snapshots requested/source observable and units, artifact
type/contextualization, curve, explicit slice, supplied geometry, observation ID and the optional
origin. No axis orientation, world coordinate, date, range/currency conversion,
dictionary, committed revision or cross-fetch consistency is invented. Missing
metadata remains None. Geometry describes the original observation support, not
the sampled rows' grid or a generated coordinate index.

## Origin and consistency

New sync and async indexed reads include an immutable `ScientificDataOrigin`:
configured Runtime URL, configured/cached known Runtime ID (or None), root context and focused scope.
The origin is captured before the first point request and compared afterward;
identity-knowledge drift or configured credential material in it fails closed.
This does not establish a storage revision lease, cryptographic provenance or
unchanging values across requests. The existing service/authorization remains
authoritative; origin metadata and manually constructed DTOs grant no permission.

Legacy/manually constructed ScientificData defaults to `origin=None`,
`artifact_type=None` and `contextualization=None`. No origin is reconstructed from a bare numerical observation
ID. Set a declared type only when supported by actual source knowledge; the adapter
does not bless user-authored metadata as server validation.

## Limits and local postprocessing

- Default/max row budget: 256; an exceeded budget fails, never truncates.
- Default serialized metadata budget: 1 MiB, configurable to at most 16 MiB.
  This is not a universal peak-memory estimator for arbitrary caller objects.
- Metadata are JSON-snapshotted so edits to DataFrame.attrs do not modify source
  geometry/slice dictionaries. Source scalar objects are immutable supported types.
- Pandas attributes are ordinary editable metadata, not a semantic arithmetic
  enforcement system. CSV, concatenation and third-party transformations may drop
attributes or coerce types. A CSV alone is not a faithful scientific package.
- Frame creation is local analysis, not a k.LAB-resolved scientific computation.
  Any derived result returned to k.LAB needs a separate import/provenance contract.
- Read origins may contain private context identities. Review metadata and data
  rights before publishing them. Credentials are never included as origin fields.

## Acceptance scope

Tests independently check scalar precision, null/zero/False, duplicates/order,
metadata snapshot isolation, budgets, unsupported categories, malformed wire/type
combinations and optional dependency behavior. Client/HTTP-codec contract tests
(mocked responses) verify sync
and async source-origin capture, unknown Runtime IDs, drift and credential-echo
rejection. These are software/representation checks, not new real-provider or PaL
reproduction evidence. The original native export unit issue #10 remains open.
