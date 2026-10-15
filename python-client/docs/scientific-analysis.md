# Analysis-ready results with traceable origins

Install optional readers with `pip install 'klab-python-client[analysis]'`.
Core SDK imports, connections, Jobs, saved handles and native exports do not
require NumPy, Rasterio or xarray. No scientific dependencies are imported by
ordinary SDK import.

## Restore, export, analyze and inspect

```python
from klab_client import (
    Client, load_handle, export_for_analysis, open_raster,
    fetch_provenance, trace_analysis,
)

with Client.from_env() as client:
    observation = client.restore_handle(load_handle("observation.json"))
    artifact = export_for_analysis(observation, "field.tif", max_bytes=64 * 1024**2)
    analysis = open_raster(artifact, max_bytes=128 * 1024**2)
    ds = analysis.dataset
    print(ds.value.where(ds.valid).mean().item())  # zero remains valid
    lineage = fetch_provenance(observation, limit=32)
    traced = trace_analysis(analysis, lineage)
    print(traced.consistency)  # association is NOT a revision-pinned snapshot
    for edge in lineage.links:
        print(edge.relationship, edge.source.kind, edge.source.id, edge.target.id, edge.evidence)
```

The Runtime must advertise the applicable `image/tiff;application=geotiff` exporter.
Numerical correctness requires the [corrected component](../component-patches/geospatial/README.md);
advertisement or HTTP success alone does not certify it. Python never silently
repairs native bytes. Closing local objects does not release remote state.

## Scientific reader contract

`open_raster(path)` opens a local artifact without any connection or implied
observation association. `open_raster(artifact)` additionally checks the captured
export SHA-256 and supplied observation units. A missing expected measurement unit
or conflict rejects. Absent units remain `None`, never inferred from CRS units.

- One single-band plain numerical GeoTIFF, rectilinear affine transform and supplied
  CRS. Rotation/shear, missing/degenerate/identity georeferencing, complex/palette,
  multiband and scaled/offset representations reject explicitly.
  Pixel centers must also be finite and distinct at float64 precision; extreme
  transforms which overflow or collapse adjacent coordinates reject.
- Dataset `value(y,x)` retains native numeric dtype; `valid(y,x)` is a separate
  boolean mask. No implicit float32 conversion, resampling, unit mediation or
  reordering. Coordinates are pixel centers in native row/column order.
- Coordinates are in the supplied CRS, not necessarily longitude/latitude.
  `crs_wkt` and six affine coefficients are retained on `value.attrs`.
- The eager Dataset owns detached mutable arrays. No reader stays open, and
  Dataset inspection does not perform network requests. Caller-selected `.where()`
  operations may themselves coerce integer values; the reader does not.
- `max_bytes` defaults to 256 MiB: preflight accounts for file size, conservative
  value/mask buffers and coordinates. It is an estimated owned-allocation budget,
  not a guarantee on GDAL/native-library or total process RSS. This is not lazy
  server access. Files exceeding the budget reject even if compressed. Do not
  mutate artifacts concurrently with loading.
- Filesystem/Rasterio invalid-file errors propagate. Unsupported representation
  uses `UnsupportedOperationError`; budget/arguments use `InvalidRequestError`;
  contradictory associations use `ProtocolError`.

Digests identify local bytes, not server revision, authenticity or scientific
validity. AnalysisArtifact is not a new persistent handle schema or signature.

## Read-only recorded provenance

`fetch_provenance(observation, limit=64, offset=0, max_response_bytes=1048576)`
rechecks scoped observation membership and fetches one page of incident links.
It performs two read-only POST queries, not scientific submissions. Both responses
are byte-bounded. Graph requests fetch at most `limit + 1` links; the extra determines
`next_offset`. Limit is 1..128; byte budget is at most 16 MiB.

Selected relationships cover creation, typed effects, observation implementation/
dependency links and resolution references. Embedded endpoints expose typed identity
and sanitized immutable raw extensions. Parallel links remain separate; no recursion
can loop on cycles. Unknown endpoint classes or activity states reject explicitly.

Every view is `partial=True`: **one-hop neighborhoods are not complete lineage**.
Inputs beyond that neighborhood, unexposed external resource versions, agent
attribution and commit history are not reconstructed. Empty successful pages mean
no matches for that query/page, not no provenance. HTTP 422 means unsupported;
authentication/authorization, missing membership and backend failures remain errors.

`RESOLVED` and `CONTEXTUALIZED_BY` describe planned resolution/implementation, not
proof that work ran. For execution, inspect the Activity, outcome and actual typed
effect link. Even successful execution is not a portable reproduction recipe.

Refresh by calling `fetch_provenance` again. There is no cache, hidden monitoring
or permanent current-state assertion. Pagination is deterministic only in an
unchanged graph; concurrent writes can cause gaps/overlap. `next_offset=None` does
not mean complete lineage. A retained view may already be stale.

`trace_analysis` requires exactly matching origin-qualified observation Handles.
Export and query remain separately timed operations without a shared authoritative
revision/lease; consistency stays **unknown**. No replay or server-crash durability
is implied. No credentials are forwarded to referenced origins.

## Controlled acceptance

Shared scenario: `tools/workflows/scientific_analysis.py`. A fresh process restores
the original observation, uses the independent native GeoTIFF oracle, checks adapter
values/coordinates/masks/statistics, inspects a successful numerical Activity effect
and explicitly refreshes lineage.

```powershell
python tools/run_export_acceptance.py --server-repo '<compatible-compiled-server>' --java '<jdk21-java.exe>' --worldview '<pinned-worldview>' --component '<corrected-component.jar>' --component-sha256 '<verified-sha256>' --component-source '<corrected-source-checkout>' --state-dir '<fresh-owned-state>' --functional-suite scientific-analysis-provenance --functional-suite connection-access
```

The runner creates/releases owned TEST scopes and stages private TEST credentials
outside the repo; never publish those files. Existing connection-access checks
complement, but do not prove, every new artifact/lineage authorization path.
See the [verification/review record](scientific-analysis-review.md) and
[contract inventory](scientific-analysis-contracts.md) for actual evidence and gates.
