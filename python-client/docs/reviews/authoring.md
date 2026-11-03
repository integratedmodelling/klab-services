# Authoring review: bounded acceptance passed, final integration checks pending

**Keep PR #110 draft until final-head checks pass.** This is an
author-conducted review, not independent approval. Namespace-only ADD now has a
fresh controlled acceptance pass on the rebuilt backend. Full editing/history/
conflict/publishing remains outside scope.

Original integration base: `8b675ac30f5c37ab44eaf6cce211a5c98321f4db`. Last failed
old-tuple Python executable: `00672db8fe3d8180bb5ce2cfe8a69b274af2b992`. Old Java tuple:
`a0a013a188fb752907f2d143a61b86f4a9cb0a0b`, not blanket upstream compatibility.

## Checked implementation

Authoring/ParsedSource/AuthoringReceipt reuse native Java parsing, transport,
immutable JSON and notification checking. Require exact class/source/URNs, safe
identifiers, explicit write consent and matching CREATE acknowledgements. Parents
must be newly confirmed in the same session. Project workspace/submission
coordinates are checked separately from its returned bare URN. No overwrite,
admin-lock elevation, publication, automatic rollback or mutation replay exists.
Receipts are not permissions, leases or history. Other source kinds fail before I/O.

Local software checks: 46 focused cases; final rerun 2041 offline tests, one existing
Rasterio warning, 61.08s; new module 100% unexcluded lines/branches; all existing gates,
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

## Rebuilt acceptance and remaining merge gates

User authorized separate workspace-deletion and duplicate-handler fixes. PR115
merged normally at `91319acead67d43a154b0018b7dcf28e1d238d21`; PR114 contains
empty-only owner/admin+DELETE checks and catalog/config/index removal, with no
cascade and no crash-atomicity claim. Fifteen new deletion tests pass. Six broader
authoring-source failures reproduce exactly on clean integration base and remain
preserved, not silently repaired or ignored as successful tests.

Fresh actual Resources-only acceptance uses Python
`852ae9b25d9a7d31bb8a01f96a3fa53dbe50391e` and rebuilt Java
`84afa1a4165faaa424cd58efeaad45370e021242`. Exact namespace source, invalid-source
rejection, three matching CREATE receipts, project/workspace matching DELETE
receipts and independent lists pass. Separate read-only restart confirms deleted
assets remain absent and workspace YAML membership is absent, with zero mutation
requests. Both owned JVM identities independently absent, hub threads stopped;
class/dependency fingerprints unchanged. No cleanup assertions were weakened.
The two preliminary rebuilt-server starts failed before writes (real Web UI assets
missing, then existing duplicate handler mapping); both remain preserved.

Latest facts/hashes: [authoring-rebuilt-20261102.json](../results/authoring-rebuilt-20261102.json).
PR114 merged normally at `67be84be9ff248c4e25b97816448bd303a28e3fd`.
Final software rerun: 2041 offline tests pass; all six historical/new-module coverage
gates, lint, wheel/sdist build pass. A fresh isolated core-only wheel installation
passes imports without scientific dependencies plus consent, three CREATE receipts
and exact-source mocked checks. Python source/tests/workflow are unchanged from
the live-accepted candidate; only integration prerequisites and evidence docs changed.
Before merge require final hosted CI. The accepted tuple is not blanket compatibility with
old binaries or all deployments. The fixture uses a distinct signed administrator;
ordinary-user write support and two-user privacy are not established.
SCRIPT fidelity, ordinary-user CREATE and history/conflict remain separate gates.
No maintainer approval or assignment is implied by this report.
