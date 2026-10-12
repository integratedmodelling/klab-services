# Stream native observation exports to files

Tracking issue: [#91](https://github.com/integratedmodelling/klab-services/issues/91).
Base: combined Python integration `90bebb5f35e60ad002f106c8d655e66d7e00d89d`.
This implements the existing-exporter/file-transfer slice of the roadmap's Phase 2.

## Usage

```python
formats = client.export_formats(observation)
for format in formats:
    print(format.schema_id, format.media_type, format.extensions)

result = observation.export_to_file(
    "region.geojson", media_type="application/geo+json", max_bytes=10_000_000
)
print(result.path, result.bytes_written, result.media_type, result.schema_id)
```

`Client.export_to_file(observation, path, ...)` is equivalent. A bound committed
native ObservationImpl is required; bare IDs, unbound objects and detached
mediated views are not accepted. Runtime URL and known client/context identities
are checked before requests, then capabilities establish the current home ID.
Discovery contacts only the configured Runtime and never follows advertised URLs.

`ExportFormat` and `ExportResult` are frozen descriptions, not local authority.
The exact advertised media-type spelling is used because the Java schema selector
matches its string. Duplicate overloads of one schema ID are deduplicated, while
different schema IDs for one requested media type are rejected as ambiguous:
the route cannot select a schema ID. An advertisement does not guarantee that
the exporter applies to every observation; the server decides asset applicability.

The route is `GET /export/OBSERVATION/{context-qualified-numeric-id}`, with the
existing scope header and Accept negotiation. Java `BaseService.resolveUrn` /
`ObservationImpl.idFromUrn` support this numeric identity even for named assets.
No new server exporter, binary job protocol or per-cell export loop is introduced.

The generic example restores an existing observation reference without releasing it:

```powershell
# Configure KLAB_RUNTIME_URL/TOKEN and the deployment's required peers first.
python examples/stream_export.py observation.json region.geojson --media-type application/geo+json
```

## Streaming and file policy

- Export body memory is bounded to fixed **64 KiB** chunks. The existing small
  JSON capabilities request still uses the buffered metadata transport.
- The client requests identity encoding, preserves raw exported bytes, checks
  the returned media type/required parameters and rejects unexpected compression
  or partial status. A declared Content-Length must match the delivered bytes.
- Without a declared length/checksum, orderly HTTP completion does not prove
  application-level format completeness. The client does not invent a checksum,
  revision, CRS, units, dtype, coordinates or validity metadata.
- `max_bytes` is an optional positive delivered-byte limit. Network operations
  retain the configured per-read timeout; this is not an overall stream deadline.
- The destination parent must already exist. Default no-clobber publication uses
  atomic hard-link creation; explicit `overwrite=True` uses atomic replace.
  The same-directory temporary file is fully written, synced and closed first.
- Failed/interrupted downloads leave existing files unchanged and close the
  response/remove temporary files. A cleanup failure after publication is reported
  honestly: inspect the destination, which may contain the complete result.
- Unsupported hard-link filesystems fail explicitly. POSIX temporary permissions
  are restrictive; Windows access remains governed by filesystem ACLs.

No automatic retry, remote cancel, resubmit or release is performed. ExportIOError
is a local filesystem failure. HTTP 400/401/403/404/422 retain existing distinct
errors; other statuses (including redirects) are ServerError. Binary error bodies
are not buffered or echoed. Midstream network failures are TransportError; invalid
representation/length/stream state is ProtocolError.

## Genuine exporter acceptance

The isolated reference uses the official published **klab.component.geospatial**
component archive `1.0-20260926.083046-222`, SHA-256
`e7d470f69943e93ce48e9669bad6a41b618dfbe0e7bc748076e010583daef644`.
It advertises GeoJSON, GeoTIFF and PNG; the accepted example uses **GeoJSON for
a substantial earth:Region**, not a numeric raster or rendered PNG.

Acquire that artifact through the deployment's normal component distribution.
The test runner accepts the supplied archive explicitly and copies it only into
fresh TEST Resources/Runtime plugin directories. It does not install production
components or fetch/follow component URLs from a file or capability response.

```powershell
python tools/run_export_acceptance.py --server-repo C:\explicit-server-checkout --java C:\jdk\bin\java.exe --worldview C:\pinned-imod --component C:\klab.component.geospatial-1.0-20260926.083046-222-component.kar --state-dir C:\existing-parent\new-export-state
```

Server checkout must already have compiled test fixtures/classpaths. The tested
server integration is `244883d7b04f1eae2cc22e799575d33a9bdb2a43` (draft PR #90,
issue #89); it is not silently substituted for this feature's integration base.
The helper records both Git states and client source hashes, and confirms owned
process cleanup. Fixture credentials/state remain outside the repository.

The live test compares asset identity and every exported polygon corner against
an independent inverse Web Mercator calculation using radius 6378137 m. The
pinned export quantizes this fixture to six decimal degrees; the declared
`1e-6`-degree tolerance permits that precision, not swapped axes or wrong location.
This substantial GeoJSON does not supply raster values, numeric units or a
validity mask: zero/missingness for raster data and full scientific metadata are
N/A here, not synthesized or certified.

Current geospatial source tip `8d4ef866` fails to compile against the selected
service API because its deprecated Reference.Exporter annotation is absent.
That setup failure was recorded as Blocked. Acceptance uses the unmodified
published archive above, not a fake exporter or a patched scientific algorithm.

## Focused checks and limits

`test_exports.py` verifies incremental delivery (including a generated 64 MiB
response with under 2 MiB traced client allocation), exact bytes, partial/zero
OS writes, truncation, media mismatch, encoding/status failures, byte limits,
interruption, HTTP/error redaction, file/publication/cleanup errors, schema
malformation/ambiguity and pre-network identity/policy rejection.

Run the full maintained suite, all three module gates, targeted lint and packaging:

```powershell
python -m pytest -q --cov=klab_client --cov-branch --cov-report=json
python tools/check_feature_coverage.py coverage.json --base 90bebb5f35e60ad002f106c8d655e66d7e00d89d --new-module src/klab_client/exports.py
python -m ruff check src tests examples tools/run_export_acceptance.py --select E9,F63,F7,F82
python -m build
```

CI retains the earlier module gates and adds the same unexcluded 100% line/branch
gate for this new production module. This threshold does not establish scientific
correctness, general exporter coverage or maintainer acceptance.

No temporal/semantic parameter facade, general chunk/window protocol, typed
resolution diagnostics, resource catalog, provenance browser, scientific adapter
or export re-encoding is delivered. Server-side exporter generation can buffer
or materialize data; only client download memory is bounded by this contribution.
Other advertised representations require their own compatibility/scientific checks.
