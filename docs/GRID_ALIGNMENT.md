# Context-wide grid anchoring

For nonrectangular shapes, covered-cell traversal and contextualizer cell access follow the
[shape coverage contract](GRID_COVERAGE.md).

A digital twin can install one named k.IM `define grid` instruction. The runtime resolves it once,
freezes its coordinate lattice, and applies it to new observations with areal spatial geometry
before resolution and storage allocation. Original shape coverage and non-spatial dimensions are
retained. Existing observations are never regridded by installing an instruction.

```kim
private namespace examples.grids version 1.0;

define grid conform_50m as {
    projection: "EPSG:4326"
    anchor: "POINT (21.910616123751442 38.79364335500986)"
    span: 50.m
    strict: false
    snap: true
};
```

The anchor is **plain point WKT in the separately declared projection**. Do not repeat `EPSG:...`
in the WKT. The projection defaults to `EPSG:4326`. For a projected lattice, use native projected
coordinates, for example `projection: "EPSG:32634"` with `anchor: "POINT (500000 4200000)"`.
The anchor establishes cell vertices even when it is outside an observation's bounds.

| Field | Meaning |
| --- | --- |
| `projection` | Working CRS, default `EPSG:4326`. |
| `anchor` | Required, nonempty plain point WKT in working-CRS coordinates. |
| `span` | Required, positive finite number or length/angular quantity. A number uses CRS units. Decimal quantities such as `50.5.m` are supported. |
| `strict` | Default `true`. All observations use the exact **resolved** native X/Y steps. |
| `snap` | Default `true`. In a supported global CRS, move the anchor to the nearest world lattice vertex and report the adjustment. `false` preserves the exact anchor. |
| `resolutions` | Optional k.IM list of native numbers or quantities, including `span`, ordered into an integer nesting hierarchy. |

Unknown fields, invalid geometries, nonpositive spans, incompatible physical dimensions, and
non-nested resolution hierarchies are validation errors. Unit dimensional mismatches between a
length and angular CRS cause warnings rather than rejection. Conversion estimates ground spacing
at the anchor using the ellipsoid's meridian and parallel radii, once per instruction. It does not
recompute spacing for each observation. At a pole, where longitude has zero ground length,
metric longitude spacing uses the adjacent row midpoint and reports this additional approximation. A quantity in the same physical dimension as the CRS is
converted into the native axis units normally. The resolved lattice may have different native
X/Y steps and does not imply equal ground areas everywhere.

k.IM namespace validation reports these errors and warnings at the definition. The same warnings
are carried in the runtime's resolved DTO, including actual anchor adjustments.

## Resolution normalization

Bounds snap outward to cell edges, preserving coverage. `strict: true` also turns an areal object
smaller than one cell into the covering cell or cells required by its position relative to vertices.
An area straddling a vertex can need multiple cells even when its width and height are smaller
than the span.

With `strict: false`, the runtime compares the **resulting cell counts after outward snapping**
at all available hierarchy levels and chooses the closest to the originally intended cell count.
Equal errors prefer the finer level. Default levels are binary subdivisions/multiples of `span`,
from `2^-20` to `2^20`, limited by representability and the global rules below. Native steps are
rounded to 12 significant decimal digits before global normalization. Custom levels are useful
when particular resolutions must be available:

```kim
define grid nested_projected as {
    projection: "EPSG:32634"
    anchor: "POINT (500000 4200000)"
    span: 50.m
    strict: false
    resolutions: (10.m 50.m 100.m)
};
```

Here successive ratios are 5 and 2; every coarse cell comprises an integer number of finer cells.
Alignment of geometries does not eliminate aggregation/refinement of values between resolutions.
Storage retains its explicit sampling and mediation policy.

## World boundaries

Geographic CRSs use a complete longitude turn and the interval between the poles: 360 by 180
in degrees. EPSG:3857 uses its finite standard square domain (longitude +/-180 and latitude
approximately +/-85.05113); Mercator cannot represent the poles. Other projected CRSs use their
ordinary local planar lattice.

Global native steps are normalized to divide the world extents into whole cells. With `snap: true`,
the anchor is snapped to that lattice. Levels whose cell edges cannot also align with both world
edges and the snapped anchor are removed. With `snap: false`, longitude still divides a complete
turn, but the phase remains anchored exactly; polar/projection-limit rows may be partial. Coarse
levels that do not divide the complete world period are removed. The closest-cell-count rule
operates on the retained hierarchy, which can have fewer coarse levels than a local grid.

A boundary grid has uniform **logical** bounds and cell indices, plus a `world=[west east south
north]` geometry parameter. `Grid.getCellBounds(x,y)` returns its physical rectangular footprint(s):
longitude wraps and latitude clips to the declared world limits. A seam cell can have two footprints
but is stored once. Full-turn grids cap their longitude count at exactly one turn, preventing
repeated seam cells. The logical envelope can extend outside canonical world coordinates;
consumers rendering or exporting cells must use their physical footprints.

Storage scan description version 6 records the new boundary semantics through the geometry of
its shards and partitions. Exact scans rotate periodic cell indices losslessly. Conservative
sampling uses clipped physical areas; extensive totals use the clipped source area as their
normalization denominator. Bilinear sampling uses clipped row centres and retains strict validity
when its kernel has absent support. Crossing into another CRS retains the existing restriction to
its valid nonsingular transform domain; wrapping does not make Mercator represent polar areas.
Ordinary scan descriptions retain their existing versions.

## Installation, IDE and API

In the IDE, drag a `define grid` from the workspace tree onto the digital twin control panel before
making observations. Its preview says that the instruction applies to the whole twin. Successful
installation produces an informational notification and any normalization warnings. The context
button's tooltip displays the installed URN, CRS, resolved steps, strictness and anchor policy.
The automatically prepared universal user observer has no spatial cells and does not prevent
configuration of an otherwise fresh twin.

For a new twin, add the definition URN to its configuration:

```java
var configuration = DigitalTwin.Configuration.builder()
    .name("Aligned observations")
    .grid("examples.grids.conform_50m")
    // Other connection, ownership and persistence settings as usual.
    .build();
```

For an existing empty twin, use the same server path as the IDE:

```java
GridAlignment resolved = runtime.configureGrid("examples.grids.conform_50m", context);
GridAlignment metadata = context.getData().get(GridAlignment.SCOPE_KEY, GridAlignment.class);
```

HTTP clients POST `{"definitionUrn":"examples.grids.conform_50m"}` to `/api/v1/grid` with the usual
context authorization headers. The response is the resolved `GridAlignment` DTO. A repeat of the
same URN returns the frozen instruction without re-resolving its current source. A different URN
is rejected; a new instruction cannot be installed after observation registration begins.

The Context node persists the resolved DTO as `gridAlignment`. Registration seals the context's
configuration under the same graph lock used by grid installation. Resuming restores native
steps, anchor, hierarchy, warnings and fingerprint from the graph, without resolving an edited
source definition. Configuration responses and `Scope.getData()` expose that frozen instruction.
`Grid.align(GridAlignment)` is the typed alignment contract. Named geometry `gridurn` definitions
use the same decoder; the empty legacy map constructor has been removed.


Inline instructions use the same fields and normalization rules:

```java
var configuration = DigitalTwin.Configuration.builder()
    .name("Aligned observations")
    .grid(Map.of("projection", "EPSG:32634",
                 "anchor", "POINT (500000 4200000)",
                 "span", 50, "strict", false))
    .build();
```

`span` and `resolutions` accept native numbers, quantity strings such as `"50.m"`,
or `Quantity` values. The builder freezes the map and its lists and assigns a stable
content-derived URN. Configuration transport, copies and resume retain its identity;
the resolved lattice and source code are persisted with the Context as for named grids.
Only one named or inline instruction is allowed per twin.

A named grid can also be inspected independently through the standard Resources API:

```java
GridAlignment grid = resources.retrieve("examples.grids.conform_50m", GridAlignment.class, userScope);
```

This uses the existing Resources `retrieve` endpoint with knowledge class
`GRID_ALIGNMENT`. The asset carries the definition URN, service identity, reconstructed
k.IM code, resolved lattice and warnings. Retrieval does not install it on a twin.


### Suppressing intentional warnings

Place `@suppress` on the definition to suppress its warning notifications:

```kim
@suppress
define grid intended_geographic_grid as {
    projection: "EPSG:4326"
    anchor: "POINT (21.910616123751442 38.79364335500986)"
    span: 50.m
    strict: false
};
```

`@suppress("warnings")` and `@suppress(warnings = true)` are explicit equivalents.
Unknown selectors and `warnings = false` do not suppress warnings. Errors and system
errors are never suppressed; other notification levels are currently unaffected.

Definition annotations survive adaptation to k.LAB syntax, observation construction,
GridAlignment retrieval, JSON transport and Context persistence/resume. Suppression applies
to validation warnings within the annotated declaration and runtime warnings emitted with
that asset. Scope callers should pass the asset alongside the warning, for example
`scope.warn("Intentional normalization", observation)`.

`GridAlignment.warnings()` retains the normalization details for inspection;
`emittedWarnings()` applies the annotation policy. Asset-bound warning notifications retain
their content with `Notification.Mode.Silent`, while validation and channel delivery omit
suppressed warnings. No annotations change the actual lattice normalization.
