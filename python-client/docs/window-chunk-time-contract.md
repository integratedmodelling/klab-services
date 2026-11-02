# Window/chunk/time access: blocked remote scan contract

**BLOCKED / NOT IMPLEMENTED.** Source audit after PR #107, at
`bb4f62ce01bb41d93f506ee34c19687b401d8640`. The user explicitly authorized moving
past blocked items and returning to them later. This draft PR is an evidence
checkpoint, not a completed access feature or permission to expand production Java.

## Implemented surfaces actually inspected

- `klab.core.api/src/main/java/org/integratedmodelling/klab/api/data/StorageScan.java`:
  internal read plans include sourceRevision, Slice, consumer geometry/partitions,
  budgets, sampling/precision/coverage, dictionary and validity masks. Point reads
  carry sourceId, one Slice, traversal curve, geometry/semantics and one offset.
  These Java contracts must not be confused with published HTTP scan endpoints.
- `klab.core.api/src/main/java/org/integratedmodelling/klab/api/ServicesAPI.java`:
  Runtime READ_VALUE is `/api/v1/observation/value`; sharding/asset/export routes
  also exist. No general remote plan/open/read-block/close/revision-lease protocol
  was located in the inspected Runtime API.
- `klab.services.runtime.server/src/main/java/org/integratedmodelling/klab/services/runtime/server/controllers/RuntimeServerController.java`:
  READ_VALUE accepts StorageScan.Point and returns scalar text. An internal plan
  capability or HTTP exporter does not establish slice discovery/block transport.
- `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/RuntimeService.java`:
  readValue authorizes the source in the selected context and delegates through
  the storage/consumer path. It is not a remote long-lived scan session.
- `python-client/src/klab_client/client.py`:
  existing fetch_data reads 1–256 explicit offsets with checked scalar decoding,
  supplied curve and explicit temporal Slice where required. Source-origin checks
  do not certify an immutable storage revision. PR #106's DataFrame view retains
  that distinction.

This is source inspection, not a live negative capability probe of every deployed
server. No production or shared-worldview change, scientific Job, import or service
launch was performed for this audit. No new Python facade is added.

## Missing acceptance contracts

1. Authorized remote scan-plan/description and immutable source revision identity.
2. Discoverable admitted time slices, including initialization versus temporal
   transitions and events; no guessed dates, keys or inferred interpolation.
3. Bounded block/window selector with declared offset/axis/CRS conventions, cell
   coverage versus missingness, storage dtype and category dictionary commitments.
4. Remote scan lifetime/pinning, expiry, revision conflict and explicit close;
   cancellation/ownership and resume rules across connection or Runtime restart.
5. Versioned transport for values, masks and descriptors with byte/partition limits,
   plus independent nonuniform, missing-cell and valid-zero scientific fixtures.

Repeated point calls can be useful existing functionality, but are not an atomic
time slice or a pinned chunk/window session. Do not manufacture a bulk endpoint,
reconstruct Java resampling/mediation in Python, or advertise lazy xarray/dask
consistency without the required source/version and dimensional contracts.

## Concrete unblock paths

- Supply a maintained remote scan contract/deployment implementing these boundaries
  and its authorized fixture. Build the Python adapter against it, reusing current
  storage semantics rather than porting algorithms.
- Or authorize a separately specified server API addition with maintainer approval,
  including byte/mask/coordinate/revision and lifecycle behavior.
- Or explicitly choose a smaller non-snapshot point-batch convenience feature,
  retaining the richer roadmap gap. No such scope reduction is assumed here.

Future acceptance must independently verify nonuniform values, declared precision,
zero versus nodata, coordinate traversal, time/event alignment, unit/dictionary
identity and fail-closed revision/expiry/permission cases. Existing constant Java
fixtures and raw HTTP count overlap do not establish those scientific guarantees.

The PR remains draft/unmerged and the item remains blocked. Continue to the next
feature under the user's explicit deferral instruction; return when contracts or
authorization are available. The existing native GeoTIFF-unit issue #10 is neither
fixed nor hidden by this audit.
