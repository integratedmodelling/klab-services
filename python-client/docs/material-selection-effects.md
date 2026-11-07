# Typed planning and material observation options

`context.resolve(request, options=...)` returns a Resolver Job using the same typed
inputs and checked option/scope rules as `context.observe`. It resolves definitions
through the configured Reasoner, then calls the existing Resolver route. It does
not submit Runtime computation, force a model or interpret local semantics as
server-issued identities. An error never falls back to Runtime or simulation.
The native async counterpart awaits both operations without a synchronous facade.

```python
request = ObservationRequest(definition, options=ObservationOptions(
    namespace="my.namespace", project="my.project", scenarios=("my.scenario",),
    observer=existing_agent))
plan = context.resolve(request).result(timeout=30)
observation = context.observe(request).result(timeout=30)
```

For async use `plan = await (await context.resolve(request)).result(timeout=30)`.
An already-built request carries its options; passing another options argument
refuses. Planning and execution are separate Jobs and can observe changed server
state; no lease, guaranteed identical plan, scientific commit or automatic execution
is inferred. An existing relationship selection and focus survive typed planning;
observer selection follows the same authenticated root/Runtime rules as observe.

Namespace/project/scenario fields are **preferences**, not permission grants or hard
filters. Inspected `PrioritizerImpl.computeLexicalScope` scores selected scenario
namespaces100, a matching lexical namespace75, matching project50, otherwise0;
native ranking has additional admission/semantic/spatial/temporal policies. A lexical
namespace can supply ranking policy. The finite simulator does not implement those
policies or establish general semantic compatibility. `selected_model` / `UsingModel`
remains explicitly unsupported instead of implying an implemented native consumer.

## Independent numerical reference

The separate `material-selections` scenario admits only `synthetic:Signal in m`
and two declared persisted synthetic observers. Three fixed candidates have offsets
0/10/20; source-shaped lexical preferences pick a candidate, ties use declared order.
ObserverA adds0 and ObserverB adds100. Only an explicit4x4metre rectangle and two
origin-anchored EPSG:3857 lattices (span1 or2, strict, no snap) are admitted.
Each output samples `x + 2*y + model_offset + observer_bias` at cell centres.
The lattice therefore changes actual sample positions/counts (16vs4), not merely
metadata. The returned native-unit initialization point values, units and geometry
are independently checked through installed/public SDK workflows, not trusted from
echoed fixture receipts. Other geometry, grids, mediation/fill curves and forced
model kinds refuse explicitly.

This finite numerical reference requires root scope (an observer marker is allowed).
Focused `within` scopes explicitly refuse rather than silently sampling the root
lattice; ordinary SDK focus/relationship capabilities remain separate scenarios.

`selection` in a simulated Dataflow and `simulator:selection` in an observation are
declared reference receipts, **not native Java fields or scientific provenance**.
The fixture is not a compiler, ontology, scientific engine or native ranking oracle.
Tests exercise both clients, five preference cases, absent/two observers and both
lattices:60actual numerical execution combinations in four parameterized workflows.
Supplementary fixture intake tests remain separate from public SDK acceptance.

## Native compatibility

`tests/functional/test_material_selection_plans.py` is opt-in and **NOT_RUN** here.
An operator must prepare `KLAB_FIXTURE_STATE_DIR/material-selection-plans.json`:
an authorized context Handle; `allow_owned_resolver_jobs=true`; an explicit
`resolver_job_retention_and_cleanup_policy`; and1..8cases with maintained native
`definition`, `options` and `expected_native_plan_subset`. It uses configured real
endpoints/current credentials and creates only those approved Resolver Jobs, never
Runtime observations or scope disposal. No synthetic receipt field is demanded of
Java. Actual native numerical/grid/observer execution and appropriate approved
fixtures remain separate acceptance; no live service was contacted for this work.
