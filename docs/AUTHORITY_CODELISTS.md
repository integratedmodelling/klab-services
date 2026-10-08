# Authority codelists and proposals

Authority codelists expose approved names as worldview-local namespaces. Resolving
`taxonomy.species:FelisCatus` returns the same concept as its canonical authority
code. No alias ontology, class, or equivalence axiom is created.

## Configuration

An authority declares provider-local list IDs, seed entries, and proposal policy through
`getCodelistDefinitions(configurationId)`. Its default adapts the existing
`getCodelists(configurationId)` method (which delegates to `getCodelists()`), preserving
proposal acceptance for legacy declarations. TAXA advertises empty `species` and `genera`
lists. Bind these in the existing worldview authority parameters:

```text
identity Taxon requires authority TAXA {
    urn: "<installed TAXA provider URN>",
    codelists: {
        species: "taxonomy.species",
        genera: "taxonomy.genera"
    }
};
```

Keep any other parameters required by the provider, including its pinned release.
The keys identify codelists; the values are local namespaces. When a key matches a provider
declaration, the list retains that declaration's seed data and proposal policy. When the key
is not declared by the provider, the worldview binding creates an empty community codelist
that accepts proposals. Creating these bindings requires worldview editing rights; the review
API cannot create arbitrary namespaces. For example, `local_species: "community.species"`
can be added to the mapping without changing the provider.

A provider can declare an officially curated list that rejects new proposals:

```java
@Override
public Map<String, CodelistDefinition> getCodelistDefinitions(String configurationId) {
    return Map.of("official_species", new CodelistDefinition(officialSpecies(), false));
}
```

The worldview binds `official_species` to its preferred namespace but cannot override the
provider's policy. Use `true` to accept proposals. Administrator CREATE, UPDATE, and DELETE
operations remain available for directly managed entries in either kind of list. Provider
seed entries remain defined by provider code; accepted community entries retain their existing
protection. Closing a list blocks new submissions, including from administrators, without
discarding prior proposals or approved aliases. Administrators can still review existing
pending proposals. No ledger migration is required.

LIST responses include `policies`, keyed by bound namespace, containing `listId`,
`providerDeclared`, and `acceptsProposals`. The dashboard displays this policy. Old responses
without the policy map remain readable. The reserved `codelists` parameter is retained by
the Reasoner and is not passed into provider configuration.
Namespace collisions with ontologies or other lists are rejected. Runtime configuration
must match the loaded worldview declaration. Lists may be empty or contain seed entries.
Seed values must be resolvable provider codes and are normalized to canonical codes.

`CodelistImpl.Entry` accepts string/numeric keys and transportable object values.
Numeric code comparisons retain their exact integral behavior. Existing `@code`
lists continue to carry `KimConcept` values. Authority list values are canonical
provider-code strings; `authorityId` identifies their configured authority. These DTOs
use the standard k.LAB Jackson configuration, including interface and nested value
serialization. Rebuild consumers because the Entry accessor signatures have changed.

## Proposals from source

```text
@proposal(authoritycode="taxonomy.species:FelisCatus")
model each TAXA:[3DXV3] life:Individual;
```

The extractor traverses parsed model observables and dependencies, or concept
statement references. It infers the target only when there is one distinct authority
identity. For an ambiguous statement, supply `target="TAXA:[3DXV3]"`; that identity
must occur in the statement. Unrelated annotation types are ignored.

After a confirmed IDE save, proposals are submitted to the Reasoner hosting the
configured authority. The service validates the configured list and resolves the
identity independently. Pending entries do not resolve as aliases. Repeated saves
of the same alias/identity pair retain one proposal; source occurrences are recorded
separately using document URN, SHA-256 source hash, and statement span.

The IDE reads outcomes when opening source and when the hosting service's
`authority.codelist.revisions` status metadata changes. It displays lexical
notifications only for matching annotations and saved source. Dirty text suppresses
those notifications. Removing an annotation does not delete a community proposal.

## Service API and review

`Reasoner.authorityCodelists(request, scope)` and
`POST /api/v1/authority/codelists` support:

- `LIST`: retrieve published codelists and proposal outcomes.
- `SUBMIT`: submit a namespace, alias, authority identity and optional source occurrence.
- `REVIEW`: accept or reject a pending proposal, with an optional alternative approved
  alias and a rationale or suggested replacement in `message`.
- `CREATE` / `UPDATE`: directly add or edit an administrator-managed alias.
- `DELETE`: remove a directly managed alias, preserving its tombstone.

Authenticated authority access is required. Review, creation, editing and deletion require
an administrator, checked in both HTTP and service layers. These mutation requests
include the snapshot's `expectedRevision`; stale decisions return HTTP 409. Errors
never imply successful publication. The service persists an atomic ledger before
publishing changes and refuses to treat unreadable persistence as an empty list.

The Reasoner dashboard exposes **Authority codelists** at `/ui/authority-codelists`.
Administrators select an authority, review pending proposals, accept with the proposed
or another alias, or reject with an explanation. Accepted community entries are read-only.

Outcomes distinguish `PENDING`, `ACCEPTED`, `REDIRECTED`, `REJECTED`, `DELETED`, and `MANAGED`.
Accepted community aliases cannot be reassigned or withdrawn. Directly managed aliases remain editable until adopted through community review.
Deleting an alias stops future resolution; it does not delete the canonical authority
concept or retract previously materialized semantics.

Persistent JSON ledgers live under `authority-codelists` beside `authority-cache` in
the Reasoner's configuration directory. They are scoped by stable worldview name,
authority name, root identity and provider URN, and survive provider reconfiguration.
Treat this directory as service data, not an expendable cache.

## Search

With no filter, authority search combines provider results, direct code lookup, and
approved alias matches. Results are deduplicated by canonical identity and expose
all approved names in `AuthorityIdentity.aliases`. A codelist namespace in the existing
`filter` field restricts matches to that list. Existing provider subdivision filters
remain supported. Descriptions and documentation come from canonical authority terms.

The IDE displays an **Aliases** column and a codelist selector when non-empty lists
are available. **All identities** searches aliases and authority terms together.
Selection continues to insert the canonical authority identity; approved aliases can
also be written directly in source.

## Verification

Validated on 2026-10-07:

- 54 focused backend tests passed in an isolated source snapshot, including actual
  k.IM parsing, canonical concept reuse without extra axioms, search filtering,
  standard Jackson transfer, restart persistence, status revisions, and HTTP authorization.
- The IDE compiled against private jars from that snapshot; 20 existing proposal/editor
  regression tests passed.
- TAXA compiled against the updated API and passed its 13 tests.
- The dashboard type-check and production build passed. Two Edge browser tests cover
  the review payload/revision and absence of review controls for non-administrators.

Backend command: `mvn -o -pl klab.services.reasoner.server -am test
-Dtest=Authority*Test,CachedAuthorityTest,ConceptCodelistBuilderTest,WorldviewAuthorityValidatorTest
-Dsurefire.failIfNoSpecifiedTests=false` (quote the properties in PowerShell).
The isolated build is under `target/authority-codelists-verification`, with its log at
`target/authority-codelists-isolated-tests.log`. No shared Maven artifacts were installed.
This verification uses local fixtures, not a deployed authenticated multi-service session.


### Direct code management

The dashboard uses worldview authority bindings for its authority selector and the selected
authority's configured codelist namespaces for its codelist selector. It cannot create either.
The Code management pane can add, rename, change the authority identity of, and remove
directly managed aliases. CREATE and UPDATE validate the target with the authority and
commit atomically, with the displayed ledger revision protecting against concurrent edits.

Accepted community proposals (including redirects) and predefined provider entries are
read-only in both the dashboard and the API. A directly managed alias becomes read-only
if it is subsequently accepted through community proposal review.

The command API adds administrator-only CREATE and UPDATE operations. CREATE takes
namespace, alias, identity and expectedRevision. UPDATE takes the existing alias and the
replacement approvedAlias and identity, plus namespace and expectedRevision. DELETE
is now limited to directly managed aliases. Direct entries have MANAGED status in the
ledger, distinct from ACCEPTED and REDIRECTED community decisions. Existing ledgers
need no migration; previously accepted entries are automatically protected.
