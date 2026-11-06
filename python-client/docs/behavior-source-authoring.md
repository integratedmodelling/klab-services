# Nonexecuting behavior draft creation

```python
from klab_client import Authoring

author = Authoring(client)
parsed = author.parse(behavior_source, kind="BEHAVIOR")
workspace = author.create_workspace("new_workspace", allow_write=True)
project = author.create_project(workspace, "new_project", allow_write=True)
receipt = author.create_document(parsed, project, allow_write=True)
```

`AsyncAuthoring` exposes the same operations with ordinary native awaits. This is
ADD-only source persistence in this author's confirmed newly created project. It
returns `AuthoringReceipt`, **not** an Agent, a compiled class or a running behavior.
No instantiate/start/broker/session/unlock/publication request is made. Explicit
consent and authoritative server ACLs still apply; no role is acquired by the SDK.

Resources parses the exact source through `POST /parseAsset/BEHAVIOR`; the checked
`KActorsBehaviorImpl` must retain the exact source and plain `BEHAVIOR` subtype.
`PUT /api/v1/submit/BEHAVIOR/ADD?urn=...` receives that DTO, with the newly created
project selected explicitly. Existing source selectors, scripts/apps/testcases and
other lifecycle families are refused. Subtype is rechecked at creation, including
manually replaced receipts. No Python grammar, inference or compiler is introduced:
the Xtext grammar/API/analyzer authority hierarchy in `docs/AGENTS.md` still applies.

Native source references are ResourcesProvider's `KlabDocument` ADD handling and
WorkspaceManager.createDocument/FileProjectStorage creation. Source inspection and
mock protocol tests are **not executed Java persistence/validation compatibility**.
Rejected parsing or unknown write outcomes never become confirmed creation or
automatic replay. An error can follow persistence; reconcile remote state before
another explicit attempt, rather than assuming rollback. Closing a client never
deletes a draft or releases a project lock.

SIM-BEHAVIOR-DRAFT-001 is the named CAP14/RC-P4.2/RC-P4.4 reason for the separate
simulator's finite BEHAVIOR parse/ADD variants. Only one literal synthetic draft is
admitted; this is not a k.Actors parser, compiler, scheduler or execution simulator.
No source update, semantic-history, ontology project-policy or publication API is
added. Existing source previews remain read-only; deferred agent registration is a
separate explicit operation, never an effect of draft creation.
