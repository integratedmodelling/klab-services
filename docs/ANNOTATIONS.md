# System annotations

Annotations attach named parameters to language declarations. This reference covers source-language annotations, rather than Java extension annotations such as `@Exporter` or `@Verb`. An annotation can be preserved without having a system handler; applications may interpret additional names.

## Propagation to observations

Observations retain annotations from their syntactic `KimObservable` and `KimConcept` definitions, including contributors resolved from documents on other services. Collection visits the main observable first, then predicates in reverse declaration order. Later contributors replace earlier annotations with the same name.

Concept annotations have the lowest precedence. Model dependencies and the contextualizing model override them; model-level annotations apply only to the model's first declared observable. An explicit `define observation ... as {...}` contributes the highest-precedence annotations. Replacement is by annotation name, not a merge of individual parameters.

Known annotations are present at submission. Resolution and contextualization add further contributors while preserving this precedence, and the observation returned when submission completes retains the result.

## Recognized annotations

| Annotation | Context and interpretation |
| --- | --- |
| `@colormap` | Observation visualization; generalized color ramps and exact value colors, specified below. |
| `@test` | k.Actors action participating in testcase execution. |
| `@override` | k.Actors action explicitly replacing an inherited action. |
| `@handle(CONSTANT)` / `@handle(class=CONSTANT)` | k.Actors message handler. Ordinary action parameters receive payload and scope bindings. |
| `@stdin` | k.Actors console input handler. |
| `@return("behavior.urn")` / `@return(urn="behavior.urn")` | k.Actors action declaring the behavior of a returned agent. |
| `@type` | k.Actors argument type constraint; behavior URN or Java class constraint. In runtime storage configuration, selects a storage type. |
| `@adapt` | k.Actors adaptation action. |
| `@split`, `@maxSize`, `@minSplitSize`, `@fillCurve` | Runtime dataflow storage configuration: splitting, buffer size, minimum split size, and filling curve. |

See [the k.Actors reference](AGENTS.md) for action signatures, inheritance, and compiler restrictions. The same annotation name can have different meanings in different declaration contexts.

## `@colormap`

The shared API evaluator is `ColorRamp` in `org.integratedmodelling.klab.api.view.modeler.visualization`. It accepts an observation's effective annotation and returns ARGB colors without depending on a rendering toolkit. The existing `Colormap` class remains a visualization descriptor.

Choose at most one definition mode: `palette`, `colors`, `stops`, or `values`. With no definition, the palette is `viridis`. A single unnamed argument is shorthand for `palette`.

These examples use source-language syntax: lists use **parentheses**, including nested lists. Color strings must be quoted.

```kim
@colormap("viridis")
@colormap(palette="magma", reverse=true)
@colormap(colors=("#001133" "#33aaff" "#ffffff"), min=0, max=100)
@colormap(colors=("red" "white" "green"), center=0)
@colormap(colors=((255 0 0) "#ffffff" (0 128 0)), center=0, min=-2, max=8)
@colormap(stops={-10: "blue", 0: "white", 5: "red"})
@colormap(values=(("land:Forest" "#228b22") ("land:Water" (0 0 255))), unknown="gray")
```

Place the annotation on a contributing concept/observable, model, or observation definition. For example, a definition-level `@colormap` replaces the model's entire `@colormap` specification according to the precedence above.

### Continuous and centered ramps

`colors` supplies at least two colors, spaced evenly along the ramp. Adjacent colors blend linearly in their encoded sRGB and alpha channels; this is not interpolation in a perceptually uniform color space. `palette` selects one of the predefined ramps. `reverse=true` reverses either ramp.

By default, the renderer supplies the finite minimum and maximum of the rendered data. Explicit `min` and `max` must be provided together, must be finite, and must satisfy `min < max`. Values outside the limits clamp to the endpoint colors. Constant data without explicit bounds uses the ramp midpoint.

`center` anchors a numeric value to the ramp midpoint. With explicit bounds, each side scales independently: in the example above, -2 is red, 0 is white, and 8 is green. Without bounds, the range is symmetric around the center and extends far enough to cover both observed extrema. With three colors, the second color is exactly the center color. With two colors, the center receives their blend.

`stops` specifies at least two distinct, finite **data values** and their colors, as a numeric-key map or a list of `(value color)` pairs. Stops are sorted numerically; interpolation occurs between adjacent stops and values outside them clamp. It does not use observed bounds, and cannot be combined with `min`, `max`, or `center`. `reverse` reverses its colors while retaining stop positions.

### Exact value and concept colors

`values` maps individual values to colors without interpolation. Supply a map or a list of `(value color)` pairs. Pair lists permit quoted concept URNs, unlike the source grammar's restricted map keys. Concepts match their canonical URN exactly; numeric values match across numeric representations. There is no semantic subsumption or label matching. Duplicate normalized keys are invalid.

Categorical mode cannot be combined with `min`, `max`, `center`, or `reverse`. `unknown` supplies the color for an unmapped category and defaults to transparent. Null and non-finite numeric values use `nodata`, also transparent by default. For example, `nodata="#ffffff00"` explicitly requests transparent white.

### Color formats

* Web hex: `#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`; alpha is last.
* Integer RGB/RGBA lists: `(255 128 0)` or `(255 128 0 128)`, each channel from 0 through 255.
* Strings `rgb(255,128,0)` and `rgba(255,128,0,0.5)`; functional alpha is from 0 through 1.
* Names: black, white, red, green, blue, lime, yellow, cyan/aqua, magenta/fuchsia, gray/grey, silver, orange, purple, navy, teal, olive, maroon, transparent. CSS `green` is `#008000`; `lime` is `#00ff00`.

Other CSS syntax and color names are not supported. Invalid options, colors, bounds, or incompatible modes raise an error instead of silently selecting a fallback.

### Built-in palettes

| Name | Definition |
| --- | --- |
| `viridis` | Original 256-sample scientific sequential palette, purple through green to yellow; default. |
| `magma` | Original 256-sample sequential palette, dark purple through red to pale yellow. |
| `inferno` | Original 256-sample sequential palette, dark purple through orange to yellow. |
| `plasma` | Original 256-sample sequential palette, purple through pink to yellow. |
| `gray` | Black to white. |
| `red-white-blue` | Red through white to blue. |
| `red-white-green` | Red through white to CSS green. |
| `heat` | Black through red and yellow to white. |
| `terrain` | Bathymetry/topography: deep blue through blue and turquoise to a pale coastal midpoint, then green, tan, brown, and near-white high elevations. Use `center=0` for sea level. |

The four scientific tables retain the original samples from the [BIDS colormap project](https://bids.github.io/colormap/) and its [CC0 source](https://github.com/BIDS/colormap/blob/master/colormaps.py); attribution accompanies the resources. The remaining names are simple ramps, not reproductions of ColorBrewer tables. Diverging ramps with a meaningful center follow the usage described in [ColorBrewer's scheme guidance](https://colorbrewer2.org/learnmore/schemes_full.html). Red/green distinctions may be unsuitable for viewers with color-vision deficiencies.

### Elevation relative to sea level

The custom `terrain` palette combines underwater depths and land elevations in one continuous ramp. Anchor its coastal midpoint explicitly to zero:

```kim
@colormap("terrain", center=0)
```

Negative elevations use the underwater half; positive elevations use the land half. Automatic bounds are symmetric around zero. For consistent colors across maps, specify fixed bounds in the raster's elevation units, for example for elevations in metres:

```kim
@colormap("terrain", center=0, min=-8000, max=4000)
```

Here -8000 is deep blue, 0 is pale coastal sand, 1000 is green, and 4000 is near-white. Each side scales independently and values outside the bounds clamp. The nine equally spaced colors are `#081d58`, `#225ea8`, `#1d91c0`, `#7fcdbb`, `#e8e6b5`, `#78a75a`, `#b5a16b`, `#896a52`, and `#f5f5f5`. This is a custom palette, not a reproduction of another library's palette named terrain.

The palette does not convert units or vertical datums: input zero must represent the intended sea-level reference. It colors elevation rather than classifying water, so inland depressions below sea level also receive underwater colors. Without `center=0`, `terrain` behaves like any other relative palette and its midpoint need not coincide with sea level. Missing values remain transparent by default.

### Geospatial PNG rendering

Exporting through Runtime discovers missing exporters through Resources using the media-type query endpoint, loads the providing component, and refreshes capabilities before dispatch. An installed dependency is checked for updates from its source service before export. The numeric and keyed implementations share the `png` schema but retain separate Java method bindings. Request viewport parameters are passed to the selected implementation, and float storage can be adapted to its numeric scanner.

The renderer consumes the resolved or reloaded observation's annotations: it does not re-read source definitions. Annotation parameters and precedence therefore survive service JSON transport and knowledge-graph persistence. A missing exporter is reported as a discovery/installation failure, rather than an authorization failure; actual permission failures remain distinct.

When verifying a rebuilt renderer, package and install the geospatial **component** artifact (`mvn install`), not just its classes or ordinary JAR. The deployable artifact has classifier `component` and extension `.kar`; services install it as a plugin JAR. An already running service may still hold the previous component until its update is applied. The annotation-aware component includes `RasterRenderer` and both numeric and keyed PNG exporters. Grey output without an explicit grey palette is a reason to check the loaded component: the current unannotated default is viridis.

Changes to source annotations apply to newly resolved observations. Existing observations retain their stored annotation snapshot; rebuilding or updating a renderer does not retroactively add missing annotation parameters. After changing parser behavior or a model annotation, reparse the document and create a new observation when checking the full source-to-render path.

The sibling `klab.component.geospatial` component's `png` exporter uses the rendered observation's effective `@colormap`. Continuous rasters default to viridis and derive automatic bounds from the selected raster, not from all observations or an entire time series. Keyed rasters require `values`; their storage codes are translated through the observation's `DataKey` before color lookup.

The renderer preserves geographic envelope aspect ratio within `viewportX` and `viewportY` (800 pixels each by default), uses nearest-neighbor spatial sampling, and preserves alpha in PNG output. Color interpolation affects numeric values, not spatial category boundaries. Non-finite samples use `nodata`. Raster data exports such as GeoTIFF are separate from this PNG visualization contract.
