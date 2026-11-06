# Explicit ADD-only source authoring

Plain `BEHAVIOR` source drafts now use the same sync/native-async parse and ADD
flow. This persists source only and never registers, compiles or starts an agent.
See [behavior draft limits](behavior-source-authoring.md) for strict subtype,
existing-source-selector and consent checks and separate source/simulator/Java
evidence. Apps/scripts/testcases, ontology policy, updates and publication are not
added by this increment. Historical namespace acceptance below is not new behavior
draft Java compatibility.

**Controlled namespace-only acceptance passed on the rebuilt backend tuple below;
this is not permission to write to retained deployments.** This is a bounded
extension of the existing Resources API, not the complete editing/publishing SDK.
`Authoring` (Client) and `AsyncAuthoring` (AsyncClient) admit NAMESPACE source and
new workspace/project/document creation with shared safety checks. Native async
awaits transport directly, without thread wrappers. No running deployment/worldview
change or parser port.
Server prerequisites merged separately in PRs #114 and #115. Final client-head
checks are still required before PR #110 can merge.

Current development increment additionally admits `OBSERVATION_STRATEGY_DOCUMENT`
through the same exact-source ADD-only guards. See
[strategy draft contract](strategy-source-authoring.md) for its separate pending
installed/live acceptance, explicit TEST operator provisioning and actual project/
document denial controls. This does not add ontology/worldview editing or publication.

```python
from klab_client import Authoring, Client

with Client.from_env() as client:
    author = Authoring(client)
    source = author.parse("namespace python.my_draft;\n")
    workspace = author.create_workspace("my-new-owned-workspace", allow_write=True)
    project = author.create_project(workspace, "my-new-owned-project", allow_write=True)
    receipt = author.create_document(source, project, allow_write=True)
    print(receipt.kind, receipt.urn, receipt.operation)
```

Each operation is explicit. Closing Python only closes local HTTP: **the assets
remain on the service**. The old fixture reported workspace deletion unimplemented;
the rebuilt candidate including the separate empty-workspace deletion fix verified
project/workspace cleanup and read-only restart persistence. That server operation
refuses nonempty workspaces and never cascades. No general cleanup across arbitrary
server versions is claimed. There is no automatic rollback/deletion hidden
in a context manager, and no remote resource upload/import here.

Native async uses the same consent, exact-source, identity, bounded-response and
uncertain-write rules:

```python
from klab_client import AsyncAuthoring

author = AsyncAuthoring(async_client)
parsed = await author.parse("namespace python.my_draft;\n")
workspace = await author.create_workspace("my-new-owned-workspace", allow_write=True)
project = await author.create_project(workspace, "my-new-owned-project", allow_write=True)
receipt = await author.create_document(parsed, project, allow_write=True)
```

The facade adds no permission, compiler, automatic cleanup or retry. The separate
development simulator exercises finite exact-source imports/bindings over HTTP;
that is Python contract evidence, not new live Java admission/scientific evidence.

The caller must have user authorization plus CREATE/project privileges
on the explicitly configured Resources service. In the pinned Java ScopeManager,
ordinary remote principals receive READ only; local/admin/system scopes receive
the broader mask. Do not assume changing an issued token's arbitrary permission
claims upgrades an already-cached scope. The owned write fixture therefore needs
a distinct explicitly provisioned operator identity; success there is not ordinary
remote-user write acceptance. This Python helper never provisions/elevates roles.
Project-lock HTTP routes are
administrator-only in the inspected server. This helper does not elevate roles,
secretly acquire locks or claim that a lock is a revisioned write transaction.

## Boundaries and safety

- Parse is remote Java parsing, not a Python semantic validator. Require exact
  returned source/class/kind, known Resources identity and checked notifications.
  Empty/error/mismatched parser output is not an authorable success.
- Parsing checks syntax/adaptation only; do not equate a parsed document with
  dependency/worldview acceptance, scientific validity or executable agent startup.
- Each write requires literal `allow_write=True` and uses only ADD. There is no
  UPDATE, CREATE_OR_UPDATE, MERGE, VALIDATE/deferred persistence, lock, publish or
  automatic retry option. Version history/conditional writes remain blocked in
  [the original audit](authoring-contract.md).
- The helper only creates projects in a workspace it confirmed newly created in
  this same Authoring instance, and documents in a similarly confirmed new project.
  It cannot attach to an existing project to edit scientific or shared source.
- Returned ResourceSet descriptors must confirm the matching CREATE, asset class,
  URN and service identity. Project receipts use workspace/project submission
  coordinates but require the server's bare project URN plus matching workspace.
- An error notification, malformed/empty acknowledgement, interrupted/5xx write
  or identity drift can occur after file persistence. Report unknown outcome;
  inspect the remote state before choosing any further action. A failed call is
  **not** proof of rollback and does not authorize dependent cleanup or replay.
- Permission denial remains distinct from unknown transport/write outcome. Stored
  receipts are passive confirmations, not current permission, lock or revision leases.
- Source UTF-8 limit: default/max 1 MiB. Payload/response limit: default 2 MiB,
  max 16 MiB. Source/identity containing configured credential material is rejected
  rather than silently changing a source before it is submitted.
- Snapshots deeply freeze metadata and preserve exact source including comments/
  whitespace. Source and change payloads are omitted from object repr; do not
  assume source or private asset identifiers are safe to publish.

Default service permissions/privacy are server-owned. This API makes no untested
two-user privacy guarantee and never assigns remote publication rights. SCRIPT is
blocked after the genuine Java response failed class/source fidelity validation.
The inspected Java behavior adapter uses `syntax.encode()` for sourceCode; exact
original-script preservation must be established separately. No reconstructed
script is silently written. ONTOLOGY and other document types remain unsupported.

## Acceptance

Mocked source-derived cases establish request/response and failure contracts only.
The opt-in `test_authoring_workflow.py` requires explicit authorization, creates a
fresh uniquely named workspace/project and source documents, re-retrieves exact
source, and checks known-owned cleanup. Four original failed Java attempts remain
unchanged in [historical facts](results/authoring-controlled-20261102.json).
Fresh Resources-only acceptance passed with Java
`84afa1a4165faaa424cd58efeaad45370e021242` and Python
`852ae9b25d9a7d31bb8a01f96a3fa53dbe50391e`: exact namespace source, invalid-source
rejection, three confirmed creations, matching DELETE descriptors plus separate
listings, then read-only restart absence. Distinct signed fixture administrator,
not ordinary remote-user write acceptance. Both owned JVM identities and hub
threads stopped; backend fingerprints unchanged. The two earlier rebuilt-server
startup failures occurred before writes and remain preserved as failures.
See [current facts](results/authoring-rebuilt-20261102.json) and [review](reviews/authoring.md).
No preserved scientific project was modified; no scientific reproduction is claimed.
