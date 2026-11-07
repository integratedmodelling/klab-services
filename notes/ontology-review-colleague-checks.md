Current staged pilot: follow [the participant guide](../ONTOLOGY_REVIEW_PILOT.md) and [staged review contract](../REVIEW_CYCLES_PILOT.md). Older evidence below describes its dated implementation checkpoint, not verification of the new stage revision.

# Colleague pilot setup and real-auth acceptance

Status: prepared for later use with existing colleagues; no designated test account is available.
Do not create accounts, change identity infrastructure, bypass authentication or wait for new
accounts to complete source preparation. All real-auth rows below start **NOT RUN**. The passing
four-principal scenario uses a loopback fixture that constructs authenticated scopes; it does not
exercise the identity provider, production filter/session lifecycle or real account revocation.

## Operator setup

Use an operator-approved staging deployment and disposable ontology host project. Deploying,
restarting services, changing grants, or inviting colleagues is outside the preparation performed
here. Use ordinary existing authentication and the site's authorized administrator process.

| Participant | Required authority |
| --- | --- |
| Editor | Authenticated existing account, `workflow.roles=EDITOR` and `workflow.permitted=ontology-review-pilot`; owns this flow or is its assigned editor |
| Reviewer A and B | Distinct authenticated existing accounts, actual `workflow.roles=REVIEWER` and `workflow.permitted=ontology-review-pilot` |
| Community | Authenticated existing account with a nonblank identity; no reviewer or workflow-wide editor grant |
| Operator for revocation | Existing administrator authorized to change the selected staging account/session grants through the normal site process |

Those workflow settings are authoritative group custom properties, not editable form fields or
HTTP command claims. Avoid wildcard/admin grants to make a test pass. Workflow roles do not
grant ontology/project access or service administration. The editor additionally needs ontology
read access for bootstrap and actual host-project material write access for the digest commit.
If a participant/authority is unavailable, leave the corresponding rows NOT RUN; do not substitute
an administrator or a synthetic principal and mark the row passed.

Before login, record the exact build commit, Resources public base, workflow id/version, ontology
target, flow/cycle/round IDs and expected role-to-stage matrix in private pilot evidence. Record
the site's expected token/session lifetime and revocation propagation policy. Keep tokens,
cookies, private contributions, mailbox data and personal account identifiers out of public Git
or shared reports. Use separate browser profiles for distinct users; tabs may share a session.

Verify the public Resources URL and auth return route preserve `flow`, `cycle` and `round` query
parameters. The advertised service base is used in persistent digest links and must remain valid
or have a durable redirect. Back up protected workflow data and the private outboxes together.
The local database supports one owning process. A new digest requires a clean initialized ordinary
host repository with an attached branch, no conflicting mutation owner and writable storage.

## Create the disposable review

An authorized editor can use the existing create-flow API. Substitute an actual accessible
disposable ontology URN for `pilot.ontology`; send this JSON through the normal authenticated
client, not through the synthetic test bridge:

```text
POST <Resources-base>/api/v1/flows?workflowId=ontology-review-pilot
Content-Type: application/json
```

```json
{
  "schemaId": "bootstrap",
  "assetUrn": "pilot.ontology",
  "assetType": "ONTOLOGY",
  "permissionsOwnerUrn": "pilot.ontology",
  "title": "Colleague ontology review pilot"
}
```

The web UI also offers **Create private bootstrap**, with server-derived creation permission.
Use the returned flow ID and its current bootstrap state ID in
`<Resources-base>/ui/review?flow=<id>&stage=<id>`. The editor uses the explicit bootstrap action,
checks the generated draft, saves a candidate and opens the desired rounds. From the selected
iteration, copy the URL retaining its real `stage` ID along with `flow`, `cycle` and `round`. Do not share the private bootstrap URL with reviewers. Participants
need no direct access to unrelated editing attachments; the narrow cycle artifact endpoint checks
stage admission and the exact bound proposal/source. Never place an access token in the URL.

## Acceptance worksheet

For each row record result, build, principal role, observed status and sanitized evidence privately.
Use the actual deployment path. An HTTP 403/401 assertion in the synthetic fixture is not a pass.

| Check | Expected result | Result |
| --- | --- | --- |
| Signed-out cycle link, then real login | Details require authentication; return preserves the intended cycle/round | NOT RUN |
| Community account, peer-only link | Peer feedback and candidate are denied; no fallback silently opens another iteration | NOT RUN |
| Community account, community link | Bound candidate and submitted community feedback visible; no peer text or foreign drafts | NOT RUN |
| Reviewer account | Peer feedback visible; another author's draft remains excluded | NOT RUN |
| Flow editor and unrelated workflow editor | Owner/assigned editor controls this flow; unrelated editor cannot open/close/integrate/archive it | NOT RUN |
| Reviewer A/B and community save/submit | Independent attributed drafts, submission shared only in admitted stage; editor sees submitted work | NOT RUN |
| Contributor/proposal filters | Attribution and candidate revision remain clear; filtering never overwrites the editor's unsaved candidate | NOT RUN |
| Two concurrent edits/close vs submission | One current-revision write wins; loser keeps local work for explicit refresh/retry; closure remains immutable | NOT RUN |
| Creator bootstrap with unrelated editor/admin and public flag | Only creating editor with current grants can read/update bootstrap; no private catalog reference or generic API bypass | NOT RUN |
| Create interrupted after server persistence | No blind repeat; reconcile current-account flows and open the saved bootstrap | NOT RUN |
| Move to coalescing | Open rounds/cycle close atomically; new creator-owned editor stage is editable; historical stages remain immutable | NOT RUN |
| Untouched coalescing seed | Termination is available without pretending the automatically loaded candidate is a user edit; return requires a saved local candidate | NOT RUN |
| Integrated proposal and return to review | New saved revision opens a distinct cycle and review stages; originals remain consultable | NOT RUN |
| Explicit termination and restart | Flow stays CLOSED, historical views read-only; no generic reopen, acceptance, application or publication | NOT RUN |
| Reviewer grant removed, fresh credentials | After the site's revocation propagation takes effect, new peer reads/writes are denied, including direct API/artifact calls | NOT RUN |
| Existing token/session after role removal | Behavior matches the recorded token/revocation policy; any continued old-token access is timed and reported, never claimed as immediate revocation | NOT RUN |
| Session expiry/sign-out and stale browser view | Next protected requests fail; reauthentication restores only current authority; refresh clears denied shared content without redirecting private work | NOT RUN |
| Peer-to-community visibility change with unsaved work | Original context retained/read-only; recover or discard own work explicitly; no automatic transfer to community | NOT RUN |
| Closed cycle after refresh/operator-approved restart | Same frozen history and stage permissions; no editing/reopening of the closed record; later cycles allowed | NOT RUN |
| Close with clean authorized host | Minimal digest committed locally; successful receipt identifies actual commit/path/manifest hash; no remote push | NOT RUN |
| Digest link copied to an unauthorized account | Link grants no access; persistent stage API and exact artifact checks still deny | NOT RUN |
| Dirty host/other mutation owner/revoked project write | Archive deferred, unrelated work preserved, workflow closure remains successful | NOT RUN |
| Fresh editor archive retry, including after restart | Rechecks workflow and actual host write access, writes only that job; exact retry has original receipt and no duplicate commit | NOT RUN |
| Inspect project digest and private history | Digest has no contribution text, drafts, participant identities or proposal metadata; detailed history stays in protected workflow data | NOT RUN |
| Notice lifecycle | Intents may be queued; no real messages sent and no mailbox/consent assumptions introduced | NOT RUN |

JWT/session revocation is established only by the actual site's observed policy. Cached scope
simulation cannot establish it. Previously downloaded material cannot be retracted by revocation.
Pending/blocked archives require a fresh editor retry; startup replay retains no user scope and
does not write projects. Real mail remains disabled until authoritative mailbox verification,
consent and delivery authorization exist. No production acceptance/publication is part of review
closure or of this pilot.
