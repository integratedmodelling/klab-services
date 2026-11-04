# Host H2GIS dependency correction

Contributor-guide author-agent review; not maintainer approval or deployment authorization.
Base: accepted Python delivery backend `fda27fb2ab976eab65b2cd5b5dd96847f8016920`.

## Defect, correction and scope

The existing GeoTools platform management selects H2 **1.1.119** for H2GIS **1.5.0**,
although H2GIS requests **1.4.197**. Actual `H2GISFunctions.load` fails with
`NoClassDefFoundError: org/h2/api/Aggregate`; resolution/compilation alone missed it.
Manage the H2GIS-required H2 release explicitly at the host root, keeping GeoTools
34.4, Jackson and all H2GIS declarations unchanged. This also permits the focused
HM0.12 component proposal (component PR#11) to use a compatible host-provided H2,
instead of fixing only the component's test classpath while packaging excludes it.
No GeoTools35, moving HM snapshot, plugin rewrite or broad database migration.

## Validation and review

- Original driver failure and isolated successful substitution are preserved in
  `.consolidation-evidence/hm012-h2gis-probe-20261104`.
- Root override resolves H2 **1.4.197** throughout the selected Runtime dependency
  reactor. The clean-source baseline plus this POM delta passes **222 tests**,
  zero failures/errors/skips; exact dirty diff/command and selected trees retained
  in `h2gis-host-candidate-reactor-20261104`.
- New host regression initializes the actual resolved H2GIS extension, then checks
  the independent planar rectangle oracle **4×3=12** coordinate-units squared.
  It passes at the same POM delta, with exact source hashes in
  `h2gis-host-new-regression-20261104`. No alternate test-selected driver is used.
- Dedicated hosted workflow added; merge waits for applicable build/Jenkins/checks
  and the owned host/new-component compatibility run.

Review found no demonstrated defect in this narrow management correction. The
new component's packaging excludes host artifacts, so actual-host loading must
not be inferred from standalone component tests. Current science baseline still
uses the separately recorded preserved component; do not claim HM0.12 deployed.

**Deployment boundary:** tests use fresh disposable memory/owned state only.
No existing H2 database, Modeler service, shared Maven artifact or deployment is
replaced. Compatibility/migration of arbitrary legacy persisted H2 files is not
established; any real deployment must handle that separately with scoped consent.
This is a dependency-source correction, not permission to upgrade user databases.

Recommendation: publish a focused dependency PR; finish host/component/science
and normal hosted gates before merging. Existing independently qualifying Python
PRs continue in parallel.
