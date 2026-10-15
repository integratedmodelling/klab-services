# Documentation index

## Python client and contributor workflow

- [Python-client branch preview and short demos](../python-client/docs/release-preview.md):
  delivered capabilities, reliability polish, upcoming work and verification limits.
- [Python SDK](../python-client/README.md): installation, usage and testing.
- [Contributor guides](contributor-guides/README.md): Eric's complete planning reference,
  CLI contribution review guide and connection-layer implementation specification.
- [Current Python planning guide](PYTHON_CLIENT_PLANNING_GUIDE.md),
  [upstream roadmap](PYTHON_CLIENT_ROADMAP.md) and
  [federation compatibility](PYTHON_CLIENT_FEDERATION_COMPATIBILITY.md).
- [SDK reference and verification](../python-client/docs/),
  [examples](../python-client/examples/) and [working notes](../notes/python-client/).
- [Notes and history](../notes/README.md): working logs, early Python plans,
  PR drafts and the GeoTIFF completion record.
- [Scientific analysis and provenance specification](contributor-guides/KLAB_Scientific_Analysis_Provenance_CLI_Spec.md):
  bounded next-feature scope, implementation sequence and acceptance gates.
- [Scientific analysis API](../python-client/docs/scientific-analysis.md): numerical
  raster/xarray reader, recorded neighborhoods and explicit consistency limits.

## Architecture and operations

- [Technical overview](KLAB.md) and [architecture](ARCHITECTURE.md).
- [Services](SERVICES.md), [scopes](SCOPES.md) and
  [service coordination/discovery](SERVICE_COORDINATION_AND_DISCOVERY.md).
- [Resources](RESOURCES.md), [reasoning](REASONING.md),
  [resolution](RESOLUTION.md) and [digital twins](DIGITALTWINS.md).
- [Storage](STORAGE.md), [provenance](PROVENANCE.md),
  [components](COMPONENTS.md), [Maven](MAVEN.md) and [testing](TESTING.md).

## Languages and semantic modeling

- [Observable expressions](OBSERVABLES.md), [worldview ontologies](ONTOLOGY_LANGUAGE.md),
  [k.IM](KIM.md) and [k.Actors](AGENTS.md).
- [Agent reference](AGENTS_REFERENCE.md) and [compiler](AGENT_COMPILER.md).
- [Semantic modeling](SEMANTIC_MODELING.md), [ODO-IM](ODO_IM.md) and
  [observation strategies](OBSERVATION.md).
- [Grammar drafts](grammar/README.md), [examples](examples/) and [testcases](testcases/).

## Ontology and proposal review

- [Editing](ONTOLOGY_REVIEW_EDITING.md), [reviewing](ONTOLOGY_REVIEW_REVIEWING.md)
  and [behavior design](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md).
- [Proposal-review contracts, implementation records and evidence](review/README.md).
- [Domain context pack](../llm/DOMAIN_CONTEXT_PACK.md) and [review tooling](../tools/review/).

## Where documents belong

Keep architecture, language references and established topic documents directly in
`docs/` to preserve existing links. Put supplied contributor planning/review guides
in `docs/contributor-guides/`, proposal-review handoffs and evidence in `docs/review/`,
SDK usage and verification in `python-client/docs/`, and working logs in `notes/`.
Executable review helpers belong in `tools/review/`. Add navigation links when adding
a new document. Source modules, Maven paths and package layout remain unchanged.

[Repository overview](../README.md)
