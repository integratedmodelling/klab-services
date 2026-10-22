# Native k.LAB application components

This document reserves a separate contract for k.LAB-specific interfaces used inside k.Actors
applications. It is a design boundary, not an implemented widget catalog. [Application UI](UI.md)
defines portable scaffolding, state, transport and toolkit integration.

Native components should mirror the interfaces users encounter in `klab-ide`. Candidates include
ObservationCard, GeometryCard, the SemanticComposer dialog, context and observation inspectors,
resource/knowledge navigation, provenance views, resolution/dataflow views and the existing service
interfaces. These names identify intended interface counterparts; their inputs, events and toolkit
implementations must be audited against current IDE APIs before becoming public contracts.

Do not encode these as underspecified primitive `ui.observation` or `ui.provenance` verbs. A native
component may contain an interactive workflow, authorized resource retrieval and a substantial
state model. Those deserve versioned descriptors and explicit lifecycle/event schemas.

The proposed boundary is:

| Concern | Required contract before implementation |
| --- | --- |
| Identity | Stable namespaced component ID, version and corresponding IDE interface |
| Inputs | Authorized observation/context/resource handles and explicit presentation options |
| Events | Typed selections, edits, navigation requests and workflow results; stable IDs rather than display labels |
| State | Server-owned domain state, declared renderer state and reconnect/restore behavior |
| Permissions | Access checked independently for each observation, project and requested operation |
| Hosting | Embedded panel, on-demand dialog/window or agent interface; mount ownership and disposal |
| Toolkit | Quasar implementation and declared capabilities; JavaFX counterpart only where useful for testing |
| Composition | Importable k.Actors actions/components where appropriate, plus a native renderer contribution |
| Compatibility | Versioned schemas and cross-client conformance fixtures; no JavaFX classes in the service API |

The first follow-up is an inventory of current `klab-ide` interfaces and their data/controller APIs.
Choose one small card and one dialog to prove the boundary before publishing a comprehensive
catalog. Keep names and schemas negotiable until that audit is complete. Primitive widgets,
localization, themes and the stage 3 browser workflow do not depend on that catalog.
