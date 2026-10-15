# Work with k.LAB from Python

**Development preview · October 2026 · Python 3.11+**

Ask k.LAB for an observation, bring the result into Python, and keep working with
the tools you already use. You can also save a reference to your work and pick it
up in another Python process without submitting it again.

This page covers the Python-client branch, not a published release. The package
version is still `0.1.0`.

## Watch it in Python

The [seven notebook walkthroughs](notebook-videos.md) cover connecting, requesting
observations, controlling Jobs, reopening saved work, exporting files, analyzing
results and sharing access. Each comes with an executed notebook you can inspect
and download. The videos use actual Jupyter execution on controlled TEST services,
not slide animations.

## Ask for an observation

Once you have connected to a deployment and created a spatial context, request
what you want to observe:

```python
job = context.observe("geography:Elevation in m")
elevation = job.result(timeout=120)
```

The work runs on k.LAB. Python gives you the Job and its result. You can choose
the area and grid using typed requests rather than assembling service payloads
by hand.

[Connect to a deployment](connection-layer.md) ·
[Choose an area and submit a request](typed-observe.md)

## Take the result into your analysis

Export a supported numerical result as a GeoTIFF and open it as an xarray Dataset:

```python
from klab_client import export_for_analysis, open_raster

result = open_raster(export_for_analysis(elevation, "elevation.tif"))
ds = result.dataset
elevation_m = ds.value.where(ds.valid)
elevation_m.plot()
```

From here, use xarray to select an area, inspect values or calculate statistics.
Plotting uses ordinary Python tools; there is no separate k.LAB plotting API to
learn. Missing cells have their own validity mask, so a real zero stays a real zero.

Install the optional readers with `pip install '.[analysis]'`; plotting also needs
Matplotlib. The current reader supports single-band numerical rectilinear rasters,
not every kind of scientific result.

[Analysis guide](scientific-analysis.md) · [Native file exports](stream-exports.md)

## Pick up where you left off

Save a reference before closing Python:

```python
from klab_client import save_handle

save_handle(elevation, "elevation.json")
```

In your next process, reconnect and retrieve the same observation:

```python
from klab_client import load_handle

elevation = client.restore_handle(load_handle("elevation.json"))
```

The file contains the reference, not your credentials or a copy of the data.
The server still needs to retain the work, and your current identity must still
have access. Reopening it does not run the computation again.

[Saved-work guide](saved-handles.md)

## Look at the work recorded behind a result

```python
from klab_client import fetch_provenance

view = fetch_provenance(elevation)
for link in view.links:
    print(link.relationship, link.source.id, link.target.id)
```

You can inspect the relationships and activities the server exposes around that
observation. This is a partial view, not a complete history or a recipe for
reproducing the result.

[What the provenance view includes](scientific-analysis.md#read-only-recorded-provenance)

The notebook examples use a small synthetic grid, not a real terrain dataset.
A richer real-world example needs a suitable model and data on a connected
deployment, not just a more elaborate animation.

## What is on this branch

| Work | What you can do |
| --- | --- |
| [#84](https://github.com/integratedmodelling/klab-services/pull/84) | Use the synchronous Client, sessions, contexts, Jobs and cell reads |
| [#85](https://github.com/integratedmodelling/klab-services/pull/85) | Describe supported requests with typed geometry and options |
| [#86](https://github.com/integratedmodelling/klab-services/pull/86) | Save references and reopen work |
| [#92](https://github.com/integratedmodelling/klab-services/pull/92) | Download advertised native file formats |
| [#93](https://github.com/integratedmodelling/klab-services/pull/93) | Connect through profiles and credential providers; inspect service readiness |
| [#94](https://github.com/integratedmodelling/klab-services/pull/94) | Open supported rasters in xarray, inspect recorded origins, and improve reliability, documentation and notebook examples |

These contributions target `feature/python-client`, not necessarily `develop`.
PR #94 contains the complete scientific-analysis/provenance and polish pass;
this remains a branch preview, not a tagged package release. The branch also
carries a [correction for the separate GeoTIFF component](../component-patches/geospatial/README.md);
it is not an upstream component release.

## What comes next

- A useful real-world notebook example on a suitable deployment.
- Genuine Hub onboarding and remaining separate-deployment identity/access checks.
- Better discovery of available observations and resources.
- More scientific result types as the necessary metadata and service contracts become available.

## Details for reviewers

The controlled tests use TEST services and known-answer synthetic models. They
do not certify production onboarding. The separate server integration
[#90](https://github.com/integratedmodelling/klab-services/pull/90) remains open and
is not claimed as delivered here. Saved work does not promise server-crash
recovery; exports and provenance reads do not share a revision-pinned snapshot.

The reliability pass bounds cell-read input, checks Runtime identity before
release, keeps renewed credentials out of saved references, and preserves the
original error if local cleanup also fails. See the [current corrections and
verification](review-corrections.md), [branch review](polish-review.md),
[API inventory](public-api.md) and [functional suites](functional-testing.md) for
the exact behavior, tested versions and remaining limits.
