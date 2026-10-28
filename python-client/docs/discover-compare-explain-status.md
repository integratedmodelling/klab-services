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

### Hosted retest — 28 October 2026

The authenticated read-only prerequisite suites now pass: **3 passed in 8.16s**,
exit 0. Command from `python-client`, using the isolated Python 3.12.7 environment:

```powershell
python tools/run_functional_acceptance.py --suite connected-semantic-readiness --suite connected-unit-contract --profile examples/hosted-deployment.json --certificate C:/Users/lumsd/.klab/klab.cert --report C:/Users/lumsd/AppData/Local/Temp/opencode/discover-hosted-retest-20261028.xml
```

This establishes authenticated hosted connectivity, positive accepted Reasoner
generation, and preservation of requested `m` and `mm` units in both raw responses
and SDK checks. The earlier hosted connection and missing-unit blockers were not
reproduced; no Java root cause or deployed build identity is inferred.
No computation/session/context was created; persisted execution evidence,
scientific values, four-cell experiments and permission acceptance remain untested.
Separate direct public probes still found local Resources empty and local Reasoner
at generation 0/non-operational; hosted readiness does not repair the local stack.
The numbered items below retain the outstanding deployment/delivery prerequisites.

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

The shared workflow now has a first tested implementation in
`tools/workflows/discover_compare_explain.py`: typed four-cell matrix checks,
independent full raster/analysis validation, successful recorded-activity checks,
native runner integration, batch manifests and original-handle restoration helper.
The full offline suite passes 1,322 tests; the new helper has 100% line/branch
coverage and existing coverage/lint gates pass. This is not live scientific
acceptance. See `discover-compare-explain-workflow.md` for boundaries.

Additional authenticated read-only hosted probes on 28 October succeeded with F1
observable resolution for m/mm and F2 retrieval of
`staging.vxii.basic.elevation-quantifier` in `klab.staging.vxii` (one advertised
observable). This does not establish executable-model selection or scientific
correctness. UN.M49 authority search for Austria returned OK with two distinct
canonical identifiers; no candidate was implicitly selected or resolved. The
initial diagnostic script used nonexistent `AssetInspection.project` and failed
while printing; corrected `project_name` inspection passed. No SDK defect or
computation result is inferred from that diagnostic-script error.

Named integrated scientific acceptance wrappers, executed notebook,
validated sequential/parallel throughput, fail-closed deliverable builder,
installed-wheel/core-only/interpreter checks and final integration review remain
unfinished. Restoring the IDE does not complete those tasks or establish acceptance.
