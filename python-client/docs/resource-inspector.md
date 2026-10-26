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
