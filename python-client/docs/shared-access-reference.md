# Experimental shared-read access reference

`SharedAccess` and `AsyncSharedAccess` are **explicitly proposed reference APIs**,
not wrappers around an established native Java grant endpoint. Construction requires
`allow_experimental=True`, an ordinary bound root context and a matching client
model. Every operation first requires the selected Runtime to advertise
`sharedAccessProtocol="klab.experimental.shared-access/1"`; absent/foreign/failed
advertisements refuse without trying a management endpoint or fallback.

```python
owner_access = SharedAccess(owner_context, allow_experimental=True)
grant = owner_access.grant_read("bob", ttl_seconds=300)
state = owner_access.inspect_grant("bob")
# Bob attaches/restores using his own currently issued credential.
reader_access = SharedAccess(reader_context, allow_experimental=True)
assert reader_access.inspect().rights == ("READ",)
owner_access.revoke(grant.grant_id)
```

The async counterpart awaits `grant_read`, `inspect`, `inspect_grant` and `revoke`;
it uses native async transport, no sync thread facade. The management API binds
Runtime URL/identity and root. Focus, observer and relationship-selected management
scopes refuse. Current credentials are used for every request, never those of an
Observation/Job handle's original client. Credential-reflecting responses refuse.

`ReadGrant`, `AccessInspection` and `GrantInspection` are immutable server-reported
experimental receipts. They are **not access tokens, native permission grants,
cryptographic proof or local authorization**. Creating/copying a DTO grants nothing.
`inspect_grant` reports `ABSENT`, `ACTIVE`, `EXPIRED` or `REVOKED` from the server;
do not use receipt timestamps or a cached status as authority for later actions.
Only the current server authorizes each operation.
Lookup is a current state snapshot, not a lease, idempotency receipt or proof of
which concurrent owner request created a grant. No CAS/history guarantee is implied.

## Bounded independent contract

The separate `shared-read` simulator scenario admits Alice/Bob and owner-created
Runtime `READ` grants. Read collaborators can attach an existing root, restore its
Observation/runtime Job references and read declared native-unit initialization
points. This finite reference does not authorize Resolver/Resources/worker/agent
operations, graph crawling, metadata editing, exports, focus selection, delegation
or cross-service federation. Unsupported routes refuse rather than infer rights.
All non-owner submission, cancellation, scope release, granting and revocation deny.
Closing a local reader never releases owner scopes.

Grants have an explicit1..3600second TTL (default300). One active grant per
grantee/root is admitted; duplicate creation returns409, not implicit renewal.
The ephemeral reference retains up to128grant/tombstone records, then returns429.
Owner context/session cleanup removes the records. Durable-storage mode refuses:
neither process restart nor a saved grant receipt establishes durable authorization.

Revoked/missing grants return403; expired grants return410, distinguishable through
the SDK error's `status_code`. A valid read grant does not change existing Job
expiration semantics: absent/expired Job status remains `EMPTY`, not cancellation.
Owner inspection remains available after expiry/revocation for explicit recovery.
Grant IDs are opaque and do not themselves authorize anything.

## Ambiguity and concurrent revocation

Mutations are never automatically retried. A lost mutation response raises
`SubmissionOutcomeUnknown`; local async cancellation retains the transport's
unknown-outcome note. If binding changes after a mutation response, its outcome is
also treated as unknown. Explicitly reconstruct the original owned root and call
`inspect_grant(grantee)` before deciding what to do; never blindly create again.
Malformed confirmations are not success and likewise need remote reconciliation.

The server checks rights for every indexed read. Revocation between point requests
may invalidate a multi-point read: the public SDK returns an error, not a partial
`ScientificData` object. Already returned local data is not retroactively erased or
made revocable by this reference. Six public sync/nativeasync HTTP workflows verify
positive reads/restoration, denied mutations, expiry/revocation, mid-read revocation,
lost reply reconciliation without replay and owner cleanup. Supplemental fixture
tests do not substitute for those public workflows.

## Native boundary

Pinned source currently establishes generic user/group ACL checks, not this granular
READ/TTL management protocol. In particular, native context release uses the generic
managed-scope ACL; **do not assume an admitted native collaborator is read-only**.
No native defect or safe negative-disposal result is fabricated from this source.
See `notes/python-roadmap-execution/evidence/shared-access-source-authority-20261107.json`.

`tests/functional/test_shared_context_read.py` is opt-in and **NOT_RUN** here. It
requires prepared `shared-context-read.json` with an Observation handle, exact raw
`expected_texts`, `offsets`, `unit`, pinned `native_build`, `authorized_reader_principal`
and a `reader_credential_env` naming an environment variable holding its issued token.
It performs read-only current-credential restoration/point retrieval and verifies
the owner's read still works after reader close. It creates no grants, scopes or Jobs,
does not test native write/disposal denial and proves no new scientific computation.
Native grant transport, expiry/incarnation and granular mutation policy need maintainer
confirmation plus separately approved disposable fixtures before compatibility runs.
