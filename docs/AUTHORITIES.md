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

- The **authority URN** is the stable provider identifier returned by `Authority.getUrn()` and
  declared by the Java `@Authority` annotation. Components and services advertise this value.
- The **component id and version** identify the archive containing the provider. Version selection
  during installation applies to the component, not to a worldview-local authority name.
- The **authority name** in `requires authority NAME` is local to one worldview. It becomes the
  namespace used by semantic expressions in that worldview.

The configuration map must contain at least the provider reference:

```kwv
identity ChemicalSpecies
    requires authority SPECIES {
        urn: "authority.example",
        catalog: "accepted-species"
    }
;
```

Use a quoted string for the provider URN. `NAME` does not rename the provider globally. A worldview may
bind the same provider URN more than once under different names or configurations, and each binding
must be treated as an independent configured authority. Registries therefore index providers by
their authority URN. Each Reasoner owns bindings keyed by local name within its loaded worldview.

Only an `identity` concept may declare an authority requirement. That concept is the worldview
anchor for the provider's top-level/base identity. Other identities inherit through the provider's
base identity and parent graph. An explicit semantic boundary mapping can instead attach a
reconciled identity directly to the anchor and mapped identity categories, deferring ancestry.
The authority is responsible for the correctness of its hierarchy. Source validation and ingestion
require an identity declaration, an uppercase local name, and a nonblank string `urn` parameter.

## Java Provider Contract

An authority implementation is a public component class that:

- implements `org.integratedmodelling.klab.api.services.Authority`;
- carries `org.integratedmodelling.klab.api.services.reasoner.Authority`;
- declares a nonblank provider URN in the annotation;
- has a no-argument constructor; and
- returns exactly the annotated URN from `getUrn()`.

For example:

```java
@org.integratedmodelling.klab.api.services.reasoner.Authority(
    urn = "authority.example",
    version = "1.0.0",
    embeddable = true,
    subAuthorities = {"SPECIES", "HABITATS"})
public final class ExampleAuthority
    implements org.integratedmodelling.klab.api.services.Authority {
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
| `configure(ConfigurationRequest)` | Validate a bridge and return a nonblank opaque configuration ID. The request contains worldview, local name, root identity URN, and the full parameter map. Provider-held state must be independent for each bridge. Throw a validation exception on failure. |
| `releaseConfiguration(id)` | Release provider-held bridge state on removal/reload. The default does nothing; stateful providers should override it. |
| `resolveIdentity(configurationId, identityId)` | Resolve an external identifier within one bridge to its canonical name, label, locator, parents, base identity, and diagnostics. |
| `search(query, subAuthority, configurationId)` | Return scored identities when capabilities declare the authority searchable. |
| `reconcile(configurationId, fields)` | Optionally resolve a name with provider-defined disambiguating fields. Advertise `isReconciliationSupported()`; reject ambiguous or failed matches with error notifications. Default providers do not support it. |
| `getCapabilities()` | Describe search, fuzzy matching, sub-authorities, documentation formats, and any worldview restriction. |
| `getCodelists()` | Expose named codelists supplied by the provider. |
| `subAuthority(catalog)` | Return the provider handling a declared subdivision. |

An `Identity` is provider data, not yet a Reasoner `Concept`. It supplies a stable concept name,
the official identifier, authority name, optional base identity, parent identifiers and parent
relationships, display metadata, score, source locator, and notifications. An error notification
must prevent concept creation.

The old `api.knowledge.Authority` interface is deprecated and is no longer a discoverable provider
contract. Its `setup()`, `Configuration.getResolutionEndpoint()`, documentation, and distance methods
have not been carried over as an operational remote protocol. New providers use `api.services.Authority`.
Distance delegation still needs an API decision. Identity documentation is published through the authenticated Reasoner documentation routes.

Provider projects using the previous service interface must add `configure(ConfigurationRequest)`,
pass configuration IDs to `resolveIdentity` and `search`, and return the annotated URN from
`getUrn()` and declare a parseable authority `version` in the annotation. The sibling
`klab.authority.taxa` now implements this contract against an explicitly pinned Catalogue of Life
Extended Release in ChecklistBank. Its README is the provider-specific reference for configuration,
code identity, hierarchy, search, reconciliation, release selection and legacy GBIF migration.

Providers may declare `Capabilities.areSubAuthoritiesSearchFilters()` when subdivisions only limit
search space. For these providers, `NAME.RANK:<id>` resolves through the configured `NAME` bridge
when `RANK` is advertised in capabilities. It shares the canonical concept, ontology and locator;
the provider still resolves all parent ranks unless semantic boundaries are explicitly configured.
An exact explicitly configured dotted binding takes
precedence. Without this capability, the Reasoner does not infer subdivision semantics.

### Semantic boundaries and deferred ancestry

Reconciliation establishes what an identity denotes; reconstructing its external hierarchy is
optional when the provider can establish a sufficiently precise semantic boundary. A worldview
opts in by mapping provider-defined boundary IDs to declared worldview **identity** concepts:

```kwv
identity ChemicalIdentity requires authority CHEM {
    urn: "klab.authority.pubchem",
    semanticBoundaries: {
        "COMPOUND": "chemistry:Compound"
    }
};
```

This example assumes `chemistry:Compound` is a declared identity category. The Resources validator
rejects absent, invalid or non-identity targets. The Reasoner requires these mappings to match the
worldview declaration. Providers advertise supported IDs through `getSemanticBoundaries()` and
validate them during configuration with `request.requireSupportedBoundaries(...)`.

The provider must reconcile and validate the canonical identity before assigning boundary
membership. It returns that membership through `Identity.getSemanticBoundaries()`. Search filters,
user-selected subdivisions and community aliases never establish membership by themselves.
Boundaries may be provider subdivisions, or authoritative codelists whose membership the provider
can independently attest. A provider could advertise `codelist:official` and classify members of
that list using exactly the same contract; merely adding a codelist namespace does not opt in.

For mapped identities, providers may return `HierarchyStatus.DEFERRED` without parent/base edges.
The Reasoner adds the bridge anchor and every mapped category as superclasses, retains the canonical
identity, and records `authorityHierarchyStatus=DEFERRED` in concept metadata. This explicitly
distinguishes unavailable ancestry from an authoritative empty parent list. Providers can use the
immutable `Authority.ClassifiedIdentity` helper. Unmapped identities and legacy providers retain
the existing hierarchy behavior; default membership is empty and default status is `COMPLETE`.

PubChem supports `COMPOUND` for reconciled PubChem CIDs and `CHEBI` for ChEBI identities that remain
distinct after reconciliation. A ChEBI code reconciled to a CID belongs to `COMPOUND`, regardless
of the search catalog. TAXA uses the **accepted usage's rank**, such as `SPECIES` or `GENUS`;
synonym rank and the selected search filter cannot override it. For example, map `SPECIES` to
`life:SpeciesIdentity` and `GENUS` to `life:GenusIdentity`, provided these are declared identities.

Full ancestry remains available through
`resolveIdentity(configurationId, code, ResolutionMode.FULL_HIERARCHY)`. Providers adopting
deferral must implement this overload; it must never return deferred ancestry. The Reasoner exposes
`resolveAuthorityHierarchy(authority, code, scope)` and authenticated
`POST /api/v1/authority/hierarchy` with `{"authority":"CHEM","identity":"CID:2244"}`.
This uses the configured authority name and a canonical code. It validates the complete graph,
enriches the **same concept**, marks it complete and invalidates semantic caches. A failed provider
lookup or graph validation leaves the accepted concept intact and retryable. Inspecting parents
also requests enrichment, as does direct authority-identity subsumption. Operations that use only
the declared worldview category need no ancestry. Other consumers of the OWL graph must inspect
the status and explicitly request expansion before assuming external ancestry is complete.

The persistent authority cache includes mappings in its configuration fingerprint and keeps
configured and full-hierarchy resolution records separate. Membership and hierarchy status survive
cache reloads; a deferred record cannot satisfy a full-hierarchy request. Deploy updated providers
and services, then reload a worldview with the desired mappings to enable this behavior.

### Configuration endpoint

`POST /api/v1/authority/configure` is exposed by the Reasoner, through
`Reasoner.configureAuthority(request, scope)` and `ReasonerClient`. There was no authority
`submit` endpoint to rename. The existing Resources and Runtime submission endpoints are unrelated.
An authorized request uses the same `Authority.ConfigurationRequest` as ingestion:

```json
{
  "worldview": "example.worldview",
  "name": "SPECIES",
  "rootIdentity": "biology:SpeciesIdentity",
  "parameters": {"urn": "authority.example", "catalog": "accepted-species"}
}
```

The response is the Reasoner's stable bridge/cache ID; its wrapper retains the provider's transient
configuration handle internally. The worldview must match the loaded worldview,
and the root must resolve to a loaded identity. Exact repeated requests reuse the existing bridge;
conflicting declarations of the same local name fail. Provider capabilities can restrict the
worldview. Source declarations automatically configure their bridge after the identity is built.
During startup without a user scope, providers must already be installed locally; Resources
discovery/transfer is available in service and user scopes.

### Worldview discovery, search, and semantic insertion

`Worldview.getAuthorityBindings()` supplies validated local names, identity roots, source ranges
and hashes, selected provider descriptors and exact component versions. It excludes configuration
parameters and provider handles. Client synchronization selects matching configured Reasoners,
prefers a local host, and records its URL. Missing or stale hosts are not selected.

`Reasoner.searchAuthority(AuthoritySearchRequest, scope)` and `ReasonerClient` use authenticated
`POST /api/v1/authority/search`:

```json
{"authority":"TAXA","query":"oak","filter":"GENUS","offset":0,"limit":20}
```

`authority` is an exact configured local name, never a provider URN. Omit `filter` for an
unfiltered search. A filter must be advertised by a provider whose capabilities explicitly declare
`areSubAuthoritiesSearchFilters()`. An advertised subdivision is not implicitly a separate binding.
Query length is 1â€“256 characters, limit is 1â€“100, and offset is 0â€“10000. Invalid requests return
HTTP 400; missing authentication or component access returns 403. Successful transport returns
an `AuthoritySearchResponse` with status `OK`, `UNSUPPORTED`, `UNAVAILABLE`, or `FAILED`.
Only `OK` with no matches means an empty search. The client propagates transport failures.

Matches implement `Authority.Identity` and carry canonical code, local authority name, label,
description, relevance score, canonical locator, and provider notifications. They omit provider
configuration IDs and documentation URLs; use the authenticated documentation operation for the
latter. Paging slices the provider-returned list, deduplicated by canonical code in provider order.
`total` is the size of that list, not an exhaustive catalog count; `nextOffset == -1` marks its end
or the offset bound. Providers may cap their own results or reorder them between calls.

For insertion, initialize an ordinary semantic session, then send `SemanticSearchRequest` with
`searchMode: "IDENTITY"`, `authority`, `identityCode`, the existing `searchId`, an increasing
`requestId`, and `matchesRequestId` equal to the current semantic response ID. This is distinct
from `SELECT`: authority candidates never become arbitrary semantic proposal IDs.

The Reasoner checks the configured binding and component access, resolves the exact canonical
code (aliases are rejected), rejects provider error notifications and unsatisfiable identities,
and replays the insertion through the session's normal operand, predicate, clause, scope, and
result-category validation. Rejected edits preserve the expression. Successful insertion adds
one styled token and one undo step. HTTP sessions belong to the authenticated user; another user
cannot read, edit, or cancel them. Worldview knowledge updates and reloads expire existing sessions.

`AuthorityIdentitySyntax` serializes simple codes as `TAXA:123` and complex codes as `TAXA:[A B]`.
Inside brackets, backslashes and closing brackets are escaped. Resources adaptation and OWL
dispatch preserve embedded colons and escaped payloads. The Reasoner supplies the canonical local
locator rather than accepting a provider-supplied expression as executable semantic text.

Provider search is still synchronous and provider-side cancellation/paging are not introduced by
this contract. The IDE authority chooser and identity table remain a separate UI stage.

### Persistent Reasoner cache

Production bindings use a shared Reasoner-side wrapper for every authority. Identical declarations
already reuse the live bridge; after reload/restart the provider is configured again to restore its
live state, while successful results are reused from disk. Provider configuration handles need not
be serializable or stable across processes.

Bridge requests and persistent keys use the stable worldview name (`Worldview.getUrn()`), not its
generated loaded-instance ID. The configuration endpoint also accepts the current instance ID for
compatibility and normalizes it to the name before invoking the provider or selecting a cache.

The external bridge ID contains the worldview and local configuration name plus a SHA-256 fingerprint
of the worldview root, complete parameter map, provider URN, implementation revision and cache policy.
Nested maps are serialized in sorted-key order. Cache directories under the Reasoner's service data
directory (`services/reasoner/authority-cache`) retain readable worldview/configuration names with
hashed suffixes. Parameters themselves are not written to disk. The provider revision includes its
annotation version and artifact checksum; development classes fall back to the provider class checksum,
so helper-only development changes must also bump the provider's explicit cache-policy revision.

`Authority.getCachePolicy()` supplies a revision and lifetimes in seconds for identity, search and
reconciliation results. Defaults are one day for identities and five minutes for queries. Zero
disables caching; `Long.MAX_VALUE` denotes immutable data without time expiry. TAXA uses this for its
pinned taxon identities and limits query retention to one day. Policy/release/provider/root changes
select another partition; they never silently reuse incompatible results.

The cache stores core DTOs rather than plug-in objects. Only clean successful identities are retained;
nulls, exceptions and diagnostic-bearing results are not cached. Empty successful searches may be
cached briefly. Search candidates do not seed identity lookup, because providers may perform stronger
validation there. Canonical IDs and their resolved aliases share the validated lookup result. Dotted
search-filter dispatch uses the base bridge; explicit provider sub-authority views have separate keys.

An in-memory LRU retains 512 entries per bridge. Atomic JSON writes persist entries up to 4 MiB;
periodic trimming (on the first write and every 64 writes) targets 10,000 entries and 256 MiB per
partition, removing oldest-written entries. There may be up to 63 writes of temporary overshoot.
Expiry/corruption produces a cache miss; disk errors fall back to memory/provider access with a
warning. Live bridge release leaves disk data available for subsequent activation. Concurrent
requests on one bridge serialize to avoid duplicate upstream misses. Administrative cross-partition
cleanup and coordinated rate-limit/backoff policies remain future work.

Caching does not establish component provenance, authorize a provider or supply an automatic offline
configuration path. Providers may still validate their source during `configure()`. Already materialized
OWL concepts follow worldview/binding reload semantics; cache expiry alone does not rewrite their axioms.

## Discovery, Installation, And Updates

### Worldview provider integrity rule

The intended integrity rule is that authority plug-ins come from the same authorized Resources
services that supply the loaded worldview. A Resources service supplying only ordinary resources
must not become an authority source merely because it is available in the caller's scope.
Discovery now filters Resources services by `isWorldviewProvider` and an `adoptedWorldview`
matching the loaded worldview name (`Worldview.getUrn()`), in both startup service scopes and user
scopes. The generated `getWorldviewId()` identifies a loaded instance and must not be compared with
the advertised name. This is an
initial eligibility check, not the complete enforcement contract: contributor certification,
component provenance verification and explicitly installed provider checks remain pending.

For a remote Reasoner, its service certificate establishes the permitted service peers. Authority
selection must further restrict those peers to the providers contributing to its loaded worldview.
Membership in a user scope and an advertised `isWorldviewProvider` flag alone do not establish
that trust. Provider identity, the worldview commitment, and component provenance must agree.
An unavailable approved source must produce a diagnostic rather than fall back to an unrelated
Resources service advertising the same authority URN.

Enforcement is needed at both ends:

- Resources services outside the authorized worldview-provider role must not advertise or resolve
  authority offerings. General component distribution does not confer that role.
- The Reasoner must restrict discovery, verify the selected component's source, and apply the same
  rule to cached, updated, explicitly installed, and non-embeddable providers before binding them.
  Importing a component through its generic component ID must not bypass authority source checks.
- Bridge provenance must identify the supplying Resources service, the declaring ontology and its
  source, and the selected component revision. Updates must retain these constraints.

For a distributed worldview, the eligible set includes its certified higher-tier contributors,
not just the provider of the root ontology. The existing boolean capability does not describe
contributor membership or namespace ownership; the certificate/manifest representation and the
handling of authority delegation between contributors remain design decisions. See
[worldview composition and authority provenance](SERVICE_COORDINATION_AND_DISCOVERY.md#worldview-composition-and-authority-provenance).

### Full-local editing and testing

The rule must preserve a full-local stack. The local Resources service supplying the editable
worldview also supplies its authority components, and the local Reasoner can discover them at
startup using its service scope. This must work without a production coordination cluster or a
remote production certificate. Local development configuration identifies the local worldview
provider explicitly; localhost connectivity alone must not authorize every local Resources service.

A full-local stack is supported, but a local service may also obtain its worldview and authority
components from authorized remote providers serving the same worldview as the local provider.
Source locality is not an integrity criterion. Worldview identity, contributor authorization and
compatible revisions govern eligibility, while the local working copy remains editable. The
current Reasoner startup filter that excludes remote Resources from a local stack must eventually
be replaced by these compatibility checks.

Local Maven builds, component archives, unsaved editor validation and project reloads remain part
of that workflow. Development components need an explicit association with the local worldview
provider, and must follow the existing update and diagnostic paths. Local validation errors may
remain visible for editing without silently treating unrelated authority sources as trusted.
Startup discovery through a service scope is implemented. The explicit development association
and its provenance checks remain pending.

### Current acquisition path

Authority components follow the normal component distribution path:

1. A Resources service obtains a component from a local `.kar`, the local Maven repository, or a
   configured remote Maven repository.
2. `ComponentRegistry` scans the component for `@Authority`, validates the implementation shape,
   and adds an `AuthorityDescriptor` containing the URN, embeddable flag, and sub-authorities to the
   component descriptor.
3. Resources advertises and exports the component but does not instantiate its authority classes.
4. A Reasoner first checks its own registry. For an embeddable authority it may resolve the
   provider through matching worldview Resources providers, import the component as a dependency,
   and instantiate it locally. Startup uses the same component loader as user-driven ingestion;
   authenticated client credentials remain in effect when no user scope exists yet. Authority
   resolution includes the source service URL, as other component acquisition routes do.
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
components hosted by a local or remote Resources service. When Resources transports Maven
provenance for a dependency, a secondary service accepts a newer SNAPSHOT from its local Maven
repository first. If none exists, the advertising Resources service remains authoritative. The
secondary service never polls or downloads from remote Maven repositories. The lifecycle event
records the selected source and this decision rationale for the IDE History view. See
[component update modes](COMPONENTS.md#update-modes) for the complete source matrix and failure
behavior.

## Worldview And Semantic Contract

For each valid `requires authority NAME {...}` declaration, the Reasoner:

1. Verify that the declaring concept is an identity and that `urn` names an available provider.
2. Resolve or install the provider according to its embeddable policy.
3. Calls `configure()` with the worldview, name, anchor URN, and complete parameter map, including
   `urn`, and rejects empty IDs or an incompatible worldview.
4. Registers the resulting configuration under `(worldview, NAME)` independently of every other
   binding, even when provider URNs match.
5. Make `NAME:<identifier>` available to semantic parsing and resolve it lazily through
   `resolveIdentity(configurationId, identityId)`.
6. For a provider-attested mapped boundary, attaches the reconciled identity directly to the
   anchor and mapped categories and marks ancestry deferred. Otherwise recursively obtains
   missing base/parent identities, stopping each branch at the first known
   concept. A visited graph prevents endless recursion; the provider remains responsible for
   hierarchy consistency. Top-level identities (no remaining base/parent edges) inherit from the
   worldview anchor; supplied base/parent edges carry that inheritance to descendants. A
   `baseIdentity` equal to the bridge's root URN terminates directly at that known worldview concept.
7. Materializes the validated graph in an internal ontology isolated by worldview and local name,
   preserving labels, descriptions, local authority name, and locator. Identity error notifications
   prevent graph materialization. Explicit parent relationship properties currently fail with a
   diagnostic instead of being silently translated as subclass links.

Ontology reload removes affected bridges and their materialized ontologies; full worldview reload
and shutdown release provider configurations. This initial lifecycle does not atomically stage a
whole worldview replacement and does not yet track component replacement against active bridges.

Bracket-delimited expressions after the local namespace carry provider-defined expressions:

```text
NAME:[complex expression interpreted by the authority]
```

The observable grammar already accepts this through `AUTHORITY_CONCEPT` and the `EXPR` terminal.
This syntax must preserve the complete bracketed payload rather than applying ordinary concept-token
rules. It enables an authority to expose intuitive local aliases while evaluating equivalence,
subsumption, or distance from a multidimensional external definition. Ordinary OWL reasoning can
still use the materialized parent structure; comparisons whose meaning belongs to the external
classification will need a configured-provider distance API, which is not currently exposed.

Concepts materialized from an authority should behave like normal concepts for hierarchy,
restriction, caching, display, and transport. Their origin remains significant: normal asserted
distance is appropriate for ingested parent links, while two identities from the same configured
authority may require provider-defined semantic distance. Bindings with the same provider but
different worldview configurations must never share results unless the cache key proves the
configuration equivalence.

## Current Implementation Status

| Area | Status |
| --- | --- |
| Java annotation and provider interface | Available for provider identity, bridge configuration, search, codelists and hierarchy data. Reasoner documentation transport is available; authority-specific distance remains pending. |
| Component scanning and validation | Available. Empty URNs, wrong interface types, missing no-argument constructors, and annotation/implementation URN mismatches are rejected. |
| Descriptor advertisement and serialization | Available. `ComponentDescriptor.authorities()` is published with service capabilities. |
| Reasoner-only instantiation | Available. Resources indexes and advertises providers without hosting them. |
| Embeddable discovery and component transfer | Available through Resources component resolution. |
| Non-embeddable installation | Available when the component is explicitly installed on the Reasoner; automatic transfer is intentionally refused. |
| Dependency update before use | Available for adapter and authority resolution when the source Resources service advertises a newer installed copy. |
| Authority source integrity | Discovery accepts only Resources services advertising the worldview-provider role and the loaded worldview name. The generated instance ID is a separate value. Resources-side restrictions, certificate-backed contributor selection, installed-provider checks and provenance verification remain pending. |
| Worldview parsing and transport | Available for local name, parameter map, and clause source spans. Validation checks identity/name/URN; ingestion repeats these checks on transported beans. |
| Binding activation and `configure()` lifecycle | Implemented for locally hosted providers and service/user-scoped component discovery. Provider-held IDs are retained with worldview and anchor context; reload/removal releases affected configurations. |
| Ingestion diagnostics | Startup and user-driven ingestion retain lexical notifications for ontology semantic validation. A missing provider remains visible on the binding declaration after restart. |
| Persistent authority result cache | Shared Reasoner core support for all providers. Stable bridge keys, policy-controlled identity/search/reconciliation retention, DTO persistence, restart reuse, parameter/provider isolation, atomic writes and memory fallback are implemented. Live provider handles are restored on activation; cross-partition administration and coordinated throttling remain pending. |
| Authority concept materialization | Initial path implemented: configured lookup, recursive base/parent expansion to known concepts, root inheritance, graph error handling, isolated ontologies, and display/locator metadata. Parent relationship properties and component revision invalidation remain pending. |
| Search-only sub-authority dispatch | Implemented for opted-in providers: advertised dotted suffixes resolve through the base bridge, cache and canonical ontology. Exact configured dotted bindings take precedence. |
| Taxonomic provider | The sibling `klab.authority.taxa` implements pinned COL XR configuration, code lookup, synonym canonicalization, hierarchy validation, scientific/vernacular search and explicit reconciliation. See its README for parameters and limits. |
| Explicit reconciliation | Optional shared Java API and capability flag available. TAXA rejects ambiguous and higher-rank matches; service/UI transport remains pending. |
| Authority-aware semantic distance | Not implemented in Reasoner matching. |
| Bracketed complex expressions (`NAME:[...]`) | Canonical serialization, Resources parser/adapter retention, OWL decoding, and HTTP semantic insertion/undo are implemented and regression-tested. |
| Remote authority API | Authenticated Reasoner configuration, search, documentation, and semantic identity insertion are available. Standalone provider hosting, distance, and CRUD protocols remain undefined. |

## Development Plan

The next implementation work should proceed in dependency order:

1. **Harden worldview validation.** Identity/name/URN checks and retained clause spans are now
   available. Add cross-document duplicate diagnostics before providers are configured.
2. **Publish a binding model.** Internal bindings now retain the request, provider and ID. Add a
   serializable authority-binding descriptor containing the
   worldview id, local name, provider URN, anchor identity, configuration parameters, component
   provenance, and configuration status. Keep it separate from the provider descriptor.
3. **Harden activation during knowledge load/update.** Configuration and release are wired into
   ingestion and ontology updates. Stage replacements atomically and handle active bridges when
   their component is updated or removed. Support component discovery during service startup,
   restricted to the authorized worldview contributors, with a full-local development path.
4. **Extend materialization.** Recursive base/parent ingestion, known-parent stopping, graph visit
   tracking, error rejection and display/locator annotations are implemented. Add typed parent
   relationships, full provenance, and component-driven invalidation.
5. **Complete expression transport.** The grammar accepts simple identifiers and bracketed provider
   expressions (`NAME:[...]`). Preserve payloads, including embedded colons and escaped closing
   brackets, through qualified-name splitting, provider dispatch and canonicalization. Canonical
   forms must remain unambiguous and round-trip through service DTOs.
6. **Integrate reasoning.** Route authority identity comparisons to the correct configured binding,
   combine provider distance with ordinary concept reasoning, and invalidate all related caches on
   binding, component, or worldview revision changes.
7. **Define remote hosting.** Specify request/response DTOs for capabilities, configuration,
   identity lookup, search, distance, documentation, and optional CRUD. Include authorization,
   timeouts, cancellation, health, and failure semantics before treating
   a provider-hosted endpoint as an interoperable protocol.
8. **Harden synchronization.** Expose update state administratively, test all four acquisition
   paths (local Maven, remote Maven, local Resources, remote Resources), and verify that dependent
   Reasoners never use a known-stale authority after a successful source update.
9. **Add end-to-end tests.** Cover two independently configured names for one provider, an
   embeddable transfer, a configured non-embeddable provider, recursive and cyclic parents,
   provider errors, bracketed expressions, update replacement, cache invalidation, and semantic
   distance delegation.

The current `Authority` API is intentionally open to revision while there are no deployed
providers. In particular, activation may require richer lifecycle/disposal hooks, a scope or
request context for authorization, asynchronous lookup and cancellation, typed parent
relationships, and a transport-neutral documentation result instead of an `OutputStream`.

## Source Map

- Provider API: [Authority.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/Authority.java)
- Discovery annotation: [Authority.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/reasoner/Authority.java)
- Component descriptors: [Extensions.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/runtime/extension/Extensions.java)
- Registry and hosting: [ComponentRegistry.java](../klab.core.services/src/main/java/org/integratedmodelling/klab/components/ComponentRegistry.java)
- Resources resolution: [ResourcesProvider.java](../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/ResourcesProvider.java)
- Reasoner-side resolution helper: [Utils.java](../klab.core.services/src/main/java/org/integratedmodelling/klab/utilities/Utils.java)
- Worldview semantic model: [KimConceptStatement.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/lang/kim/KimConceptStatement.java)
- Grammar: sibling `klab-languages/org.integratedmodelling.languages.worldview/src/org/integratedmodelling/languages/Worldview.xtext`, `RequiresClause`.
- Parser model: sibling `klab-languages/org.integratedmodelling.languages.worldview/src/org/integratedmodelling/languages/ConceptDeclarationSyntaxImpl.java`, `Requirement(AUTHORITY, targets, name, parameters)`; source clause ranges are also retained.
- Resources adaptation: [LanguageAdapter.java](../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/lang/LanguageAdapter.java), `adaptConceptDefinition()` copies the name/map/ranges into the statement declaring the root.
- Source validation: [KimWorldviewValidator.java](../klab.core.services/src/main/java/org/integratedmodelling/klab/runtime/language/KimWorldviewValidator.java)
- Ingestion: [ReasonerService.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/ReasonerService.java), `loadKnowledge()` â†’ `defineConcept()` â†’ `build()` â†’ `configureAuthorityBinding()`; `updateKnowledge()` releases/rebuilds affected bridges.
- Bridge registry: [AuthorityBindings.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/AuthorityBindings.java)
- Lazy resolution: [OWL.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/OWL.java), uppercase namespace lookup delegates to [AuthorityIdentityResolver.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/AuthorityIdentityResolver.java). The old global `ServiceConfiguration` lookup and synthetic-root materializer are bypassed for configured bridges.
- REST configuration: [ReasonerController.java](../klab.services.reasoner.server/src/main/java/org/integratedmodelling/klab/services/reasoner/controllers/ReasonerController.java)

The sibling language parser already retains the required payload, so this pass does not require
grammar changes. The old Reasoner YAML `authorities`/`classpath:` loader remains a legacy global
registration path; it does not create a worldview bridge. Install authority components through the
component registry and declare their bridges with `requires authority`.

### Verification of this initial path

Focused compilation and 16 regression tests passed using the existing Maven dependency classpath
and a standalone runner. Coverage includes real language parsing and JSON transport, registry
hosting/discovery, independent configurations, known-parent stopping, inheritance through the base
identity, and provider-error rejection. The complete Maven reactor build remains unverified:
the API test sources fail compilation separately, and sandbox access to generated dependency JARs
blocked the earlier downstream build. In the taxa development pass, the API main sources compiled
and installed locally, the focused Reasoner Maven suite passed six tests (including dotted names
through OWL lookup), and the sibling provider passed nine fixture-based Maven tests and packaged a
component archive. REST integration, full Resources-to-Reasoner worldview ingestion, complete
worldview reloads, and component revision changes still need integration coverage.

The shared persistent-cache pass added eight passing cache regression tests, alongside the six
binding/materialization tests and nine TAXA tests. Coverage includes restart/reload reuse, canonical
alias reuse, immutable retention, expiry/opt-out, concurrent miss deduplication, partition isolation,
query/identity separation, uncached failures, corruption and unavailable-disk fallback.

The startup-discovery repair passed five core discovery/registry tests and nine Reasoner
binding/materialization/loading/notification tests. Coverage includes discovery without a user
scope, user-scope refresh, rejection of unrelated Resources providers, failed installation, and
lexical diagnostic retention with listener cleanup. Core dependencies built and installed locally;
Resources packaged and Reasoner main sources compiled. The live Resources service advertised TAXA
and its component JAR exporter. Full-stack transfer and binding activation still require a restart
with these changes.

Live follow-up exposed the distinction between Resources' advertised worldview name (`imod`)
and the generated loaded-instance ID. Discovery now compares names; bridge configuration also
normalizes the current instance ID to that stable name for persistent cache reuse. The live
Resources resolver and JAR export both succeeded. Five discovery/registry tests, sixteen Reasoner
binding/cache/materialization/notification tests, and six HTTP/advertisement tests passed after
this correction. Full bridge activation awaits deployment of the corrected Reasoner/core builds.
