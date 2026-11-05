# Authorized shared-context native readers

Base597b2fed15f3b25960667a7d47322a55ef96d796 (PR224). Bounded RC-P1.2 /
CAP-04 / issue89 acceptance slice; no new Java policy or Python principal roles.
ScopeManager.allowsManagedScope50–61 admits context owner or ACL-authorized user
independently of parent-session membership. RuntimeService.releaseContext uses the
same policy, so an admitted collaborator must NOT be called read-only. No claim
that admitted B is forbidden to submit/release/cancel without a separate contract.

Use explicit ordinary A/B principals from controlled TEST authority; owner A creates
one private context excluding B and another explicitly admitting B. A private-context
positive lookup followed by B's AuthorizationError/MissingAssetError provides the
negative contrast. B independently creates/releases a private session as valid-token
positive control. Do not count transport/protocol errors as authorization evidence.

A creates original234.5m/20cell observation once in the shared context. B restores
the exact credential-free observation handle; check original ID/scope and unowned
local attachment. Barrier-proven overlap of A/B native async indexed reads must
retain actual expected values/units/order/duplicates, not a mock scientific result.
Barrier at public HTTP event hooks proves concurrent client operations before send,
not simultaneous Java scanner execution or a multi-reader revision lease.

After preparation audit B's calls: restoration/reads only, no submit/cancel/release/
recreation. B transport close must not release remote shared work; A must still read
original observation afterward and then explicitly release its owned context/session.
B's independent session cleanup happens outside that read-only workflow audit. Use
bounded barrier timeout/cancel/join to avoid stranded tasks; unknown writes are not
replayed. Save primary failure/cleanup evidence. No Java production/dependency,
principal authorization expansion, service restart or scientific fixture change.

Unit tests cover barrier overlap, timeout/cleanup, audit refusal and scalar/identity
guards with mocks clearly distinguished from actual Java acceptance. Installed gates,
one fresh owned live stack, exact identity/privacy/cleanup binding, guide review and
normal PR/CI/eligible merge/postmerge required. Full issue89/A/B remain unaccepted.

## Candidate checks

Executable22af36adf:121focused testsPASS;3,250installed/all17existinggates/lint/
build/sourceparityPASS. Wheel6414eced69f05464804bf8142ba967f921b302ed31861adb6ac09a73d28caf90;
sdistb42d12c412e895ad56c86b67aca968fa1fa9b1de67c6962d7f12995930a5f13d.
Unit controls cover barrier success and reader-error/timeout cancellation+join+
hook removal, forbidden workflow mutation paths and value/order/unit failures.
One fresh owned shared-context-readers-live run ACTIVE; no live PASS predeclared.
This acceptance contribution changes no SDK production source or Java policy.

First live22af36adf FAIL retained: workflow used nonexistent ObservableImpl.urn
after read-value checks; actual DTO exposes native URN in raw. Private denial was
AuthorizationError with valid B session control. All owned scopes/processes/
listeners cleanup PASS, unrelatedJava unchanged. This is a harness defect, not a
Java/SDK policy defect or accepted shared-reader result. Corrected identity guard
uses raw['urn'] with a new regression test; corrected installed validation active.
Prior failed run/receipt retained without overwriting; next run uses new state root.

Corrected7c33ab67f:122focused/3,251installed/all17gates/lint/build/parityPASS.
Wheel82a59ea005331c3e2c687a6719339ffd6b48b1a077b4b04aa83e825b6e7c61a4;
sdist974b5c8714af716e0cd53437a4a07e7dd218b294c6ca533f39675e72438e8800.
New shared-context-readers-corrected-live run ACTIVE. This rerun follows an actual
harness correction, not unchanged scientific retries. No live result predeclared.

## Verified corrected live result

7c33ab67f6862037888c4206fd4589f750e453f9 actual Java PASS: valid B session control,
private-context AuthorizationError, explicit shared ACL original-observation16
restoration/focused scope/unowned attachment, overlapping A20cell/B[19,0,19,1]
native reads with234.5m values and exact observable. Audited B made no scientific
submission/cancel/release/recreation. A's original work survived B local transport
close and A reread [0,19] before owned cleanup. Context/privatecontext/A session/
B controlsession/services/listeners cleanup PASS; unrelatedJava unchanged.
Exact installed/source/raw harness/live identity, saved-handle/report credential
absence and hashes verified. Failed original run retained, not promoted to PASS.
Server scanner simultaneity and revision leases NOT_CLAIMED; controlled TEST/local
federation evidence is not production Hub licensing/onboarding/full issue89 acceptance.
Final focused checks/normalPR/hostedCI/eligiblemerge/postmerge remain required.
