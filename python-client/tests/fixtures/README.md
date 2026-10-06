# Fixture provenance

`elevation.json` is a **source-derived offline transport fixture**, not a live
capture and not independent proof of deployed compatibility. Fields/discriminators
were transcribed from this checkout's ObservationImpl, COMMON ConceptImpl and
ObservableImpl, API UnitImpl, GeometryImpl, MetadataImpl/ParametersImpl, and
COMMON JacksonConfiguration.PolymorphicSerializer. Synthetic identifiers replace
deployment identity. `im:commit` is opaque metadata retained for testing; its
presence does not claim a server commit format. Job status fixtures in tests use
JobStatus's status/stackTrace fields and Scope.Status enum, with transitions
derived from JobManager.status/cancel.

The request assertions check controller routes, content types, ScopeRequest and
ResolutionRequest envelopes independently of Python serialization. The opt-in
live test is required to establish actual stack compatibility and scientific
completion. Do not label the source-derived fixtures as sanitized production responses.

`unresolved-observable.json` is an actual response captured on 5 October 2026
from this checkout's running Reasoner using the public imod worldview at
`608bef150ced0a109db98a5aad64ba4461beaa54`. The request was
`geography:Region`, which returned HTTP 200 but `owl:Nothing` / `VOID` with no
error notifications. The generated localhost administration key authorized this
diagnostic request; no credential appears in the capture. This is independent
evidence of the unresolved-observable wire shape, not evidence of successful
scientific computation or ordinary scientist authentication. The correct Region
definition in that worldview is `earth:Region`.
