# Observation workflow diagrams

These diagrams illustrate the current contracts in [Observation](../OBSERVATION.md),
[Resolution](../RESOLUTION.md), [Admission](../ADMISSION.md),
[Occurrences](../OCCURRENCE.md) and [Consequences](../CONSEQUENCE_ENGINE.md).

| Diagram | What it explains | Editable source | Preview |
| --- | --- | --- | --- |
| Observation lifecycle | Request, planning/admission, binding, contextualization, root publication and scheduled/post-commit work | [Draw.io](observation-lifecycle.drawio) | [PNG](observation-lifecycle.drawio.png) |
| Model admission | Parsed Resources supply and the owning workflow's source, implementation, context and execution gates | [Draw.io](model-admission.drawio) | [PNG](model-admission.drawio.png) |
| Collective selection | Native partial completion, classification, fresh receipts and a transaction-bound model input | [Draw.io](collective-selection.drawio) | [PNG](collective-selection.drawio.png) |
| Consequence cycle | Native relevance, optional admitted work, causal commits, decisions and cycle drain | [Draw.io](consequence-cycle.drawio) | [PNG](consequence-cycle.drawio.png) |
| Occurrence scheduling | Process simulation, event instantiators, individual preparation and START/END execution | [Draw.io](occurrence-scheduling.drawio) | [PNG](occurrence-scheduling.drawio.png) |

The native files contain editable nodes and connectors. Each PNG embeds the same diagram XML
and can also be opened in Draw.io. Update the source and preview together, preserving identifiers,
edge endpoints and readable labels. Use PNG export with embedded diagram data when exporting
from Draw.io Desktop.

Blue identifies planning or semantic inputs, green Runtime execution/publication, amber guards,
purple scheduled/causal work and red incomplete evidence. Dashed connectors have the specific
meaning stated in each diagram's legend.

The diagrams describe implemented paths and explicit limits. They are not execution receipts.
In particular, the occurrence diagram does not claim complete event-lifecycle test acceptance;
see the [event acceptance requirements](../OCCURRENCE.md#event-acceptance-requirements).
