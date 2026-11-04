# Process-local Job identity and retained-work limits

An existing `Job` or `AsyncJob` must not silently move to another numeric Job,
scope, service, configured peer, credential, home Runtime or result decoder after
submission or explicit construction. Both clients now retain an immutable private
process-local binding and check it before and after status/result/cancel I/O and
before offline handle creation. No network or capability discovery is added to
construction or offline capture.

Pinned fields include client/transport object identity, positive integer Job ID,
executing service, exact scope token (including focus/observer), scope client,
scope origin/configured Runtime and reported context identity, configured home
Runtime ID, both home and executing peer URL/base path/credentials, previously
known Runtime/peer capability IDs, decoder identity and result kind. Credentials
are excluded from binding repr, errors and saved handles. Existing same-Runtime
cross-client offline capture remains supported and still uses its established
canonical-origin/known-identity checks; the new guard does not replace those.

Initially known cached IDs cannot change or disappear. Previously unknown cache IDs can be
learned without making the Job unusable, but this is not a remote identity or
incarnation proof. No code watches configuration continuously. Client/Context
configuration must not be raced against Job operations from another thread/task;
this guard is not a transactional configuration lock. Deliberately replacing
private guard state or constructing a new object is outside its guarantee. This
is a comparison against the construction-time binding, not a permanent drift
latch: restoring those exact original local values can permit operations again.

## Example: no implicit scope rebinding

```python
job = context.job(5)  # explicit reference, not submission/recreation
context.context_id = "another.context"
job.status()  # ProtocolError before HTTP; it must not query Job5 in that scope
job.cancel()  # also rejected before HTTP
job.to_handle()  # cannot bless the changed binding as an offline reference
```

Changing a configured URL/base path or credential also makes operations on
existing Jobs reject while that mismatch remains. Do not assign `job.id`, `job.scope`, `job.service`, `job.client`, decoder or
result kind to migrate work. Configure a new Client and explicitly restore a
saved reference through the existing checked restoration API if appropriate.
This is not permission to restore across an unverified service restart.

Read-response binding drift raises `ProtocolError` before decoding/returning the
result. Drift inside a custom decoder also prevents returning its result, though
the decoder's own side effects cannot be undone. If binding changes while a
cancellation response is in flight, the mutation outcome is
`SubmissionOutcomeUnknown`; the client neither records accepted cancellation nor
retries, rolls back or reassigns the response. No guard proves that cancellation
did not happen. Normal cancellation still requires a boolean reply, and an
accepted request is not confirmation of interruption. Local wait timeout/task
cancellation keeps the original Job rather than cancelling or resubmitting it.
EMPTY still means unavailable/expired, not successful cancellation or ownership
of remote cleanup.

## Reproduction and local verification

From `python-client/`, with the declared development dependencies:

```powershell
python -m pip install '.[dev,local,analysis,tabular,notebook,events]'
python -m pytest -q tests/unit/test_job_binding.py tests/integration/test_job_binding_http.py
python -m pytest -q tests/unit tests/integration --cov=klab_client --cov-branch --cov-report=json
```

The unit regression was first run on unchanged base
`e09a7de7cac6b48526fd8b7ac72879abb36a77a3`: both sync/async scope-token checks
failed because the old code sent the request under the changed scope instead of
raising. To reproduce that failure without overwriting a maintained worktree,
create a separate worktree at that commit and copy only this PR's
`tests/unit/test_job_binding.py` into it, then run
`python -m pytest -q tests/unit/test_job_binding.py -k 'scope_token and status'`
with `PYTHONPATH` pointing at that worktree's `src` and `tools`.

The loopback HTTP fixture is committed at `tests/fixtures/job_binding_http.py`.
It starts its own ephemeral server in an isolated subprocess, establishes positive
controls for numeric Job5 in two configured service bases, checks distinct
results, and verifies no request/cancel reaches the wrong base or changed scope.
Only the separately bound second Job may cancel that fixture's state. Test
threads, sockets and clients close in `finally`; the offline network guard is
not weakened. This is synthetic HTTP contract testing, **not Java or scientific
work execution**. No real retained/ambiguous Job is resumed or cancelled.

## What remains outside this fix

This binding exists only for this live Python object. Saved handle schema and
reattachment behavior are unchanged. It is not an authenticated principal binding,
remote generation/lease, durable mutation journal, simulation checkpoint,
scientific lineage, Job history inventory or exactly-once guarantee.

`klab.core.services/.../JobManager.java` initializes a numeric `AtomicLong` for
each manager; `KlabJobController` resolves Job IDs in the current authorized
scope's manager. The stable capabilities server ID is not a manager-generation
token. These are source-level reasons **not** to infer restart identity from a
number, server ID or uptime. They are not a reproduced deployment in which the
same authorized restored scope actually aliases a different operation.

Cross-restart retained-Job acceptance still needs a maintained backend retention/
generation/ownership contract and an owned two-deployment/same-wire-ID fixture
with positive and negative authorization controls, explicit status/result
outcomes and independent cleanup. Full Runtime builds remain blocked by the
documented LanguageAdapter EXTENT/ATTRIBUTE dependency mismatch; see #89.
Do not invent a generation token, automatically recreate missing work, or replay
a historically ambiguous cancellation/submission to fill that evidence gap.
