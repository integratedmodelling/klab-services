# Try the staged ontology review pilot

Use `ontology-review-pilot@0.4`. Existing colleague accounts can be used after an operator
checks the Resources review URL and permissions. Email setup is not required.

1. Open `<Resources-base>/ui/review` and sign in. Enter the accessible **Ontology URN**,
   **Review title** and **Review description**. You may select an existing proposal and reference
   files before **Create private bootstrap**. The state and files are submitted atomically.
   After success the Start form closes; use **Start another ontology review** only when you
   deliberately want a separate flow. A failed/uncertain response does not invite a second creation.
2. **Bootstrap** belongs exclusively to the creating editor. Edit its title/description, add
   reference material and editor notes, and either upload your proposal or use **Prepare review
   draft**. Preparation uses the current ontology, available `@review` annotations and optional
   project `BOOTSTRAP.md`. Complete the labeled proposal/revision/domain/purpose/community fields
   and choose a requested tier that is a whole number of 1 or greater. Proposal and revision IDs
   must start with a letter and use only letters, digits, dots, underscores or hyphens. Invalid
   preparation fields return a field-specific HTTP 400 without advancing the stage or publishing
   attachments; correct the value and retry against the same revision. Then inspect and save the
   candidate before starting review. Preparation does not overwrite an
   already supplied proposal. If preparation cannot be discovered or the ontology is unavailable,
   the page shows a diagnostic rather than silently hiding the next step. Administrators and
   other editors cannot read or edit this private bootstrap stage.
3. **Peer review** and **Community review** are separate stage instances. Starting a cycle
   freezes the selected proposal/source and opens peer review. The editor may open community
   review alongside it, close individual rounds, or open further rounds. Each stage has its own
   protected link and frozen candidate; a newer draft never replaces that candidate.
4. Choose **Coalesce feedback** to close the open review rounds and their cycle. In the
   editor-only **Coalescing** stage, compare submitted contributions with the candidate,
   integrate changes and save new proposal/source attachments. An altered proposal needs a
   fresh revision identifier. Historical submissions and stages remain immutable.
5. Either **Return to peer review**, which freezes the new candidate into a new cycle, or
   **Terminate flow without publication**. Termination closes the flow. It does not accept, apply or publish
   the ontology, and the terminated flow cannot be reopened through a generic API.

Reviewers and community participants sign in through their supplied stage link. Peer review
requires an actual permitted reviewer role. Community review requires an authenticated,
nonanonymous account, not an email address or an invented reviewer role. Participants can
save private drafts, submit feedback and propose alternatives. Other participants and editors
cannot see someone else's private draft. Submitted corrections are new contributions.

The stage selector includes permitted historical stages, displayed read-only. Stale commands
require refresh and explicit retry; unsaved feedback is retained. The IDE's specialized stage
panel provides native bootstrap title/description, preparation and attachment controls, and
opens the Resources review UI for review-cycle operations. New-workflow choices show only the
latest definition with its version. The structure button shows staged behavior and persisted
history without inventing ordinary transitions for the review protocol.

Cycle closure records a minimal protected Git digest intent. Its link grants no access and its
content excludes contribution text, drafts and identities. Check its receipt or retry a deferred
archive from a closed cycle. Real email delivery remains disabled.

Automated multi-user checks use simulated identities. Actual identity-provider login,
permissions and revocation still require the separate
[real-account acceptance worksheet](notes/ontology-review-colleague-checks.md).
