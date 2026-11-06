# Explicit resource file imports

`ResourceImporter` and `AsyncResourceImporter` upload an explicitly selected local
file through **one advertised Resources RESOURCE/STREAM schema**. They never
install components, change a worldview, run a scientific model, annotate an input
implicitly, or claim an upload acknowledgement proves successful admission.

```python
from klab_client import ResourceImporter

# client has an authorized Resources endpoint/credential.
imports = ResourceImporter(client, max_source_bytes=64 * 1024 * 1024)
for format in imports.formats():
    print(format.schema_id, format.media_types)

submission = imports.upload(
    "input.tif", urn="my:explicit:suggested:resource",
    schema_id="raster.geotiff.import", media_type="image/tiff;application=geotiff",
    allow_write=True,
)
print(submission.source_sha256, submission.source_bytes, submission.job.id)
resource_set = submission.job.result(timeout=30)
# Inspect the returned descriptors/notifications, then retrieve actual admitted URNs.
# The suggested URN need not be honored. No model or scientific result is created.
```

Native async shares validation and receipt semantics without a thread facade:

```python
from klab_client import AsyncResourceImporter

imports = AsyncResourceImporter(async_client)
formats = await imports.formats()
submission = await imports.upload(
    "input.tif", urn="my:explicit:suggested:resource",
    schema_id="raster.geotiff.import", media_type="image/tiff;application=geotiff",
    allow_write=True,
)
resource_set = await submission.job.result(timeout=30)
```

## Safety and lifecycle

- No writes without literal `allow_write=True`; invalid requests fail locally.
- Endpoint/credentials/scopes remain those of the configured Client. Descriptors
  cannot expand destination trust or cause credential forwarding.
- Only current RESOURCE/STREAM schemas are exposed. Duplicate schema/media pairs
  are ambiguous and refused. Properties-based Git imports and COMPONENT installation
  schemas are outside this resource-file API.
- A nonempty regular file must fit the explicit source budget: default64MiB,
  configurable up to1GiB. Actual consumed bytes are hashed/counted in64KiB reads,
  not buffered into one upload body. Server filename is `upload.<suffix>`, never
  the original basename or local directory. This is not a remote snapshot/lease.
- Lost transport, invalid acknowledgement, changed file length or changed binding
  after submission means `SubmissionOutcomeUnknown`: inspect remote state; never
  automatically retry or assume rollback. Authorization denial remains distinct.
  Local async cancellation retains cancellation and notes uncertain remote outcome.
- The Job polls/retrieves/cancels on Resources, not Runtime. A cancellation request
  is not confirmation; timeout preserves the in-process Job. Durable saved Runtime
  handle support is not claimed for import Jobs.
- No automatic remote deletion/release. This inspected ResourcesProvider.delete has
  no RESOURCE deletion implementation. Positive Java-upload acceptance requires an
  approved disposable/retained-resource fixture, not pollution of the preserved IDE.

## Native contracts and actual verification

`ServicesAPI.IMPORT` is `/import/{schema}/{urn}`. KlabServiceController.uploadAsset
accepts multipart `file`, finds an authorized advertised schema and returns a
positive long Job ID. BaseService.importAsset returns ResourceSet asynchronously.
The suggested URN is not an overwrite/precondition guarantee. Python does not port
the raster adapter or hide registration/scanner limitations.

Actual Modeler202610301844advertises raster.geotiff.import andresources.legacy.files;
both public facades decode identical native capabilities via read-only GET.
Deterministic negative controls and real loopback HTTP multipart1MiB/hash/scopedJob
round trips pass. The socket server is a protocol fixture, **not Java registration
or scientific acceptance**. See the ongoing A+B completion ledger for delivery.
