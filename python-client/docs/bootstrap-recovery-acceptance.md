# Owned bootstrap, expiry and outage acceptance controls

These tests exercise the **existing** production `connect`, `Connection`,
`IssuedTokenProvider`, `CredentialGrant`, ordinary Client/Job and bounded data-read
APIs over separate owned simulator HTTP processes. No new production SDK protocol,
credential issuer, native endpoint, fallback or automatic federation API is added.

The positive path selects explicit approved endpoints, creates one owned Job and
reads all400synthetic cells in two bounded256/144cell calls. An injected SDK clock
expires the first grant after submission; same-principal provider reacquisition
allows that existing Job to finish. Exactly one native-shaped `/api/v1/submit`
request is observed: no replay. Synthetic token rotation retains the server owner.
This proves local SDK expiry handling, not real Hub issuance/license/role approval.

Negative controls reject an initially expired grant before service requests, and
reject provider failure or principal drift during reacquisition before scientific
requests. Credentials do not appear in diagnostics/traces. Cleanup uses an explicit
current owned test credential after denial, never a hidden fallback in the failed
connection; no unrelated user scope/service is touched.

Mixed-provider tests host one selected Runtime and an explicitly trusted standby
Runtime with other discovery peers on the standby backend. Stopping the owned
primary process does not switch selection or create any standby work. Discovery
may report unreachable or consume its bounded global deadline and raise TransportError,
depending on OS refusal behavior; an incomplete refresh must mark readiness unknown,
not retain an old healthy label. Both outcomes require no scientific request/failover.
No production timeout or retry policy is relaxed. Restarting the same synthetic
Runtime identity at the same endpoint permits explicit refresh and newly requested
work; no old work is resumed or resubmitted. Changing the restarted service identity
instead makes the old connection stale: explicit fresh connection/catalog required,
never silent rebind or migration of old scopes/work.

The test launcher accepts a bounded explicit backend identity seed, default7, so
different owned processes can advertise distinct deterministic service IDs. Invalid
seeds fail before spawning a process. Child services retain CREATE_NO_WINDOW and
existing owned-process cleanup. No live Modeler/Graphdb readiness or repair needed.

JAVA-GAP-0015 remains **NOT_TESTED** for production issuer/license/roles/deployment
evidence. JAVA-GAP-0007 remains **MISSING_API** for maintained composition/migration.
`docs/DISTRIBUTED_TWINS.md` explicitly requires a coordinator-owned C twin, source
ownership, origin-qualified identities and per-source revision vectors; a client
union or standby-selection test does not satisfy that contract. These controls
preserve refusal/trust boundaries, not composition, cross-source science, automatic
failover or global consistency acceptance. Issues89/124/129 remain open.
