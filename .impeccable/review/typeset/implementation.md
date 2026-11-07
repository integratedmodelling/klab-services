# Typeset and polish refinement

User-requested continuation of the review workspace, including the shared font declaration and shell typography. Preserves Quasar, the existing sans-serif/Georgia roles, approved review topology, content, authorization and lifecycle behavior. No new font files, font dependencies, palette or replacement identity were introduced.

## Changes

- Removed the unsupported Inter candidate from the root fallback stack. Quasar still sets the actual production body font cascade. No hook ignore was added.
- Made review body/heading/label/source sizes relative to the browser root without changing their default 16px-root appearance. Kept the reader's 72ch maximum and 1.65 leading; explanatory prose wraps prettily and headings balance where supported.
- Set empty-state headings to 600 weight and 1.4 leading, matching other task headings; normalized review field-label emphasis to 600. Dialog headings now explicitly use 600.
- Overrode inherited Quasar heading metrics: full-page Georgia headings use 1.15 leading; dashboard section headings use 1.25. Removed the redundant full-page kicker and enabled tabular metric numerals.
- Restored dark service-name text against the light header, used legible public-access text, and made the compact service menu dark against the light header.
- Fixed workspace jump links under a service base URL. They retain the current review path/query and history state, move keyboard focus to the destination, and scroll without leaving or discarding the review. Added a behavior regression with unsaved feedback.

## Evidence

Computed fixture metrics: review body 16px/24.8px; document title 24px/32.4px at 600; field label 14px/23.1px at 600; empty-state task heading 18px/25.2px at 600; shell heading 48px/55.2px; dashboard section heading 28px/35px; leave-dialog heading 24px/31.2px at 600. A 20px root preference produced a 20px review body without horizontal overflow. No browser page errors or new text-font requests were observed; Material Icons remains separate.

Ten captures in this directory cover shell/review at 1440px, 768px and 390px, mobile feedback, a 20px text preference, the settled unsaved-work dialog, editor desktop/mobile, and dashboard desktop/mobile. These local fixtures demonstrate rendering and behavior, not connected-service login or assistive-technology certification. Independent typography review found no material visual issue.

TypeScript checking, production and embedded builds passed. All 51 review browser regressions passed. Build still reports the existing full-Quasar/chunk-size warnings. Automatic design hooks reported no deterministic issue after the root font cleanup; no additional manual polish detector pass or ignore was added. The critique snapshot lookup returned exit 2, so this pass did not consume or explicitly close a snapshot.

DESIGN.md and its sidecar received targeted typography-record updates only. Their broader dated baseline and unapproved-future-identity boundary remain intact.
