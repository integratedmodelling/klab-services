# Contributor review: pinned CI packaging-plugin bootstrap

2026-11-05 UTC. Author/CLI review, not independent human approval.
Independent branch `fix/ci-pinned-packaging-bootstrap`, base
`fe46ddc5ec3c188f62cd1d0b0703c6e54a4b9241`; no Q1/Q5 implementation inherited.

## Before / after and reason for scope

PR201's actual parser/Python/Jenkins checks passed, but its unchanged packaging
command failed twice resolving `org.integratedmodelling:klab.product:1.0.1-SNAPSHOT`.
The configured Central Portal snapshot metadata URL also returned404. A warm
developer cache is not a reproducible fresh-runner dependency contract.

This CI-only increment checks out immutable official plugin source
`integratedmodelling/klab.product@0c80acb1f16a1d405f888cf99286d6e365293559`,
whose POM declares the **same1.0.1-SNAPSHOT** version, installs the current reactor
API first, then builds/installs the packaging plugin. The existing full
`./mvnw -B -U clean package -DskipTests` command remains unchanged. No dependency
version, repository URL, server/scientific/worldview source or product behavior is
changed. The secondary checkout never persists credentials. No deploy/publish,
release upload, external repository write or branch-protection bypass occurs.

## Consequence-ordered findings

1. This is an explicit source-built toolchain candidate, **not proof of byte
   equivalence to the unavailable published snapshot**. Exact GitSHA supplies
   provenance; changing it later requires a reviewed change. No floating master,
   arbitrary replacement plugin or binary vendoring is used.
   This does not pin every transitive dependency of the preexisting Maven reactor
   or turn a development SNAPSHOT into an approved public release.
2. API-first install is necessary because the plugin depends on
   klab.core.api1.0.0-SNAPSHOT. Building both from the selected sources avoids
   relying on a cached incompatible API. The packaging job still skips Java tests
   exactly as before; compile/package success is not a full Java-test/science pass.
3. Local feasibility uses an isolated services clone at the accepted base, a
   bounded immutable source download and a fresh **private Maven repository** with
   explicit minimal settings. It does not rewrite the user's repository/cache,
   change existing compiled fixtures, run services or publish artifacts.
4. Actual full packaging and hosted job results are separate acceptance gates.
   Source checkout/compilation alone cannot establish distribution packaging.
   Original two failed CI attempts remain preserved, not waived as optional.
5. No client identity/provenance/scientific calculations or remote mutations are
   introduced. Scope/agent lifecycle, permissions, notebook concurrency, units and
   historical replication checks are N/A to this CI-only toolchain change—not
   silently accepted by it. Normal checks/protection/review still govern merging.

## Actual checks / pending gates

Official pinned source download and acceptedAPI/plugin compilation+install pass
in the isolated privateM2 environment. Exactarchive/POM/JAR digests, commands and
exit statuses: external program evidence `packaging-bootstrap/source-receipt.json`,
`commands.json`, `result.json`. Full unchanged packaging is currently running in
that same sandbox; no pass predeclared. FinalCI/normalmerge remain required.

No scientific claim, release authorization or supported A/B completion follows.
Program Q1 and Q5 each have locally accepted bounded workflows but remain unmerged;
this addresses their demonstrated packaging prerequisite only.
