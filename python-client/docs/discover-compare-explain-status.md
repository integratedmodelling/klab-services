# Discover / compare / explain: checkpoint and blockers

This feature branch is an incomplete milestone checkpoint, not accepted scientific
delivery. F1 semantic discovery, F2 inspection, F3 execution evidence and F4 bounded
experiments have Python implementations. The F1–F4 contribution reviews distinguish
offline evidence from missing live acceptance.

## Verified checkpoint

- Python 3.12.7, Windows: 1,287 unit/integration tests pass; existing new-module and
  changed-code coverage gates pass; established Ruff fatal-error checks pass.
- No Java, component or worldview source changes were made for this milestone.
- Earlier read-only IDE diagnostics found four local services responding. Resources
  had no workspaces/adopted worldview; Reasoner had accepted generation 0 and was
  not operational. These are observations, not a guarantee of current service state.
- A native local-authenticated, read-only Resources capabilities request advertised
  CREATE and ADMINISTER. The anonymous endpoint advertised READ only; that anonymous
  response is not evidence that the IDE user lacks permission.

## Resolve in this order

1. **IDE form/catalog:** reopen the workspace form after opening the local Resources
   dashboard. If still disabled, save work and try a normal IDE relaunch (check any
   prompt about stopping local services). If reproducible, collect installed IDE
   version, Resources URL, sanitized logs and reproduction steps for the IDE
   maintainer. Current public IDE source filters services by CREATE, but its exact
   match to the installed binary is unverified. Do not broaden permissions, delete
   secrets, or update components as a workaround.
2. **Compatible knowledge:** identify the intended existing worldview and executable
   model project, exact revisions and their supported import/adoption procedure.
   Load only into an explicitly authorized owned deployment. An empty workspace
   alone is insufficient. Recheck Resources' advertised worldview/workspaces and a
   positive accepted Reasoner generation with operational semantic readiness.
3. **Compatible unchanged stack:** obtain exact service build identities and the
   existing component archive required by the scientific fixture. Earlier hosted
   connection setup failed at Resources transport; repair the endpoint/network or
   select an explicitly authorized working deployment. HTTP health and login alone
   do not establish scientific readiness.
4. **Scientific matrix:** specify one independently verifiable executable fixture,
   two genuinely supported scientific variations and two grid resolutions. Supply
   reference values, units, CRS, orientation and missing-data expectations. Do not
   invent request parameters, model variations, units or expected results.
5. **Live acceptance:** verify actual discovery/model association, computed values,
   persisted dataflow/activity evidence, retained-result restoration from a fresh
   process, and permission boundaries using a configured authority and second test
   identity where required. Reproduce historical unit/dataflow problems before
   declaring them repaired.

## Remaining implementation, not deployment blockers

Shared integrated scientific workflow/acceptance wrappers, executed notebook,
validated sequential/parallel throughput, fail-closed deliverable builder,
installed-wheel/core-only/interpreter checks and final integration review remain
unfinished. Restoring the IDE does not complete those tasks or establish acceptance.
