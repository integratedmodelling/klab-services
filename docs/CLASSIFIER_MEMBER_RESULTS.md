# Classifier member invocation results (P1 foundation)

The isolated fifth slice connects complete **per-supplied-member invocation facts** to the
production Runtime classification Activity. It does not certify that the supplied membership
is complete, or resume Resolver dependency planning.

`MemberClassifierExecutor.executeDetailed` returns immutable `ClassificationMemberResult`
values separately from pending effects. CLASSIFIER records one new qualified concrete result;
EXISTING records all valid existing direct traits/roles in the requested family without a new
classifier call or attribution. Existing evidence may be tentative in the enclosing attempt.
OPTIONAL_ABSENCE explicitly records an allowed null for the original optional model dependency.
An empty supplied list has zero member records; a missing producer remains an error. None of
these cases independently certifies an empty expansion closure.

Results retain the full input observable and requested predicate URNs, all concrete predicate
URNs, portable support and event, and member identity **at invocation**. Provisional IDs may
remain negative after root commit: transient member identity plus enclosing root-attempt ID
are retained; these fields are not advertised as final durable identifiers. Source objects
cannot mutate the frozen scalar/list evidence. The invocation cache includes input meaning
and available Reasoner service/revision identity. Generation drift and parent failure/cancellation
fail closed, including a recheck after characterization before publication. Unavailable
semantic generation is explicit null/-1, never a snapshot certificate.

CompiledDataflow invokes this real executor, stages only new attributions, characterizes them,
and stores versioned `im:classification-member-results` evidence in the CLASSIFICATION Activity.
The graph's explicit audit-metadata allowlist preserves the result map through ordinary Activity
property encoding/recovery. Activity evidence remains tentative under existing parent atomicity;
child completion cannot publish it durably after root rollback. The legacy effect-only API
remains an adapter. No Resolver planning path changes or speculative Tier-0 work are introduced.

## Precise integration blocker

The current Runtime boolean and this result map cannot implement the documented
`classifyPrerequisite(parentAttempt, Request, runtimeContext)` completion contract:

* `classificationMembers` separately reads a current cohort and transaction views. It exposes
  no coherent member/read snapshot receipt, complete producer-result membership, or completion
  certificate. Supplied members are not silently reinterpreted as a certified closed set.
* `KnowledgeGraph.getSemanticRevision` explicitly denotes a local semantic cache generation,
  not a persisted graph revision. An embedded Neo4j test proves a committed HAS_MEMBER write
  leaves it unchanged. Matching this counter cannot certify unchanged membership.
* Runtime.register rejects semantic-update directives as observations. Resolver's ordinary
  dependency loop has no execution-before-planning continuation, typed UNKNOWN channel, or
  snapshot-bound completed outcome adapter. A failed attempt is terminal and cannot resume.
* Concrete expansion still needs occurrence-bound executable admission/redeclaration and
  eligibility validation before semantic distance. P0's keyed asynchronous application is
  tested separately; no simulated runtime closure is connected to it here.

These are implementation prerequisites, not a newly requested owner policy. The next coherent
increment should introduce an attempt-local coherent membership/read receipt with explicit
completion/absence evidence, then a typed prerequisite outcome transport and pending Resolver
continuation bound to P0 RequestIdentity. Decide any producer-set versus cohort-view contract
explicitly before certification. Do not claim that semantic generation alone solves invalidation.
Cross-request reuse, external discovery, shared-binding syntax, outer composition, coverage and
scientific reducers remain outside this foundation. Positional Cartesian semantics and the
existing explicit-correlation contract remain unchanged.

## Verification scope

ClassificationExecutionTest exercises actual compiled UPDATE execution, transactions, fresh and
reused evidence, failed characterization, and generation drift with mocked service/registry
boundaries. ActivityHistoryPersistenceTest exercises production property encoding/recovery.
ClassificationPersistenceTest uses embedded Neo4j for membership revision evidence. The optional
actual generator-source fixture supplies its companion sourcepath and runs against the local
generator checkout; no generator artifact is installed. There is no end-to-end classifier-to-
Resolver continuation test because that continuation is deliberately not fabricated.

## Sixth candidate continuation

The later isolated candidate supplies a request-local cohort-plus-attempt snapshot and an
explicit Runtime-to-Resolver planning hop. The absence of continuation described above applies
to P1 alone. See [the current bounded capability](CLASSIFIER_DEPENDENCY_CONTINUATION.md) for
production wiring, native atomic read guards, integration seams and remaining parent-model work.
