# Review workspace implementation brief

User-confirmed scope: all five issues from the 2026-11-07 critique, implemented through shape, layout, distill, clarify and harden. Build path: code-first. Mode: Operate, with a readable scientific document as the main material.

- Job: regular participants read a frozen scientific candidate and contribute feedback to its actual authorized round. Editors privately prepare details/materials, generate or upload a candidate, inspect/save it and start review.
- Approved structure: readable candidate beside feedback on wide screens; guided preparation for editors. Stack at narrow/intermediate widths in DOM order with candidate/feedback jump links. Keep exact source, history, alternatives and filters accessible through named disclosures.
- First viewport: title and current context, quiet navigation, then candidate reading alongside the feedback task. Source serialization must not be the default reading surface. Optional technical fields must not dominate ordinary feedback.
- Signature interaction: private save remains distinct from shared immutable submission; review the audience/revision/consequence before sharing and receive an authoritative contribution receipt afterward. Unknown outcomes retain work and require an explicit status check before retry.
- Safety: shared shell links, sign-out and owned browser history navigation consult a dirty-work contract; failed private saving retains both route and work. Browser unload remains protected. No new persistent storage of review content is introduced.
- Constraints: Quasar preferred; use only supplied document content, support JSON and YAML, preserve exact source/download, bound large reading views with an explicit completeness notice. Preserve backend protocol, server authorization, provenance, frozen candidates and immutable records.
- Boundaries: no broader dashboard/k.Actors restyle and no replacement global visual identity. Root DESIGN.md remains a dated baseline, not an approved future aesthetic. This extension reuses functional application control colors and Quasar conventions.

Validation: review fixture lifecycle/access/concurrency regressions; reader safety and YAML fidelity; submission receipt/uncertain response; shell stay/save/discard, failed-save retention and browser-back protection; desktop/mobile/intermediate/zoomed overflow. Connected-service login and assistive-technology certification are outside the fixture evidence.
