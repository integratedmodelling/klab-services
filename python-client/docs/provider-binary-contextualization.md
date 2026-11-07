# Source-defined binary Resources contextualization

The sync and native-async `client.resources.contextualize(resource, observation,
geometry, scope, *, start_time=0, end_time=0, input_data=None)` facades now implement
the inspected Java Resources binary contract. They return a `ProviderDataJob` or
`AsyncProviderDataJob`. A resource asset URN, observation observable URN, explicit
scope and encodable geometry are required. Event bounds are exact signed64-bit
milliseconds. Optional input is an explicitly constructed bounded `ProviderData`.
`ProviderData.from_fields(fields)` builds it from exact native Instance field names;
all schema fields, including explicit null channels, must be supplied. It never
guesses defaults, scientific axes or a data dictionary from the values.
No semantics, units, dimensions or geometry are inferred locally.

The source-defined routes are Resources POST `/contextualize`, ordinary scoped
Job status/cancel routes, and GET `/jobs/retrieveData/{id}`. Request and result are
**raw Avro datums**, not JSON, object containers, arbitrary serialized classes or
the experimental worker registration/lease/result protocol. The codec implements
only the pinned DataRequest/Instance schema. The Resources controller resolves
the observable through its Reasoner and registers an observation before submitting
contextualization. Thus this is an explicit computation request, not a read-only
catalog call. Server authorization and component/model readiness remain authoritative.

```python
job = client.resources.contextualize(resource, observation, geometry, context)
data = job.result(timeout=30)  # await job.result(...) for AsyncClient
print(data.fields["metadata"], data.fields["doubleData"])
```

`ProviderData.payload` retains exact bounded Instance bytes; `fields` is deeply
immutable, preserving native channel names, nested instances, filling curve,
metadata, dictionary, notifications and explicit `empty`. NaN/Infinity are raw IEEE
provider values, not guessed nodata masks. Separate numeric channels are not merged,
reshaped, unit-converted or promoted to indexed `ScientificData`. Empty provider
data remains explicitly empty, never synthetic nonempty success. ERROR notifications
raise redacted `JobFailedError`. Successful payloads containing configured credential material refuse;
payload/fields are excluded from repr. Caller-provided raw binary remains caller
data, not a trusted signature or a scientific provenance receipt.

Limits are8MiB per raw datum/HTTP binary body,262144aggregate collection items,
65536UTF8bytes per string and16codec structural nesting levels. Counts, negative
Avro block byte lengths, unions, enum values, integer ranges, duplicate map keys,
truncation and trailing bytes are checked before unbounded allocations. Schemas or
classes are never loaded from network data. These are finite Python policy limits,
not native capacity or hostile-host process isolation guarantees.

The binary body is exclusive of JSON/text/multipart. Responses may declare
application/octet-stream or omit Content-Type (the inspected native controller
does not set it); explicitly incompatible media is rejected. Identity encoding and
strict bounded datum validation still apply. Failed/malformed/oversized
submission acknowledgements do not authorize replay; ambiguous outcomes require
inspection. Origin/scope rebinding at acknowledgement refuses as unknown. Local
wait timeout or async waiter cancellation retains the remote Job; explicit cancel
uses the existing server transition. Saved-handle restoration must not
silently treat provider data as a JSON mapping Job. The restoration feature now
supports Resources/provider-data references after reading supplier capabilities:
save with `save_handle(job, path)` and restore with a new client's `restore_handle`.
Both home Runtime and supplier identities/URLs are checked using current credentials.
Restoration reconnects the existing context (native POST `/connect`), polls the
existing Job and uses binary retrieval; it never resubmits contextualization.
Restored contexts are unowned; reader closure does not dispose owner state.
Missing/expired or inaccessible Jobs refuse without recreation. Context/session
ownership and explicit cleanup remain with caller.

## Evidence and remaining gates

The codec was independently compared with fastavro1.13.1 using the generated Java
DataRequest schema. That oracle is a development-only tool, **not an SDK or simulator
runtime dependency**. The simulator serves separately generated golden bytes in a
finite `provider-avro` scenario; it neither imports the SDK codec nor reasons about
science. It accepts only declared exact request fixtures, including input/event/
empty/error/malformed/media cases, and explicitly refuses other valid Avro variants
as outside its finite fixture. This is not a claim that Java rejects those variants.

Evidence: **READ_ONLY_JAVA_SOURCE / SIMULATED_PROVISIONAL**, JAVA-GAP-0001/0013.
No fresh native provider execution or scientific acceptance is established. The
preserved USGS null-scanner failure remains unresolved; synthetic direct-provider
results do not repair Resources→Resolver→Runtime numerical execution, test its
400-cell oracle, or certify native graph-observation cleanup. Native worker ABI,
deployed roles, schemas, adapter output and permission/lifecycle acceptance remain
separate gates. No Java implementation was changed.
Pinned [source and independent-oracle authority](../../notes/python-roadmap-execution/evidence/provider-binary-source-authority-20261107.json)
records the inspected hashes and fixture schema identity.

Source caveat: the inspected Data Job controller writes the raw Instance and then
falls through to an access exception. Actual HTTP commitment/error behavior has not
been executed here. Non-success HTTP status or appended/trailing bytes are errors,
not permission to salvage a partial result or silently fall back.
