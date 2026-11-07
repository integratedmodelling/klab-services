---
target: "k.LAB web interfaces: dashboard, k.Actors scaffolding and review workflows"
total_score: 20
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:C:\\Users\\mario\\git\\klab-services\\klab.core.services\\src\\main\\webui"
timestamp: 2026-11-07T12-32-50Z
slug: klab-core-services-src-main-webui
closed: true
---
Method: dual-agent (A: /root/design_assessment · B: /root/detector_assessment)

# k.LAB web interface critique

The service stack has a credible scientific lifecycle, but its priority regular-user review experience still presents too much of the workflow machinery. The largest opportunity is a readable scientific review workspace built on Quasar. The current styling is a baseline, not an approved future identity.

Scope: `klab.core.services/src/main/webui`, including the shared dashboard and k.Actors scaffolding, plus the Resources review extensions. Scores emphasize the user-prioritized review workflows. These are expert judgments from source and fixture inspection, not measured user-study results or a production accessibility audit.

## Design health

| # | Heuristic | Score / 4 | Key finding |
|---|---|---:|---|
| 1 | Visibility of system status | 3 | Pending/private/historical states are explicit; submission needs a clear receipt. |
| 2 | Match with the real world | 2 | Scientific terminology competes with cycles, attachments and internal identifiers. |
| 3 | User control and freedom | 2 | Draft safeguards exist, but shell navigation bypasses unload protection. |
| 4 | Consistency and standards | 2 | Quasar shell/application controls and native review forms differ substantially. |
| 5 | Error prevention | 2 | Revision/busy guards help; immutable sharing lacks a distinct commitment moment. |
| 6 | Recognition over recall | 2 | Users must reconcile flow, stage, cycle, iteration and candidate. |
| 7 | Flexibility and efficiency | 2 | Filters/downloads help; source editors and duplicated navigation slow ordinary review. |
| 8 | Aesthetic and minimalist design | 1 | Optional technical fields and large empty editors compete with the current task. |
| 9 | Error recovery | 2 | Work retention is useful; raw exceptions and generic recovery instructions remain. |
| 10 | Help and documentation | 2 | Useful lifecycle prose, but little field-level explanation or example content. |
| | **Total** | **20/40** | **Acceptable; significant improvement needed.** |

## Design specificity

Meaning, provenance, frozen candidates, privacy and immutable history are specific to this product. The composition is less specific: users see a workflow administration form rather than a scientific reading and discussion environment. Dashboard polish currently exceeds the polish of the paramount review task. The existing serif/green dashboard styling should not be promoted into a future identity by default.

The detector returned **8 findings: 1 warning and 7 advisories** across two markup scans: `overused-font` ×1, `design-system-font-size` ×2, and `design-system-color` ×5. The font warning points to the Inter declaration in `src/styles.css:3`; it does not prove Inter renders. Advisories occur in ResourcesOverview.vue:23–24, ResourcesWorkflows.vue:68–69, and ResourcesWorkspace.vue:17–18. They identify implementation values absent from DESIGN.md, including stub-panel styles, not violations of an approved future design. None is a confirmed parsing false positive, but treating them as eight UX defects would misinterpret the evidence. These scans do not establish contrast or accessibility compliance.

## Overall impression

The product is more careful about protecting scientific work than its interface makes apparent. Preserve that care while replacing the protocol-oriented presentation with a clear document, audience, revision and next action.

## What's working

- Privacy and lifecycle copy distinguishes private drafts, shared submissions, frozen candidates, historical records and termination without publication. This avoids implying scientific acceptance.
- Busy/dirty guards, revision checks, retained text, downloads and browser-unload protection recognize the cost of intellectual work.
- Quasar controls and k.Actors theme, focus, reduced-motion and responsive-region support provide a useful implementation foundation.

## Priority issues

### 1. [P1] Reviewers encounter serialization before scientific content

**Evidence:** `klab.services.resources.server/src/main/webui/extensions/ResourcesReview.vue:515–525` presents the reviewed candidate in a raw textarea; `ReviewCyclesPanel.vue:237` displays an optional alternative JSON/YAML editor directly. In the populated participant fixture, raw candidate text dominates half the desktop workspace and ordinary feedback falls below the initial viewport. A disabled editor-only save action also remains visible.

**Why it matters:** Participants must extract meaning from transport documents before they can judge the proposal.

**Fix:** Make a structured reading view primary: title, revision, scope, proposal content and evidence. Where authored summaries/claims exist, expose them; do not invent scientific content. Keep exact source/download available in a disclosure and technical editing available to editors. Start ordinary contributions with feedback; reveal alternative proposal authoring on request.

**Suggested command:** `$impeccable shape`.

### 2. [P1] Unsaved-work protection ends at the review component boundary

**Evidence:** `ResourcesReview.vue:398–405` protects `beforeunload` and removes the listener on unmount. Shared `klab.core.services/src/main/webui/src/App.vue:84–88` changes routes with `pushState`; its conditional rendering switches components without consulting dirty work. Review selectors lock when dirty, but that is a different navigation path.

**Why it matters:** A user can see protective selector locks and still lose unsaved work by following a shell link. This is a source-supported integration gap, not a reproduced connected-service incident; hard-refresh protection does exist.

**Fix:** Introduce a shared dirty-work contract for shell links and browser back. Offer save privately, stay or explicitly discard before unmounting. Consider recoverable drafts only within the established privacy/access boundaries.

**Suggested command:** `$impeccable harden`.

### 3. [P1] Immutable sharing looks too much like private saving

**Evidence:** `ReviewCyclesPanel.vue:240–244` gives Save private draft and Submit feedback the same native button treatment. Submission calls the command immediately at `:133–134`; successful submission clears the editor without a distinct completion receipt at `:120–124`.

**Why it matters:** Sharing an immutable scientific contribution deserves a clear consequence and a reassuring end. An empty editor communicates little about what was shared or who can see it.

**Fix:** Distinguish saving from sharing. At commitment, show the candidate revision, actual audience and immutable-record consequence. Provide a concise review step or equally explicit commit treatment, followed by a receipt and an Add another contribution action.

**Suggested commands:** `$impeccable clarify`, `$impeccable harden`.

### 4. [P2] Navigation exposes the workflow's internal coordinate system

**Evidence:** `ResourcesReview.vue:465–474` supplies Flow/Stage; `ReviewCyclesPanel.vue:185–213` adds Proposal and cycle, Iteration, Contributor and Contribution proposal. The participant view exposed six selectors; some stage names repeat, while drafts use ID prefixes and raw timestamps.

**Why it matters:** Users must reconcile workflow objects to answer the simple question of which candidate they are reviewing.

**Fix:** Show one current-review context: title/ontology, revision, audience and status. Put historical stages/rounds in a named timeline; keep contribution filters separate. Use recognizable draft/round labels and human-readable dates.

**Suggested commands:** `$impeccable distill`, `$impeccable clarify`.

### 5. [P2] Review composition gives too many elements equal weight

**Evidence:** `ResourcesReview.vue:505–513` renders six preparation inputs together; `:553–561` gives controls similar outlines and applies dark editor styling even to short prose fields. Bootstrap exposes materials, notes, preparation metadata and a large empty candidate editor in one long page.

**Why it matters:** The current next action is difficult to distinguish from optional setup and future work.

**Fix:** Establish a Quasar review composition with current stage, one next action, readable candidate and contextual feedback. Collapse optional materials/technical metadata, use ordinary prose fields for natural language, and replace empty editors with compact actionable empty states. Choose the future visual direction separately from this structural work.

**Suggested commands:** `$impeccable layout`, `$impeccable typeset`.

## Cognitive load and emotional journey

Review fails **6 of 8** checklist items: single focus, chunking, hierarchy, one decision at a time, minimal choices and progressive disclosure. Grouping passes; working-memory support partially passes because the candidate and selected revision remain available. Six navigation selectors and six preparation fields illustrate the burden; these are not all choices within a single dropdown.

Scientific judgment and immutable history create legitimate intrinsic complexity. Exposing the server object model as navigation adds avoidable complexity. Privacy guidance reassures users at entry, but blank technical editors create an early confidence dip. Sharing is the highest-stakes moment yet receives little visual distinction; clearing the editor makes the ending weaker than the underlying recordkeeping.

## Persona red flags

- **Jordan, first-time reviewer:** JSON and overlapping stage/cycle/iteration controls precede the question to be reviewed and where to respond.
- **Sam, keyboard/screen-reader user:** Labels, fieldsets and status regions help, but lengthy administrative traversal and source-first reading delay feedback. No complete assistive-technology audit was performed.
- **Casey, interrupted reviewer:** Save/download and unload protection help, but shell-navigation recovery is incomplete and stacked document-first pages can put feedback far away. Fixture scaling is not a confirmed production mobile defect.

## Minor observations

- Show when dashboard data last refreshed successfully, particularly when retaining older values after an error (`App.vue:90–110`).
- Distinguish normal application Stop status from errors and separate instance-management actions (`ActorApplication.vue:119–123`).
- Localize renderer-owned Close and Progress strings for k.Actors applications; do not expand that requirement to dashboards/review.
- Replace expected raw server exceptions with task-specific recovery language and disclose diagnostic detail separately.
- The captured full-health bar looks nearly empty; verify the rendered bar before treating this as a confirmed semantic defect.

## Questions to consider

- What if reviewers first saw the scientific document and their contribution, with exact source and workflow history one action away?
- What should a participant see immediately before, and immediately after, their private text becomes shared immutable feedback?
