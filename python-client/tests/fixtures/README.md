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
completion. Do not label these fixtures as sanitized production responses.
