# Classification contract candidate — 2026-10-26

Unpublished isolated candidate. User checkouts and dirty files are preserved. No commits,
pushes, merges, remote configuration changes or remote deployments were performed. Local
acceptance uses task-owned processes/storage and fresh disposable contexts, with authorized
normal federation messaging. Exact executed outcomes and patch identities belong in the task
handoff and manifest; this design note does not claim an entirely green distribution build.

## Coordinated baselines

- Services develop: 402c13723b292486a69be7bf01e33b2c9d981fca (actual local baseline).
- Services master: 680692a9319cb5d51900d079bca259418dc534d3 (not a merge target here).
- imod master: 556f3562aad44ead96d2b1e646a67664901e1fc5.
- imod worldview/strawman-2026: b9834b519ea805016ddbc510091a3fdec6573d21.
- staging: baa9717f8c118501b77a6460e4c885bdf456a7cd.

Published imod master and strawman already contained classification.members with identical
strategy blob acccacc3e61c05e170a65cfd7e634e8956f9e731. The old local git/imod checkout is
not evidence of a missing published strategy. Both isolated strategy candidates remove only
the singular-context requirement from classification.members, retaining characterization.ensure.
No experimental ontology divergence is imported into master.

## Semantic and implementation boundaries

CLASSIFICATION remains an effect-only UPDATE: no observation output, observation=null, ID 0.
Its dependencies can create native members; classification transforms existing members. The
predicate-of-each form ensures and classifies them. The qualified substantial input exposes
the same member producer, or a complete cohort query reference, to its consumer. It does not
turn classification itself into an observation producer or resolve the member dependency twice.

CollectiveClassification normalizes one selected abstract predicate or concrete ANY on a bare
named native subject/agent. Saved syntax remains authoritative, including ANY and parser EACH
wrappers; canonical declarations are compared for runtime identity and optionality. Ordinary
concrete qualification without ANY follows the previous observation path. This is separate from
quality dependency expansion and introduces no quality Cartesian-product multiplication.

If no accepted classifier exists, an intrinsic check can establish satisfaction using complete
member discovery. Missing predicates, nonmatching predicates, unknown discovery or incomplete
support cannot become empty-dataflow success. Completed empty cohorts require affirmative
completion evidence. Existing admissible predicates return EXISTING before classifier invocation.
No-op execution preserves member identity and predicates, although activity/bookkeeping may occur.

Qualified partial support uses existing completed-cohort composition. A refreshed reference after
classification exposes reused plus new members, with conservative complete coverage proof and
explicit no-generator certification. Native persisted membership read sets and transaction-local
member/producer evidence survive through root commit. Native byte-array properties use immutable
snapshot values and fresh byte arrays on materialization; unsupported property types still fail.

A consumer can itself be a native cohort member, as in the Region examples. Its scheduler INIT
timestamps and registration flag legitimately change after classification capture. Exact root-shared
owned-write records project only those writes backwards in detached validation snapshots. Each
guard records a cursor; current values must match recorded after-values, then exact before-values
are compared with the captured input. Unrelated payload changes remain visible. Rollback restores
only unchanged owned values. No whole metadata/getter exemption or broad guard rebasing is used.
This is consistency validation, not a claim that arbitrary external ObservationImpl mutation is a
thread-safe transactional API or that unobservable same-value/ABA writes can be detected.

CLASSIFICATION needs geometry, from an explicit request, resolution constraints, context or observer.
It does not require a previously observed singular native context. Missing geometry still fails;
CHARACTERIZATION retains its separate singular-context requirement.

## Reflexive closure and consequence audit

OWL semantic closure now includes the satisfiable requested concept. The existing bottom-node
exclusion remains. allChildren remains strict. ReasonerService projects effective predicate status
before returning closure values; abstractness must agree with canonical resolution, including
identity-dependent expressions. Failed derived-predicate syntax lookup fails closed with a detached
ABSTRACT value rather than mutating the canonical OWL DTO.

Concrete closure is semantic closure filtered by concreteness. The installed generator is unchanged;
its existing closure call and ABSTRACT filter now permit concrete self. A generator-specific
compensation was withdrawn. No generator source patch is part of publication.

The audit found current direct production callers in the Reasoner forwarding/service path, generator,
and dormant legacy IntelligentMap emergence path. Active identity, matching, strategy, dependency
expansion and source-admission checks were exercised separately from live execution. Dependency
expansion deduplicates outcomes and consumes ANY; no recursive self-branch or added product factor
was found. Legacy map defects were documented and its code restored unchanged at user direction.

Root-equivalent named aliases remain a separate existing policy: requested root is included, other
aliases in its equivalence node are omitted, while descendant aliases are included. This candidate
does not broaden that policy. Legacy sibling reasoner APIs and unavailable external consumers are
not proven equivalent to the current service/client endpoint.

## Diagnoses and limits

The four initial classification pipeline failures used named instantiators without stable source
admission receipts. Their fixtures were repaired and retain rejection controls. This was not the
cause of the original live example. The untouched live Services baseline separately reproduced
that example's singular-context classifier rejection; the candidate collective route completed it
with source geometry and no initial context observation.

The new authored dependency route is bounded to one selected predicate and a bare native collective
subject/agent. It does not claim multiple predicates, arbitrary member qualifiers, generic/value/unit/
observer wrappers, direct top-level semantic-update submission, or general alias normalization.
Unsupported residual forms retain existing routing or rejection; no broad equivalence is claimed.
The runtime continues to reject semantic-update directives submitted as observations at the root.

The tentative staging query namespace is independently parsed/validated with builder ID-0 checks.
Its Elevation scanner/unit expectations are documented, not live acceptance results. Modeler already
accepts query symbols. The k.Actors submit verb still lacks direct named KimSymbolDefinition
resolution; no new actor mechanism, inspector assertions, competing runner or Maven goal was added.

Publication must coordinate Services develop and the minimal imod strategy change on both master
and strawman. Tentative staging inputs can follow. Required existing LAND catalog packaging and
normal semantic-generation readiness must be checked for the target distribution. No publication
is authorized or performed by this candidate.