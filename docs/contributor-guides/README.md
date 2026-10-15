# Contributor guides

Eric's frequently used planning and CLI references, imported unchanged from the
supplied Downloads files. Their original filenames are retained so companion
references remain recognizable. The originals outside the repository are untouched.

| Guide | Use |
| --- | --- |
| [Complete Python client planning guide](KLAB_Python_Client_Planning_Guide.md) | Full supplied 14 October 2026 plan, including the historical roadmap. |
| [CLI contribution review](KLAB_CLI_Contribution_Review.md) | Read-only review prompt, evidence standards and report format. |
| [Connection-layer CLI specification](KLAB_Connection_Layer_CLI_Spec.md) | Original implementation scope, contract inventory and acceptance expectations. |

## New feature specifications

- [Scientific analysis and provenance CLI specification](KLAB_Scientific_Analysis_Provenance_CLI_Spec.md):
  one faithful numerical raster/xarray workflow with bounded read-only recorded lineage.
  Prepared in-repository on 15 October 2026; unlike the three supplied originals,
  this is a new implementation specification.

## Which planning document should I use?

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
