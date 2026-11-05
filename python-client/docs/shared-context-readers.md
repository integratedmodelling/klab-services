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
