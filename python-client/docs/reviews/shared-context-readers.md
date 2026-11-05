# Contribution review: authorized shared-context readers

## Recommendation

Ready for maintainer review for the bounded existing-ACL native-reader acceptance.
Contributor/CLI review under KLAB_CLI_Contribution_Review.md, not human approval.
This is a bounded acceptance contribution using existing public SDK operations,
not a new Java policy or permission-role implementation.

## Reviewed state

- Repository klab-services, integration feature/python-experiment-runner at
  597b2fed15f3b25960667a7d47322a55ef96d796 (PR224).
- Current feature/python-shared-context-readers; contractc62ed0137 before code.
- Adds workflow/functional test/unit harness controls and existing runner selection;
  no SDK production, Java, dependencies or original scientific model changes.
- Source authority: ScopeManager.allowsManagedScope50–61 admits context owner or
  explicit context ACL principal regardless of parent-session membership.

## Capability and boundaries

Ordinary A shares existing context with named ordinary B. B restores A's original
credential-free observation handle; both issue overlapping native async reads.
B closes its local transport, A verifies original work survived, then A cleans up.
Private non-admitted context and valid B session are negative/positive controls.
No assertion that admitted B is read-only: Runtime release uses that same context
ACL policy. No credentials stored in handles; file data never grants permission.
Barrier proves overlapping client operations before first native point send, not
simultaneous Java scanner execution, mutation isolation, revision or snapshot lease.

## Findings, ordered by consequence

Source-only contract question resolved before implementation: context ACL does not
define separate read/write operation rights. Contract explicitly avoids claiming
those rights; audit restricts this workflow's behavior, not B's server permission.
No Java defect or important SDK defect demonstrated. First live run revealed a
blocking harness defect in shared_context_readers.py observable identity check:
ObservableImpl has no .urn attribute. Replaced by served raw['urn'] and added a
unit guard without that nonexistent attribute. Failed run preserved; full corrected
validation required. Positive live ACL/handle/read/lifecycle evidence remains required.
Failure/timeout controls cancel and join
both reader tasks and remove temporary hooks; no ambiguous-write replay.

## Validation actually performed

| Check / command | Revision/environment | Status | Establishes / limits |
|---|---|---|---|
| pytest unit/test_shared_context_readers.py plus existing client/async/isolation tests | 22af36adf, Windows Python3.12.7 | Passed:121 | Barrier/audit/cleanup guards with mocked transport, not Java science |
| Fresh build/install/offline and17 existing coverage gates | 22af36adf | Passed:3,250/all17gates | Package/source parity and lint; no coverage threshold changes |
| Actual owned Java ACL/shared-native-read suite | 22af36adf | Failed:AttributeError | Harness identity check used absent attribute; private negative/valid B positive and all cleanup passed; not accepted |
| Corrected focused/installed validation | 7c33ab67f | Passed:122focused/3,251installed/all17gates | Build/install/parity/lint and new identity guard; no new SDK/Java production changes |
| Corrected owned live suite | 7c33ab67f | Passed | PrivateAuthorizationError/validBpositive, original16restoration/unowned, native reads234.5m/clientoverlap, localclose owner survival, all cleanup |

Exact local commands (explicit installed-env Python3.12.7 on Windows):
`-m pytest -q python-client/tests/unit/test_shared_context_readers.py
python-client/tests/unit/test_read_semantics_isolation.py python-client/tests/unit/test_client.py
python-client/tests/unit/test_async_client.py` exit0/122PASS on7c33ab67f;
`-m ruff check python-client/src python-client/tests python-client/tools --select E9,F63,F7,F82`
exit0. Existing `run_gates.py --repo <owned-core-worktree> --label ab-shared-context-readers-corrected`
completed build/install/offline/all17coverage/lint commands exit0. Owned runtime command
uses `run_reliability_acceptance.py --scenario shared-context-readers` with existing
numeric component manifest and Temurin21; evidence root is the separate corrected-live
directory, not the original failed-run root. No older live outcome reused for new code.

## Scientific and lifecycle evidence

Reuses independently declared Java scenario234.5m/20cells. Checks exact original
observation ID/focused scope, metre units, order and duplicate offsets. No Python
algorithm replaces Java; no historical study model involved. Live negative
authorization requires AuthorizationError/MissingAssetError, not protocol/transport
failures. Scope/privatecontext/A-session/B-controlsession/service/listener cleanup
confirmed, unrelatedJava unchanged. Exact installed/source/raw harness/live identity,
saved-handle/report credential absence and hashes verified in existing private
shared-context-readers-final-validation.json. Failed22af36adf run retained separately.
No important/blocking defect demonstrated after harness correction in this bounded
scope; not an exhaustive proof or human maintainer acceptance.

## Contributor coordination

No Java/component publication, principal expansion or external contact. Full issue89
identity/shared-session/restart/managed-runtime/broker lifecycle gates stay open.
Failed peer and unpublished categorical evidence preserved. No NiFi overlap.
Controlled TEST authority/local-federation setup is not production Hub onboarding,
licensing or a promise that arbitrary deployed collaborator tokens will be admitted.

## Smallest next actions

Final focused map/public docs checks, normal exact-head PR/CI/eligiblemerge, then
ancestry/source/postmerge confirmation. No further unchanged live rerun needed.

## Suggested PR description

Add bounded live acceptance for existing ACL-authorized multi-principal native
indexed reads, positive-controlled private-context denial, original-handle restoration
and owner-work survival after collaborator local close. State client overlap and
server permissions/revision limits separately from actual value/cleanup evidence.
