# Integration readiness checkpoint

Public remote check on 2026-11-06, approximately 20:24 UTC:
`https://github.com/integratedmodelling/klab-services.git` still advertises
`refs/heads/develop = 212548f761ada2b5eecaac013191be0d5ce4152d`.
This matches the pilot's actual published develop base and merge base. No rebase or conflict
resolution is needed against that observed remote. `ontology-review-pilot` and
`ontology-review-digest` were not advertised as remote branches in the same read-only check.
Recheck immediately before a later authorized push; this is a dated observation, not a remote lock.

The isolated checkout's `origin` is intentionally the local `services-develop.bundle`, not GitHub.
Do not run an unqualified `git push origin`. Preserve all existing checkpoint refs. In particular,
local `ontology-review-pilot` is an earlier checkpoint; it must not be mistaken for the current
colleague-pilot branch. Use the delivery receipt's exact head/ref and an explicitly approved remote
destination. No force push, direct develop merge, PR, deployment or communication was performed.

Source implementation checkpoint: `9fe2105c33218275b2fc27157da39e2410b1f10b`, including independent
audit `54c307645a7c8713d2aecc952cc07016420564d3`. The new colleague-preparation commit changes
documentation only. It preserves the tested implementation. The combined applicable Maven reactor
passed 280 checks (zero failure/error/skip); 14 browser tests, Vue type-check and both builds passed.
The real k.Actors bootstrap, workflow DTO binding, stage authorization, native close/reopen,
closure CAS races, fake notices, minimal Git writer and durable receipts were covered. This is
source-level integration evidence; no live IdP or real-account revocation result exists.

## Before a user-approved feature-branch push

1. Verify the latest bundle/patch hashes and clean checkout against its delivery receipt. Use a
   fresh isolated clone; do not reset, merge into or edit the user's current worktree.
2. Re-read remote develop and the proposed destination branch. If either moved/appeared, review
   the actual new commits in isolation and resolve/retest affected code before proposing a push.
   Never assume permission to overwrite or force a branch.
3. Review the complete pilot diff and its intentional policy changes: authenticated community
   admission, actual reviewer grants, owner/assigned-editor control, private drafts, immutable
   submitted history, atomic existing-flow CAS and reserved metadata. Generic workflow request
   JSON binding and persistence changes have applicable regressions but affect shared modules.
4. Use the recorded published Languages artifacts or equivalent verified publication; retain
   task-local Maven/test-home isolation. CI/build availability on the intended remote has not
   been exercised. Both `Jenkinsfile` and `.github/workflows/build.yml` currently pass `-DskipTests`.
   Their green build is packaging evidence, not execution of the 280 review regressions. Run the
   explicit handoff commands or an operator-approved equivalent test job before treating those
   checks as CI-verified. The native/browser scenario currently uses this Windows/headless-Edge
   setup and isolated UI dependencies; Linux CI browser execution has not been established.
   No Jenkins job was triggered; the user will trigger it if needed.
5. Present the exact branch/head, destination and evidence for the user's push authorization.
   A source feature-branch push can precede colleague scheduling; absence of designated test
   accounts does not block preparing/reviewing the code. It does not certify live-auth acceptance
   or authorize merge, deployment, email delivery, project writes or a remote project push.

The checked-in Jenkins pipeline uses Maven `deploy`, container publishing and service-update
stages for `develop`/`master`. A feature branch normally uses `install`, but a last commit message
containing `[docker build]` enables its container/update path too. Keep the reviewed source push
on the explicitly approved feature branch, with no such marker. Do not merge/push to develop or
assume that user approval for a source feature-branch push includes those publication/service
actions. Actual Jenkins job configuration and triggers have not been inspected or exercised.

## Later deployment/pilot gates

The [colleague worksheet](ontology-review-colleague-checks.md) records all real-auth checks as
NOT RUN. Use existing authorized colleagues when convenient. Confirm a stable publicly reachable
Resources URL/auth return path, intentional workflow/project grants, protected workflow/outbox
storage and initialized clean host Git repositories. Pending archives require fresh editor retry.
Real mail still requires a verified-mailbox/consent directory and explicit delivery authorization.
Keep the minimal digest default; do not enable the older detailed-history mapper implicitly.

## Access limitation

Reading `remote.origin.url` through `git -C C:/Users/Ferd/git/klab-services` was denied by the
filesystem before the read executed. It was not retried. The original checkout's current metadata
and unpublished work were not inspected or modified. The public remote was checked independently
using the repository URL declared in this isolated copy's `pom.xml`, with credentials disabled.
Therefore this checkpoint assesses integration against published develop, not unpublished local
changes in that original checkout. No push approval was requested or consumed.
