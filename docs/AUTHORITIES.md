# Authorities

Authorities connect a k.LAB worldview to an external terminology or classification without
loading that terminology in its entirety. A Reasoner materializes only the identities referenced
by semantic expressions, together with the parents and relationships needed to reason about them.
The provider may also interpret expressions whose meaning is richer than a practical description
logic encoding and supply its own subsumption distance.

This document separates the component and language support that exists now from the runtime and
reasoning work still required to complete the authority contract. See [components](COMPONENTS.md)
for component distribution and updates, [reasoning](REASONING.md) for the general semantic
contract, and [the ontology language](ONTOLOGY_LANGUAGE.md#79-requires) for worldview syntax.

## Names And Ownership

Three identifiers have distinct roles:

- The **authority URN** is the stable provider identifier returned by `Authority.getURN()` and
  declared by the Java `@Authority` annotation. Components and services advertise this value.
- The **component id and version** identify the archive containing the provider. Version selection
  during installation applies to the component, not to a worldview-local authority name.
- The **authority name** in `requires authority NAME` is local to one worldview. It becomes the
  namespace used by semantic expressions in that worldview.

The configuration map must contain at least the provider reference:

```kwv
identity ChemicalSpecies
    requires authority SPECIES {
        urn: authority:urn,
        catalog: "accepted-species"
    }
;
```

The snippet expresses the authority contract; acceptance of a particular unquoted URN form remains
subject to the worldview grammar. `NAME` does not rename the provider globally. A worldview may
bind the same provider URN more than once under different names or configurations, and each binding
must be treated as an independent configured authority. Registries therefore index providers by
their authority URN, while the Reasoner's future binding registry must be keyed by worldview and
local name.

Only an `identity` concept may declare an authority requirement. That concept is the worldview
anchor for identities supplied through the local authority namespace. The current parser and
service model retain the local name and parameter map, but this identity constraint and the
mandatory `urn` entry are not yet enforced by runtime authority activation.

## Java Provider Contract

An authority implementation is a public component class that:

- implements `org.integratedmodelling.klab.api.knowledge.Authority`;
- carries `org.integratedmodelling.klab.api.services.reasoner.Authority`;
- declares a nonblank provider URN in the annotation;
- has a no-argument constructor; and
- returns exactly the annotated URN from `getURN()`.

For example:

```java
@org.integratedmodelling.klab.api.services.reasoner.Authority(
    urn = "authority.example",
    embeddable = true,
    subAuthorities = {"SPECIES", "HABITATS"})
public final class ExampleAuthority
    implements org.integratedmodelling.klab.api.knowledge.Authority {
  // Authority methods
}
```

The annotation describes installation characteristics. `embeddable = true` permits a Reasoner to
obtain and install the containing component on demand. A non-embeddable authority must already be
installed on a Reasoner specifically configured to host it. `subAuthorities` advertises known
provider subdivisions; runtime capabilities may add descriptions and may represent the provider's
root catalog with an empty sub-authority id.

The interface divides the provider contract into these areas:

| API | Responsibility |
| --- | --- |
| `setup(parameters)` | Validate one worldview binding and return its `Configuration`. Error notifications make the binding unusable. |
| `getIdentity(id, catalog)` | Resolve one external identifier to its canonical name, label, locator, parents, optional parent relationships, and diagnostics. |
| `search(query, catalog)` | Return scored identities when capabilities declare the authority searchable. |
| `getSemanticDistance(a, b)` | Supply directional compatibility and subsumption using the same non-negative/negative convention as Reasoner semantic distance. |
| `getCapabilities()` | Describe search, fuzzy matching, sub-authorities, documentation formats, and any worldview restriction. |
| `getCodelist()` | Expose a codelist when the authority is codelist-backed. |
| `document(id, mediaType, output)` | Produce supported documentation for an identity. |

An `Identity` is provider data, not yet a Reasoner `Concept`. It supplies a stable concept name,
the official identifier, authority name, optional base identity, parent identifiers and parent
relationships, display metadata, score, source locator, and notifications. An error notification
must prevent concept creation.

A returned `Configuration` belongs to one worldview binding. It can constrain the worldview,
provide a resolution/CRUD endpoint, declare required setup arguments, expose namespaces and
metadata, select a sub-authority, and report configuration diagnostics. It must not be cached only
by provider URN because two local names using the same provider may have different configurations.

## Discovery, Installation, And Updates

Authority components follow the normal component distribution path:

1. A Resources service obtains a component from a local `.kar`, the local Maven repository, or a
   configured remote Maven repository.
2. `ComponentRegistry` scans the component for `@Authority`, validates the implementation shape,
   and adds an `AuthorityDescriptor` containing the URN, embeddable flag, and sub-authorities to the
   component descriptor.
3. Resources advertises and exports the component but does not instantiate its authority classes.
4. A Reasoner first checks its own registry. For an embeddable authority it may resolve the
   provider through Resources, import the component as a dependency, and instantiate it locally.
5. A non-embeddable provider is usable only from a Reasoner where its component was installed by
   configuration or administration. Its component descriptor remains visible in that Reasoner's
   capabilities.

Installed authority implementations are indexed by provider URN and selected using the containing
component version. Only Reasoners instantiate them. Removing or replacing a component removes its
authority registrations along with the other component contributions.

Resolution also participates in dependency freshness checks. A secondary service re-queries the
source Resources service before using a library service call, Java actor, adapter, or authority
component. If the source advertises the same component version with a newer installed timestamp,
the dependency archive is exported, validated, and replaced before lookup continues. This covers
components hosted by a local or remote Resources service. Maven SNAPSHOT discovery remains owned
by the service that imported the Maven coordinates and runs at startup, on a schedule, or through
an explicit update action; a secondary service does not independently poll Maven. See
[component update modes](COMPONENTS.md#update-modes) for the complete source matrix and failure
behavior.

## Worldview And Semantic Contract

For each valid `requires authority NAME {...}` declaration, the Reasoner should eventually:

1. Verify that the declaring concept is an identity and that `urn` names an available provider.
2. Resolve or install the provider according to its embeddable policy.
3. Call `setup()` with the complete parameter map, including `urn`, and reject configurations with
   errors or an incompatible worldview.
4. Register the resulting configuration under `(worldview, NAME)` independently of every other
   binding, even when provider URNs match.
5. Make `NAME:<identifier>` available to semantic parsing and resolve it lazily through
   `getIdentity()`.
6. Recursively obtain missing parents, stop safely on cycles, and materialize the returned
   identities and relationships as ordinary concepts in an authority-owned ontology.
7. Preserve the original locator and provider metadata so concepts can be displayed, documented,
   refreshed, and traced to their source.

Quoted identifiers after the local namespace are intended to carry provider-defined expressions:

```text
NAME:"complex expression interpreted by the authority"
```

This syntax must preserve the complete quoted payload rather than applying ordinary concept-token
rules. It enables an authority to expose intuitive local aliases while evaluating equivalence,
subsumption, or distance from a multidimensional external definition. Ordinary OWL reasoning can
still use the materialized parent structure; comparisons whose meaning belongs to the external
classification must delegate to `getSemanticDistance()`.

Concepts materialized from an authority should behave like normal concepts for hierarchy,
restriction, caching, display, and transport. Their origin remains significant: normal asserted
distance is appropriate for ingested parent links, while two identities from the same configured
authority may require provider-defined semantic distance. Bindings with the same provider but
different worldview configurations must never share results unless the cache key proves the
configuration equivalence.

## Current Implementation Status

| Area | Status |
| --- | --- |
| Java annotation and provider interface | Available. The API covers provider identity, configuration, search, hierarchy data, documentation, and semantic distance. |
| Component scanning and validation | Available. Empty URNs, wrong interface types, missing no-argument constructors, and annotation/implementation URN mismatches are rejected. |
| Descriptor advertisement and serialization | Available. `ComponentDescriptor.authorities()` is published with service capabilities. |
| Reasoner-only instantiation | Available. Resources indexes and advertises providers without hosting them. |
| Embeddable discovery and component transfer | Available through Resources component resolution. |
| Non-embeddable installation | Available when the component is explicitly installed on the Reasoner; automatic transfer is intentionally refused. |
| Dependency update before use | Available for adapter and authority resolution when the source Resources service advertises a newer installed copy. |
| Worldview parsing and transport | Partial. The local authority name and configuration parameters are retained in `KimConceptStatement`; runtime validation currently warns that the requirement is not enforced. |
| Binding activation and `setup()` lifecycle | Not implemented. No worldview-scoped configured-authority registry exists yet. |
| Authority concept materialization | Not implemented. Identity lookup, recursive parents, ontology creation, provenance, and invalidation are pending. |
| Authority-aware semantic distance | Not implemented in Reasoner matching. |
| Quoted complex expressions | Contract defined here; grammar, parsing, canonicalization, and resolution are pending. |
| Remote authority API | Not implemented. `Configuration.getResolutionEndpoint()` exists, but transport operations and authorization are not defined. |

## Development Plan

The next implementation work should proceed in dependency order:

1. **Finish worldview validation.** Require an identity declaration, a nonblank local name, and a
   `urn` parameter; preserve source ranges for precise diagnostics and update the worldview OWL
   translation audit to reflect the already-retained parameter map.
2. **Add a binding model.** Introduce a serializable authority-binding descriptor containing the
   worldview id, local name, provider URN, anchor identity, configuration parameters, component
   provenance, and configuration status. Keep it separate from the provider descriptor.
3. **Activate bindings during knowledge load/update.** Resolve providers, invoke `setup()` once per
   binding, publish diagnostics, and replace or dispose affected bindings atomically when a
   worldview changes.
4. **Materialize identities.** Define stable authority ontology and concept URNs, recursively ingest
   parents and relationship properties, detect cycles, reject error notifications, and preserve
   labels, descriptions, locators, and official ids.
5. **Extend semantic syntax.** Parse both simple identifiers and quoted provider expressions after
   `NAME:`. Canonical forms must remain unambiguous and round-trip through service DTOs.
6. **Integrate reasoning.** Route authority identity comparisons to the correct configured binding,
   combine provider distance with ordinary concept reasoning, and invalidate all related caches on
   binding, component, or worldview revision changes.
7. **Define remote hosting.** Specify request/response DTOs for capabilities, configuration,
   identity lookup, search, distance, documentation, and optional CRUD. Include authorization,
   timeouts, cancellation, health, and failure semantics before treating
   `getResolutionEndpoint()` as an interoperable protocol.
8. **Harden synchronization.** Expose update state administratively, test all four acquisition
   paths (local Maven, remote Maven, local Resources, remote Resources), and verify that dependent
   Reasoners never use a known-stale authority after a successful source update.
9. **Add end-to-end tests.** Cover two independently configured names for one provider, an
   embeddable transfer, a configured non-embeddable provider, recursive and cyclic parents,
   provider errors, quoted expressions, update replacement, cache invalidation, and semantic
   distance delegation.

The current `Authority` API is intentionally open to revision while there are no deployed
providers. In particular, activation may require explicit lifecycle/disposal hooks, a scope or
request context for authorization, asynchronous lookup and cancellation, typed parent
relationships, and a transport-neutral documentation result instead of an `OutputStream`.

## Source Map

- Provider API: [Authority.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/knowledge/Authority.java)
- Discovery annotation: [Authority.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/reasoner/Authority.java)
- Component descriptors: [Extensions.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/runtime/extension/Extensions.java)
- Registry and hosting: [ComponentRegistry.java](../klab.core.services/src/main/java/org/integratedmodelling/klab/components/ComponentRegistry.java)
- Resources resolution: [ResourcesProvider.java](../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/ResourcesProvider.java)
- Reasoner-side resolution helper: [Utils.java](../klab.core.services/src/main/java/org/integratedmodelling/klab/utilities/Utils.java)
- Worldview semantic model: [KimConceptStatement.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/lang/kim/KimConceptStatement.java)
