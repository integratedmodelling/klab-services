# Explicit Resources project locks

```python
from klab_client import ProjectLock

lock = ProjectLock(client, "my_project")
acquired = lock.acquire(allow_write=True)
if acquired.confirmed:
    # Decide explicitly when to release; client.close() never unlocks remotely.
    released = lock.release(allow_write=True)
```

Use `AsyncProjectLock(async_client, project)` and await `acquire`/`release` for
native async HTTP. There is no thread facade, hidden event loop, context-manager
unlock, automatic retry or destructor mutation. Both operations require exact
`allow_write=True`. Only a simple local project identifier is admitted; no URL,
workspace path or credential material. Resources origin/service identity is pinned.

Native source contract: `ServicesAPI.RESOURCES.LOCK_PROJECT/UNLOCK_PROJECT` are
`GET /api/v1/project/lock/{urn}` and `/api/v1/project/unlock/{urn}`. Despite older
comments describing a URL response, `ResourceAdminController` returns **boolean**
and requires **ADMINISTRATOR**. Client consent grants no role or authority; actual
HTTP authorization errors remain errors. Source inspection is not executed Java
compatibility and the SDK does not acquire administrator credentials.

`WorkspaceManager.projectLockOwner` uses the authenticated principal where known,
not the editor/client instance. Another client of the same principal may acquire
the same lock or unlock it. These operations are **not editor-exclusive leases,
fencing tokens, revision/version checks or authorization to overwrite source**.
No unversioned update or publication API is added. Project locks and source/version
history are distinct native contracts; the client does not invent the missing ones.

`ProjectLockReceipt` is immutable, containing only the requested project/action,
exact boolean acknowledgement and originating service identity. It is a historical
acknowledgement, not proof that a lock remains held. `state`/`receipt` are local-only
inspection. False acquire means `ACQUIRE_DENIED`, not a diagnosed reason or missing
permission; an explicit later attempt is allowed. Repeated successful acquire and
repeated release on the same journey are refused. False release remains
`RELEASE_UNCONFIRMED`, never a successful cleanup claim.

Admission sets `OUTCOME_UNKNOWN` before the native mutation request. Lost/malformed
replies, interruption or cancellation cannot authorize blind replay or implicit
unlock on that object. Async attempts serialize; cancelling a wait does not prove
remote rollback. Operator reconciliation is required before creating a new journey
after an unknown outcome. HTTP403 is never reinterpreted as a boolean denial or
success. Maintaining principal continuity across credential changes is the caller's
responsibility; a capability service ID cannot prove principal identity.

SIM-PROJECT-LOCK-001 (RC-P4.4/CAP14) is the only reason to extend the separate HTTP
simulator with these two routes: verify exact native booleans, administrator denial,
different-principal contention, principal-shared acquisition, explicit release and
absence of implicit unlock on close. Synthetic administrator credentials and finite
lock state are SIMULATED_PROVISIONAL, not production role grants or Java execution.
