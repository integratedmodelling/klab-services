# SEEA single-year extent: second attempt

## Outcome

**Syntax-only subset parses; the standard Java account/report path is blocked.**
No actual SEEA account, scientific Job, semantic admission or resource registration
was performed. This is a deeper contract check of the least-bad C14 candidate,
not a successful scientific reproduction. No production Java, ontology, service
or SDK change, push or PR was made.

The 324-cell real CCI 2020 input and independent raw-class area reference remain
available externally (total 22.509794461 km²). They were not silently substituted
with a synthetic raster or relabelled as Java account output.

## What changed since the first feasibility check

Extracted the original `landcover_extent_static` definition from
`integratedmodelling/un.seea.aries@db6aa1a3b65cc2aba3f341c8c2681cb872b435f7`,
retaining its namespace, class names, table structure and labels. Original file
SHA-256: `4c532111e87d38e5c8506c6062b58a75418fa1ae39cbde9cf55ad65437c9c594`.

The only source-token adaptation is:

```text
im:Area in km^2  ->  {{im:Area in km*km}}
```

This wraps an observable-valued map entry in the current grammar and spells its
unit without a numeric exponent. Extraction/line-ending normalization are recorded
separately. It does not rename `im`/`landcover`, map classes, scale values or supply
model equations. Original UTF-8 decoding round-trips; original bytes remain unchanged.
Generated derivative stays external with unapproved semantic bindings, not a
registered project or distributable accepted migration.

| Actual check | Pinned parser | Current-local parser |
| --- | --- | --- |
| Positive control | Pass | Pass |
| Unchanged extracted static definition | Fail, five recovery diagnostics | Fail, five recovery diagnostics |
| Syntax-only static derivative | Pass, zero syntax errors | Pass, zero syntax errors |

The earlier full-source failures remain preserved. These are actual Java parser
results, not semantic/unit mediation or scientific-computation acceptance.

## Concrete standard-path barriers

Verified against upstream Services `4d5d9028b4de4f4b04f7ad0d3228087208a01bfd`:

1. `klab.core.api/.../knowledge/observation/impl/ObservationBuilderImpl.java:235–254`
   converts classified symbol maps only for `observation`, `observer`, and `query`.
   The original `define table` class is not handled. Changing its name to
   `observation` would not implement table aggregation and is not a valid repair.
2. `klab.modeler/.../modeler/ModelerImpl.java:233–242` likewise has submission
   handling for those classes, but no corresponding `table` branch.
3. `klab.core.services/.../services/scopes/ServiceContextScope.java:433–435`
   implements `getReport()` as a TODO returning `null`.
4. The API does contain `Report.View.TABLES` and a table section enum; those
   declarations are not an implementation of the missing account compiler.
   Repository searches found no legacy `tables.compiler.*` or replacement static
   extent-table compiler. External/custom components have not been ruled out.

The observation-builder source blob is identical in accepted Python integration
`6bcee78ee` and current upstream: `ccb2a9f766311fa6089560d3fa925a6edd08d1aa`.
This makes the standard-path finding current-source evidence, not an inference
solely from an old build. It is not full hosted-deployment attestation.

### Bounded executable contract control and its limits

An external Groovy probe exercised the real available compiled API builder with
two deliberately semantics-free symbol maps. Both correctly remain empty:

- `table`: map marker ignored;
- `observation`: map marker consumed.

This checks schema dispatch only. The marker is synthetic test input, explicitly
not a synthetic scientific account or numerical acceptance fixture. It supplies
no data, Reasoner, authority, runtime or service context.

The attempt to adapt the actual parsed derivative through `NamespaceSyntaxImpl`
remains **BLOCKED**: the missing language-validation scope causes a null-scope
descriptor lookup. This is a harness limitation, not demonstrated invalidity of
the original scientific concepts. No fake descriptor or mocked semantic approval
was inserted to make it pass.

The compiled tuple uses the older Resources-package `LanguageAdapter`; current
source has moved it to core services. Actual loaded JAR locations/hashes are
recorded. The compiled builder JAR is not asserted byte-equivalent to upstream.
Initial probe compilation failures (wrong adapter package, then abstract namespace
class) and the subsequent null-scope result are retained, not counted as model
failures. Probe exit 0 means its diagnostics/control checks completed, not an account pass.

## Classification: newer upstream evidence, no automatic migration

The previously selected worldview remains `d6c37c0b82afadd004d94bc5311983b66677532c`.
A read-only remote-head check found newer upstream
`1328000fff63a1951465b1f7b130c0af15264c95`. Its actual `src/land.kwv` now declares
`land:LandCover` requiring `klab.authority.land` in `ENTRY_ONLY` mode, domain LULC,
with a CORINE catalog/codelist binding.

This corrects any impression that all modern worldviews lack a land-cover binding.
It does NOT establish a CCI encoding crosswalk for our 2020 input. The inspected
authority documentation labels its seed catalogs tentative and distinguishes entry
identity from approved interpretation/mediation. A search of the selected local
Maven tree found no matching land-authority JAR; that is not proof about other
component caches or deployments. No new worldview or authority was loaded.

Switching the account to the independently available CORINE 2018 accounts-ready
resource would be an explicit different fixture choice, not recovery of this CCI
2020 account. It still would not repair the static-table/report path. No such
switch or scientific equivalence assertion was made.

## Decision and smallest repair gate

The first pass left static-table support merely unverified. This pass establishes
that the **inspected standard Java path lacks handling required by the original
static account**, even after the narrow syntax obstacle is removed. That is a
concrete repair/integration requirement, not another Python API gap.

Before live execution, provide either:

1. an existing supported component/submission path that actually compiles this
   static table and returns per-class km² values, with exact version and contracts;
   or
2. separately authorized, narrowly scoped Java/component work for the account
   aggregation/report path, reviewed independently of scientific acceptance.

Either path also needs approved classification bindings, verified cell-area/mask
conventions, permissions and input-unit metadata handling. Do not silently patch
the TIFF's inconsistent/missing units or count Python area sums as Java computation.
An alternative Java area model plus Python presentation could be a separately
defined workflow, but would not establish that the original SEEA table executes.

No live service work was attempted after these gates failed. All owned parser/
Groovy processes exited; no background service or visible terminal was launched.
Existing services and user terminals were untouched.

## Evidence

External root:
`C:/Users/lumsd/AppData/Local/Temp/opencode/seea-extent-attempt-2-20261101/`.

- `static-adaptation.json`, original/adapted subset ZIPs and all four parser results/logs.
- `parser-commands.json`: exact installed Java, dependency argfiles and probe commands.
- `static-contract-final.json`: loaded JAR hashes, unresolved semantic adaptation,
  dispatch controls, exact command and probe hash; earlier failed attempts retained.
- `current-source-contracts.json`: upstream commit, source blobs/hashes and relevant
  method bodies; selected/latest worldview evidence with source hashes.
- `upstream-land.kwv`: external unchanged latest land namespace snapshot.
- Earlier real input/reference under `seea-extent-evidence-20261101/`, including
  SHA-pinned raster, retrieval receipt and independently crosschecked area reference.

No new executable production contribution was made; no full Python regression run
was warranted or claimed for this documentation-only increment.
