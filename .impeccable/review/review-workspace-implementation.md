# Review workspace implementation

2026-11-07 — approved code-first operational extension; finish-review disposition: **ship**.
Implements the five review issues through shape, layout, distill, clarify and harden, within
the [approved brief](review-workspace-brief.md) and [finish review](finish-review.md).

## Scope and source of truth

`ResourcesReview.vue` and `ReviewCyclesPanel.vue` in the resources-service web UI provide the
readable candidate beside the authorized feedback task and guided private editor preparation.
Shared `ProposalReader.vue` / `ProposalValue.vue` supply the text reader; core `App.vue` and
`types.ts` supply the optional dirty-work navigation contract. Backend protocol, authorization,
provenance, frozen candidates and immutable submitted history remain authoritative. Completing
or terminating a review does not mean scientific acceptance, application or publication.

Root `DESIGN.md` and `.impeccable/design.json` are preserved as the dated baseline. Their earlier
review description (native buttons, dark source editor, equal columns and 850px stacking) differs
intentionally from this local extension. This document records that scoped change rather than
refreshing global tokens or approving a new identity. The dashboard and k.Actors styling are
outside this extension; the incumbent shell eyebrow is not canonized as a reusable review rule.

## Local visual roles

| Role | Implemented behavior |
| --- | --- |
| Reading and layout | Review width caps at 1400px with 24px padding; candidate/feedback columns use 1.35:1 with a 48px gap and a divider. At 1000px they stack in DOM reading order with a 32px gap; candidate/feedback jump links remain available. At 600px padding becomes 16px 8px and editor preparation fields stack. |
| Type | Inherited application sans-serif; body 16px/1.55, reader 16px/1.65 capped at 72ch, page heading 26–36px/1.2 at weight 600, section headings 22px, task headings 18px, preparation legends 20px, source 14px/1.6 monospace, supporting labels/notes 14px. Reader title is 24px/1.35. |
| Spacing | 8px label gap; 12px control gap and field padding; 16px feedback/control separation; 24px document-field rhythm; 32px section separation; 48px desktop task gutter. These are local semantic roles, not a replacement global spacing scale. |
| Controls and boundaries | Quasar buttons/dialogs provide actions and navigation decisions. Native labeled fields use 4px corners, white fill and a #91a1b1 border. Disclosures preserve visible closed/open markers and 44px minimum summary targets; mobile Quasar buttons and jump links have 44px minimum height. |
| Functional colors | Text #17283b, supporting text #53667b, action/focus #2459a8, dividers #cad5e2 and source/confirmation ground #f7f9fc. Success/private-save feedback uses #183f35 on #edf7f3; alerts #8c3030 on #fbecec; uncertainty #805c1d on #fff7e5. These are scoped task roles, not a new global palette. |
| Focus and motion | Fields, disclosures and links show a 3px action-color focus outline with 3px offset. Text selection remains legible. Sticky operation feedback accounts for shell-header height, caps at 30vh and scrolls. Its spinner stops under reduced motion. |

Named disclosures retain exact source, supplied metadata, alternative proposals, materials,
filters and history without placing technical tasks ahead of ordinary reading and feedback.
Editor preparation separates private details/materials, generation/upload and inspect/save/start
review steps. No replacement brand imagery, display font or global decorative vocabulary is added.

## Supplied content and completeness

The reader parses JSON-compatible YAML with strict parsing and a 20-alias expansion limit.
It renders escaped Vue text, arrays and labeled object fields; it does not interpret supplied
markup as HTML or invent scientific prose. A recognized proposal envelope separates supplied
metadata; unknown fields and non-string title shapes remain visible.

Structured reading is bounded by an 800-node budget, depth 8, 100 items/fields per collection and
20,000 characters per long string. A visible completeness notice directs users to the exact
source/download when shortened. Malformed or scalar documents show an honest text fallback,
also capped at 20,000 characters with a completeness notice. These limits bound rendered reading;
they are not a claim that parsing arbitrary input has no resource cost. Exact source and download
retain the supplied document string without reader truncation or YAML-to-JSON reserialization.

## Private saving, sharing and retained work

Private saving is separate from shared immutable submission. Before sharing, confirmation names
the audience, proposal/revision and immutable consequence. A contribution receipt appears only
after the authoritative response matches the pending identity, round, contribution and content;
it records audience, proposal, revision and contribution identifiers before private text clears.
Later corrections require a further contribution rather than rewriting submitted history.

An uncertain outcome retains private work and blocks repeat sharing until an explicit status
check reconciles it. A status check finding no matching submission retains the draft for review
and an explicit retry. Access failures clear protected view/receipt while retaining private text.
No new persistent browser storage of review content is introduced.

The optional `DashboardContext.registerNavigationGuard` contract is documented in
`docs/WEBUI_ARCHITECTURE.md`. The review registers live dirty/busy/save/discard callbacks on mount,
unregisters on unmount, and separately retains `beforeunload` protection. Shell-owned navigation,
sign-out and owned history offer stay, private save or explicit discard; failed saving retains
the route and work. Save is unavailable when mixed or unsupported dirty state cannot be safely
saved. These client protections do not grant access or replace server checks.

## Verification evidence and limits

Parent-run evidence: all 50 browser regressions passed; after the final disclosure-marker and
fallback-bounding fixes, all nine focused workspace regressions passed. Type checking and both
production and embedded builds passed. Relevant suites include `review-workspace.spec.ts` for
source fidelity/escaping, narrow and zoomed layouts, share confirmation/receipts, uncertain
submission and shell/history/failed-save retention, plus the existing review lifecycle/access/
concurrency suites. These results were supplied by the implementation pass, not rerun by the
documentation pass.

The finish reviewer inspected ten valid refreshed captures in this directory: `desktop.png`
(1440px), `mobile.png` (390px), `intermediate.png` (768px), `editor-desktop.png`,
`editor-mobile.png`, `mobile-feedback.png`, `submission-confirmation.png`,
`submission-receipt.png`, `shell-desktop.png` and `unsaved-work.png`. Evidence combines native
screenshots and headless Edge captures of local fixtures; fixture scientific material and
feedback identify themselves as illustrative. Final captures show readable task content, visible
disclosure states, working shell icons and the completed three-decision unsaved-work dialog.
They do not establish connected-service login behavior or screen-reader certification; backend
implementation was not independently reviewed in this finish pass.
