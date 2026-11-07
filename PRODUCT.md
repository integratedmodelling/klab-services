# k.LAB

<!-- impeccable:product-schema 1 -->

## Platform

web

## Scope

This record covers all k.LAB services in this repository, with the web UI as the current interface. The Java service stack, modeler controllers, and native IDE integrations remain part of the wider product; the platform value identifies the current browser interface rather than prescribing a platform for every client.

## Users

- Application users interact through the k.Actors scaffolding and the applications it hosts.
- Administrators use service dashboards and the surrounding service-management interfaces.
- Regular users depend on review workflows. These workflows are a paramount product priority, although their interfaces are still primitive.
- The implemented ontology-review pilot distinguishes editors, peer reviewers, and community participants. Preserve those roles and their different access and lifecycle rules.

## Product Purpose

k.LAB turns an observation request expressed as meaning in context into a semantically validated, executable dataflow and live scientific artifacts. The resulting observation belongs to a digital twin and retains its semantics, provenance, and lifecycle state.

Users must be able to carry out application and review tasks while administrators understand and manage service operation. Successful interface work preserves the semantic observation workflow, provenance, federated service model, and server-side authorization while making those tasks clearer.

## Positioning

Independent providers contribute data, models, components, and compute to a shared semantic knowledge commons. Meaning and context support composition across services without requiring every asset to move into one platform or each provider to prearrange interoperability with every other provider.

## Operating Context

Resources services own projects, data, models, components, and adapters; the Reasoner owns worldview semantics and context-appropriate strategies; the Resolver owns resolution graphs and executable dataflows; the Runtime owns sessions, digital twins, execution, storage, and provenance. An Engine authenticates users, institutions, or agents and coordinates service access through user, session, and context scopes.

The shared Vue 3, Quasar, and TypeScript shell lives at `klab.core.services/src/main/webui`. Individual services supply dashboard panels and full-page extensions. Published k.Actors applications run through the runtime service using the shared shell, with server-owned application state and renderer reconnect support. Modeler controllers also support IDE integration.

The current ontology-review pilot supports private editorial bootstrap, frozen candidates for peer and community rounds, feedback coalescing, further review cycles, and termination without publication. Historical stages and submitted contributions remain immutable; private drafts and creator-only stages retain their access boundaries. Completing or terminating a review must not imply scientific acceptance, application, or publication.

## Capabilities and Constraints

- Preserve semantic observation workflows, provenance, federated services, and server-side authorization across interface changes.
- Keep service ownership and scope propagation intact; a dashboard manifest or hidden control is not an authorization boundary.
- Build on the existing Java 21/Maven backend and Vue/Quasar web infrastructure, including service-specific extensions and the toolkit-neutral k.Actors UI contract.
- Quasar is the preferred UI stack for the system (user-confirmed). The current visual styling is not an endorsed future design direction.
- Treat English as the primary interface language. Localization is a product requirement for k.Actors applications; it is not a general requirement for dashboards or review interfaces.
- Keep implemented functionality distinct from proposals. This repository implements the developing k.LAB 1.0 stack used in pilots; k.LAB 0.11 is maintained separately. Global semantic/capability discovery and some transport and execution paths remain incomplete or transitional.
- The project is licensed under GNU Affero General Public License v3; consult `LICENSE.txt` for the actual terms.

## Brand Commitments

Preserve the k.LAB name and established domain terminology, including k.Actors, k.IM, worldview, observation, provenance, and digital twin. No additional voice or visual commitments were established during initialization.

## Evidence on Hand

- `README.md`, `docs/KLAB.md`, and `docs/ARCHITECTURE.md`: product mechanism, architecture, repository boundaries, and development status.
- `docs/WEBUI_ARCHITECTURE.md`: shared shell, service extensions, authentication, and routing contracts.
- `docs/UI.md` and `docs/examples/ui/README.md`: k.Actors UI implementation boundaries and executable workflow examples.
- `ONTOLOGY_REVIEW_PILOT.md` and `REVIEW_CYCLES_PILOT.md`: current review workflow, roles, privacy, history, and publication boundaries.
- `klab.modeler/README.md`: controller layer and separately provided views.

These sources establish technical behavior and pilot workflows, not adoption, usability, or performance claims. Do not invent customer evidence or present planned capabilities as shipped.

## Product Principles

1. Preserve meaning, context, provenance, and lifecycle through every user action.
2. Design around each audience's actual work: application scaffolding, service administration, and review participation.
3. Prioritize usable review workflows while respecting private work, frozen candidates, and immutable history.
4. Preserve server authority and federated ownership across all client surfaces.
5. Use English by default and provide localization where k.Actors applications require it.

## Open Decisions

Product-specific accessibility requirements, measurable usability targets, and additional binding brand or voice requirements have not been established. Their absence does not waive ordinary accessibility practice.
