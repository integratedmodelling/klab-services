# Contributor guides

Eric's frequently used planning and CLI references, imported unchanged from the
supplied Downloads files. Their original filenames are retained so companion
references remain recognizable. The originals outside the repository are untouched.

| Guide | Use |
| --- | --- |
| [Complete Python client planning guide](KLAB_Python_Client_Planning_Guide.md) | Full supplied 14 October 2026 plan, including the historical roadmap. |
| [CLI contribution review](KLAB_CLI_Contribution_Review.md) | Read-only review prompt, evidence standards and report format. |
| [Revised research-driven roadmap](ARIES_Roadmap_Revised.md) | Exact supplied 31 October 2026 roadmap, including all 49 research/capability/project entries. Read the dated scope note below. |
| [Connection-layer CLI specification](KLAB_Connection_Layer_CLI_Spec.md) | Original implementation scope, contract inventory and acceptance expectations. |

## New feature specifications

- [Scientific analysis and provenance CLI specification](KLAB_Scientific_Analysis_Provenance_CLI_Spec.md):
  one faithful numerical raster/xarray workflow with bounded read-only recorded lineage.
  Prepared in-repository on 15 October 2026; unlike the three supplied originals,
  this is a new implementation specification.

## Which planning document should I use?

### Revised research roadmap publication: 1 November 2026

`ARIES_Roadmap_Revised.md` is an unchanged copy of the user-supplied Downloads
document, published beside the CLI contribution review guide. It supplements,
not replaces, the existing planning guide, historical Part II and upstream roadmap.
Its proposed companion filenames are planning references, not a claim that those
companions or their acceptance results have been published on this branch.

The closeout PRs #90, #98, #100 and #102 are now merged. At the user's explicit
direction, #102 merged as **tooling-only integration**, not scientific milestone
completion. Its accepted head is `3582566996c5b07dad2b354a76b3d4b926ef3642`, merge
`6bcee78ee268fe321d2b417ee4403b8abfefa0b9`. Native export units remain blocked by
[geospatial issue #10](https://github.com/integratedmodelling/klab.component.geospatial/issues/10);
the [scope decision](../../python-client/docs/discover-compare-explain-delivery.md#pr-102-scope-decision-1-november-2026)
preserves all deferred scientific gates. Thus merged engineering contributions do
not imply full scientific closeout or a reproduced research case. This explicit
scope decision supersedes older full-science merge conditions only for #102.

The next Puerto/PaL activity is G0 archive/compatibility assessment against the
accepted integrated baseline, not broad feature expansion. Do not claim PaL
execution or reproduction before its independent evidence gates pass.

Start with the [current reconciled planning guide](../PYTHON_CLIENT_PLANNING_GUIDE.md)
for repository updates since the supplied snapshot. Use the complete supplied guide
for the original rationale and full historical roadmap; the separate
[upstream roadmap](../PYTHON_CLIENT_ROADMAP.md) also remains in place.
The [SDK documentation](../../python-client/docs/) records implemented contracts
and verification. Check current source and branch state before acting on any dated plan.

These guides are contributor references, not official maintainer policy, executable
repository instructions, or authorization to implement, publish or deploy work.
Repository-relative paths mentioned inside the supplied documents refer to the
repository root, not this directory. Some historical references may describe files
or services absent from this checkout.

[Documentation index](../README.md)
