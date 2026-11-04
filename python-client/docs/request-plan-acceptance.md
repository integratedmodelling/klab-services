# Typed request options to served Java plans

This is the bounded follow-up to #85's typed-request contract, not a new request
API or evidence that every valid serialized option changes a scientific plan.
Use the installed SDK with the explicit owned reliability stack:

```console
python tools/run_reliability_acceptance.py --server-repo <compiled-checkout> --java <jdk21-java.exe> --components <pins.json> --state-dir <fresh-private-state> --scenario request-plan
```

## Authoritative contract inventory

| Option | Existing wire/Java authority | Independent selected evidence / boundary |
|---|---|---|
| Lexical namespace | ResolutionRequest.resolutionConstraints → ResolutionConstraintImpl ResolutionNamespace; RuntimeServerController → ServiceContextScope; ModelVisibility/ModelKbox and PrioritizerImpl | Private `consolidation.lexical` model, served successful resolution chart model URN and independently expected66.5 output |
| Scenarios | Scenarios constraint; ModelVisibility participation and PrioritizerImpl | `consolidation.scenario` plan model plus independently expected234.5 output |
| Project | ResolutionProject constraint, ModelVisibility/ModelKbox/PrioritizerImpl | Implemented contract, not yet claimed material-plan acceptance in this minimal recipe |
| Observer | Observer constraint and `#observerId` scope; ServiceContextScope.withResolutionConstraints/withObserver | Supported binding, not proof that choosing a different observer changes dependencies; material alternate-observer control unproven |
| Focus | Existing focused Context scope token `session.context.observation` | Returned observation remains bound to selected region; not a new resolution option or complete lineage proof |
| Geometry | ObservationImpl/GeometryImpl in existing observe submission and Runtime scale alignment | Independent5×4 EPSG:3857 bbox checks on actual returned region/result,20 ordered executed values |
| Inline grid | ConfigurationImpl.gridDefinition → GridAlignmentSupport/context preparation | Actual EPSG:3857 gridAlignment response: requested100-unit span normalized to whole-world cells, independently checked using EPSG spherical circumference; warning required. Configuration proof, not an executed grid-plan claim |
| Forced model | `ObservationOptions.selected_model`; UsingModel has no supported consumer | Explicit UnsupportedOperationError before HTTP; not silently ignored or called accepted |
| Conflicting lexical/unknown constraint | Client typed construction rejects duplicate kinds or unsupported consumers | Explicit InvalidRequestError/UnsupportedOperationError before HTTP |

Source anchors are existing `requests.py`, Java `ResolutionRequest`,
`ResolutionConstraintImpl`, `ConfigurationImpl`, RuntimeServerController,
`ServiceContextScope`, Resolver `PrioritizerImpl` and Resources model visibility.
No new server DTO/route/header, Reasoner algorithm, shared ontology or worldview
is introduced. The two added models are separately labelled synthetic alternatives
inside the existing test-only fixture project, not scientific-source repairs.

## Evidence stages are separate

`request-plan.json` distinguishes **requested**, **accepted**, **reflected in plan**,
**executed**, **rejected**, and unasserted/missing proof. A Java Job completing alone
is not sufficient: selected namespace/scenario cases must expose the expected
model namespace in a successful recorded resolution-plan chart and return the
independently chosen exact constant at all20 cells. Serializer output is not the
expected result. An unconstrained baseline additionally verifies the original
nonuniform model with independently authored offsets, missingness, zero and
precision. Different model URNs and distinct values bind all three cases.
The grid case checks an authoritative server configuration response only; it
cannot be promoted to executed plan proof. Unsupported selection and conflicting
constraints are rejected without a semantic-resolution/submission request.

All created contexts and the session are released independently; errors retain
their primary cause and mark unconfirmed cleanup. The runner owns only fresh
private TEST state and generated credentials; imports are bounded/journalled and
never replayed. Results are partial recorded evidence, not revision-pinned lineage
or durable root-commit proof. Full RC-P1.5 remains PARTIAL until the complete agreed
supported observer/project/grid/material-plan matrix is independently established.

The first live attempt's exact100 grid-step assertion failed: Java correctly
normalizes global EPSG:3857 spacing to integer cells across its world extent.
The corrected expectation checks `2π·6378137 / round(2π·6378137 / 100)` to1e-9
native units and requires the explicit normalization warning. It does not change
Java, erase the failed evidence, or silently call requested spacing exact.
