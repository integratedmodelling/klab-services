# Explicit ADD-only source authoring

**Implementation in progress; live acceptance pending.** This is a bounded
extension of the existing Resources API, not the complete editing/publishing SDK.
Only synchronous Client, NAMESPACE and SCRIPT source, and new workspace/project/
document creation are admitted. No production Java/worldview change or parser port.

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
remain on the service**. Use the project's existing authorized administrative/UI
workflow to manage/delete them. There is no automatic rollback or deletion method
hidden in a context manager, and there is no remote resource upload/import here.

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
two-user privacy guarantee and never assigns remote publication rights. SCRIPT
creation stores source only: it does not instantiate or run an agent. ONTOLOGY and
other document types remain unsupported here to avoid inferring wider contracts.

## Acceptance

Mocked source-derived cases establish request/response and failure contracts only.
The opt-in `test_authoring_workflow.py` requires explicit authorization, creates a
fresh uniquely named workspace/project and source documents, re-retrieves exact
source, and checks known-owned cleanup. It must run against real pinned Java
services before this feature is accepted. No preserved scientific project is
modified, and no authoring test result is scientific reproduction evidence.
