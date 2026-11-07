# Capability-gated Resources candidate source inspection

`inspect_model_candidates(client, query)` and its awaited `async_` counterpart read
source snapshots from the configured Resources supplier. This is a catalog operation,
not a request to choose a Runtime model, validate semantics, register source or execute
an observation. No source is evaluated by Python. Core imports need no extra packages.

```python
from klab_client import ModelCandidateQuery, inspect_model_candidates

catalog = inspect_model_candidates(client, ModelCandidateQuery(
    namespaces=("my.namespace",), projects=("my.project",)), timeout=10)
for candidate in catalog.candidates:
    print(candidate.model_urn, candidate.namespace_urn, candidate.source_sha256)
```

The source-defined Java contract is POST `/api/v1/models/candidates` under Resources,
with `version:1` and namespaces/projects/scenarios arrays, returning ResourceSet
MODEL results and NAMESPACE descriptors. The SDK first requires Resources capabilities
to advertise exact integer `modelCandidateProtocol:1` and a verified Resources ID/type.
Old/unadvertised servers refuse explicitly. Unlike the Java ResourcesClient's broader
legacy machinery, this bounded inspection helper deliberately has **no legacy fallback**;
404/500/denial and transport failure are errors, never empty candidate success.

The ordinary configured supplier credential is used, with no scope, originating Runtime,
transaction or semantic-cycle header delegation. Provider URLs in returned metadata are
not followed. This matches the source's `withResourceSupplierScope` separation, not
an inferred federation or context-sharing contract. Current credentials and server
project READ permissions remain authoritative. Selector strings cannot grant access.

Each descriptor must match the advertised provider/class/identity/timestamp and contain
`klab.model.candidate.source.v1` plus exact UTF8 SHA256 under
`klab.model.candidate.sourceHash.v1`. The SDK checks captured namespace/project/timestamp,
active flag, typed MODEL member identity and exact matching NAMESPACE snapshot; duplicate
members/namespaces, malformed/duplicate/nonfinite JSON, invalid Unicode, bad hashes,
inconsistent empty flags and error notifications refuse. Returned immutable records
retain exact source text/hash without silently redacting or repairing it. Known
credential-bearing sources refuse, including escaped strings/keys checked after
JSON decoding; source text is excluded from object repr. Explicit NaN/Infinity and
exponent overflow such as1e999 refuse while finite optional JSONnumbers remain valid.

Client bounds are deliberately finite:32unique selectors per axis,256characters per
selector,128models/namespaces/statements per response and1MiB per response/source JSON.
A single monotonic timeout in(0,300]seconds covers capabilities plus source transfer.
These are Python inspection limits, not alleged native storage or total memory guarantees.
The source hash detects changed captured bytes; it is not a server signature, permission
grant, scientific committed revision, published semantic admission or native model-selection
receipt. Fresh inspection is explicit; no automatic observation submission/replay occurs.
Returned ordering is provider order, not a ranking or indication of which model a
Resolver will select. Namespace/source publication and executable admission remain
separate server operations outside this inspector.

Pinned authority: repository source at the feature's baseline, including
`ModelCandidateRequest.java`, `ModelCandidateSources.java`, ResourcesProviderController,
ResourcesClient and `SemanticValidationRequest.sourceHash`. Exact source hashes are in
the [source receipt](../../notes/python-roadmap-execution/evidence/candidate-source-authority-20261107.json). Historical installed Modeler202610301844 did not advertise this
protocol; that does not mean newer Java source lacks it. Fresh deployed protocol
compatibility remains **NOT_TESTED / JAVA-GAP-0003**, not VERIFIED_JAVA.

The separate `candidate-sources` simulator scenario uses deterministic synthetic
namespace/model JSON. Public/private/project-private/scenario visibility follows the
inspected request predicates, with explicitly synthetic project-owner READ policy;
inactive/unauthorized source is absent. Same namespace/project selectors do not authorize
another principal. Tests create no sessions, Jobs, accepted assets or scientific values.
Source evidence is separate from **SIMULATED_PROVISIONAL** transport/fixture acceptance.
