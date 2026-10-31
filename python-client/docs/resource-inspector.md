# Resource and model inspection

```python
from klab_client import ResourceInspector

inspector = ResourceInspector(connection.client)
models = inspector.list("MODEL")
model = inspector.retrieve(chosen_model_urn, "MODEL")
print(model.summary())
for dependency in model.listed_dependencies or ():
    print(dependency.urn, dependency.unit, dependency.optional)
resource_set = inspector.resolve(chosen_resource_urn, "RESOURCE")
```

The examples require actual URNs returned by the selected service. Both MODEL and
RESOURCE use the existing typed list/retrieve/resolve interfaces, with the original
authorization, scope and home Runtime headers. Existing `*Impl` methods gain only
an optional keyword-only `max_response_bytes`; old calls and return types remain
compatible.

Frozen views preserve canonical identifiers, supplied labels, advertised observable
and dependency descriptors, resource URNs, namespace/project, available version,
provider metadata, notifications and redacted raw extensions. Metadata missing or
null remains `None`; an explicitly empty list remains an empty tuple. A label,
unit, version, executable status or complete dependency closure is never inferred.
`origin` records the selected Resources endpoint, its observed identity and scope;
an observed identity change during inspection fails explicitly.

Inspection views redact configured credentials even if a service echoes them in
an asset or service identifier. Identity and drift checks still compare the
original values before producing the redacted view.

`list` is a whole-service list, bounded locally by bytes and items, not server
pagination or a global semantic model search. Duplicate URNs remain separate
entries. No namespace/ranking constraint forces a listed model, and inspector
output is **advertised applicability**, never evidence of actual Resolver
selection or execution. Link it to recorded evidence through actual identifiers.

`resolve` preserves the returned ResourceSet service descriptors and versions as
data. It never forwards credentials to those URLs, auto-selects another provider,
or recursively loads dependencies. Every result is explicitly partial, including
an empty successful ResourceSet. Failed, forbidden, missing and malformed results
are errors; HTTP 422 becomes `UnsupportedOperationError`, not an empty success.

Defaults are 100 items and 1 MiB per response (caller maxima: 1,000 and 16 MiB).
Over-budget whole lists fail rather than silently truncate into apparent complete
metadata. `summary()` and escaped notebook HTML use the same decoded model without
a required UI/scientific package.

Source-derived offline acceptance is not a claim that a deployed executable model
was inspected. Live executable-model/resource association remains a separate gate.
See the [contract inventory](discover-compare-explain-contracts.md) and
[contribution review](reviews/discover-compare-explain/resource-inspector.md).

## PR #99 live acceptance pilot

`tools/run_resource_inspector_acceptance.py` and the existing functional runner's
`connected-resource-inspection` suite perform one explicitly authorized owned
session/context pilot. The request is a 5×4 EPSG:3857 region followed by elevation
in metres, using a namespace as ranking context, never forced model selection.
The pilot checks actual grid metadata, finite actual data, a successful numerical
effect and the inspected model URN in this result's recorded accepted resolution.
This narrow identifier check does not implement F3's general diagram viewer or
claim independent scientific accuracy/full lineage/durable root commit.

```powershell
python tools/run_resource_inspector_acceptance.py --profile examples/hosted-deployment.json --certificate <private-ENGINE-certificate-path> --model staging.vxii.basic.elevation-quantifier --report <fresh-external-report.json> --allow-owned-work
```

This is not a read-only command. The explicit flag is required; there are at most
two observation submissions and no retries, imports, component changes or restarts.
Only the pilot's own context/session are released. Failures remain failures, with
known handles and cleanup outcomes retained. Arbitrary server exception/stack text
is not published. A failed release makes the pilot unsuccessful.

The follow-up prerequisite guard snapshots configured service component/function
advertisements with byte/item bounds and credential redaction. It requires the
selected Runtime to advertise the model's explicit computation service before
spending any Jobs; dynamic/remote component loading is not silently assumed.
Snapshot versions and `fileHash` fields are advertisements, not verified source
commits or locally calculated SHA-256 values.

**Observed 28 October 2026:** the single authorized hosted pilot inspected the
model and completed its region Job, but the elevation Job failed (`JobFailedError`).
Java confirmed release of both owned objects. A read-only follow-up found Resources
advertising `klab.generators.geospatial.terrain` in the generators/geospatial
components, while Runtime and Resolver advertised only their internal component.
The original Job was no longer accessible after cleanup. This is a concrete
deployment compatibility concern, not proof of the exact Job failure cause.

The prerequisite/diagnostic guards were added after that failed pilot and have not
been used to claim a successful second computation. See the
[sanitized failure/capability evidence](results/resource-inspector-hosted-pilot-20261028.json).
PR #99 remains draft until a compatible execution deployment is established and a
separately authorized pilot passes model/result association. No component was
installed and no failed check was skipped or replaced by mock scientific output.
