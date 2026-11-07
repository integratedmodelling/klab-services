# Try the staged ontology review pilot

Use `ontology-review-pilot@0.3`. Existing colleague accounts can be used after an operator
checks the Resources review URL and permissions. Email setup is not required.

1. Open `<Resources-base>/ui/review`, sign in and choose **Create private bootstrap**. Supply the real,
   accessible ontology URN. The IDE also offers **Create workflow** rather than
   requiring a transition to persist its initial draft.
2. **Bootstrap** belongs exclusively to the creating editor. Prepare the initial proposal
   using the current ontology, available `@review` annotations and optional project
   `BOOTSTRAP.md`. Edit and save the candidate before starting review. Administrators and
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
panel opens the Resources review UI for stage operations rather than presenting generic
controls that could conflict with this lifecycle.

Cycle closure records a minimal protected Git digest intent. Its link grants no access and its
content excludes contribution text, drafts and identities. Check its receipt or retry a deferred
archive from a closed cycle. Real email delivery remains disabled.

Automated multi-user checks use simulated identities. Actual identity-provider login,
permissions and revocation still require the separate
[real-account acceptance worksheet](notes/ontology-review-colleague-checks.md).
