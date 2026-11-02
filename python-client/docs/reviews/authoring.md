# Authoring review: do not merge yet

**Keep PR #110 draft.** This is an author-conducted read-only review, not independent
approval. Namespace-only ADD implementation is software-tested, but overall
acceptance is BLOCKED. Full editing/history/conflict/publishing remains outside scope.

Integration base: `8b675ac30f5c37ab44eaf6cce211a5c98321f4db`. Latest real-Java
executable: `00672db8fe3d8180bb5ce2cfe8a69b274af2b992`. Java fixture binary tuple:
`a0a013a188fb752907f2d143a61b86f4a9cb0a0b`, not blanket upstream compatibility.

## Checked implementation

Authoring/ParsedSource/AuthoringReceipt reuse native Java parsing, transport,
immutable JSON and notification checking. Require exact class/source/URNs, safe
identifiers, explicit write consent and matching CREATE acknowledgements. Parents
must be newly confirmed in the same session. Project workspace/submission
coordinates are checked separately from its returned bare URN. No overwrite,
admin-lock elevation, publication, automatic rollback or mutation replay exists.
Receipts are not permissions, leases or history. Other source kinds fail before I/O.

Local software checks: 46 focused cases; 2041 offline tests, one existing Rasterio
warning, 71.42s; new module 100% unexcluded lines/branches; all existing gates,
lint and wheel/sdist pass. Installed-core checks cover consent/receipts/URN safety
without optional scientific dependencies. These do not establish Java acceptance.

## Four preserved failed real-Java attempts

1. Generated numeric namespace suffix rejected: zero writes. Fixture corrected to
   alphabetic source suffixes without weakening parser checks.
2. Workspace confirmed; project ADD HTTP 500 remained unknown. No replay or
   dependent deletion. Pinned ScopeManager grants ordinary remote scopes READ;
   exact server exception was not captured, so this is source-supported context,
   not a proven classification of the HTTP error.
3. Distinct signed fixture-only administrator created workspace/project/namespace
   and retrieved exact namespace source. SCRIPT failed class/source fidelity;
   Java behavior adapter uses syntax.encode(). SCRIPT was excluded, not silently
   reconstructed. Cleanup retrieve was malformed after project DELETE receipt;
   that protocol error was not relabelled absence.
4. Namespace round trip and parser rejection verified. Project cleanup verified by
   matching DELETE descriptor plus separate bounded listing. Workspace DELETE
   returned Error: descriptor removal explicitly unimplemented. **Test stays FAIL.**

All 13 recorded owned JVM identities were absent after each attempt. No shared
service/worldview/scientific project was edited. Residual metadata/uncertain state
remain in stopped private fixtures, not erased. No agent or scientific Job ran.
Facts/hashes: [authoring-controlled-20261102.json](../results/authoring-controlled-20261102.json).

## Required next decision

Do not merge under green offline/hosted checks, remove cleanup assertions or repeat
unchanged fixtures to seek a pass. Authorize a minimal server workspace-descriptor
removal fix with tests, or explicitly scope an existing-owned-workspace workflow
with verified identity/ownership and no new unremovable descriptor. An arbitrary
workspace string or silent relaxation of parent checks is not a safe substitute.
SCRIPT fidelity, ordinary-user CREATE and history/conflict remain separate gates.
No maintainer approval or assignment is implied by this report.
