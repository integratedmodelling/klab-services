# Short feature walkthroughs

> **Rejected first presentation, retained for traceability.** These slide-based
> clips are no longer linked as release demos. They did not show the user's
> workflow clearly enough. See the [replacement direction](../../demo-plan.md).

Five **18-second, 1280×720 H.264 MP4s**, with animated GIF alternatives and PNG
posters. All five MP4s were decoded successfully after encoding. The complete
media directory is approximately 1.1 MB; no private logs or TEST credentials are
included. The [manifest](manifest.json) records video hashes, actual functional
report hashes, the tested SDK source fingerprint, server checkout and component.

| Feature | Video | Animated alternative |
| --- | --- | --- |
| Connection providers and typed computation | [MP4](connect-and-compute.mp4) | [GIF](connect-and-compute.gif) |
| Secret-free saved work and fresh-process recovery | [MP4](save-and-resume.mp4) | [GIF](save-and-resume.gif) |
| Native exports and faithful numeric rasters | [MP4](native-exports.mp4) | [GIF](native-exports.gif) |
| xarray analysis and partial recorded origins | [MP4](analyze-and-trace.mp4) | [GIF](analyze-and-trace.gif) |
| Job outcomes, cancellation and access denial | [MP4](jobs-and-access.mp4) | [GIF](jobs-and-access.gif) |

## What these recordings mean

These are **rendered result walkthroughs**, not recordings of a terminal, desktop
or notebook UI. Each pairs a clearly labeled API sketch with facts from actual
passing functional workflows. The raster animation inspects the real exported
TIFF and its validity/coordinates; the graph rows show actual returned links.
They are not generated sample values pretending to be a service response.

Service waiting time is omitted. Playback duration and animation speed are not
performance measurements. The services use the explicitly controlled TEST
authority and synthetic known-answer scientific models, not a production Hub or
an independently certified terrain provider. Partial lineage and unknown revision
consistency remain visible even after successful tests.

## Reproduce

From `python-client/`, install optional developer tools:

```powershell
python -m pip install '.[dev,local,analysis,demo]'
```

Run the owned exporter-enabled stack with the pinned corrected component and these
additional suites (all were selected in the recorded `polish-live1` run):

```text
--functional-suite connection-onboarding
--functional-suite connection-readiness
--functional-suite connection-access
--functional-suite typed-compute
--functional-suite resume-saved-work
--functional-suite compute-and-analyze
--functional-suite geotiff-export
--functional-suite scientific-analysis-provenance
```

`scientific-analysis-provenance` also includes the new `release-notebook` journey:
certificate-based computation and token-provider restoration in a fresh process.
The stack runner additionally executes the existing GeoJSON export regression.
See [exact invocation and validation](../../polish-review.md).

After owned cleanup has succeeded, render into a **fresh** directory:

```powershell
python tools/render_release_demos.py --reports '<owned-state>/functional.xml' '<owned-state>/exports.xml' --run-report '<owned-state>/run-report.json' --raster '<owned-state>/analysis-provenance-<run>/field.tif' --output '<fresh-media-directory>'
```

Use the actual TIFF whose SHA-256 matches the accepted notebook workflow. The
renderer rejects failed/skipped/empty or incomplete evidence, changed SDK sources,
unconfirmed cleanup, missing feature evidence and mismatched raster bytes. It
requires both credential paths, saved identity checks, real numerical checks,
confirmed cancellation and authorization positive/negative controls. No public
network or desktop capture is needed during rendering.

The recorded run used external state under
`C:/Users/lumsd/AppData/Local/Temp/opencode/polish-live1`. That directory contains
private TEST enrollment/configuration material and **must not be published**.
The clips and manifest are safe selected outputs, not a copy of that directory.

[Back to branch preview](../../release-preview.md)
